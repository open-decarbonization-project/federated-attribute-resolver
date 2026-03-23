package net.far.resolver.driver.http;

import java.util.Map;
import java.util.Set;

/** Configuration for an HTTP-based registry driver. */
public record HttpDriverConfig(
    String name,
    Set<String> namespaces,
    String template,
    Map<String, String> headers,
    String path) {
  public HttpDriverConfig {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Driver name must not be blank");
    }
    if (template == null || template.isBlank()) {
      throw new IllegalArgumentException("URL template must not be blank");
    }
    if (namespaces == null || namespaces.isEmpty()) {
      throw new IllegalArgumentException("Namespaces must not be empty");
    }
    if (headers == null) headers = Map.of();
    if (path == null) path = "";
  }
}
