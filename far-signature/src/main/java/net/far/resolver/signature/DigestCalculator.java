package net.far.resolver.signature;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Calculates Content-Digest per RFC 9530. */
public class DigestCalculator {

  private DigestCalculator() {}

  /** Computes a SHA-256 content digest formatted as {@code sha-256=:base64:}. */
  public static String compute(final byte[] content) {
    try {
      final var digest = MessageDigest.getInstance("SHA-256");
      final var hash = digest.digest(content);
      return "sha-256=:" + Base64.getEncoder().encodeToString(hash) + ":";
    } catch (final NoSuchAlgorithmException exception) {
      throw new RuntimeException("SHA-256 algorithm not available", exception);
    }
  }

  /** Validates that the computed digest of the content matches the expected value. */
  public static boolean validate(final byte[] content, final String expected) {
    return compute(content).equals(expected);
  }
}
