package net.far.resolver.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import net.far.resolver.model.Value;

/** Deserializes JSON to the {@link Value} sealed type hierarchy via {@link Converter#value}. */
public class ValueDeserializer extends JsonDeserializer<Value> {

  @Override
  public Value deserialize(final JsonParser parser, final DeserializationContext context)
      throws IOException {
    final var node = (JsonNode) parser.readValueAsTree();
    return Converter.value(node);
  }
}
