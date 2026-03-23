package net.far.resolver.spi;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.far.resolver.model.History;
import net.far.resolver.model.Rendition;
import net.far.resolver.model.Resolution;
import net.far.resolver.model.Urn;
import net.far.resolver.model.query.Page;
import net.far.resolver.model.query.Query;

/**
 * SPI interface for certificate registry drivers. Implementations resolve URNs against specific
 * registries.
 */
public interface Driver {

  /** Returns the driver name. */
  String name();

  /** Returns the set of namespaces this driver supports. */
  Set<String> namespaces();

  /** Checks if this driver supports the given namespace. */
  default boolean supports(final String namespace) {
    return namespaces().contains(namespace);
  }

  /** Resolves a URN to its attribute data. */
  Optional<Resolution> resolve(final Urn urn);

  /** Retrieves the history of a certificate. */
  Optional<History> history(final Urn urn);

  /** Checks if the URN exists in the registry. */
  boolean exists(final Urn urn);

  /**
   * Fetches a pre-formatted rendition from the upstream registry. Returns empty if the registry
   * does not provide the requested media type.
   */
  default Optional<Rendition> rendition(final Urn urn, final String media) {
    return Optional.empty();
  }

  /**
   * Searches the registry using a parametric filter query. Default returns empty results; drivers
   * override to support search.
   */
  default Page query(final Query query) {
    return new Page(List.of(), 0, query.skip(), query.top());
  }
}
