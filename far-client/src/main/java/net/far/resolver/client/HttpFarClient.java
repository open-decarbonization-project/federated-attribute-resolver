package net.far.resolver.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import net.far.resolver.core.PeerRegistry;
import net.far.resolver.json.Converter;
import net.far.resolver.model.DelegationException;
import net.far.resolver.model.History;
import net.far.resolver.model.Peer;
import net.far.resolver.model.Rendition;
import net.far.resolver.model.Resolution;
import net.far.resolver.model.Urn;
import net.far.resolver.model.query.Filter;
import net.far.resolver.model.query.Operator;
import net.far.resolver.model.query.Page;
import net.far.resolver.model.query.Query;
import net.far.resolver.signature.DigestCalculator;
import net.far.resolver.signature.KeyManager;
import net.far.resolver.signature.MessageComponents;
import net.far.resolver.signature.Signer;
import net.far.resolver.signature.Verifier;
import net.far.resolver.spi.FarClient;

/**
 * HTTP-based FAR client for peer-to-peer communication. Signs outbound requests and verifies
 * inbound response signatures per RFC 9421. Signature verification is strict by default — unsigned,
 * tampered, or unverifiable responses are rejected with {@link DelegationException}. The strictness
 * can be relaxed via the {@code strict} constructor parameter for testing against unsigned mocks.
 */
public class HttpFarClient implements FarClient {

  private static final Logger LOGGER = Logger.getLogger(HttpFarClient.class.getName());

  private final HttpClient client;
  private final ObjectMapper mapper;
  private final Signer signer;
  private final PeerRegistry peers;
  private final Verifier verifier;
  private final boolean strict;

  public HttpFarClient(final Signer signer, final PeerRegistry peers, final Verifier verifier) {
    this(signer, peers, verifier, true);
  }

  public HttpFarClient(
      final Signer signer,
      final PeerRegistry peers,
      final Verifier verifier,
      final boolean strict) {
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    this.signer = signer;
    this.peers = peers;
    this.verifier = verifier;
    this.strict = strict;
  }

  public HttpFarClient() {
    this(null, null, null, true);
  }

  private static String encode(final Urn urn) {
    return urn.toString().replace(" ", "%20");
  }

  static String serialize(final Filter filter) {
    return switch (filter) {
      case Filter.Comparison comparison -> comparison(comparison);
      case Filter.And and ->
          and.operands().stream()
              .map(HttpFarClient::serialize)
              .reduce((a, b) -> a + " and " + b)
              .orElse("");
      case Filter.Or or ->
          or.operands().stream()
              .map(HttpFarClient::serialize)
              .reduce((a, b) -> a + " or " + b)
              .map(s -> "(" + s + ")")
              .orElse("");
    };
  }

  private static String comparison(final Filter.Comparison comparison) {
    if (comparison.operator() == Operator.CONTAINS) {
      return "contains(" + comparison.field() + ",'" + escape(comparison.operand()) + "')";
    }
    if (comparison.operator() == Operator.IN) {
      final var items = (List<?>) comparison.operand();
      final var values =
          items.stream()
              .map(v -> v instanceof String ? "'" + escape(v) + "'" : v.toString())
              .reduce((a, b) -> a + "," + b)
              .orElse("");
      return comparison.field() + " in (" + values + ")";
    }
    final var value =
        comparison.operand() instanceof String
            ? "'" + escape(comparison.operand()) + "'"
            : comparison.operand().toString();
    return comparison.field() + " " + comparison.operator().name().toLowerCase() + " " + value;
  }

  private static String escape(final Object value) {
    return value.toString().replace("\\", "\\\\").replace("'", "\\'");
  }

  private static Filter scoped(final Query query) {
    if (query.namespaces().isEmpty()) {
      return query.filter();
    }
    final Filter constraint;
    if (query.namespaces().size() == 1) {
      constraint =
          new Filter.Comparison("namespace", Operator.EQ, query.namespaces().iterator().next());
    } else {
      constraint = new Filter.Comparison("namespace", Operator.IN, List.copyOf(query.namespaces()));
    }
    if (query.filter() == null) {
      return constraint;
    }
    return new Filter.And(List.of(constraint, query.filter()));
  }

