package net.far.resolver.model.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class FilterTests {

  @Test
  void shouldCreateComparison() {
    final var filter = new Filter.Comparison("volume", Operator.GT, 50000);

    assertThat(filter.field()).isEqualTo("volume");
    assertThat(filter.operator()).isEqualTo(Operator.GT);
    assertThat(filter.operand()).isEqualTo(50000);
  }

  @Test
  void shouldCreateStringComparison() {
    final var filter = new Filter.Comparison("certification", Operator.EQ, "platinum");

    assertThat(filter.field()).isEqualTo("certification");
    assertThat(filter.operand()).isEqualTo("platinum");
  }

  @Test
  void shouldCreateInComparison() {
    final var filter =
        new Filter.Comparison("certification", Operator.IN, List.of("platinum", "gold"));

    assertThat(filter.operator()).isEqualTo(Operator.IN);
    assertThat(filter.operand()).isEqualTo(List.of("platinum", "gold"));
  }

  @Test
  void shouldCreateAnd() {
    final var left = new Filter.Comparison("a", Operator.EQ, "x");
    final var right = new Filter.Comparison("b", Operator.EQ, "y");
    final var filter = new Filter.And(List.of(left, right));

    assertThat(filter.operands()).hasSize(2);
  }

  @Test
  void shouldCreateOr() {
    final var left = new Filter.Comparison("a", Operator.EQ, "x");
    final var right = new Filter.Comparison("b", Operator.EQ, "y");
    final var filter = new Filter.Or(List.of(left, right));

    assertThat(filter.operands()).hasSize(2);
  }

  @Test
  void shouldRejectBlankField() {
    assertThatThrownBy(() -> new Filter.Comparison("", Operator.EQ, "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNullOperator() {
    assertThatThrownBy(() -> new Filter.Comparison("a", null, "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNullOperand() {
    assertThatThrownBy(() -> new Filter.Comparison("a", Operator.EQ, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectAndWithOneOperand() {
    final var single = new Filter.Comparison("a", Operator.EQ, "x");
    assertThatThrownBy(() -> new Filter.And(List.of(single)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectOrWithOneOperand() {
    final var single = new Filter.Comparison("a", Operator.EQ, "x");
    assertThatThrownBy(() -> new Filter.Or(List.of(single)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldBeSealed() {
    final Filter filter = new Filter.Comparison("a", Operator.EQ, "x");
    assertThat(filter).isInstanceOf(Filter.class);

    switch (filter) {
      case Filter.Comparison c -> assertThat(c.field()).isEqualTo("a");
      case Filter.And a -> throw new AssertionError("unexpected");
      case Filter.Or o -> throw new AssertionError("unexpected");
    }
  }
}
