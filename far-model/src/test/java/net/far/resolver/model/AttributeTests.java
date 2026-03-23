package net.far.resolver.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AttributeTests {

  @Test
  void shouldCreateValidAttribute() {
    final var name = "email";
    final var value = Value.of("test@example.com");
    final var source = "idp";
    final var timestamp = Instant.now();
    final var verified = true;

    final var attribute = new Attribute(name, value, source, verified, timestamp);

    assertThat(attribute.name()).isEqualTo(name);
    assertThat(attribute.value()).isEqualTo(value);
    assertThat(attribute.source()).isEqualTo(source);
    assertThat(attribute.verified()).isEqualTo(verified);
    assertThat(attribute.timestamp()).isEqualTo(timestamp);
  }

  @Test
  void shouldRejectNullName() {
    assertThatThrownBy(() -> new Attribute(null, Value.of("v"), "s", true, Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Attribute name must not be blank");
  }

  @Test
  void shouldRejectBlankName() {
    assertThatThrownBy(() -> new Attribute("  ", Value.of("v"), "s", true, Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Attribute name must not be blank");
  }

  @Test
  void shouldAcceptNullValue() {
    final var attribute = new Attribute("n", null, "s", true, Instant.now());
    assertThat(attribute.value()).isNull();
  }

  @Test
  void shouldAcceptNullSource() {
    final var attribute = new Attribute("n", Value.of("v"), null, true, Instant.now());
    assertThat(attribute.source()).isNull();
  }

  @Test
  void shouldAcceptBlankSource() {
    final var attribute = new Attribute("n", Value.of("v"), "  ", true, Instant.now());
    assertThat(attribute.source()).isEqualTo("  ");
  }

  @Test
  void shouldDefaultNullTimestamp() {
    final var before = Instant.now();
    final var attribute = new Attribute("n", Value.of("v"), "s", true, null);
    assertThat(attribute.timestamp()).isNotNull();
    assertThat(attribute.timestamp()).isAfterOrEqualTo(before);
  }
}
