package net.far.resolver.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.far.resolver.driver.stub.StubDriver;
import net.far.resolver.model.NamespaceNotFoundException;
import net.far.resolver.spi.DriverRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RouterTests {

  private Router router;

  @BeforeEach
  void setup() {
    final var registry = new DriverRegistry();
    registry.register(new StubDriver());
    router = new Router(registry);
  }

  @Test
  void shouldRouteKnownNamespace() {
    final var result = router.route("stub");

    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("stub");
  }

  @Test
  void shouldReturnEmptyForUnknownNamespace() {
    final var result = router.route("unknown");

    assertThat(result).isEmpty();
  }

  @Test
  void shouldRequireKnownNamespace() {
    final var driver = router.require("stub");

    assertThat(driver.name()).isEqualTo("stub");
  }

  @Test
  void shouldThrowForUnknownRequiredNamespace() {
    assertThatThrownBy(() -> router.require("unknown"))
        .isInstanceOf(NamespaceNotFoundException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void shouldReturnTrueForLocalNamespace() {
    assertThat(router.local("stub")).isTrue();
  }

  @Test
  void shouldReturnFalseForNonLocalNamespace() {
    assertThat(router.local("unknown")).isFalse();
  }
}
