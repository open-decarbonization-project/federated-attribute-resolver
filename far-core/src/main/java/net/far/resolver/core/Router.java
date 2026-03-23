package net.far.resolver.core;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.Set;
import net.far.resolver.model.NamespaceNotFoundException;
import net.far.resolver.spi.Driver;
import net.far.resolver.spi.DriverRegistry;

/** Routes namespace requests to the appropriate driver. */
@ApplicationScoped
public class Router {

  private final DriverRegistry registry;

  @Inject
  public Router(final DriverRegistry registry) {
    this.registry = registry;
  }

  /** Find the driver for a namespace. */
  public Optional<Driver> route(final String namespace) {
    return registry.find(namespace);
  }

  /** Find the driver or throw. */
  public Driver require(final String namespace) {
    return registry.find(namespace).orElseThrow(() -> new NamespaceNotFoundException(namespace));
  }

  /** Check if a namespace can be resolved locally. */
  public boolean local(final String namespace) {
    return registry.local(namespace);
  }

  /** Returns all locally supported namespaces. */
  public Set<String> namespaces() {
    return registry.supported();
  }
}
