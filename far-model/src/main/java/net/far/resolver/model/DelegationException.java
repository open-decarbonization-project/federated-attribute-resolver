package net.far.resolver.model;

public class DelegationException extends FarException {

  public DelegationException(final String message) {
    super("delegation_failed", message);
  }

  public DelegationException(final String message, final Throwable cause) {
    super("delegation_failed", message, cause);
  }
}
