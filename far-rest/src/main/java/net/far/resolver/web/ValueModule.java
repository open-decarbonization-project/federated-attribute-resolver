package net.far.resolver.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import net.far.resolver.json.EventDeserializer;
import net.far.resolver.json.EventSerializer;
import net.far.resolver.json.HistorySerializer;
import net.far.resolver.json.NamespaceSerializer;
import net.far.resolver.json.PeerSerializer;
import net.far.resolver.json.ResolutionSerializer;
import net.far.resolver.json.ValueDeserializer;
import net.far.resolver.json.ValueSerializer;
import net.far.resolver.model.Event;
import net.far.resolver.model.History;
import net.far.resolver.model.Namespace;
import net.far.resolver.model.Peer;
import net.far.resolver.model.Resolution;
import net.far.resolver.model.Value;

@ApplicationScoped
public class ValueModule implements ObjectMapperCustomizer {

  @Override
  public void customize(final ObjectMapper mapper) {
    final var module = new SimpleModule("ValueModule");
    module.addSerializer(Value.class, new ValueSerializer());
    module.addDeserializer(Value.class, new ValueDeserializer());
    module.addSerializer(History.class, new HistorySerializer());
    module.addSerializer(Event.class, new EventSerializer());
    module.addDeserializer(Event.class, new EventDeserializer());
    module.addSerializer(Resolution.class, new ResolutionSerializer());
    module.addSerializer(Namespace.class, new NamespaceSerializer());
    module.addSerializer(Peer.class, new PeerSerializer());
    mapper.registerModule(module);
  }
}
