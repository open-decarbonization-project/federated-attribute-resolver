package net.far.resolver.model.query;

import net.far.resolver.model.FarException;

public class InvalidFilterException extends FarException {

  public InvalidFilterException(final String message) {
    super("invalid_filter", message);
  }
}
