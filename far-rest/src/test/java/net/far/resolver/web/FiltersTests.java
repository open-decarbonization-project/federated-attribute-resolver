package net.far.resolver.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import net.far.resolver.model.query.Filter;
import net.far.resolver.model.query.InvalidFilterException;
import net.far.resolver.model.query.Operator;
import org.junit.jupiter.api.Test;

class FiltersTests {

  @Test
  void shouldParseSimpleEquality() {
    final var result = Filters.parse("certification eq 'platinum'");

    assertThat(result).isInstanceOf(Filter.Comparison.class);
    final var comparison = (Filter.Comparison) result;
    assertThat(comparison.field()).isEqualTo("certification");
    assertThat(comparison.operator()).isEqualTo(Operator.EQ);
    assertThat(comparison.operand()).isEqualTo("platinum");
  }

  @Test
  void shouldParseNumericComparison() {
    final var result = Filters.parse("volume gt 50000");

    assertThat(result).isInstanceOf(Filter.Comparison.class);
    final var comparison = (Filter.Comparison) result;
    assertThat(comparison.field()).isEqualTo("volume");
    assertThat(comparison.operator()).isEqualTo(Operator.GT);
    assertThat(comparison.operand()).isEqualTo(50000L);
  }

  @Test
  void shouldParseDecimalComparison() {
    final var result = Filters.parse("methane_intensity lt 0.15");

    final var comparison = (Filter.Comparison) result;
    assertThat(comparison.field()).isEqualTo("methane_intensity");
    assertThat(comparison.operator()).isEqualTo(Operator.LT);
    assertThat(comparison.operand()).isEqualTo(0.15);
  }

  @Test
  void shouldParseNotEquals() {
    final var result = Filters.parse("status ne 'retired'");

    final var comparison = (Filter.Comparison) result;
    assertThat(comparison.operator()).isEqualTo(Operator.NE);
    assertThat(comparison.operand()).isEqualTo("retired");
  }

  @Test
  void shouldParseGreaterOrEqual() {
    final var result = Filters.parse("volume ge 10000");

    final var comparison = (Filter.Comparison) result;
    assertThat(comparison.operator()).isEqualTo(Operator.GE);
  }

  @Test
  void shouldParseLessOrEqual() {
    final var result = Filters.parse("volume le 500");

    final var comparison = (Filter.Comparison) result;
    assertThat(comparison.operator()).isEqualTo(Operator.LE);
  }

  @Test
  void shouldParseAndComposition() {
    final var result = Filters.parse("a eq 'x' and b eq 'y'");

    assertThat(result).isInstanceOf(Filter.And.class);
    final var and = (Filter.And) result;
    assertThat(and.operands()).hasSize(2);
  }

  @Test
  void shouldParseOrComposition() {
    final var result = Filters.parse("a eq 'x' or b eq 'y'");

    assertThat(result).isInstanceOf(Filter.Or.class);
    final var or = (Filter.Or) result;
    assertThat(or.operands()).hasSize(2);
  }

  @Test
  void shouldParseParenthesizedGrouping() {
    final var result = Filters.parse("(a eq 'x' or b eq 'y') and c eq 'z'");

    assertThat(result).isInstanceOf(Filter.And.class);
    final var and = (Filter.And) result;
    assertThat(and.operands()).hasSize(2);
    assertThat(and.operands().getFirst()).isInstanceOf(Filter.Or.class);
    assertThat(and.operands().get(1)).isInstanceOf(Filter.Comparison.class);
  }

  @Test
  void shouldParseInOperator() {
    final var result = Filters.parse("certification in ('platinum','gold')");

    assertThat(result).isInstanceOf(Filter.Comparison.class);
    final var comparison = (Filter.Comparison) result;
    assertThat(comparison.operator()).isEqualTo(Operator.IN);
    assertThat(comparison.operand()).isEqualTo(List.of("platinum", "gold"));
  }

  @Test
  void shouldParseContainsFunction() {
    final var result = Filters.parse("contains(producer,'Appalachian')");

    assertThat(result).isInstanceOf(Filter.Comparison.class);
    final var comparison = (Filter.Comparison) result;
    assertThat(comparison.field()).isEqualTo("producer");
    assertThat(comparison.operator()).isEqualTo(Operator.CONTAINS);
    assertThat(comparison.operand()).isEqualTo("Appalachian");
  }

