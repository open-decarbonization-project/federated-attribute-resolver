package net.far.resolver.model;

public class SignatureException extends FarException {

  public SignatureException(final String message) {
    super("signature_invalid", message);
  }

  public SignatureException(final String message, final Throwable cause) {
    super("signature_invalid", message, cause);
  }
}
