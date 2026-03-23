package net.far.resolver.model;

/** Represents a FAR URN: urn:far:{namespace}:{identifier} */
public record Urn(String namespace, String identifier) {

  public static final String SCHEME = "urn";
  public static final String NID = "far";
  public static final String PREFIX = SCHEME + ":" + NID + ":";

  public Urn {
    if (namespace == null || namespace.isBlank()) {
      throw new InvalidUrnException("Namespace must not be blank");
    }
    if (identifier == null || identifier.isBlank()) {
      throw new InvalidUrnException("Identifier must not be blank");
    }
    if (!namespace.matches("[a-z][a-z0-9-]*")) {
      throw new InvalidUrnException("Invalid namespace: " + namespace);
    }
  }

  public static Urn parse(final String value) {
    if (value == null || value.length() < PREFIX.length()) {
      throw new InvalidUrnException("Invalid URN: " + value);
    }
    final var prefix = value.substring(0, PREFIX.length());
    if (!prefix.equalsIgnoreCase(PREFIX)) {
      throw new InvalidUrnException("Invalid URN: " + value);
    }
    var remainder = value.substring(PREFIX.length());
    // Strip RFC 8141 r-component (?+), q-component (?=), and fragment (#)
    final var fragment = remainder.indexOf('#');
    if (fragment >= 0) {
      remainder = remainder.substring(0, fragment);
    }
    final var query = remainder.indexOf("?=");
    if (query >= 0) {
      remainder = remainder.substring(0, query);
    }
    final var resolution = remainder.indexOf("?+");
    if (resolution >= 0) {
      remainder = remainder.substring(0, resolution);
    }
    final var colon = remainder.indexOf(':');
    if (colon < 1 || colon == remainder.length() - 1) {
      throw new InvalidUrnException("Invalid URN structure: " + value);
    }
    final var namespace = remainder.substring(0, colon).toLowerCase(java.util.Locale.ROOT);
    final var identifier = decodeUnreserved(remainder.substring(colon + 1));
    return new Urn(namespace, identifier);
  }

  /** Decodes unnecessarily percent-encoded unreserved characters per RFC 3986 section 2.3. */
  private static String decodeUnreserved(final String value) {
    if (value == null || !value.contains("%")) {
      return value;
    }
    final var result = new StringBuilder(value.length());
    for (var i = 0; i < value.length(); i++) {
      if (value.charAt(i) == '%' && i + 2 < value.length()) {
        try {
          final var code = Integer.parseInt(value.substring(i + 1, i + 3), 16);
          final var decoded = (char) code;
          if (unreserved(decoded)) {
            result.append(decoded);
            i += 2;
            continue;
          }
        } catch (final NumberFormatException ignored) {
        }
      }
      result.append(value.charAt(i));
    }
    return result.toString();
  }

  private static boolean unreserved(final char character) {
    return (character >= 'A' && character <= 'Z')
        || (character >= 'a' && character <= 'z')
        || (character >= '0' && character <= '9')
        || character == '-'
        || character == '.'
        || character == '_'
        || character == '~';
  }

  @Override
  public String toString() {
    return PREFIX + namespace + ":" + identifier;
  }
}