  private static String extract(final String header) {
    var value = header;
    if (value.startsWith("sig1=:")) {
      value = value.substring(6);
    }
    if (value.endsWith(":")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  private static ArrayList<String> candidates(final Peer peer, final String keyid) {
    final var result = new ArrayList<String>();
    final var now = Instant.now();
    if (keyid != null && !keyid.isBlank()) {
      if (keyid.equals(peer.keyId()) && peer.key() != null) {
        result.add(peer.key());
        return result;
      }
      for (final var previous : peer.previous()) {
        if (keyid.equals(previous.id())
            && previous.key() != null
            && previous.expires() != null
            && previous.expires().isAfter(now)) {
          result.add(previous.key());
          return result;
        }
      }
    }
    if (peer.key() != null) {
      result.add(peer.key());
    }
    for (final var previous : peer.previous()) {
      if (previous.key() != null && previous.expires() != null && previous.expires().isAfter(now)) {
        result.add(previous.key());
      }
    }
    return result;
  }

  @Override
  public Optional<Resolution> resolve(
      final String endpoint, final Urn urn, final List<String> chain, final String token) {
    try {
      final var uri = URI.create(endpoint + "/resources/" + encode(urn));
      final var builder =
          HttpRequest.newBuilder(uri)
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(30));

      if (chain != null && !chain.isEmpty()) {
        builder.header("Far-Delegation-Chain", String.join(", ", chain));
      }

      if (token != null) {
        builder.header("Authorization", "Bearer " + token);
      }

      final var joined = chain != null && !chain.isEmpty() ? String.join(", ", chain) : null;
      sign(builder, uri, "GET", null, null, token, joined);
      final var request = builder.GET().build();
      final var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        verify(response, endpoint);
        final var node = mapper.readTree(response.body());
        return Optional.of(Converter.resolution(node, urn));
      }
      if (response.statusCode() == 404) {
        return Optional.empty();
      }
      throw new DelegationException(
          "Peer " + endpoint + " returned " + response.statusCode() + ": " + response.body());
    } catch (final DelegationException exception) {
      throw exception;
    } catch (final Exception exception) {
      throw new DelegationException("Failed to resolve via peer: " + endpoint, exception);
    }
  }

