package net.far.resolver.signature;

import java.util.Base64;
import java.util.Optional;
import net.far.resolver.model.Requester;

/**
 * Parses JWS compact tokens to extract requester identity. Does not verify the signature —
 * verification is the peer's responsibility per ADR-007.
 */
public class TokenParser {

  private TokenParser() {}

  /** Parses a JWS compact token and extracts the requester identity from the payload. */
  public static Optional<Requester> parse(final String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    final var parts = token.split("\\.");
    if (parts.length != 3) {
      return Optional.empty();
    }
    try {
      final var payload = new String(Base64.getUrlDecoder().decode(parts[1]));
      final var subject = extract(payload, "sub");
      if (subject == null) {
        return Optional.empty();
      }
      final var issuer = extract(payload, "iss");
      final var issued = extractLong(payload, "iat");
      return Optional.of(new Requester(subject, issuer, issued));
    } catch (final Exception exception) {
      return Optional.empty();
    }
  }

  private static String extract(final String json, final String field) {
    final var key = "\"" + field + "\"";
    final var index = json.indexOf(key);
    if (index < 0) {
      return null;
    }
    final var colon = json.indexOf(':', index + key.length());
    if (colon < 0) {
      return null;
    }
    final var start = json.indexOf('"', colon + 1);
    if (start < 0) {
      return null;
    }
    final var end = json.indexOf('"', start + 1);
    if (end < 0) {
      return null;
    }
    return json.substring(start + 1, end);
  }

  private static long extractLong(final String json, final String field) {
    final var key = "\"" + field + "\"";
    final var index = json.indexOf(key);
    if (index < 0) {
      return 0L;
    }
    final var colon = json.indexOf(':', index + key.length());
    if (colon < 0) {
      return 0L;
    }
    var start = colon + 1;
    while (start < json.length() && json.charAt(start) == ' ') {
      start++;
    }
    var end = start;
    while (end < json.length()
        && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
      end++;
    }
    if (start == end) {
      return 0L;
    }
    return Long.parseLong(json.substring(start, end));
  }
}
