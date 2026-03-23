package net.far.resolver.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import net.far.resolver.model.Value;

/**
 * Serializes the {@link Value} sealed type hierarchy to JSON. Strings, numbers, and booleans write
 * as primitives; quantities write as {@code {amount, unit}}; lists write as arrays; maps write as
 * objects; instants write as ISO-8601 strings.
 */
public class ValueSerializer extends JsonSerializer<Value> {

  @Override
  public void serialize(
      final Value value, final JsonGenerator generator, final SerializerProvider provider)
      throws IOException {
    switch (value) {
      case Value.Text text -> generator.writeString(text.raw());
      case Value.Numeric numeric -> number(numeric.raw(), generator);
      case Value.Bool bool -> generator.writeBoolean(bool.raw());
      case Value.Temporal temporal -> generator.writeString(temporal.raw().toString());
      case Value.Quantity quantity -> {
        generator.writeStartObject();
        generator.writeFieldName("amount");
        number(quantity.amount(), generator);
        generator.writeStringField("unit", quantity.unit());
        generator.writeEndObject();
      }
      case Value.Arr arr -> {
        generator.writeStartArray();
        for (final var item : arr.raw()) {
          serialize(item, generator, provider);
        }
        generator.writeEndArray();
      }
      case Value.Record record -> {
        generator.writeStartObject();
        for (final var entry : record.fields().entrySet()) {
          generator.writeFieldName(entry.getKey());
          serialize(entry.getValue(), generator, provider);
        }
        generator.writeEndObject();
      }
    }
  }

  private void number(final Number value, final JsonGenerator generator) throws IOException {
    switch (value) {
      case Integer i -> generator.writeNumber(i);
      case Long l -> generator.writeNumber(l);
      case Double d -> generator.writeNumber(d);
      case Float f -> generator.writeNumber(f);
      case BigDecimal bd -> generator.writeNumber(bd);
      case BigInteger bi -> generator.writeNumber(bi);
      default -> generator.writeNumber(value.doubleValue());
    }
  }
}
