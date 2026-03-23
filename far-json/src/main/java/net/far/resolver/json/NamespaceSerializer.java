package net.far.resolver.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import net.far.resolver.model.Namespace;

/** Serializes {@link Namespace} to JSON for the namespace catalogue response. */
public class NamespaceSerializer extends JsonSerializer<Namespace> {

  @Override
  public void serialize(
      final Namespace value, final JsonGenerator generator, final SerializerProvider provider)
      throws IOException {
    generator.writeStartObject();
    generator.writeStringField("namespace", value.name());
    generator.writeStringField("name", value.description());
    generator.writeStringField("type", value.local() ? "local" : "delegated");
    generator.writeStringField("status", value.status());
    if (value.local()) {
      generator.writeStringField("driver", value.driver());
    } else {
      generator.writeStringField("peer", value.registry());
    }
    generator.writeEndObject();
  }
}