  @Override
  public Optional<History> history(
      final String endpoint, final Urn urn, final List<String> chain, final String token) {
    try {
      final var uri = URI.create(endpoint + "/resources/" + encode(urn) + "/history");
      final var builder =
          HttpRequest.newBuilder(uri)
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(30));

      if (chain != null && !chain.isEmpty()) {
        builder.header("Far-Delegation-Chain", String.join(", ", chain));
      }
      if (token != null) {
        builder.header("Authorization", "Bearer " + token);
      }

      final var joined = chain != null && !chain.isEmpty() ? String.join(", ", chain) : null;
      sign(builder, uri, "GET", null, null, token, joined);
      final var response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        verify(response, endpoint);
        final var node = mapper.readTree(response.body());
        return Optional.of(Converter.history(node, urn));
      }
      if (response.statusCode() == 404) {
        return Optional.empty();
      }
      throw new DelegationException(
          "Peer " + endpoint + " returned " + response.statusCode() + ": " + response.body());
    } catch (final DelegationException exception) {
      throw exception;
    } catch (final Exception exception) {
      throw new DelegationException("Failed to fetch history via peer: " + endpoint, exception);
    }
  }

  @Override
  public boolean exists(final String endpoint, final Urn urn, final List<String> chain) {
    try {
      final var uri = URI.create(endpoint + "/resources/" + encode(urn));
      final var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10));

      if (chain != null && !chain.isEmpty()) {
        builder.header("Far-Delegation-Chain", String.join(", ", chain));
      }

      final var joined = chain != null && !chain.isEmpty() ? String.join(", ", chain) : null;
      sign(builder, uri, "HEAD", null, null, null, joined);
      final var request = builder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
      final var response = client.send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() == 200;
    } catch (final Exception exception) {
      return false;
    }
  }

  @Override
  public Optional<Rendition> rendition(
      final String endpoint,
      final Urn urn,
      final String media,
      final List<String> chain,
      final String token) {
    try {
      final var uri = URI.create(endpoint + "/resources/" + encode(urn));
      final var builder =
          HttpRequest.newBuilder(uri).header("Accept", media).timeout(Duration.ofSeconds(30));

      if (chain != null && !chain.isEmpty()) {
        builder.header("Far-Delegation-Chain", String.join(", ", chain));
      }
      if (token != null) {
        builder.header("Authorization", "Bearer " + token);
      }

      final var joined = chain != null && !chain.isEmpty() ? String.join(", ", chain) : null;
      sign(builder, uri, "GET", null, null, token, joined);
      final var response =
          client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() == 200) {
        final var type = response.headers().firstValue("Content-Type").orElse(media);
        return Optional.of(new Rendition(response.body(), type, urn, Instant.now()));
      }
      if (response.statusCode() == 404) {
        return Optional.empty();
      }
      throw new DelegationException("Peer " + endpoint + " returned " + response.statusCode());
    } catch (final DelegationException exception) {
      throw exception;
    } catch (final Exception exception) {
      throw new DelegationException("Failed to fetch rendition via peer: " + endpoint, exception);
    }
  }

  @Override
  public Page query(
      final String endpoint, final Query query, final List<String> chain, final String token) {
    try {
      final var params = new StringBuilder();
      final var filter = scoped(query);
      if (filter != null) {
        params
            .append("$filter=")
            .append(URLEncoder.encode(serialize(filter), StandardCharsets.UTF_8));
      }
      params.append("&$top=").append(query.top());
      params.append("&$skip=").append(query.skip());
      if (query.orderby() != null) {
        params
            .append("&$orderby=")
            .append(URLEncoder.encode(query.orderby(), StandardCharsets.UTF_8));
      }
      final var uri = URI.create(endpoint + "/resources?" + params);
      final var builder =
          HttpRequest.newBuilder(uri)
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(30));

      if (chain != null && !chain.isEmpty()) {
        builder.header("Far-Delegation-Chain", String.join(", ", chain));
      }
      if (token != null) {
        builder.header("Authorization", "Bearer " + token);
      }

      final var joined = chain != null && !chain.isEmpty() ? String.join(", ", chain) : null;
      sign(builder, uri, "GET", null, null, token, joined);
      final var request = builder.GET().build();
      final var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        verify(response, endpoint);
        final var node = mapper.readTree(response.body());
        return Converter.page(node);
      }
      if (response.statusCode() == 404) {
        return new Page(List.of(), 0, query.skip(), query.top());
      }
      throw new DelegationException(
          "Peer " + endpoint + " returned " + response.statusCode() + ": " + response.body());
    } catch (final DelegationException exception) {
      throw exception;
    } catch (final Exception exception) {
      throw new DelegationException("Failed to query via peer: " + endpoint, exception);
    }
  }

  @Override
  public Optional<Map<String, Object>> configuration(final String endpoint) {
    try {
      final var uri = URI.create(endpoint + "/v1/peers/configuration");
      final var request =
          HttpRequest.newBuilder(uri)
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(10))
              .GET()
              .build();

      final var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        final var type = new TypeReference<Map<String, Object>>() {};
        return Optional.of(mapper.readValue(response.body(), type));
      }
      return Optional.empty();
    } catch (final Exception exception) {
      return Optional.empty();
    }
  }

  private void sign(
      final HttpRequest.Builder builder,
      final URI uri,
      final String method,
      final String type,
      final String digest,
      final String token,
      final String chain) {
    if (signer == null) return;
    final var headers = new LinkedHashMap<String, String>();
    if (token != null) {
      headers.put("authorization", "Bearer " + token);
    }
    if (chain != null) {
      headers.put("far-delegation-chain", chain);
    }
    final var components =
        new MessageComponents(method, uri.toString(), uri.getAuthority(), type, digest, headers);
    final var signature = signer.sign(components);
    builder.header("Signature-Input", signature.input());
    builder.header("Signature", "sig1=:" + signature.value() + ":");
  }

  private void verify(final HttpResponse<String> response, final String endpoint) {
    if (verifier == null || peers == null) return;
    final var input = response.headers().firstValue("Signature-Input").orElse(null);
    final var signature = response.headers().firstValue("Signature").orElse(null);
    final var digest = response.headers().firstValue("Content-Digest").orElse(null);
    if (input == null || signature == null) {
      reject("Peer response from " + endpoint + " is not signed");
      return;
    }

    if (digest != null) {
      final var body = response.body().getBytes(StandardCharsets.UTF_8);
      if (!DigestCalculator.validate(body, digest)) {
        reject("Content-Digest mismatch from peer: " + endpoint);
        return;
      }
    }

    final var id = verifier.keyid(input);
    final var signer = id.contains("#") ? id.substring(0, id.indexOf('#')) : id;
    final var peer = peers.get(signer).or(() -> peers.match(signer));
    if (peer.isEmpty() || peer.get().key() == null) {
      reject("Unknown or keyless peer: " + signer + " (" + endpoint + ")");
      return;
    }

    final var value = extract(signature);
    final var type = response.headers().firstValue("Content-Type").orElse(null);
    final var headers = new LinkedHashMap<String, String>();
    response.headers().firstValue("Far-Resolver").ifPresent(v -> headers.put("far-resolver", v));
    response.headers().firstValue("Far-Namespace").ifPresent(v -> headers.put("far-namespace", v));
    final var components =
        new MessageComponents(response.statusCode(), null, null, null, type, digest, headers);

    for (final var candidate : candidates(peer.get(), id)) {
      try {
        verifier.verify(components, value, input, KeyManager.parse(candidate));
        return;
      } catch (final Exception ignored) {
      }
    }
    reject("Response signature verification failed from peer: " + endpoint);
  }

  private void reject(final String message) {
    if (strict) {
      throw new DelegationException(message);
    }
    LOGGER.warning(() -> message);
  }
}
