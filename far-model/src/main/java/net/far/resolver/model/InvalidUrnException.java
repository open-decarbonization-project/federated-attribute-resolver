package net.far.resolver.model;

public class InvalidUrnException extends FarException {

  public InvalidUrnException(final String message) {
    super("invalid_urn", message);
  }
}
