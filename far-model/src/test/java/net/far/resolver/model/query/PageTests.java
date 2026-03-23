package net.far.resolver.model.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.far.resolver.model.Resolution;
import net.far.resolver.model.Urn;
import org.junit.jupiter.api.Test;

class PageTests {

  @Test
  void shouldDefaultNullValue() {
    final var page = new Page(null, 0, 0, 25);

    assertThat(page.value()).isEmpty();
  }

  @Test
  void shouldDefaultNegativeSkip() {
    final var page = new Page(List.of(), 0, -1, 25);

    assertThat(page.skip()).isZero();
  }

  @Test
  void shouldDefaultZeroTop() {
    final var page = new Page(List.of(), 0, 0, 0);

    assertThat(page.top()).isEqualTo(Query.DEFAULT_TOP);
  }

  @Test
  void shouldPreserveValues() {
    final var urn = new Urn("stub", "TEST-001");
    final var resolution =
        new Resolution(urn, "stub", "TEST-001", Map.of(), null, "test", Instant.now());
    final var page = new Page(List.of(resolution), 1, 0, 25);

    assertThat(page.value()).hasSize(1);
    assertThat(page.count()).isEqualTo(1);
    assertThat(page.top()).isEqualTo(25);
  }

  @Test
  void shouldCopyValues() {
    final var urn = new Urn("stub", "TEST-001");
    final var resolution =
        new Resolution(urn, "stub", "TEST-001", Map.of(), null, "test", Instant.now());
    final var list = new java.util.ArrayList<>(List.of(resolution));
    final var page = new Page(list, 1, 0, 25);
    list.clear();

    assertThat(page.value()).hasSize(1);
  }
}
