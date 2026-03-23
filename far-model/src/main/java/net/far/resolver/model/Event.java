package net.far.resolver.model;

import java.time.Instant;
import java.util.Map;

/** A certificate lifecycle event (issuance, transfer, status change, retirement, verification). */
public record Event(EventType type, Instant timestamp, String actor, Map<String, Object> details) {

  public Event {
    if (type == null) {
      throw new IllegalArgumentException("Event type must not be null");
    }
    if (timestamp == null) {
      timestamp = Instant.now();
    }
    if (details == null) {
      details = Map.of();
    }
  }

  public enum EventType {
    ISSUANCE("issuance"),
    TRANSFER("transfer"),
    RETIREMENT("retirement"),
    CANCELLATION("cancellation"),
    STATUS_CHANGE("status_change");

    private final String label;

    EventType(final String label) {
      this.label = label;
    }

    public static EventType parse(final String value) {
      for (final var candidate : values()) {
        if (candidate.label.equals(value) || candidate.name().equals(value)) {
          return candidate;
        }
      }
      return switch (value) {
        case "ISSUED" -> ISSUANCE;
        case "TRANSFERRED" -> TRANSFER;
        case "RETIRED" -> RETIREMENT;
        case "UPDATED" -> STATUS_CHANGE;
        case "VERIFIED" -> STATUS_CHANGE;
        default -> throw new IllegalArgumentException("Unknown event type: " + value);
      };
    }

    public String label() {
      return label;
    }
  }
}
