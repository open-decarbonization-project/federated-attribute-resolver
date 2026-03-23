package net.far.resolver.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.far.resolver.model.Event;
import net.far.resolver.model.Urn;
import net.far.resolver.model.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConverterTests {

  private ObjectMapper mapper;

  @BeforeEach
  void setup() {
    mapper = new ObjectMapper();
  }

  @Test
  void shouldConvertTextNode() {
    final var node = mapper.getNodeFactory().textNode("hello");
    final var result = Converter.value(node);

    assertThat(result).isInstanceOf(Value.Text.class);
    assertThat(((Value.Text) result).raw()).isEqualTo("hello");
  }

  @Test
  void shouldConvertNumberNode() {
    final var node = mapper.getNodeFactory().numberNode(42);
    final var result = Converter.value(node);

    assertThat(result).isInstanceOf(Value.Numeric.class);
    assertThat(((Value.Numeric) result).raw()).isEqualTo(42);
  }

  @Test
  void shouldConvertBooleanNode() {
    final var node = mapper.getNodeFactory().booleanNode(true);
    final var result = Converter.value(node);

    assertThat(result).isInstanceOf(Value.Bool.class);
    assertThat(((Value.Bool) result).raw()).isTrue();
  }

  @Test
  void shouldConvertQuantityNode() throws Exception {
    final var json = "{\"amount\": 1000, \"unit\": \"tCO2e\"}";
    final var node = mapper.readTree(json);
    final var result = Converter.value(node);

    assertThat(result).isInstanceOf(Value.Quantity.class);
    final var quantity = (Value.Quantity) result;
    assertThat(quantity.amount()).isEqualTo(1000);
    assertThat(quantity.unit()).isEqualTo("tCO2e");
  }

  @Test
  void shouldConvertArrayNode() {
    final var array = mapper.createArrayNode();
    array.add("a");
    array.add(1);
    final var result = Converter.value(array);

    assertThat(result).isInstanceOf(Value.Arr.class);
    final var arr = (Value.Arr) result;
    assertThat(arr.raw()).hasSize(2);
  }

  @Test
  void shouldReturnNullForNullNode() {
    assertThat(Converter.value(null)).isNull();
    assertThat(Converter.value(mapper.nullNode())).isNull();
  }

  @Test
  void shouldConvertAttribute() throws Exception {
    final var json =
        "{\"value\": \"active\", \"source\": \"registry\", \"verified\": true, \"timestamp\":"
            + " \"2024-01-15T00:00:00Z\"}";
    final var node = mapper.readTree(json);
    final var result = Converter.attribute("status", node);

    assertThat(result.name()).isEqualTo("status");
    assertThat(result.source()).isEqualTo("registry");
    assertThat(result.verified()).isTrue();
    assertThat(result.timestamp()).isNotNull();
    assertThat(result.value()).isInstanceOf(Value.Text.class);
  }

  @Test
  void shouldConvertSimpleAttribute() {
    final var node = mapper.getNodeFactory().textNode("wind");
    final var result = Converter.attribute("source", node);

    assertThat(result.name()).isEqualTo("source");
    assertThat(result.value()).isInstanceOf(Value.Text.class);
    assertThat(result.source()).isEqualTo("unknown");
    assertThat(result.verified()).isFalse();
    assertThat(result.timestamp()).isNotNull();
  }

  @Test
  void shouldConvertResolution() throws Exception {
    final var json =
        """
        {
          "urn": "urn:far:stub:TEST-001",
          "namespace": "stub",
          "identifier": "TEST-001",
          "resolver": "test-server",
          "timestamp": "2024-01-15T00:00:00Z",
          "attributes": {
            "status": {"value": "active", "source": "registry", "verified": true}
          }
        }
        """;
    final var node = mapper.readTree(json);
    final var fallback = new Urn("fallback", "ID");
    final var result = Converter.resolution(node, fallback);

    assertThat(result.urn()).isEqualTo(new Urn("stub", "TEST-001"));
    assertThat(result.namespace()).isEqualTo("stub");
    assertThat(result.identifier()).isEqualTo("TEST-001");
    assertThat(result.resolver()).isEqualTo("test-server");
    assertThat(result.attributes()).containsKey("status");
  }

  @Test
  void shouldFallbackUrnWhenMissing() throws Exception {
    final var json = "{\"namespace\": \"stub\", \"identifier\": \"ID\", \"attributes\": {}}";
    final var node = mapper.readTree(json);
    final var fallback = new Urn("stub", "FALLBACK");
    final var result = Converter.resolution(node, fallback);

    assertThat(result.urn()).isEqualTo(fallback);
  }

  @Test
  void shouldConvertHistory() throws Exception {
    final var json =
        """
        {
          "urn": "urn:far:stub:TEST-001",
          "events": [
            {"type": "issuance", "timestamp": "2024-01-15T00:00:00Z", "actor": "registry"},
            {"type": "status_change", "timestamp": "2024-02-01T00:00:00Z", "actor": "auditor"}
          ]
        }
        """;
    final var node = mapper.readTree(json);
    final var fallback = new Urn("stub", "TEST-001");
    final var result = Converter.history(node, fallback);

    assertThat(result.urn()).isEqualTo(new Urn("stub", "TEST-001"));
    assertThat(result.events()).hasSize(2);
    assertThat(result.events().get(0).type()).isEqualTo(Event.EventType.ISSUANCE);
    assertThat(result.events().get(1).type()).isEqualTo(Event.EventType.STATUS_CHANGE);
  }

  @Test
  void shouldConvertHistoryWithNewKey() throws Exception {
    final var json =
        """
        {
          "urn": "urn:far:stub:TEST-001",
          "history": [
            {"type": "issuance", "timestamp": "2024-01-15T00:00:00Z", "actor": "registry"}
          ]
        }
        """;
    final var node = mapper.readTree(json);
    final var fallback = new Urn("stub", "TEST-001");
    final var result = Converter.history(node, fallback);

    assertThat(result.events()).hasSize(1);
    assertThat(result.events().get(0).type()).isEqualTo(Event.EventType.ISSUANCE);
  }

  @Test
  void shouldConvertEvent() throws Exception {
    final var json =
        "{\"type\": \"transfer\", \"timestamp\": \"2024-04-15T00:00:00Z\", \"actor\": \"trader\","
            + " \"details\": {\"to\": \"buyer\"}}";
    final var node = mapper.readTree(json);
    final var result = Converter.event(node);

    assertThat(result.type()).isEqualTo(Event.EventType.TRANSFER);
    assertThat(result.actor()).isEqualTo("trader");
    assertThat(result.details()).containsEntry("to", "buyer");
  }

  @Test
  void shouldConvertRecordNode() throws Exception {
    final var json = "{\"street\": \"123 Main\", \"city\": \"Springfield\"}";
    final var node = mapper.readTree(json);
    final var result = Converter.value(node);

    assertThat(result).isInstanceOf(Value.Record.class);
    final var record = (Value.Record) result;
    assertThat(record.fields()).hasSize(2);
    assertThat(record.fields().get("street")).isEqualTo(Value.of("123 Main"));
    assertThat(record.fields().get("city")).isEqualTo(Value.of("Springfield"));
  }

  @Test
  void shouldConvertPrimitives() {
    assertThat(Converter.primitive(null)).isNull();
    assertThat(Converter.primitive(mapper.nullNode())).isNull();
    assertThat(Converter.primitive(mapper.getNodeFactory().textNode("abc"))).isEqualTo("abc");
    assertThat(Converter.primitive(mapper.getNodeFactory().numberNode(99))).isEqualTo(99);
    assertThat(Converter.primitive(mapper.getNodeFactory().booleanNode(false))).isEqualTo(false);
  }
}
