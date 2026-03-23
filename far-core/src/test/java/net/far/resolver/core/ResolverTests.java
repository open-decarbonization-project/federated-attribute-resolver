package net.far.resolver.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.far.resolver.driver.stub.StubDriver;
import net.far.resolver.model.Event;
import net.far.resolver.model.IdentifierNotFoundException;
import net.far.resolver.model.NamespaceNotFoundException;
import net.far.resolver.model.UnsupportedFormatException;
import net.far.resolver.model.Urn;
import net.far.resolver.spi.DriverRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResolverTests {

  private Resolver resolver;

  @BeforeEach
  void setup() {
    final var registry = new DriverRegistry();
    registry.register(new StubDriver());
    final var router = new Router(registry);
    final var peers = new PeerRegistry();
    final var delegator = new Delegator(peers, null, "test-server", 5);
    resolver = new Resolver(router, delegator, peers);
  }

  @Test
  void shouldResolveLocalUrn() {
    final var urn = new Urn("stub", "TEST-001");
    final var result = resolver.resolve(urn);

    assertThat(result).isNotNull();
    assertThat(result.namespace()).isEqualTo("stub");
    assertThat(result.identifier()).isEqualTo("TEST-001");
    assertThat(result.attributes()).containsKey("volume");
    assertThat(result.attributes()).containsKey("vintage");
    assertThat(result.attributes()).containsKey("methodology");
    assertThat(result.attributes()).containsKey("status");
  }

  @Test
  void shouldResolveHistory() {
    final var urn = new Urn("stub", "TEST-001");
    final var result = resolver.history(urn);

    assertThat(result).isNotNull();
    assertThat(result.events()).hasSize(2);
    assertThat(result.events().get(0).type()).isEqualTo(Event.EventType.ISSUANCE);
    assertThat(result.events().get(1).type()).isEqualTo(Event.EventType.STATUS_CHANGE);
  }

  @Test
  void shouldCheckExistsForKnownUrn() {
    final var urn = new Urn("stub", "TEST-001");

    assertThat(resolver.exists(urn)).isTrue();
  }

  @Test
  void shouldCheckExistsForUnknownUrn() {
    final var urn = new Urn("stub", "MISSING");

    assertThat(resolver.exists(urn)).isFalse();
  }

  @Test
  void shouldReturnFalseForUnknownNamespaceExists() {
    final var urn = new Urn("unknown", "ID-1");

    assertThat(resolver.exists(urn)).isFalse();
  }

  @Test
  void shouldThrowNamespaceNotFoundForUnknownNamespace() {
    final var urn = new Urn("unknown", "ID-1");

    assertThatThrownBy(() -> resolver.resolve(urn))
        .isInstanceOf(NamespaceNotFoundException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void shouldThrowIdentifierNotFoundForUnknownIdentifier() {
    final var urn = new Urn("stub", "NONEXISTENT");

    assertThatThrownBy(() -> resolver.resolve(urn))
        .isInstanceOf(IdentifierNotFoundException.class)
        .hasMessageContaining("NONEXISTENT");
  }

  @Test
  void shouldThrowIdentifierNotFoundForHistoryOfUnknownIdentifier() {
    final var urn = new Urn("stub", "NONEXISTENT");

    assertThatThrownBy(() -> resolver.history(urn))
        .isInstanceOf(IdentifierNotFoundException.class)
        .hasMessageContaining("NONEXISTENT");
  }

  @Test
  void shouldThrowNamespaceNotFoundForHistoryOfUnknownNamespace() {
    final var urn = new Urn("unknown", "ID-1");

    assertThatThrownBy(() -> resolver.history(urn))
        .isInstanceOf(NamespaceNotFoundException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void shouldRenderKnownUrnAsHtml() {
    final var urn = new Urn("stub", "TEST-001");
    final var rendition = resolver.rendition(urn, "text/html");

    assertThat(rendition).isNotNull();
    assertThat(rendition.media()).isEqualTo("text/html");
    assertThat(rendition.urn()).isEqualTo(urn);
    assertThat(new String(rendition.content())).contains("<html>");
  }

  @Test
  void shouldThrowUnsupportedFormatForUnknownMedia() {
    final var urn = new Urn("stub", "TEST-001");

    assertThatThrownBy(() -> resolver.rendition(urn, "application/pdf"))
        .isInstanceOf(UnsupportedFormatException.class)
        .hasMessageContaining("application/pdf");
  }

  @Test
  void shouldThrowNamespaceNotFoundForRenderOfUnknownNamespace() {
    final var urn = new Urn("unknown", "ID-1");

    assertThatThrownBy(() -> resolver.rendition(urn, "text/html"))
        .isInstanceOf(NamespaceNotFoundException.class);
  }
}
