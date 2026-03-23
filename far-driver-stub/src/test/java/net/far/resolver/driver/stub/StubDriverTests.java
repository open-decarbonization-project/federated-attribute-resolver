package net.far.resolver.driver.stub;

import static org.assertj.core.api.Assertions.assertThat;

import net.far.resolver.model.Event;
import net.far.resolver.model.Urn;
import net.far.resolver.model.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StubDriverTests {

  private StubDriver driver;

  @BeforeEach
  void setup() {
    driver = new StubDriver();
  }

  @Test
  void shouldReturnStubNamespace() {
    assertThat(driver.namespaces()).containsExactly("stub");
  }

  @Test
  void shouldReturnStubName() {
    assertThat(driver.name()).isEqualTo("stub");
  }

  @Test
  void shouldSupportStubNamespace() {
    assertThat(driver.supports("stub")).isTrue();
  }

  @Test
  void shouldNotSupportUnknownNamespace() {
    assertThat(driver.supports("unknown")).isFalse();
  }

  @Test
  void shouldResolveTestOne() {
    final var urn = new Urn("stub", "TEST-001");
    final var result = driver.resolve(urn);

    assertThat(result).isPresent();
    assertThat(result.get().namespace()).isEqualTo("stub");
    assertThat(result.get().identifier()).isEqualTo("TEST-001");
    assertThat(result.get().attributes()).containsKey("volume");
    assertThat(result.get().attributes()).containsKey("vintage");
    assertThat(result.get().attributes()).containsKey("methodology");
    assertThat(result.get().attributes()).containsKey("status");
  }

  @Test
  void shouldResolveTestTwo() {
    final var urn = new Urn("stub", "TEST-002");
    final var result = driver.resolve(urn);

    assertThat(result).isPresent();
    assertThat(result.get().namespace()).isEqualTo("stub");
    assertThat(result.get().identifier()).isEqualTo("TEST-002");
    assertThat(result.get().attributes()).containsKey("volume");
    assertThat(result.get().attributes()).containsKey("source");
    assertThat(result.get().attributes()).containsKey("status");
  }

  @Test
  void shouldReturnEmptyForUnknownIdentifier() {
    final var urn = new Urn("stub", "UNKNOWN");
    final var result = driver.resolve(urn);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnTrueForExistingUrn() {
    final var urn = new Urn("stub", "TEST-001");

    assertThat(driver.exists(urn)).isTrue();
  }

  @Test
  void shouldReturnFalseForNonExistingUrn() {
    final var urn = new Urn("stub", "MISSING");

    assertThat(driver.exists(urn)).isFalse();
  }

  @Test
  void shouldReturnHistoryForTestOne() {
    final var urn = new Urn("stub", "TEST-001");
    final var result = driver.history(urn);

    assertThat(result).isPresent();
    assertThat(result.get().events()).hasSize(2);
    assertThat(result.get().events().get(0).type()).isEqualTo(Event.EventType.ISSUANCE);
    assertThat(result.get().events().get(1).type()).isEqualTo(Event.EventType.STATUS_CHANGE);
  }

  @Test
  void shouldReturnHistoryForTestTwo() {
    final var urn = new Urn("stub", "TEST-002");
    final var result = driver.history(urn);

    assertThat(result).isPresent();
    assertThat(result.get().events()).hasSize(3);
    assertThat(result.get().events().get(0).type()).isEqualTo(Event.EventType.ISSUANCE);
    assertThat(result.get().events().get(1).type()).isEqualTo(Event.EventType.TRANSFER);
    assertThat(result.get().events().get(2).type()).isEqualTo(Event.EventType.RETIREMENT);
  }

  @Test
  void shouldReturnEmptyHistoryForUnknownUrn() {
    final var urn = new Urn("stub", "UNKNOWN");
    final var result = driver.history(urn);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnCorrectAttributeValues() {
    final var urn = new Urn("stub", "TEST-001");
    final var result = driver.resolve(urn).orElseThrow();

    final var volume = result.attributes().get("volume");
    assertThat(volume.value()).isInstanceOf(Value.Quantity.class);
    final var quantity = (Value.Quantity) volume.value();
    assertThat(quantity.amount()).isEqualTo(1000);
    assertThat(quantity.unit()).isEqualTo("tCO2e");
    assertThat(volume.verified()).isTrue();
  }
}
