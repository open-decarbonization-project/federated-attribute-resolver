package net.far.resolver.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValueTests {

  @Test
  void shouldCreateText() {
    final var value = Value.of("hello");

    assertThat(value).isInstanceOf(Value.Text.class);
    assertThat(((Value.Text) value).raw()).isEqualTo("hello");
  }

  @Test
  void shouldCreateNumeric() {
    final var value = Value.of(42);

    assertThat(value).isInstanceOf(Value.Numeric.class);
    assertThat(((Value.Numeric) value).raw()).isEqualTo(42);
  }

  @Test
  void shouldCreateNumericFromDouble() {
    final var value = Value.of(3.14);

    assertThat(value).isInstanceOf(Value.Numeric.class);
    assertThat(((Value.Numeric) value).raw()).isEqualTo(3.14);
  }

  @Test
  void shouldCreateBool() {
    final var value = Value.of(true);

    assertThat(value).isInstanceOf(Value.Bool.class);
    assertThat(((Value.Bool) value).raw()).isTrue();
  }

  @Test
  void shouldCreateBoolFalse() {
    final var value = Value.of(false);

    assertThat(value).isInstanceOf(Value.Bool.class);
    assertThat(((Value.Bool) value).raw()).isFalse();
  }

  @Test
  void shouldCreateTemporal() {
    final var instant = Instant.parse("2024-01-15T00:00:00Z");
    final var value = Value.of(instant);

    assertThat(value).isInstanceOf(Value.Temporal.class);
    assertThat(((Value.Temporal) value).raw()).isEqualTo(instant);
  }

  @Test
  void shouldCreateQuantity() {
    final var value = Value.of(1000, "tCO2e");

    assertThat(value).isInstanceOf(Value.Quantity.class);
    final var quantity = (Value.Quantity) value;
    assertThat(quantity.amount()).isEqualTo(1000);
    assertThat(quantity.unit()).isEqualTo("tCO2e");
  }

  @Test
  void shouldCreateArr() {
    final var value = Value.of(List.of(Value.of("a"), Value.of(1)));

    assertThat(value).isInstanceOf(Value.Arr.class);
    final var arr = (Value.Arr) value;
    assertThat(arr.raw()).hasSize(2);
    assertThat(arr.raw().get(0)).isEqualTo(Value.of("a"));
    assertThat(arr.raw().get(1)).isEqualTo(Value.of(1));
  }

  @Test
  void shouldMakeArrImmutable() {
    final var source = new ArrayList<>(List.of(Value.of("a")));
    final var value = Value.of(source);
    source.add(Value.of("b"));

    final var arr = (Value.Arr) value;
    assertThat(arr.raw()).hasSize(1);
  }

  @Test
  void shouldRejectModificationOfArr() {
    final var value = Value.of(List.of(Value.of("a")));
    final var arr = (Value.Arr) value;

    assertThatThrownBy(() -> arr.raw().add(Value.of("b")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldRejectNullText() {
    assertThatThrownBy(() -> Value.of((String) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  void shouldRejectNullNumeric() {
    assertThatThrownBy(() -> Value.of((Number) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  void shouldRejectNullTemporal() {
    assertThatThrownBy(() -> Value.of((Instant) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  void shouldRejectNullQuantityAmount() {
    assertThatThrownBy(() -> Value.of(null, "tCO2e"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("amount must not be null");
  }

  @Test
  void shouldRejectNullQuantityUnit() {
    assertThatThrownBy(() -> Value.of(1000, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unit must not be blank");
  }

  @Test
  void shouldRejectBlankQuantityUnit() {
    assertThatThrownBy(() -> Value.of(1000, "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unit must not be blank");
  }

  @Test
  void shouldRejectNullArr() {
    assertThatThrownBy(() -> Value.of((List<Value>) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  void shouldCreateRecord() {
    final var value = Value.of(Map.of("name", Value.of("Alice"), "age", Value.of(30)));

    assertThat(value).isInstanceOf(Value.Record.class);
    final var record = (Value.Record) value;
    assertThat(record.fields()).hasSize(2);
    assertThat(record.fields().get("name")).isEqualTo(Value.of("Alice"));
    assertThat(record.fields().get("age")).isEqualTo(Value.of(30));
  }

  @Test
  void shouldMakeRecordImmutable() {
    final var source = new HashMap<>(Map.of("a", Value.of("x")));
    final var value = Value.of(source);
    source.put("b", Value.of("y"));

    final var record = (Value.Record) value;
    assertThat(record.fields()).hasSize(1);
  }

  @Test
  void shouldRejectModificationOfRecord() {
    final var value = Value.of(Map.of("a", Value.of("x")));
    final var record = (Value.Record) value;

    assertThatThrownBy(() -> record.fields().put("b", Value.of("y")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldRejectNullRecord() {
    assertThatThrownBy(() -> Value.of((Map<String, Value>) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
  }
}
