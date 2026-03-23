package net.far.resolver.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import net.far.resolver.model.Event;

/** Serializes {@link Event} to JSON: type, timestamp, actor, and optional details map. */
public class EventSerializer extends JsonSerializer<Event> {

  @Override
  public void serialize(
      final Event value, final JsonGenerator generator, final SerializerProvider provider)
      throws IOException {
    generator.writeStartObject();
    generator.writeStringField("type", value.type().label());
    if (value.timestamp() != null) {
      generator.writeStringField("timestamp", value.timestamp().toString());
    }
    if (value.actor() != null) {
      generator.writeStringField("actor", value.actor());
    }
    if (value.details() != null && !value.details().isEmpty()) {
      generator.writeFieldName("details");
      provider.defaultSerializeValue(value.details(), generator);
    }
    generator.writeEndObject();
  }
}
