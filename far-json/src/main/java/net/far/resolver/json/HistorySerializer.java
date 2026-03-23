package net.far.resolver.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import net.far.resolver.model.History;

/** Serializes {@link History} to JSON: urn, namespace, identifier, and events array. */
public class HistorySerializer extends JsonSerializer<History> {

  @Override
  public void serialize(
      final History value, final JsonGenerator generator, final SerializerProvider provider)
      throws IOException {
    generator.writeStartObject();
    generator.writeStringField("urn", value.urn().toString());
    generator.writeStringField("namespace", value.namespace());
    generator.writeStringField("identifier", value.identifier());
    generator.writeFieldName("history");
    provider.defaultSerializeValue(value.events(), generator);
    generator.writeEndObject();
  }
}
