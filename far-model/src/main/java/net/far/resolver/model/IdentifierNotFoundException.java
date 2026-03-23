package net.far.resolver.model;

public class IdentifierNotFoundException extends FarException {

  public IdentifierNotFoundException(final String identifier) {
    super("identifier_not_found", "Identifier not found: " + identifier);
  }
}
