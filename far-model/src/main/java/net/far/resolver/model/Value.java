package net.far.resolver.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sealed type hierarchy for attribute values: strings, numbers, booleans, quantities (amount +
 * unit), instants, lists, and maps. Serialized to JSON by {@code ValueSerializer} and deserialized
 * by {@code ValueDeserializer}.
 */
public sealed interface Value {

  static Value of(final String value) {
    return new Text(value);
  }

  static Value of(final Number value) {
    return new Numeric(value);
  }

  static Value of(final boolean value) {
    return new Bool(value);
  }

  static Value of(final Instant value) {
    return new Temporal(value);
  }

  static Value of(final Number amount, final String unit) {
    return new Quantity(amount, unit);
  }

  static Value of(final List<Value> values) {
    return new Arr(values);
  }

  static Value of(final Map<String, Value> fields) {
    return new Record(fields);
  }

  record Text(String raw) implements Value {
    public Text {
      if (raw == null) {
        throw new IllegalArgumentException("Text value must not be null");
      }
    }
  }

  record Numeric(Number raw) implements Value {
    public Numeric {
      if (raw == null) {
        throw new IllegalArgumentException("Numeric value must not be null");
      }
    }
  }

  record Bool(boolean raw) implements Value {}

  record Temporal(Instant raw) implements Value {
    public Temporal {
      if (raw == null) {
        throw new IllegalArgumentException("Temporal value must not be null");
      }
    }
  }

  record Quantity(Number amount, String unit) implements Value {
    public Quantity {
      if (amount == null) {
        throw new IllegalArgumentException("Quantity amount must not be null");
      }
      if (unit == null || unit.isBlank()) {
        throw new IllegalArgumentException("Quantity unit must not be blank");
      }
    }
  }

  record Arr(List<Value> raw) implements Value {
    public Arr {
      if (raw == null) {
        throw new IllegalArgumentException("Array must not be null");
      }
      raw = List.copyOf(raw);
    }
  }

  record Record(Map<String, Value> fields) implements Value {
    public Record {
      if (fields == null) {
        throw new IllegalArgumentException("Record fields must not be null");
      }
      fields = Map.copyOf(fields);
    }
  }
}
