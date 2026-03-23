package net.far.resolver.model;

/** A content integrity digest and its hash algorithm (default SHA-256). */
public record Integrity(String digest, String algorithm) {

  public static final String SHA256 = "sha-256";

  public Integrity {
    if (digest == null || digest.isBlank()) {
      throw new IllegalArgumentException("Digest must not be blank");
    }
    if (algorithm == null || algorithm.isBlank()) {
      algorithm = SHA256;
    }
  }
}
