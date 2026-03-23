package net.far.resolver.core;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.far.resolver.model.*;
import net.far.resolver.model.query.Page;
import net.far.resolver.model.query.Query;
import net.far.resolver.spi.FarClient;

/** Handles delegation to peer FAR servers for namespaces not handled locally. */
@ApplicationScoped
public class Delegator {

  private static final Logger LOGGER = Logger.getLogger(Delegator.class.getName());

  private final PeerRegistry peers;
  private final FarClient client;
  private final String identity;
  private final int limit;
  private final Breaker breaker;

  @Inject
  public Delegator(final PeerRegistry peers) {
    this.peers = peers;
    this.client = null;
    this.identity = "";
    this.limit = 5;
    this.breaker = new Breaker();
  }

  public Delegator(
      final PeerRegistry peers, final FarClient client, final String identity, final int limit) {
    this.peers = peers;
    this.client = client;
    this.identity = identity;
    this.limit = limit;
    this.breaker = new Breaker();
  }

  static String canonical(final String uri) {
    try {
      final var parsed = URI.create(uri);
      final var scheme = parsed.getScheme() != null ? parsed.getScheme().toLowerCase() : "";
      final var host = parsed.getHost() != null ? parsed.getHost().toLowerCase() : "";
      final var port = parsed.getPort();
      final var omit =
          port < 0
              || ("https".equals(scheme) && port == 443)
              || ("http".equals(scheme) && port == 80);
      final var authority = omit ? host : host + ":" + port;
      var path = parsed.getPath() != null ? parsed.getPath() : "";
      while (path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      return scheme + "://" + authority + path;
    } catch (final Exception ignored) {
      return uri;
    }
  }

  /** Attempt to delegate resolution of a URN to a peer. */
  public Optional<Resolution> delegate(final Urn urn, final List<String> chain) {
    return delegate(urn, chain, null);
  }

  /** Attempt to delegate resolution of a URN to a peer, forwarding a bearer token. */
  public Optional<Resolution> delegate(
      final Urn urn, final List<String> chain, final String token) {
    if (loop(chain)) {
      throw new DelegationLoopException(identity);
    }

    if (chain.size() >= limit) {
      throw new DelegationException("Delegation depth limit exceeded: " + limit);
    }

    final var candidates = peers.find(urn.namespace());
    if (candidates.isEmpty()) {
      return Optional.empty();
    }

    final var extended = new ArrayList<>(chain);
    extended.add(identity);

    DelegationException last = null;
    var attempted = false;
    for (final var peer : candidates) {
      if (breaker.open(peer.identity())) {
        LOGGER.warning(() -> "Breaker open for peer: " + peer.identity());
        continue;
      }
      if (chain.size() + 1 > peer.depth()) {
        continue;
      }
      try {
        if (client == null) {
          continue;
        }
        attempted = true;
        final var result = client.resolve(peer.base(), urn, extended, token);
        if (result.isPresent()) {
          breaker.success(peer.identity());
          final var resolution = result.get();
          return Optional.of(
              new Resolution(
                  resolution.urn(),
                  resolution.namespace(),
                  resolution.identifier(),
                  resolution.attributes(),
                  resolution.integrity(),
                  resolution.resolver(),
                  resolution.status(),
                  resolution.timestamp(),
                  true));
        }
      } catch (final DelegationException exception) {
        LOGGER.warning(
            () -> "Delegation failed to " + peer.identity() + ": " + exception.getMessage());
        last = exception;
        breaker.failure(peer.identity());
      }
    }

    if (attempted && last != null) {
      throw last;
    }
    return Optional.empty();
  }

  /** Delegate a parametric search query to peers that support the given namespaces. */
  public Page query(
      final Query query,
      final List<String> namespaces,
      final List<String> chain,
      final String token) {
    if (loop(chain)) {
      throw new DelegationLoopException(identity);
    }

    if (chain.size() >= limit) {
      throw new DelegationException("Delegation depth limit exceeded: " + limit);
    }

    if (client == null) {
      return new Page(List.of(), 0, query.skip(), query.top());
    }

    final var extended = new ArrayList<>(chain);
    extended.add(identity);

    final var candidates =
        namespaces.stream()
            .flatMap(namespace -> peers.find(namespace).stream())
            .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));

