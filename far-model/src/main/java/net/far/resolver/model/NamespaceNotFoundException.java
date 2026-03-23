package net.far.resolver.model;

public class NamespaceNotFoundException extends FarException {

  public NamespaceNotFoundException(final String namespace) {
    super("namespace_not_found", "Namespace not found: " + namespace);
  }
}
