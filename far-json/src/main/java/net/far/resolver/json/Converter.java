package net.far.resolver.json;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import net.far.resolver.model.Attribute;
import net.far.resolver.model.Event;
import net.far.resolver.model.History;
import net.far.resolver.model.Integrity;
import net.far.resolver.model.Resolution;
import net.far.resolver.model.Urn;
import net.far.resolver.model.Value;
import net.far.resolver.model.query.Page;

/** Static utilities for converting between Jackson JsonNode trees and FAR model objects. */
public final class Converter {

  private Converter() {}

  /** Convert a JsonNode to a Value. */
  public static Value value(final JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isTextual()) {
      return Value.of(node.asText());
    }
    if (node.isBoolean()) {
      return Value.of(node.asBoolean());
    }
    if (node.isNumber()) {
      return Value.of(node.numberValue());
    }
    if (node.isArray()) {
      final var items = new ArrayList<Value>();
      for (final var element : node) {
        items.add(value(element));
      }
      return Value.of(items);
    }
    if (node.isObject()) {
      if (node.has("amount") && node.has("unit")) {
        return Value.of(node.get("amount").numberValue(), node.get("unit").asText());
      }
      final var fields = new LinkedHashMap<String, Value>();
      final var iterator = node.fields();
      while (iterator.hasNext()) {
        final var entry = iterator.next();
        fields.put(entry.getKey(), value(entry.getValue()));
      }
      return Value.of(fields);
    }
    return Value.of(node.toString());
  }

  /** Convert a JsonNode to an Attribute. */
  public static Attribute attribute(final String name, final JsonNode node) {
    if (node.isObject()) {
      final var value = node.has("value") ? value(node.get("value")) : Value.of(node.toString());
      final var source = node.has("source") ? node.get("source").asText() : "unknown";
      final var verified = node.has("verified") && node.get("verified").asBoolean();
      final var timestamp = node.has("timestamp") ? instant(node) : Instant.now();
      return new Attribute(name, value, source, verified, timestamp);
    }
    return new Attribute(name, value(node), "unknown", false, Instant.now());
  }

  /** Convert a JsonNode to a Resolution. */
  public static Resolution resolution(final JsonNode node, final Urn fallback) {
    final var urn = urn(node, fallback);
    final var namespace = text(node, "namespace", urn.namespace());
    final var identifier = text(node, "identifier", urn.identifier());
    final var resolver = text(node, "resolver", null);
    final var status = text(node, "status", null);

    final var attributes = new LinkedHashMap<String, Attribute>();
    final var attrs = node.get("attributes");
    if (attrs != null && attrs.isObject()) {
      final var fields = attrs.fields();
      while (fields.hasNext()) {
        final var field = fields.next();
        attributes.put(field.getKey(), attribute(field.getKey(), field.getValue()));
      }
    }

    final var integrity = integrity(node);
    final var timestamp = resolved(node, Instant.now());
    final var delegated = node.has("delegated") && node.get("delegated").asBoolean();
    return new Resolution(
        urn, namespace, identifier, attributes, integrity, resolver, status, timestamp, delegated);
  }

  private static Integrity integrity(final JsonNode node) {
    final var field = node.get("integrity");
    if (field == null || field.isNull() || !field.isObject()) {
      return null;
    }
    final var digest = text(field, "digest", null);
    if (digest == null) {
      return null;
    }
    final var algorithm = text(field, "algorithm", Integrity.SHA256);
    return new Integrity(digest, algorithm);
  }

  /** Convert a JsonNode to a History. */
  public static History history(final JsonNode node, final Urn fallback) {
    final var urn = urn(node, fallback);
    final var events = new ArrayList<Event>();
    var array = node.get("history");
    if (array == null || !array.isArray()) {
      array = node.get("events");
    }
    if (array != null && array.isArray()) {
      for (final var element : array) {
        events.add(event(element));
      }
    }
    return new History(urn, events);
  }

  /** Convert a JsonNode to an Event. */
  public static Event event(final JsonNode node) {
    final var type = Event.EventType.parse(node.get("type").asText());
    final var timestamp = instant(node);
    final var actor = text(node, "actor", null);

    final var details = new LinkedHashMap<String, Object>();
    final var raw = node.get("details");
    if (raw != null && raw.isObject()) {
      final var fields = raw.fields();
      while (fields.hasNext()) {
        final var field = fields.next();
        details.put(field.getKey(), primitive(field.getValue()));
      }
    }
    return new Event(type, timestamp, actor, details);
  }

  /**
   * Extract a primitive Java value (String, Number, Boolean, or toString fallback) from a JsonNode.
   */
  public static Object primitive(final JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isTextual()) {
      return node.asText();
    }
    if (node.isNumber()) {
      return node.numberValue();
    }
    if (node.isBoolean()) {
      return node.asBoolean();
    }
    return node.toString();
  }

  /** Convert a JsonNode to a Page. */
  public static Page page(final JsonNode node) {
    final var items = new ArrayList<Resolution>();
    final var array = node.get("value");
    if (array != null && array.isArray()) {
      for (final var element : array) {
        final var urn = urn(element, null);
        if (urn != null) {
          items.add(resolution(element, urn));
        }
      }
    }
    final var count = node.has("count") ? node.get("count").asLong() : items.size();
    final var skip = node.has("skip") ? node.get("skip").asInt() : 0;
    final var top = node.has("top") ? node.get("top").asInt() : 25;
    return new Page(items, count, skip, top);
  }

  private static Urn urn(final JsonNode node, final Urn fallback) {
    final var field = node.get("urn");
    if (field != null && field.isTextual()) {
      return Urn.parse(field.asText());
    }
    return fallback;
  }

  private static String text(final JsonNode node, final String field, final String fallback) {
    return node.has(field) ? node.get(field).asText() : fallback;
  }

  private static Instant instant(final JsonNode node) {
    if (node.has("timestamp") && node.get("timestamp").isTextual()) {
      return Instant.parse(node.get("timestamp").asText());
    }
    return null;
  }

  private static Instant instant(final JsonNode node, final Instant fallback) {
    final var result = instant(node);
    return result != null ? result : fallback;
  }

  private static Instant resolved(final JsonNode node, final Instant fallback) {
    final var result = instant(node);
    if (result != null) {
      return result;
    }
    if (node.has("resolved_at") && node.get("resolved_at").isTextual()) {
      return Instant.parse(node.get("resolved_at").asText());
    }
    return fallback;
  }
}
