package net.far.resolver.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * A federated peer registry. Stores the peer's identity, endpoint, claimed namespaces, current
 * Ed25519 public key (with key ID), and any still-valid previous keys for rotation grace periods.
 * Peers are discovered via {@code /v1/peers/configuration} and probed periodically to refresh key
 * material.
 */
public record Peer(
    String identity,
    String endpoint,
    Set<String> namespaces,
    String key,
    String keyId,
    List<PeerKey> previous,
    Instant seen,
    int priority,
    boolean enabled,
    String base,
    int depth) {

  public Peer {
    if (identity == null || identity.isBlank()) {
      throw new IllegalArgumentException("Peer identity must not be blank");
    }
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("Peer endpoint must not be blank");
    }
    if (namespaces == null) {
      namespaces = Set.of();
    }
    if (previous == null) {
      previous = List.of();
    }
    if (base == null || base.isBlank()) {
      base = endpoint + "/v1";
    }
    if (depth <= 0) {
      depth = 5;
    }
  }

  /** Backward-compatible constructor without keyId/base/depth. */
  public Peer(
      final String identity,
      final String endpoint,
      final Set<String> namespaces,
      final String key,
      final List<PeerKey> previous,
      final Instant seen,
      final int priority,
      final boolean enabled,
      final String base,
      final int depth) {
    this(identity, endpoint, namespaces, key, null, previous, seen, priority, enabled, base, depth);
  }

  /** Backward-compatible constructor without keyId/priority/enabled/base/depth. */
  public Peer(
      final String identity,
      final String endpoint,
      final Set<String> namespaces,
      final String key,
      final List<PeerKey> previous,
      final Instant seen) {
    this(
        identity,
        endpoint,
        namespaces,
        key,
        null,
        previous,
        seen,
        Integer.MAX_VALUE,
        true,
        endpoint + "/v1",
        5);
  }

  public record PeerKey(String id, String key, Instant expires) {}
}
