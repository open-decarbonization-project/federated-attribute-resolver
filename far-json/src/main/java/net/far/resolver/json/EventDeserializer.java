package net.far.resolver.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import net.far.resolver.model.Event;

/** Deserializes JSON to {@link Event}, parsing the type label back to {@link Event.EventType}. */
public class EventDeserializer extends JsonDeserializer<Event> {

  @Override
  public Event deserialize(final JsonParser parser, final DeserializationContext context)
      throws IOException {
    final var node = (JsonNode) parser.readValueAsTree();
    return Converter.event(node);
  }
}
