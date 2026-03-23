package net.far.resolver.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import net.far.resolver.core.PeerRegistry;
import net.far.resolver.model.Peer;
import net.far.resolver.signature.DigestCalculator;
import net.far.resolver.signature.KeyManager;
import net.far.resolver.signature.MessageComponents;
import net.far.resolver.signature.Signer;

/**
 * A mock peer HTTP server that signs its responses with an Ed25519 key, matching the FAR protocol's
 * signature requirements.
 */
final class SigningMockPeer {

  private final HttpServer server;
  private final KeyManager keys;
  private final Signer signer;
  private final String identity;

  SigningMockPeer(final String identity) throws IOException {
    this.server = HttpServer.create(new InetSocketAddress(0), 0);
    this.keys = KeyManager.generate(identity + "#key-1");
    this.signer = new Signer(keys);
    this.identity = identity;
  }

  void handle(final String path, final ResponseBuilder builder) {
    server.createContext(
        path,
        exchange -> {
          final var body = builder.build(exchange);
          respond(exchange, body);
        });
  }

  void start() {
    server.start();
  }

  void stop() {
    server.stop(0);
  }

  int port() {
    return server.getAddress().getPort();
  }

  String endpoint() {
    return "http://localhost:" + port();
  }

  void register(final PeerRegistry registry, final Set<String> namespaces) {
    registry.register(
        new Peer(
            identity,
            endpoint(),
            namespaces,
            keys.pem(),
            List.of(),
            Instant.now(),
            Integer.MAX_VALUE,
            true,
            endpoint() + "/v1",
            5));
  }

  private void respond(final HttpExchange exchange, final String body) throws IOException {
    final var bytes = body.getBytes(StandardCharsets.UTF_8);
    final var digest = DigestCalculator.compute(bytes);
    final var type = "application/json;charset=UTF-8";

    final var headers = new LinkedHashMap<String, String>();
    final var components = new MessageComponents(200, null, null, null, type, digest, headers);
    final var signature = signer.sign(components);

    exchange.getResponseHeaders().set("Content-Type", type);
    exchange.getResponseHeaders().set("Content-Digest", digest);
    exchange.getResponseHeaders().set("Signature", "sig1=:" + signature.value() + ":");
    exchange.getResponseHeaders().set("Signature-Input", signature.input());
    exchange.sendResponseHeaders(200, bytes.length);
    try (final var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  @FunctionalInterface
  interface ResponseBuilder {
    String build(HttpExchange exchange) throws IOException;
  }
}
