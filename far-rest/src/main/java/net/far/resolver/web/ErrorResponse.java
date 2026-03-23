package net.far.resolver.web;

import java.time.Instant;

public record ErrorResponse(Error error) {

  public ErrorResponse(final String code, final String message) {
    this(new Error(code, message, null, null, Instant.now()));
  }

  public ErrorResponse(final String code, final String message, final String urn) {
    this(new Error(code, message, urn, null, Instant.now()));
  }

  public ErrorResponse(
      final String code, final String message, final String urn, final String resolver) {
    this(new Error(code, message, urn, resolver, Instant.now()));
  }

  public record Error(
      String code, String message, String urn, String resolver, Instant timestamp) {}
}
