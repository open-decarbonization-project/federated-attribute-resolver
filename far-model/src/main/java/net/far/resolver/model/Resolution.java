package net.far.resolver.model;

import java.time.Instant;
import java.util.Map;

/**
 * The result of resolving a URN: the certificate's attributes, integrity digest, producing resolver
 * identity, and resolution timestamp. Serialized to JSON by {@code ResolutionSerializer} with field
 * names aligned to the FAR OpenAPI schema.
 */
public record Resolution(
    Urn urn,
    String namespace,
    String identifier,
    Map<String, Attribute> attributes,
    Integrity integrity,
    String resolver,
    String status,
    Instant timestamp,
    boolean delegated) {

  public Resolution(
      final Urn urn,
      final String namespace,
      final String identifier,
      final Map<String, Attribute> attributes,
      final Integrity integrity,
      final String resolver,
      final Instant timestamp) {
    this(urn, namespace, identifier, attributes, integrity, resolver, null, timestamp, false);
  }

  public Resolution(
      final Urn urn,
      final String namespace,
      final String identifier,
      final Map<String, Attribute> attributes,
      final Integrity integrity,
      final String resolver,
      final String status,
      final Instant timestamp) {
    this(urn, namespace, identifier, attributes, integrity, resolver, status, timestamp, false);
  }

  public Resolution {
    if (urn == null) {
      throw new IllegalArgumentException("URN must not be null");
    }
    if (namespace == null || namespace.isBlank()) {
      namespace = urn.namespace();
    }
    if (identifier == null || identifier.isBlank()) {
      identifier = urn.identifier();
    }
    if (attributes == null) {
      attributes = Map.of();
    }
    if (timestamp == null) {
      timestamp = Instant.now();
    }
  }
}
