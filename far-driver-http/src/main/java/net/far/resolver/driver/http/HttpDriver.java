package net.far.resolver.driver.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import net.far.resolver.json.Converter;
import net.far.resolver.model.*;
import net.far.resolver.spi.Driver;

/**
 * Configurable HTTP driver for resolving attributes against external registries. Uses URL templates
 * and header mappings to adapt to different registry APIs.
 */
public class HttpDriver implements Driver {

  private final HttpDriverConfig config;
  private final HttpClient client;
  private final ObjectMapper mapper;

  public HttpDriver(final HttpDriverConfig config) {
    this.config = config;
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.mapper = new ObjectMapper();
  }

  @Override
  public String name() {
    return config.name();
  }

  @Override
  public Set<String> namespaces() {
    return config.namespaces();
  }

  @Override
  public Optional<Resolution> resolve(final Urn urn) {
    try {
      final var url = expand(urn);
      final var builder =
          HttpRequest.newBuilder(URI.create(url))
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(30));

      config.headers().forEach(builder::header);

      final var request = builder.GET().build();
      final var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        final var attributes = extract(response.body());
        return Optional.of(
            new Resolution(
                urn,
                urn.namespace(),
                urn.identifier(),
                attributes,
                null,
                config.name(),
                Instant.now()));
      }
      if (response.statusCode() == 404) {
        return Optional.empty();
      }
      throw new RuntimeException("Registry returned status " + response.statusCode());
    } catch (final RuntimeException exception) {
      throw exception;
    } catch (final Exception exception) {
      throw new RuntimeException("Failed to resolve via " + config.name(), exception);
    }
  }

  @Override
  public Optional<History> history(final Urn urn) {
    return Optional.empty();
  }

  @Override
  public boolean exists(final Urn urn) {
    return resolve(urn).isPresent();
  }

  /** Expand the URL template with URN components. */
  String expand(final Urn urn) {
    return config
        .template()
        .replace("{namespace}", urn.namespace())
        .replace("{identifier}", urn.identifier())
        .replace("{urn}", urn.toString());
  }

  /** Extract attributes from JSON response body using configured path. */
  Map<String, Attribute> extract(final String body) {
    try {
      var node = mapper.readTree(body);

      if (config.path() != null && !config.path().isEmpty()) {
        for (final var segment : config.path().split("\\.")) {
          if (node == null) {
            break;
          }
          node = node.get(segment);
        }
      }

      if (node == null) {
        return Map.of();
      }

      final var attributes = new LinkedHashMap<String, Attribute>();
      final var fields = node.fields();
      while (fields.hasNext()) {
        final var field = fields.next();
        final var parsed = Converter.value(field.getValue());
        attributes.put(
            field.getKey(),
            new Attribute(field.getKey(), parsed, config.name(), false, Instant.now()));
      }
      return attributes;
    } catch (final Exception exception) {
      return Map.of(
          "raw", new Attribute("raw", Value.of(body), config.name(), false, Instant.now()));
    }
  }
}
