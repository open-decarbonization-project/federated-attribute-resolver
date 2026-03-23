package net.far.resolver.driver.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import net.far.resolver.model.Urn;
import net.far.resolver.model.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpDriverTests {

  private HttpDriverConfig config;
  private HttpDriver driver;

  @BeforeEach
  void setup() {
    config =
        new HttpDriverConfig(
            "test-registry",
            Set.of("verra", "miq"),
            "https://registry.example.com/api/{namespace}/{identifier}",
            Map.of("Authorization", "Bearer token123"),
            "$.data");
    driver = new HttpDriver(config);
  }

  @Test
  void shouldRejectBlankName() {
    assertThatThrownBy(
            () ->
                new HttpDriverConfig(
                    "", Set.of("verra"), "https://example.com/{identifier}", Map.of(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Driver name must not be blank");
  }

  @Test
  void shouldRejectNullName() {
    assertThatThrownBy(
            () ->
                new HttpDriverConfig(
                    null, Set.of("verra"), "https://example.com/{identifier}", Map.of(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Driver name must not be blank");
  }

  @Test
  void shouldRejectBlankTemplate() {
    assertThatThrownBy(() -> new HttpDriverConfig("test", Set.of("verra"), "", Map.of(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("URL template must not be blank");
  }

  @Test
  void shouldRejectNullTemplate() {
    assertThatThrownBy(() -> new HttpDriverConfig("test", Set.of("verra"), null, Map.of(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("URL template must not be blank");
  }

  @Test
  void shouldRejectEmptyNamespaces() {
    assertThatThrownBy(
            () ->
                new HttpDriverConfig(
                    "test", Set.of(), "https://example.com/{identifier}", Map.of(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Namespaces must not be empty");
  }

  @Test
  void shouldRejectNullNamespaces() {
    assertThatThrownBy(
            () ->
                new HttpDriverConfig(
                    "test", null, "https://example.com/{identifier}", Map.of(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Namespaces must not be empty");
  }

  @Test
  void shouldDefaultNullHeadersToEmptyMap() {
    final var result =
        new HttpDriverConfig("test", Set.of("verra"), "https://example.com/{identifier}", null, "");

    assertThat(result.headers()).isNotNull();
    assertThat(result.headers()).isEmpty();
  }

  @Test
  void shouldDefaultNullPathToEmptyString() {
    final var result =
        new HttpDriverConfig(
            "test", Set.of("verra"), "https://example.com/{identifier}", Map.of(), null);

    assertThat(result.path()).isNotNull();
    assertThat(result.path()).isEmpty();
  }

  @Test
  void shouldExpandNamespaceAndIdentifier() {
    final var urn = new Urn("verra", "VCS-1234");
    final var result = driver.expand(urn);

    assertThat(result).isEqualTo("https://registry.example.com/api/verra/VCS-1234");
  }

  @Test
  void shouldExpandUrnPlaceholder() {
    final var template =
        new HttpDriverConfig(
            "test", Set.of("verra"), "https://example.com/lookup?urn={urn}", Map.of(), "");
    final var instance = new HttpDriver(template);
    final var urn = new Urn("verra", "VCS-1234");
    final var result = instance.expand(urn);

    assertThat(result).isEqualTo("https://example.com/lookup?urn=urn:far:verra:VCS-1234");
  }

  @Test
  void shouldExpandIdentifierOnly() {
    final var template =
        new HttpDriverConfig(
            "test", Set.of("miq"), "https://miq.example.com/certs/{identifier}", Map.of(), "");
    final var instance = new HttpDriver(template);
    final var urn = new Urn("miq", "CERT-5678");
    final var result = instance.expand(urn);

    assertThat(result).isEqualTo("https://miq.example.com/certs/CERT-5678");
  }

  @Test
  void shouldHandleTemplateWithNoPlaceholders() {
    final var template =
        new HttpDriverConfig("test", Set.of("verra"), "https://example.com/static", Map.of(), "");
    final var instance = new HttpDriver(template);
    final var urn = new Urn("verra", "VCS-1234");
    final var result = instance.expand(urn);

    assertThat(result).isEqualTo("https://example.com/static");
  }

  @Test
  void shouldReturnConfiguredName() {
    assertThat(driver.name()).isEqualTo("test-registry");
  }

  @Test
  void shouldReturnConfiguredNamespaces() {
    assertThat(driver.namespaces()).containsExactlyInAnyOrder("verra", "miq");
  }

  @Test
  void shouldSupportConfiguredNamespace() {
    assertThat(driver.supports("verra")).isTrue();
    assertThat(driver.supports("miq")).isTrue();
  }

  @Test
  void shouldNotSupportUnconfiguredNamespace() {
    assertThat(driver.supports("unknown")).isFalse();
  }

  @Test
  void shouldExtractQuantityFromObject() {
    final var json =
        """
        {"volume": {"amount": 1000, "unit": "tCO2e"}}
        """;
    final var simple =
        new HttpDriver(
            new HttpDriverConfig(
                "test", Set.of("verra"), "https://example.com/{identifier}", Map.of(), ""));

    final var result = simple.extract(json);

    assertThat(result).containsKey("volume");
    assertThat(result.get("volume").value()).isInstanceOf(Value.Quantity.class);
    final var quantity = (Value.Quantity) result.get("volume").value();
    assertThat(quantity.amount()).isEqualTo(1000);
    assertThat(quantity.unit()).isEqualTo("tCO2e");
  }

  @Test
  void shouldExtractPlainNumberAsNumeric() {
    final var json =
        """
        {"count": 42}
        """;
    final var simple =
        new HttpDriver(
            new HttpDriverConfig(
                "test", Set.of("verra"), "https://example.com/{identifier}", Map.of(), ""));

    final var result = simple.extract(json);

    assertThat(result).containsKey("count");
    assertThat(result.get("count").value()).isInstanceOf(Value.Numeric.class);
    assertThat(((Value.Numeric) result.get("count").value()).raw()).isEqualTo(42);
  }

  @Test
  void shouldExtractStringAsText() {
    final var json =
        """
        {"status": "active"}
        """;
    final var simple =
        new HttpDriver(
            new HttpDriverConfig(
                "test", Set.of("verra"), "https://example.com/{identifier}", Map.of(), ""));

    final var result = simple.extract(json);

    assertThat(result).containsKey("status");
    assertThat(result.get("status").value()).isInstanceOf(Value.Text.class);
    assertThat(((Value.Text) result.get("status").value()).raw()).isEqualTo("active");
  }

  @Test
  void shouldExtractBoolean() {
    final var json =
        """
        {"verified": true}
        """;
    final var simple =
        new HttpDriver(
            new HttpDriverConfig(
                "test", Set.of("verra"), "https://example.com/{identifier}", Map.of(), ""));

    final var result = simple.extract(json);

    assertThat(result).containsKey("verified");
    assertThat(result.get("verified").value()).isInstanceOf(Value.Bool.class);
    assertThat(((Value.Bool) result.get("verified").value()).raw()).isTrue();
  }

  @Test
  void shouldReturnEmptyHistory() {
    final var urn = new Urn("verra", "VCS-1234");

    assertThat(driver.history(urn)).isEmpty();
  }
}
