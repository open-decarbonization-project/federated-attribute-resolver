package net.far.resolver.core;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.far.resolver.model.History;
import net.far.resolver.model.IdentifierNotFoundException;
import net.far.resolver.model.NamespaceNotFoundException;
import net.far.resolver.model.Rendition;
import net.far.resolver.model.Resolution;
import net.far.resolver.model.UnsupportedFormatException;
import net.far.resolver.model.Urn;
import net.far.resolver.model.Value;
import net.far.resolver.model.query.Page;
import net.far.resolver.model.query.Query;

/** Main resolution engine. Tries local drivers first, then delegates to peers. */
@ApplicationScoped
public class Resolver {

  private final Router router;
  private final Delegator delegator;
  private final PeerRegistry peers;

  @Inject
  public Resolver(final Router router, final Delegator delegator, final PeerRegistry peers) {
    this.router = router;
    this.delegator = delegator;
    this.peers = peers;
  }

  private static Comparable<?> extract(final Resolution resolution, final String field) {
    final var attribute = resolution.attributes().get(field);
    if (attribute == null || attribute.value() == null) {
      return null;
    }
    return switch (attribute.value()) {
      case Value.Text text -> text.raw();
      case Value.Numeric numeric -> numeric.raw().doubleValue();
      case Value.Quantity quantity -> quantity.amount().doubleValue();
      case Value.Temporal temporal -> temporal.raw();
      case Value.Bool bool -> bool.raw();
      case Value.Arr arr -> null;
      case Value.Record record -> null;
    };
  }

  @SuppressWarnings("unchecked")
  private static int compare(final Comparable<?> left, final Comparable<?> right) {
    try {
      return ((Comparable<Object>) left).compareTo(right);
    } catch (final ClassCastException exception) {
      return 0;
    }
  }

  /** Resolve a URN to its attribute data. */
  public Resolution resolve(final Urn urn) {
    return resolve(urn, List.of());
  }

  /** Resolve a URN with an existing delegation chain. */
  public Resolution resolve(final Urn urn, final List<String> chain) {
    return resolve(urn, chain, null);
  }

  /** Resolve a URN with delegation chain and bearer token forwarding. */
  public Resolution resolve(final Urn urn, final List<String> chain, final String token) {
    final var driver = router.route(urn.namespace());
    if (driver.isPresent()) {
      return driver
          .get()
          .resolve(urn)
          .orElseThrow(() -> new IdentifierNotFoundException(urn.toString()));
    }

    return delegator
        .delegate(urn, chain, token)
        .orElseThrow(() -> new NamespaceNotFoundException(urn.namespace()));
  }

  /** Get certificate history. */
  public History history(final Urn urn) {
    return history(urn, List.of(), null);
  }

  /** Get certificate history, forwarding a bearer token to peers. */
  public History history(final Urn urn, final String token) {
    return history(urn, List.of(), token);
  }

  /** Get certificate history with delegation chain and bearer token. */
  public History history(final Urn urn, final List<String> chain, final String token) {
    final var driver = router.route(urn.namespace());
    if (driver.isPresent()) {
      return driver
          .get()
          .history(urn)
          .orElseThrow(() -> new IdentifierNotFoundException(urn.toString()));
    }
    return delegator
        .history(urn, chain, token)
        .orElseThrow(() -> new NamespaceNotFoundException(urn.namespace()));
  }

  /** Check if a URN exists. */
  public boolean exists(final Urn urn) {
    return exists(urn, List.of());
  }

  /** Check if a URN exists with delegation chain. */
  public boolean exists(final Urn urn, final List<String> chain) {
    final var driver = router.route(urn.namespace());
    if (driver.isPresent()) {
      return driver.get().exists(urn);
    }
    return delegator.exists(urn, chain);
  }

  /** Fetch a pre-formatted rendition from the upstream registry. */
  public Rendition rendition(final Urn urn, final String media) {
    return rendition(urn, media, List.of(), null);
  }

  /** Fetch a pre-formatted rendition, forwarding a bearer token to peers. */
  public Rendition rendition(final Urn urn, final String media, final String token) {
    return rendition(urn, media, List.of(), token);
  }

  /** Fetch a pre-formatted rendition with delegation chain and bearer token. */
  public Rendition rendition(
      final Urn urn, final String media, final List<String> chain, final String token) {
    final var driver = router.route(urn.namespace());
    if (driver.isPresent()) {
      return driver
          .get()
          .rendition(urn, media)
          .orElseThrow(() -> new UnsupportedFormatException(media));
    }
    return delegator
        .rendition(urn, media, chain, token)
        .orElseThrow(() -> new NamespaceNotFoundException(urn.namespace()));
  }

  /** Execute a parametric search query across local drivers and peers. */
  public Page query(final Query query, final List<String> chain, final String token) {
    final var results = new ArrayList<Resolution>();
    final Set<String> targets;
    if (query.namespaces().isEmpty()) {
      final var combined = new HashSet<>(router.namespaces());
      combined.addAll(peers.namespaces());
      targets = combined;
    } else {
      final var known = new HashSet<>(router.namespaces());
      known.addAll(peers.namespaces());
      for (final var namespace : query.namespaces()) {
        if (!known.contains(namespace)) {
          throw new NamespaceNotFoundException(namespace);
        }
      }
      targets = query.namespaces();
    }
    final var local = new ArrayList<String>();
    final var remote = new ArrayList<String>();
    for (final var namespace : targets) {
      if (router.route(namespace).isPresent()) {
        local.add(namespace);
      } else {
        remote.add(namespace);
      }
    }
    final var federated = local.size() + remote.size() > 1 || !remote.isEmpty();
    final var window =
        federated
            ? new Query(
                query.namespaces(), query.filter(), query.top() + query.skip(), 0, query.orderby())
            : query;

    for (final var namespace : local) {
      final var driver = router.route(namespace);
      if (driver.isPresent()) {
        final var scoped =
            new Query(
                Set.of(namespace), window.filter(), window.top(), window.skip(), window.orderby());
        final var page = driver.get().query(scoped);
        results.addAll(page.value());
      }
    }

    if (!remote.isEmpty()) {
      final var delegated = delegator.query(window, remote, chain, token);
      for (final var resolution : delegated.value()) {
        if (targets.contains(resolution.namespace())) {
          results.add(resolution);
        }
      }
    }

    sort(results, query.orderby());
    final var total = results.size();
    final var skip = Math.min(query.skip(), total);
    final var end = Math.min(skip + query.top(), total);
    final var page = results.subList(skip, end);
    return new Page(page, total, query.skip(), query.top());
  }

  private void sort(final List<Resolution> results, final String orderby) {
    if (orderby == null || orderby.isBlank()) {
      return;
    }
    final var parts = orderby.trim().split("\\s+");
    final var field = parts[0];
    final var descending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);

    final Comparator<Resolution> comparator =
        (final Resolution a, final Resolution b) -> {
          final var left = extract(a, field);
          final var right = extract(b, field);
          if (left == null && right == null) return 0;
          if (left == null) return 1;
          if (right == null) return -1;
          return compare(left, right);
        };

    results.sort(descending ? comparator.reversed() : comparator);
  }
}
