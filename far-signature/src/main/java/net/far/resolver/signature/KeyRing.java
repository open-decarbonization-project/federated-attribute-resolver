package net.far.resolver.signature;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Holds the current signing key and any previous keys still valid during grace periods. */
public record KeyRing(KeyManager current, List<KeyRing.Retired> previous) {

  public KeyRing {
    if (current == null) {
      throw new IllegalArgumentException("Current key must not be null");
    }
    previous = previous != null ? List.copyOf(previous) : List.of();
  }

  /** Returns all non-expired keys: current plus any previous keys whose expiry is after now. */
  public List<KeyManager> active() {
    final var result = new ArrayList<KeyManager>();
    result.add(current);
    final var now = Instant.now();
    for (final var retired : previous) {
      if (retired.expires().isAfter(now)) {
        result.add(retired.keys());
      }
    }
    return Collections.unmodifiableList(result);
  }

  /** A retired key that remains valid for verification until its expiry. */
  public record Retired(KeyManager keys, Instant expires) {}
}