    final var results = new ArrayList<Resolution>();

    for (final var peer : candidates) {
      if (breaker.open(peer.identity())) {
        continue;
      }
      try {
        final var scope = new HashSet<>(peer.namespaces());
        scope.retainAll(new HashSet<>(namespaces));
        if (scope.isEmpty()) {
          continue;
        }
        final var scoped =
            new Query(scope, query.filter(), query.top(), query.skip(), query.orderby());
        final var page = client.query(peer.base(), scoped, extended, token);
        results.addAll(page.value());
        breaker.success(peer.identity());
      } catch (final DelegationException exception) {
        LOGGER.warning(
            () ->
                "Federated query to "
                    + peer.identity()
                    + " failed: "
                    + exception.getMessage());
        breaker.failure(peer.identity());
      }
    }

    return new Page(results, results.size(), query.skip(), query.top());
  }

  /** Check if a URN exists via peer delegation. */
  public boolean exists(final Urn urn, final List<String> chain) {
    if (loop(chain)) {
      throw new DelegationLoopException(identity);
    }
    if (chain.size() >= limit) {
      throw new DelegationException("Delegation depth limit exceeded: " + limit);
    }
    final var candidates = peers.find(urn.namespace());
    if (candidates.isEmpty() || client == null) {
      return false;
    }
    final var extended = new ArrayList<>(chain);
    extended.add(identity);
    for (final var peer : candidates) {
      if (breaker.open(peer.identity())) {
        continue;
      }
      if (chain.size() + 1 > peer.depth()) {
        continue;
      }
      try {
        final var found = client.exists(peer.base(), urn, extended);
        breaker.success(peer.identity());
        if (found) {
          return true;
        }
      } catch (final DelegationException exception) {
        breaker.failure(peer.identity());
      }
    }
    return false;
  }

  public Optional<History> history(final Urn urn, final List<String> chain, final String token) {
    if (loop(chain)) {
      throw new DelegationLoopException(identity);
    }
    if (chain.size() >= limit) {
      throw new DelegationException("Delegation depth limit exceeded: " + limit);
    }
    final var candidates = peers.find(urn.namespace());
    if (candidates.isEmpty() || client == null) {
      return Optional.empty();
    }
    final var extended = new ArrayList<>(chain);
    extended.add(identity);
    for (final var peer : candidates) {
      if (breaker.open(peer.identity())) {
        continue;
      }
      if (chain.size() + 1 > peer.depth()) {
        continue;
      }
      try {
        final var result = client.history(peer.base(), urn, extended, token);
        if (result.isPresent()) {
          breaker.success(peer.identity());
          return result;
        }
      } catch (final DelegationException exception) {
        breaker.failure(peer.identity());
      }
    }
    return Optional.empty();
  }

  public Optional<Rendition> rendition(
      final Urn urn, final String media, final List<String> chain, final String token) {
    if (loop(chain)) {
      throw new DelegationLoopException(identity);
    }
    if (chain.size() >= limit) {
      throw new DelegationException("Delegation depth limit exceeded: " + limit);
    }
    final var candidates = peers.find(urn.namespace());
    if (candidates.isEmpty() || client == null) {
      return Optional.empty();
    }
    final var extended = new ArrayList<>(chain);
    extended.add(identity);
    for (final var peer : candidates) {
      if (breaker.open(peer.identity())) {
        continue;
      }
      if (chain.size() + 1 > peer.depth()) {
        continue;
      }
      try {
        final var result = client.rendition(peer.base(), urn, media, extended, token);
        if (result.isPresent()) {
          breaker.success(peer.identity());
          return result;
        }
      } catch (final DelegationException exception) {
        breaker.failure(peer.identity());
      }
    }
    return Optional.empty();
  }

  public String identity() {
    return identity;
  }

  private boolean loop(final List<String> chain) {
    final var self = canonical(identity);
    for (final var entry : chain) {
      if (self.equals(canonical(entry))) {
        return true;
      }
    }
    return false;
  }
}
