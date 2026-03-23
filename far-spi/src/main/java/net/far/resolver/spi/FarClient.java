package net.far.resolver.spi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.far.resolver.model.History;
import net.far.resolver.model.Rendition;
import net.far.resolver.model.Resolution;
import net.far.resolver.model.Urn;
import net.far.resolver.model.query.Page;
import net.far.resolver.model.query.Query;

/** Client interface for communicating with peer FAR servers. */
public interface FarClient {

  /** Resolve a URN via a peer server. */
  default Optional<Resolution> resolve(
      final String endpoint, final Urn urn, final List<String> chain) {
    return resolve(endpoint, urn, chain, null);
  }

  /** Resolve a URN via a peer server, forwarding a bearer token if present. */
  Optional<Resolution> resolve(
      final String endpoint, final Urn urn, final List<String> chain, final String token);

  /** Get history via a peer server. */
  default Optional<History> history(final String endpoint, final Urn urn) {
    return history(endpoint, urn, List.of(), null);
  }

  /** Get history via a peer server, forwarding a bearer token if present. */
  default Optional<History> history(final String endpoint, final Urn urn, final String token) {
    return history(endpoint, urn, List.of(), token);
  }

  /** Get history via a peer server with delegation chain and bearer token. */
  Optional<History> history(
      final String endpoint, final Urn urn, final List<String> chain, final String token);

  /** Check existence via a peer server. */
  default boolean exists(final String endpoint, final Urn urn) {
    return exists(endpoint, urn, List.of());
  }

  /** Check existence via a peer server with delegation chain. */
  boolean exists(final String endpoint, final Urn urn, final List<String> chain);

  /** Fetch a pre-formatted rendition from a peer server. */
  default Optional<Rendition> rendition(final String endpoint, final Urn urn, final String media) {
    return rendition(endpoint, urn, media, List.of(), null);
  }

  /** Fetch a pre-formatted rendition from a peer server, forwarding a bearer token if present. */
  default Optional<Rendition> rendition(
      final String endpoint, final Urn urn, final String media, final String token) {
    return rendition(endpoint, urn, media, List.of(), token);
  }

  /** Fetch a pre-formatted rendition with delegation chain and bearer token. */
  Optional<Rendition> rendition(
      final String endpoint,
      final Urn urn,
      final String media,
      final List<String> chain,
      final String token);

  /** Fetch the FAR configuration from a peer. */
  Optional<Map<String, Object>> configuration(final String endpoint);

  /** Query a peer server with a parametric filter. */
  Page query(
      final String endpoint, final Query query, final List<String> chain, final String token);
}
