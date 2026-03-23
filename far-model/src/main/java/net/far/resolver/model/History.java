package net.far.resolver.model;

import java.util.List;

/** Audit trail for a certificate: a chronological list of lifecycle events. */
public record History(Urn urn, String namespace, String identifier, List<Event> events) {

  public History {
    if (urn == null) {
      throw new IllegalArgumentException("URN must not be null");
    }
    if (namespace == null || namespace.isBlank()) {
      namespace = urn.namespace();
    }
    if (identifier == null || identifier.isBlank()) {
      identifier = urn.identifier();
    }
    if (events == null) {
      events = List.of();
    }
  }

  public History(final Urn urn, final List<Event> events) {
    this(urn, urn.namespace(), urn.identifier(), events);
  }
}
