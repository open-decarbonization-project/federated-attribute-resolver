package net.far.resolver.model;

public class FarException extends RuntimeException {

  private final String code;

  public FarException(final String code, final String message) {
    super(message);
    this.code = code;
  }

  public FarException(final String code, final String message, final Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
