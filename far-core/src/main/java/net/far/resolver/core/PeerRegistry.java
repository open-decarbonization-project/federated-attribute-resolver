package net.far.resolver.core;

import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.far.resolver.model.Peer;

/** Maintains the registry of known peer FAR servers and their namespace mappings. */
@ApplicationScoped
public class PeerRegistry {

  private final Map<String, Peer> peers = new ConcurrentHashMap<>();

  /** Register a peer server. */
  public void register(final Peer peer) {
    peers.put(peer.identity(), peer);
  }

  /** Remove a peer server. */
  public void remove(final String identity) {
    peers.remove(identity);
  }

  /** Find peers that support the given namespace, filtered by enabled and sorted by priority. */
  public List<Peer> find(final String namespace) {
    return peers.values().stream()
        .filter(p -> p.enabled() && p.namespaces().contains(namespace))
        .sorted(Comparator.comparingInt(Peer::priority))
        .collect(Collectors.toList());
  }

  /** Returns all namespaces advertised by peers. */
  public Set<String> namespaces() {
    return peers.values().stream()
        .flatMap(p -> p.namespaces().stream())
        .collect(Collectors.toSet());
  }

  /** Get all known peers. */
  public Collection<Peer> all() {
    return Collections.unmodifiableCollection(peers.values());
  }

  /** Get a specific peer by identity. */
  public Optional<Peer> get(final String identity) {
    return Optional.ofNullable(peers.get(identity));
  }

  /** Find a peer whose identity URL hostname matches the given hostname. */
  public Optional<Peer> match(final String hostname) {
    for (final var peer : peers.values()) {
      try {
        if (hostname.equals(URI.create(peer.identity()).getHost())) {
          return Optional.of(peer);
        }
      } catch (final Exception ignored) {
      }
    }
    return Optional.empty();
  }
}
