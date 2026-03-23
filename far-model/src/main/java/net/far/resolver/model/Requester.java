package net.far.resolver.model;

/**
 * Represents a requester identity extracted from a JWS token in the Authorization: Bearer header.
 */
public record Requester(String subject, String issuer, long issued) {

  public Requester {
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("Subject must not be blank");
    }
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("Issuer must not be blank");
    }
  }
}
