package net.far.resolver.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import net.far.resolver.model.Peer;

/** Serializes {@link Peer} to JSON for the peers list API response. */
public class PeerSerializer extends JsonSerializer<Peer> {

  @Override
  public void serialize(
      final Peer value, final JsonGenerator generator, final SerializerProvider provider)
      throws IOException {
    generator.writeStartObject();
    generator.writeStringField("identity", value.identity());
    generator.writeStringField("endpoint", value.endpoint());
    generator.writeFieldName("namespaces");
    provider.defaultSerializeValue(value.namespaces(), generator);
    if (value.key() != null) {
      generator.writeStringField("public_key", value.key());
    }
    if (value.seen() != null) {
      generator.writeStringField("last_seen", value.seen().toString());
    }
    generator.writeStringField("status", "connected");
    generator.writeNullField("latency_ms");
    generator.writeEndObject();
  }
}
