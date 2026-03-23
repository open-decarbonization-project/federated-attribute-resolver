package net.far.resolver.model;

import java.time.Instant;

/** A named, typed attribute value with provenance (source, verification status, timestamp). */
public record Attribute(
    String name, Value value, String source, boolean verified, Instant timestamp) {

  public Attribute {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Attribute name must not be blank");
    }
    if (timestamp == null) {
      timestamp = Instant.now();
    }
  }
}
