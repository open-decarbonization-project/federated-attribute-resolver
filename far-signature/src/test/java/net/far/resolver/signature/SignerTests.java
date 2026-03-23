package net.far.resolver.signature;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SignerTests {

  private KeyManager keys;
  private Signer signer;

  @BeforeEach
  void setup() {
    keys = KeyManager.generate("test-server");
    signer = new Signer(keys);
  }

  @Test
  void base() {
    final var components =
        new MessageComponents(
            "GET",
            "/v1/resources/urn:far:verra:VCS-1234",
            "far.example.com",
            "application/json",
            "sha-256=:abc123=:",
            null);

    final var result = signer.base(components, "(\"@method\" \"@target-uri\");keyid=\"test\"");

    assertThat(result).contains("\"@method\": GET");
    assertThat(result).contains("\"@target-uri\": /v1/resources/urn:far:verra:VCS-1234");
    assertThat(result).contains("\"@authority\": far.example.com");
    assertThat(result).contains("\"content-type\": application/json");
    assertThat(result).contains("\"content-digest\": sha-256=:abc123=:");
    assertThat(result).contains("\"@signature-params\":");
  }

  @Test
  void baseWithHeaders() {
    final var components =
        new MessageComponents(
            "POST",
            "/v1/resources",
            "far.example.com",
            "application/json",
            "sha-256=:xyz=:",
            Map.of("far-delegation-chain", "https://far-a.example.com"));

    final var result = signer.base(components, "(\"@method\");keyid=\"test\"");

    assertThat(result).contains("\"far-delegation-chain\": https://far-a.example.com");
  }

  @Test
  void baseWithoutOptional() {
    final var components =
        new MessageComponents(
            "GET", "/v1/resources/urn:far:verra:VCS-1234", "far.example.com", null, null, null);

    final var result = signer.base(components, "(\"@method\");keyid=\"test\"");

    assertThat(result).doesNotContain("\"content-type\":");
    assertThat(result).doesNotContain("\"content-digest\":");
    assertThat(result).contains("\"@method\": GET");
  }

  @Test
  void sign() {
    final var components =
        new MessageComponents(
            "GET",
            "/v1/resources/urn:far:verra:VCS-1234",
            "far.example.com",
            "application/json",
            null,
            null);

    final var signature = signer.sign(components);

    assertThat(signature.value()).isNotBlank();
    final var decoded = Base64.getDecoder().decode(signature.value());
    assertThat(decoded).hasSize(64);
    assertThat(signature.input()).startsWith("sig1=");
    assertThat(signature.input()).contains("keyid=\"test-server\"");
    assertThat(signature.input()).contains("alg=\"ed25519\"");
    assertThat(signature.input()).contains("created=");
  }

  @Test
  void response() {
    final var headers = new java.util.LinkedHashMap<String, String>();
    headers.put("far-resolver", "https://far.example.com");
    headers.put("far-namespace", "carbon");
    final var components =
        new MessageComponents(
            200, null, null, null, "application/json", "sha-256=:abc123=:", headers);

    final var result = signer.base(components, "(\"@status\");keyid=\"test\"");

    assertThat(result).contains("\"@status\": 200");
    assertThat(result).doesNotContain("\"@method\"");
    assertThat(result).doesNotContain("\"@target-uri\"");
    assertThat(result).doesNotContain("\"@authority\"");
    assertThat(result).contains("\"content-type\": application/json");
    assertThat(result).contains("\"content-digest\": sha-256=:abc123=:");
    assertThat(result).contains("\"far-resolver\": https://far.example.com");
    assertThat(result).contains("\"far-namespace\": carbon");
  }

  @Test
  void signRequest() {
    final var components =
        new MessageComponents(
            "GET", "/v1/resources/urn:far:verra:VCS-1234", "far.example.com", null, null, null);

    final var signature = signer.sign(components);

    assertThat(signature.value()).isNotBlank();
    final var decoded = Base64.getDecoder().decode(signature.value());
    assertThat(decoded).hasSize(64);
    assertThat(signature.input()).contains("\"@method\"");
    assertThat(signature.input()).contains("\"@target-uri\"");
    assertThat(signature.input()).contains("\"@authority\"");
    assertThat(signature.input()).doesNotContain("\"content-type\"");
    assertThat(signature.input()).doesNotContain("\"content-digest\"");
  }

  @Test
  void signResponse() {
    final var headers = new java.util.LinkedHashMap<String, String>();
    headers.put("far-resolver", "https://far.example.com");
    headers.put("far-namespace", "carbon");
    final var components =
        new MessageComponents(200, null, null, null, "application/json", "sha-256=:abc=:", headers);

    final var signature = signer.sign(components);

    assertThat(signature.value()).isNotBlank();
    final var decoded = Base64.getDecoder().decode(signature.value());
    assertThat(decoded).hasSize(64);
    assertThat(signature.input()).contains("\"@status\"");
    assertThat(signature.input()).contains("\"far-resolver\"");
    assertThat(signature.input()).contains("\"far-namespace\"");
  }
}
