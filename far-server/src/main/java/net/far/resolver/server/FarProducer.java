package net.far.resolver.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.far.resolver.client.HttpFarClient;
import net.far.resolver.core.Delegator;
import net.far.resolver.core.PeerRegistry;
import net.far.resolver.core.Resolver;
import net.far.resolver.core.Router;
import net.far.resolver.signature.KeyManager;
import net.far.resolver.signature.KeyRing;
import net.far.resolver.signature.Signer;
import net.far.resolver.signature.Verifier;
import net.far.resolver.spi.DriverRegistry;
import net.far.resolver.spi.FarClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** CDI producer for FAR core components. */
@ApplicationScoped
public class FarProducer {

  @ConfigProperty(name = "far.identity", defaultValue = "https://far.example.com")
  String identity;

  @ConfigProperty(name = "far.delegation.limit", defaultValue = "5")
  int limit;

  @ConfigProperty(name = "far.keys.id", defaultValue = "key-1")
  String keyId;

  @ConfigProperty(name = "far.keys.directory", defaultValue = "")
  Optional<String> directory;

  @ConfigProperty(name = "far.keys.previous.directory", defaultValue = "")
  Optional<String> previousDirectory;

  @ConfigProperty(name = "far.keys.previous.expires", defaultValue = "")
  Optional<String> previousExpires;

  @ConfigProperty(name = "far.client.signature.strict", defaultValue = "true")
  boolean strict;

  @Produces
  @Singleton
  public DriverRegistry registry() {
    return new DriverRegistry();
  }

  @Produces
  @Singleton
  public PeerRegistry peers() {
    return new PeerRegistry();
  }

  @Produces
  @Singleton
  public Router router(final DriverRegistry registry) {
    return new Router(registry);
  }

  @Produces
  @Singleton
  public Delegator delegator(final PeerRegistry peers, final FarClient client) {
    return new Delegator(peers, client, identity, limit);
  }

  @Produces
  @Singleton
  public Resolver resolver(
      final Router router, final Delegator delegator, final PeerRegistry peers) {
    return new Resolver(router, delegator, peers);
  }

  @Produces
  @Singleton
  public KeyManager keys() {
    final var id = identity + "#" + keyId;
    if (directory.isPresent() && !directory.get().isBlank()) {
      final var path = Path.of(directory.get());
      if (Files.exists(path.resolve("private.pem"))) {
        return KeyManager.load(path, id);
      }
      final var generated = KeyManager.generate(id);
      generated.write(path);
      return generated;
    }
    return KeyManager.generate(id);
  }

  @Produces
  @Singleton
  public KeyRing ring(final KeyManager keys) {
    return new KeyRing(keys, retired());
  }

  @Produces
  @Singleton
  public Signer signer(final KeyRing ring) {
    return new Signer(ring.current());
  }

  @Produces
  @Singleton
  public Verifier verifier() {
    return new Verifier();
  }

  @Produces
  @Singleton
  public FarClient client(final Signer signer, final PeerRegistry peers, final Verifier verifier) {
    return new HttpFarClient(signer, peers, verifier, strict);
  }

  private List<KeyRing.Retired> retired() {
    if (previousDirectory.isEmpty() || previousDirectory.get().isBlank()) {
      return List.of();
    }
    final var path = Path.of(previousDirectory.get());
    if (!Files.exists(path)) {
      return List.of();
    }
    final var id = identity + "#" + keyId + "-previous";
    final var previous = KeyManager.load(path, id);
    final var expires =
        previousExpires
            .filter(s -> !s.isBlank())
            .map(Instant::parse)
            .orElse(Instant.now().plusSeconds(86400));
    return List.of(new KeyRing.Retired(previous, expires));
  }
}
