package net.far.resolver.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.far.resolver.core.PeerRegistry;
import net.far.resolver.model.Peer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test verifying local resolution, peer configuration, delegation to a signing mock
 * peer, and rejection of unsigned peer responses.
 */
@QuarkusTest
class DelegationIntegrationTests {

  @Inject PeerRegistry peers;

  private SigningMockPeer mock;

  @BeforeEach
  void setup() throws IOException {
    mock = new SigningMockPeer("https://remote.example.com");
    mock.handle(
        "/v1/resources/urn:far:remote:TEST-001",
        exchange ->
            """
            {
              "urn": "urn:far:remote:TEST-001",
              "namespace": "remote",
              "identifier": "TEST-001",
              "attributes": {
                "volume": {
                  "name": "volume",
                  "value": {"amount": 500, "unit": "tCO2e"},
                  "source": "remote-registry",
                  "verified": true
                }
              },
              "resolver": "remote-server",
              "timestamp": "%s"
            }
            """
                .formatted(Instant.now().toString()));
    mock.start();
    mock.register(peers, Set.of("remote"));
  }

  @AfterEach
  void teardown() {
    if (mock != null) {
      mock.stop();
    }
    peers.remove("https://remote.example.com");
    peers.remove("https://auth.example.com");
    peers.remove("https://unsigned.example.com");
  }

  @Test
  void shouldResolveLocalUrn() {
    given()
        .urlEncodingEnabled(false)
        .when()
        .get("/v1/resources/urn:far:stub:TEST-001")
        .then()
        .statusCode(200)
        .body("namespace", equalTo("stub"))
        .body("identifier", equalTo("TEST-001"))
        .body("attributes.volume", notNullValue())
        .header("Far-Resolver", notNullValue());
  }

  @Test
  void shouldIncludeSignatureHeaders() {
    given()
        .urlEncodingEnabled(false)
        .when()
        .get("/v1/resources/urn:far:stub:TEST-001")
        .then()
        .statusCode(200)
        .header("Content-Digest", startsWith("sha-256=:"))
        .header("Signature", startsWith("sig1=:"))
        .header("Signature-Input", containsString("keyid="));
  }

  @Test
  void shouldReturn404ForUnknownIdentifier() {
    given()
        .urlEncodingEnabled(false)
        .when()
        .get("/v1/resources/urn:far:stub:NONEXISTENT")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("identifier_not_found"));
  }

  @Test
  void shouldReturn404ForUnknownNamespaceWithoutPeer() {
    peers.remove("https://remote.example.com");
    given()
        .urlEncodingEnabled(false)
        .when()
        .get("/v1/resources/urn:far:absent:TEST-001")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("namespace_not_found"));
  }

  @Test
  void shouldReturnConfigurationWithPublicKey() {
    given()
        .when()
        .get("/v1/peers/configuration")
        .then()
        .statusCode(200)
        .body("public_key.algorithm", equalTo("Ed25519"))
        .body("public_key.public_key_pem", startsWith("-----BEGIN PUBLIC KEY-----"))
        .body("public_key.key_id", notNullValue())
        .body("version", equalTo("0.1.0"));
  }

  @Test
  void shouldReturnHealth() {
    given().when().get("/v1/health").then().statusCode(200).body("status", equalTo("healthy"));
  }

  @Test
  void shouldDelegateToSigningPeer() {
    given()
        .urlEncodingEnabled(false)
        .when()
        .get("/v1/resources/urn:far:remote:TEST-001")
        .then()
        .statusCode(200)
        .body("namespace", equalTo("remote"))
        .body("identifier", equalTo("TEST-001"))
        .body("attributes.volume", notNullValue());
  }

  @Test
  void shouldReturnHistoryForLocalUrn() {
    given()
        .urlEncodingEnabled(false)
        .when()
        .get("/v1/resources/urn:far:stub:TEST-001/history")
        .then()
        .statusCode(200)
        .body("history", hasSize(2))
        .body("history[0].type", equalTo("issuance"))
        .header("Far-Namespace", notNullValue());
  }

  @Test
  void shouldHeadExistingUrn() {
    given()
        .urlEncodingEnabled(false)
        .when()
        .head("/v1/resources/urn:far:stub:TEST-001")
        .then()
        .statusCode(200);
  }

  @Test
  void shouldHeadMissingUrn() {
    given()
        .urlEncodingEnabled(false)
        .when()
        .head("/v1/resources/urn:far:stub:MISSING")
        .then()
        .statusCode(404);
  }

  @Test
  void shouldForwardAuthorizationHeader() throws IOException {
    final var captured = new AtomicReference<String>();
    final var auth = new SigningMockPeer("https://auth.example.com");
    auth.handle(
        "/v1/resources/urn:far:authns:AUTH-001",
        exchange -> {
          captured.set(exchange.getRequestHeaders().getFirst("Authorization"));
          return """
          {
            "urn": "urn:far:authns:AUTH-001",
            "namespace": "authns",
            "identifier": "AUTH-001",
            "attributes": {},
            "resolver": "auth-server",
            "timestamp": "%s"
          }
          """
              .formatted(Instant.now().toString());
        });
    auth.start();
    auth.register(peers, Set.of("authns"));

    try {
      given()
          .urlEncodingEnabled(false)
          .header("Authorization", "Bearer test-token-123")
          .when()
          .get("/v1/resources/urn:far:authns:AUTH-001")
          .then()
          .statusCode(200)
          .body("namespace", equalTo("authns"));

      org.assertj.core.api.Assertions.assertThat(captured.get()).isEqualTo("Bearer test-token-123");
    } finally {
      auth.stop();
    }
  }

  @Test
  void shouldRejectUnsignedPeerResponse() throws IOException {
    final var unsigned = HttpServer.create(new InetSocketAddress(0), 0);
    unsigned.createContext(
        "/v1/resources/urn:far:unsns:UNS-001",
        exchange -> {
          final var body =
              """
              {
                "urn": "urn:far:unsns:UNS-001",
                "namespace": "unsns",
                "identifier": "UNS-001",
                "attributes": {},
                "resolver": "unsigned-server",
                "timestamp": "%s"
              }
              """
                  .formatted(Instant.now().toString());
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          final var bytes = body.getBytes();
          exchange.sendResponseHeaders(200, bytes.length);
          try (final var output = exchange.getResponseBody()) {
            output.write(bytes);
          }
        });
    unsigned.start();

    try {
      final var port = unsigned.getAddress().getPort();
      peers.register(
          new Peer(
              "https://unsigned.example.com",
              "http://localhost:" + port,
              Set.of("unsns"),
              null,
              null,
              Instant.now()));

      given()
          .urlEncodingEnabled(false)
          .when()
          .get("/v1/resources/urn:far:unsns:UNS-001")
          .then()
          .statusCode(502);
    } finally {
      unsigned.stop(0);
    }
  }
}
