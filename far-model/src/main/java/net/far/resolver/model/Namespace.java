package net.far.resolver.model;

public record Namespace(
    String name, String description, String registry, String driver, boolean local, String status) {

  public Namespace(
      final String name,
      final String description,
      final String registry,
      final String driver,
      final boolean local) {
    this(name, description, registry, driver, local, "active");
  }

  public Namespace {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Namespace name must not be blank");
    }
    if (status == null) {
      status = "active";
    }
  }
}
