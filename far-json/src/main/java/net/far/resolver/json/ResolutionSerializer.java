package net.far.resolver.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import net.far.resolver.model.Resolution;

/**
 * Serializes {@link Resolution} to JSON matching the FAR OpenAPI schema: {@code urn, namespace,
 * identifier, status, attributes, integrity, resolver, timestamp, delegated}. The integrity object
 * includes digest and algorithm fields.
 */
public class ResolutionSerializer extends JsonSerializer<Resolution> {

  @Override
  public void serialize(
      final Resolution value, final JsonGenerator generator, final SerializerProvider provider)
      throws IOException {
    generator.writeStartObject();
    generator.writeStringField("urn", value.urn().toString());
    generator.writeStringField("namespace", value.namespace());
    generator.writeStringField("identifier", value.identifier());
    if (value.status() != null) {
      generator.writeStringField("status", value.status());
    }
    generator.writeFieldName("attributes");
    provider.defaultSerializeValue(value.attributes(), generator);
    if (value.integrity() != null) {
      generator.writeObjectFieldStart("integrity");
      generator.writeStringField("digest", value.integrity().digest());
      generator.writeStringField("algorithm", value.integrity().algorithm());
      generator.writeEndObject();
    }
    if (value.resolver() != null) {
      generator.writeStringField("resolver", value.resolver());
    }
    generator.writeStringField("timestamp", value.timestamp().toString());
    generator.writeBooleanField("delegated", value.delegated());
    generator.writeEndObject();
  }
}
