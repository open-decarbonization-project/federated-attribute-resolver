package net.far.resolver.model;

public class UnauthorizedException extends FarException {

  public UnauthorizedException(final String message) {
    super("unauthorized", message);
  }
}
