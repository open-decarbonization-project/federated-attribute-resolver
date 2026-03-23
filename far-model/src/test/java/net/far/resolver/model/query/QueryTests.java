package net.far.resolver.model.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryTests {

  @Test
  void shouldUseDefaults() {
    final var query = new Query(null, null, 0, 0, null);

    assertThat(query.namespaces()).isEmpty();
    assertThat(query.top()).isEqualTo(Query.DEFAULT_TOP);
    assertThat(query.skip()).isZero();
    assertThat(query.filter()).isNull();
    assertThat(query.orderby()).isNull();
  }

  @Test
  void shouldClampTopToMax() {
    final var query = new Query(null, null, 1000, 0, null);

    assertThat(query.top()).isEqualTo(Query.MAX_TOP);
  }

  @Test
  void shouldDefaultNegativeTop() {
    final var query = new Query(null, null, -1, 0, null);

    assertThat(query.top()).isEqualTo(Query.DEFAULT_TOP);
  }

  @Test
  void shouldClampNegativeSkip() {
    final var query = new Query(null, null, 25, -5, null);

    assertThat(query.skip()).isZero();
  }

  @Test
  void shouldPreserveNamespaces() {
    final var query = new Query(Set.of("ngg", "naesb"), null, 10, 0, "volume desc");

    assertThat(query.namespaces()).containsExactlyInAnyOrder("ngg", "naesb");
    assertThat(query.top()).isEqualTo(10);
    assertThat(query.orderby()).isEqualTo("volume desc");
  }

  @Test
  void shouldCopyNamespaces() {
    final var namespaces = new java.util.HashSet<>(Set.of("ngg"));
    final var query = new Query(namespaces, null, 25, 0, null);
    namespaces.add("naesb");

    assertThat(query.namespaces()).containsExactly("ngg");
  }
}
