package net.far.resolver.model;

public class UnsupportedFormatException extends FarException {

  public UnsupportedFormatException(final String media) {
    super("unsupported_format", "No renderer available for media type '" + media + "'.");
  }
}
