package net.far.resolver.test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.far.resolver.core.PeerRegistry;
import net.far.resolver.signature.DigestCalculator;
import net.far.resolver.signature.KeyManager;
import net.far.resolver.signature.MessageComponents;
import net.far.resolver.signature.Verifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test verifying the signing infrastructure works end-to-end in the resolver: response
 * signatures, digest validation, key management, delegation header forwarding, and configuration
 * exposure.
 */
@QuarkusTest
class SignatureIntegrationTests {

  @Inject PeerRegistry peers;

  @Inject KeyManager keys;

  @Inject Verifier verifier;

  private HttpServer mock;

  @BeforeEach
  void setup() throws IOException {
    mock = HttpServer.create(new InetSocketAddress(0), 0);
    mock.start();
  }

  @AfterEach
  void teardown() {
    if (mock != null) {
      mock.stop(0);
    }
    peers.remove("https://sig-peer.example.com");
  }

  @Test
  void shouldSignResolveResponse() {
    given()
        .urlEncodingEnabled(false)
        .when()
        .get("/v1/resources/urn:far:stub:TEST-001")
        .then()
        .statusCode(200)
        .header("Content-Digest", startsWith("sha-256=:"))
        .header("Content-Digest", endsWith(":"))
        .header("Signature", startsWith("sig1=:"))
        .header("Signature", endsWith(":"))
        .header("Signature-Input", startsWith("sig1="))
        .header("Signature-Input", containsString("keyid="))
        .header("Signature-Input", containsString("alg=\"ed25519\""))
        .header("Signature-Input", containsString("created="));
  }

  @Test
  void shouldSignQueryResponse() {
    given()
        .queryParam("$filter", "namespace eq 'stub'")
        .when()
        .get("/v1/resources")
        .then()
        .statusCode(200)
        .header("Content-Digest", startsWith("sha-256=:"))
        .header("Signature", startsWith("sig1=:"))
        .header("Signature-Input", containsString("keyid="));
  }

  @Test
  void shouldSignHistoryResponse() {
    given()
        .urlEncodingEnabled(false)
        .when()
        .get("/v1/resources/urn:far:stub:TEST-001/history")
        .then()
        .statusCode(200)
        .header("Content-Digest", startsWith("sha-256=:"))
        .header("Signature", startsWith("sig1=:"))
        .header("Signature-Input", containsString("keyid="));
  }

  @Test
  void shouldNotSignHeadResponse() {
    given()
        .urlEncodingEnabled(false)
        .when()
        .head("/v1/resources/urn:far:stub:TEST-001")
        .then()
        .statusCode(200)
        .header("Signature", nullValue())
        .header("Signature-Input", nullValue())
        .header("Content-Digest", nullValue());
  }

  @Test
  void shouldVerifySignatureRoundtrip() {
    final var response =
        given()
            .urlEncodingEnabled(false)
            .when()
            .get("/v1/resources/urn:far:stub:TEST-001")
            .then()
            .statusCode(200)
            .extract();

    final var body = response.body().asString();
    final var digest = response.header("Content-Digest");
    final var signature = response.header("Signature");
    final var input = response.header("Signature-Input");

    // Validate Content-Digest matches the body
    assertThat(DigestCalculator.validate(body.getBytes(StandardCharsets.UTF_8), digest))
        .as("Content-Digest must match recomputed digest of response body")
        .isTrue();

    // Recompute and compare explicitly
    final var recomputed = DigestCalculator.compute(body.getBytes(StandardCharsets.UTF_8));
    assertThat(digest).isEqualTo(recomputed);

    // Extract signature value (strip sig1=: prefix and : suffix)
    final var value = signature.substring("sig1=:".length(), signature.length() - 1);

    // Reconstruct MessageComponents from Signature-Input covered components
    // Only include content-type if the Signature-Input lists it
    final var resolver = response.header("Far-Resolver");
    final var namespace = response.header("Far-Namespace");
    final var headers = new LinkedHashMap<String, String>();
    if (resolver != null && input.contains("\"far-resolver\"")) {
      headers.put("far-resolver", resolver);
    }
    if (namespace != null && input.contains("\"far-namespace\"")) {
      headers.put("far-namespace", namespace);
    }
    final var type = input.contains("\"content-type\"") ? response.header("Content-Type") : null;
    final var components = new MessageComponents(200, null, null, null, type, digest, headers);

    // Verify signature using injected public key
    final var valid = verifier.verify(components, value, input, keys.verifying());
    assertThat(valid).isTrue();
  }

  @Test
  void shouldHaveAutoGeneratedKey() {
    assertThat(keys).isNotNull();
    assertThat(keys.signing()).isNotNull();
    assertThat(keys.verifying()).isNotNull();
    assertThat(keys.id()).isNotNull();
    assertThat(keys.id()).contains(":");
  }

  @Test
  void shouldForwardSignatureHeadersOnDelegation() throws IOException {
    final var captured = new AtomicReference<java.util.Map<String, String>>();
    final var signing = new SigningMockPeer("https://sig-peer.example.com");

    signing.handle(
        "/v1/resources/urn:far:sigpeer:SIG-001",
        exchange -> {
          final var headers = new java.util.HashMap<String, String>();
          final var input = exchange.getRequestHeaders().getFirst("Signature-Input");
          final var signature = exchange.getRequestHeaders().getFirst("Signature");
          if (input != null) {
            headers.put("Signature-Input", input);
          }
          if (signature != null) {
            headers.put("Signature", signature);
          }
          captured.set(headers);
          return """
          {
            "urn": "urn:far:sigpeer:SIG-001",
            "namespace": "sigpeer",
            "identifier": "SIG-001",
            "attributes": {
              "status": {
                "name": "status",
                "value": "active",
                "source": "sig-registry",
                "verified": true
              }
            },
            "resolver": "sig-server",
            "timestamp": "%s"
          }
          """
              .formatted(Instant.now().toString());
        });
    signing.start();
    signing.register(peers, Set.of("sigpeer"));

    try {
      given()
          .urlEncodingEnabled(false)
          .when()
          .get("/v1/resources/urn:far:sigpeer:SIG-001")
          .then()
          .statusCode(200)
          .body("namespace", equalTo("sigpeer"));

      assertThat(captured.get()).isNotNull();
      assertThat(captured.get().get("Signature-Input"))
          .as("Delegated request must include Signature-Input header")
          .isNotNull()
          .startsWith("sig1=");
      assertThat(captured.get().get("Signature"))
          .as("Delegated request must include Signature header")
          .isNotNull()
          .startsWith("sig1=:");
    } finally {
      signing.stop();
    }
  }

  @Test
  void shouldExposePublicKeyInConfiguration() {
    given()
        .when()
        .get("/v1/peers/configuration")
        .then()
        .statusCode(200)
        .body("public_key.algorithm", equalTo("Ed25519"))
        .body("public_key.public_key_pem", startsWith("-----BEGIN PUBLIC KEY-----"))
        .body("public_key.key_id", notNullValue())
        .body("public_key.key_id", containsString(":"));
  }
}