  @Test
  void shouldExtractSingleNamespace() {
    final var filter = Filters.parse("namespace eq 'ngg' and volume gt 100");

    final var result = Filters.namespaces(filter);

    assertThat(result).containsExactly("ngg");
  }

  @Test
  void shouldExtractMultipleNamespaces() {
    final var filter = Filters.parse("namespace in ('ngg','naesb')");

    final var result = Filters.namespaces(filter);

    assertThat(result).containsExactlyInAnyOrder("ngg", "naesb");
  }

  @Test
  void shouldReturnEmptyNamespacesForBroadSearch() {
    final var filter = Filters.parse("volume gt 100");

    final var result = Filters.namespaces(filter);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldStripNamespaceFromFilter() {
    final var filter = Filters.parse("namespace eq 'ngg' and volume gt 100");

    final var stripped = Filters.strip(filter);

    assertThat(stripped).isInstanceOf(Filter.Comparison.class);
    final var comparison = (Filter.Comparison) stripped;
    assertThat(comparison.field()).isEqualTo("volume");
    assertThat(comparison.operator()).isEqualTo(Operator.GT);
  }

  @Test
  void shouldStripNamespaceFromComplexFilter() {
    final var filter =
        Filters.parse("namespace eq 'ngg' and certification eq 'platinum' and volume gt 50000");

    final var stripped = Filters.strip(filter);

    assertThat(stripped).isInstanceOf(Filter.And.class);
    final var and = (Filter.And) stripped;
    assertThat(and.operands()).hasSize(2);
  }

  @Test
  void shouldReturnNullWhenStripRemovesEverything() {
    final var filter = Filters.parse("namespace eq 'ngg'");

    final var stripped = Filters.strip(filter);

    assertThat(stripped).isNull();
  }

  @Test
  void shouldThrowOnBlankExpression() {
    assertThatThrownBy(() -> Filters.parse("")).isInstanceOf(InvalidFilterException.class);
  }

  @Test
  void shouldThrowOnNullExpression() {
    assertThatThrownBy(() -> Filters.parse(null)).isInstanceOf(InvalidFilterException.class);
  }

  @Test
  void shouldThrowOnMalformedExpression() {
    assertThatThrownBy(() -> Filters.parse("volume ??? 100"))
        .isInstanceOf(InvalidFilterException.class);
  }

  @Test
  void shouldThrowOnUnterminatedString() {
    assertThatThrownBy(() -> Filters.parse("name eq 'unterminated"))
        .isInstanceOf(InvalidFilterException.class);
  }

  @Test
  void shouldThrowOnTrailingTokens() {
    assertThatThrownBy(() -> Filters.parse("a eq 'x' garbage"))
        .isInstanceOf(InvalidFilterException.class);
  }

  @Test
  void shouldParseComplexAndOrChain() {
    final var result =
        Filters.parse("namespace eq 'ngg' and (basin eq 'Appalachian' or basin eq 'Permian')");

    assertThat(result).isInstanceOf(Filter.And.class);
    final var and = (Filter.And) result;
    assertThat(and.operands().get(1)).isInstanceOf(Filter.Or.class);
  }

  @Test
  void shouldParseMultipleAnds() {
    final var result = Filters.parse("a eq 'x' and b eq 'y' and c eq 'z'");

    assertThat(result).isInstanceOf(Filter.And.class);
    final var and = (Filter.And) result;
    assertThat(and.operands()).hasSize(3);
  }

  @Test
  void shouldRespectPrecedenceAndBeforeOr() {
    final var result = Filters.parse("a eq 'x' or b eq 'y' and c eq 'z'");

    assertThat(result).isInstanceOf(Filter.Or.class);
    final var or = (Filter.Or) result;
    assertThat(or.operands().getFirst()).isInstanceOf(Filter.Comparison.class);
    assertThat(or.operands().get(1)).isInstanceOf(Filter.And.class);
  }

  @Test
  void shouldParseInWithNumbers() {
    final var result = Filters.parse("tier in (1,2,3)");

    final var comparison = (Filter.Comparison) result;
    assertThat(comparison.operator()).isEqualTo(Operator.IN);
    assertThat(comparison.operand()).isEqualTo(List.of(1L, 2L, 3L));
  }

  @Test
  void shouldParseNegativeNumber() {
    final var result = Filters.parse("offset gt -100");

    final var comparison = (Filter.Comparison) result;
    assertThat(comparison.operand()).isEqualTo(-100L);
  }
}
