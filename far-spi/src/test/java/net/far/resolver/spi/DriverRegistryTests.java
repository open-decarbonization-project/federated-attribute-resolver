package net.far.resolver.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import net.far.resolver.model.History;
import net.far.resolver.model.Resolution;
import net.far.resolver.model.Urn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverRegistryTests {

  private DriverRegistry registry;

  @BeforeEach
  void setup() {
    registry = new DriverRegistry();
  }

  @Test
  void shouldRegisterAndFindByNamespace() {
    final var driver = new StubDriver("test-driver", Set.of("verra", "miq"));
    registry.register(driver);

    final var found = registry.find("verra");

    assertThat(found).isPresent();
    assertThat(found.get().name()).isEqualTo("test-driver");
  }

  @Test
  void shouldFindBySecondNamespace() {
    final var driver = new StubDriver("test-driver", Set.of("verra", "miq"));
    registry.register(driver);

    final var found = registry.find("miq");

    assertThat(found).isPresent();
    assertThat(found.get().name()).isEqualTo("test-driver");
  }

  @Test
  void shouldReturnEmptyForUnknownNamespace() {
    final var driver = new StubDriver("test-driver", Set.of("verra"));
    registry.register(driver);

    final var found = registry.find("unknown");

    assertThat(found).isEmpty();
  }

  @Test
  void shouldReturnAllRegisteredDrivers() {
    final var first = new StubDriver("first", Set.of("verra"));
    final var second = new StubDriver("second", Set.of("miq"));
    registry.register(first);
    registry.register(second);

    final var all = registry.all();

    assertThat(all).hasSize(2);
    assertThat(all).extracting(Driver::name).containsExactlyInAnyOrder("first", "second");
  }

  @Test
  void shouldReturnAllSupportedNamespaces() {
    final var first = new StubDriver("first", Set.of("verra", "carbon"));
    final var second = new StubDriver("second", Set.of("miq"));
    registry.register(first);
    registry.register(second);

    final var supported = registry.supported();

    assertThat(supported).containsExactlyInAnyOrder("verra", "carbon", "miq");
  }

  @Test
  void shouldReturnTrueForLocalNamespace() {
    final var driver = new StubDriver("test-driver", Set.of("verra"));
    registry.register(driver);

    assertThat(registry.local("verra")).isTrue();
  }

  @Test
  void shouldReturnFalseForNonLocalNamespace() {
    final var driver = new StubDriver("test-driver", Set.of("verra"));
    registry.register(driver);

    assertThat(registry.local("unknown")).isFalse();
  }

  @Test
  void shouldReturnEmptyWhenNoDriversRegistered() {
    assertThat(registry.all()).isEmpty();
    assertThat(registry.supported()).isEmpty();
    assertThat(registry.find("verra")).isEmpty();
  }

  @Test
  void shouldReplaceDriverWithSameName() {
    final var original = new StubDriver("shared", Set.of("verra"));
    final var replacement = new StubDriver("shared", Set.of("miq"));
    registry.register(original);
    registry.register(replacement);

    assertThat(registry.all()).hasSize(1);
    assertThat(registry.find("miq")).isPresent();
  }

  private static class StubDriver implements Driver {

    private final String name;
    private final Set<String> namespaces;

    StubDriver(final String name, final Set<String> namespaces) {
      this.name = name;
      this.namespaces = namespaces;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public Set<String> namespaces() {
      return namespaces;
    }

    @Override
    public Optional<Resolution> resolve(final Urn urn) {
      return Optional.empty();
    }

    @Override
    public Optional<History> history(final Urn urn) {
      return Optional.empty();
    }

    @Override
    public boolean exists(final Urn urn) {
      return false;
    }
  }
}
