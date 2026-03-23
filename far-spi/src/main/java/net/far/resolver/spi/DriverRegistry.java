package net.far.resolver.spi;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of available drivers. Uses ServiceLoader for auto-discovery and supports manual
 * registration.
 */
public class DriverRegistry {

  private final Map<String, Driver> drivers = new ConcurrentHashMap<>();
  private final Map<String, Driver> namespaces = new ConcurrentHashMap<>();

  public DriverRegistry() {
    discover();
  }

  /** Auto-discover drivers via ServiceLoader. */
  private void discover() {
    final var loader = ServiceLoader.load(Driver.class);
    for (final var driver : loader) {
      register(driver);
    }
  }

  /** Manually register a driver. */
  public void register(final Driver driver) {
    drivers.put(driver.name(), driver);
    for (final var namespace : driver.namespaces()) {
      namespaces.put(namespace, driver);
    }
  }

  /** Find a driver for the given namespace. */
  public Optional<Driver> find(final String namespace) {
    return Optional.ofNullable(namespaces.get(namespace));
  }

  /** Get all registered drivers. */
  public Collection<Driver> all() {
    return Collections.unmodifiableCollection(drivers.values());
  }

  /** Get all supported namespaces. */
  public Set<String> supported() {
    return Collections.unmodifiableSet(namespaces.keySet());
  }

  /** Check if a namespace has a local driver. */
  public boolean local(final String namespace) {
    return namespaces.containsKey(namespace);
  }
}
