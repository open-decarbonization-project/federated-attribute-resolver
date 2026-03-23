package net.far.resolver.signature;

import java.util.Map;

/** Represents the components of an HTTP message used for signature computation per RFC 9421. */
public record MessageComponents(
    int status,
    String method,
    String target,
    String authority,
    String type,
    String digest,
    Map<String, String> headers) {

  /**
   * Canonical constructor. Normalizes the Content-Type to a consistent form by lowercasing,
   * stripping whitespace around semicolons, and sorting parameters. This ensures the signature base
   * is identical regardless of HTTP server/client whitespace variations.
   */
  public MessageComponents {
    type = normalize(type);
  }

  /** Request-mode constructor (status = 0). */
  public MessageComponents(
      final String method,
      final String target,
      final String authority,
      final String type,
      final String digest,
      final Map<String, String> headers) {
    this(0, method, target, authority, type, digest, headers);
  }

  /**
   * Normalizes a Content-Type value for signature computation. Lowercases the media type and
   * parameters, strips whitespace around semicolons, and sorts parameters alphabetically so that
   * {@code application/json; charset=UTF-8} and {@code application/json;charset=utf-8} produce the
   * same signature base.
   */
  static String normalize(final String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    final var parts = value.split(";");
    final var builder = new StringBuilder(parts[0].trim().toLowerCase());
    if (parts.length > 1) {
      final var params = new java.util.TreeSet<String>();
      for (var i = 1; i < parts.length; i++) {
        final var param = parts[i].trim().toLowerCase();
        if (!param.isEmpty()) {
          params.add(param);
        }
      }
      for (final var param : params) {
        builder.append(';').append(param);
      }
    }
    return builder.toString();
  }
}
