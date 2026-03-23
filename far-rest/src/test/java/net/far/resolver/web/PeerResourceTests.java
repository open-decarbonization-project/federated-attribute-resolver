package net.far.resolver.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.far.resolver.core.PeerRegistry;
import net.far.resolver.signature.KeyManager;
import net.far.resolver.signature.KeyRing;
import net.far.resolver.spi.DriverRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PeerResourceTests {

  private PeerResource resource;
  private KeyRing ring;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() {
    final var keys = KeyManager.generate("test-server#key-1");
    ring = new KeyRing(keys, List.of());
    final var registry = new DriverRegistry();

    resource = new PeerResource();
    resource.identity = "https://far.example.com";
    resource.version = "0.1.0";
    resource.depth = 5;
    resource.peers = new PeerRegistry();
    resource.registry = registry;
    resource.ring = ring;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> config() {
    return (Map<String, Object>) resource.configuration().getEntity();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnConfiguration() {
    final var result = config();

    assertThat(result).containsKey("identity");
    assertThat(result.get("identity")).isEqualTo("https://far.example.com");
    assertThat(result).containsKey("public_key");
    final var key = (Map<String, Object>) result.get("public_key");
    assertThat(key.get("algorithm")).isEqualTo("Ed25519");
    assertThat(key.get("key_id")).isEqualTo("test-server#key-1");
    assertThat((String) key.get("public_key_pem")).startsWith("-----BEGIN PUBLIC KEY-----");
    assertThat(result).containsKey("version");
    assertThat(result.get("version")).isEqualTo("0.1.0");
    assertThat(result).containsKey("protocol_version");
    assertThat(result.get("protocol_version")).isEqualTo("far/1.0");
    assertThat(result).containsKey("api_base");
    assertThat(result).containsKey("delegation");
    assertThat(result).containsKey("endpoints");
    assertThat(result.get("endpoints")).isInstanceOf(Map.class);
  }

  @Test
  void shouldReturnNamespacesInConfiguration() {
    final var result = config();

    assertThat(result).containsKey("namespaces");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnPemPublicKey() {
    final var result = config();
    final var key = (Map<String, Object>) result.get("public_key");

    assertThat((String) key.get("public_key_pem")).startsWith("-----BEGIN PUBLIC KEY-----");
    assertThat((String) key.get("public_key_pem")).endsWith("-----END PUBLIC KEY-----");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnPreviousKeys() {
    final var result = config();
    final var previous = (List<Object>) result.get("previous_keys");

    assertThat(previous).isEmpty();
  }

  @Test
  void shouldReturnCacheControlHeader() {
    final var response = resource.configuration();

    assertThat(response.getHeaderString("Cache-Control")).isEqualTo("public, max-age=3600");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnEmptyPeerList() {
    final var result = resource.list(null);

    assertThat((List<?>) result.get("peers")).isEmpty();
  }
}
