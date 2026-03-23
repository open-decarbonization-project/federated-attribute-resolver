package net.far.resolver.signature;

/**
 * Holds the result of signing an HTTP message: the signature value and the Signature-Input header.
 */
public record Signature(String value, String input) {

  public Signature {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Signature value must not be blank");
    }
    if (input == null || input.isBlank()) {
      throw new IllegalArgumentException("Signature input must not be blank");
    }
  }
}
