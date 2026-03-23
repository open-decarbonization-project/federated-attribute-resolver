package net.far.resolver.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.far.resolver.core.Resolver;
import net.far.resolver.model.*;
import net.far.resolver.spi.DriverRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResolveResourceTests {

  private ResolveResource resource;
  private Resolver resolver;
  private DriverRegistry registry;

  @BeforeEach
  void setup() {
    resolver = mock(Resolver.class);
    registry = mock(DriverRegistry.class);
    resource = new ResolveResource();
    resource.resolver = resolver;
    resource.registry = registry;
    resource.identity = "https://far.example.com";
  }

  @Test
  void shouldResolveKnownUrn() {
    final var urn = new Urn("stub", "TEST-001");
    final var resolution =
        new Resolution(
            urn,
            "stub",
            "TEST-001",
            Map.of(
                "volume", new Attribute("volume", Value.of("1000"), "stub", true, Instant.now())),
            null,
            "stub",
            Instant.now());
    when(resolver.resolve(eq(urn), any(), isNull())).thenReturn(resolution);

    final var response =
        resource.resolve("urn:far:stub:TEST-001", null, null, null, null, "allow", "full");

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo(resolution);
    assertThat(response.getHeaderString("Far-Resolver")).isEqualTo("https://far.example.com");
  }

  @Test
  void shouldReturn404ForUnknownNamespace() {
    when(resolver.resolve(any(Urn.class), any(), isNull()))
        .thenThrow(new NamespaceNotFoundException("unknown"));

    try {
      resource.resolve("urn:far:unknown:ID-1", null, null, null, null, "allow", "full");
    } catch (final NamespaceNotFoundException exception) {
      assertThat(exception.code()).isEqualTo("namespace_not_found");
    }
  }

  @Test
  void shouldReturn400ForInvalidUrn() {
    try {
      resource.resolve("invalid-urn", null, null, null, null, "allow", "full");
    } catch (final InvalidUrnException exception) {
      assertThat(exception.code()).isEqualTo("invalid_urn");
    }
  }

  @Test
  void shouldResolveHistory() {
    final var urn = new Urn("stub", "TEST-001");
    final var history =
        new History(
            urn,
            List.of(
                new Event(Event.EventType.ISSUANCE, Instant.now(), "registry", Map.of()),
                new Event(Event.EventType.STATUS_CHANGE, Instant.now(), "auditor", Map.of())));
    when(resolver.history(eq(urn), eq(List.of()), isNull())).thenReturn(history);

    final var response =
        resource.history("urn:far:stub:TEST-001", null, 100, 0, null, null, null, null);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeaderString("Far-Resolver")).isEqualTo("https://far.example.com");
    assertThat(response.getHeaderString("Far-Namespace")).isEqualTo("stub");
  }

  @Test
  void shouldReturnOkForExistingUrn() {
    final var urn = new Urn("stub", "TEST-001");
    when(resolver.exists(eq(urn), eq(List.of()))).thenReturn(true);

    final var response = resource.exists("urn:far:stub:TEST-001", null);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeaderString("Far-Resolver")).isEqualTo("https://far.example.com");
    assertThat(response.getHeaderString("Far-Namespace")).isEqualTo("stub");
  }

  @Test
  void shouldReturn404ForMissingUrn() {
    final var urn = new Urn("stub", "MISSING");
    when(resolver.exists(eq(urn), eq(List.of()))).thenReturn(false);

    final var response = resource.exists("urn:far:stub:MISSING", null);

    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  void shouldParseDelegationChain() {
    final var urn = new Urn("stub", "TEST-001");
    final var resolution =
        new Resolution(urn, "stub", "TEST-001", Map.of(), null, "stub", Instant.now());
    when(resolver.resolve(eq(urn), eq(List.of("peer-a", "peer-b")), isNull()))
        .thenReturn(resolution);

    final var response =
        resource.resolve(
            "urn:far:stub:TEST-001", "peer-a, peer-b", null, null, null, "allow", "full");

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void shouldForwardBearerToken() {
    final var urn = new Urn("stub", "TEST-001");
    final var resolution =
        new Resolution(urn, "stub", "TEST-001", Map.of(), null, "stub", Instant.now());
    when(resolver.resolve(eq(urn), any(), eq("my-token"))).thenReturn(resolution);

    final var response =
        resource.resolve(
            "urn:far:stub:TEST-001", null, "Bearer my-token", null, null, "allow", "full");

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void shouldReturnHtmlRendition() {
    final var urn = new Urn("stub", "TEST-001");
    final var html = "<html><body>test</body></html>";
    final var rendition =
        new Rendition(html.getBytes(StandardCharsets.UTF_8), "text/html", urn, Instant.now());
    when(resolver.rendition(eq(urn), eq("text/html"), eq(List.of()), isNull()))
        .thenReturn(rendition);

    final var response =
        resource.resolve("urn:far:stub:TEST-001", null, null, "text/html", null, "allow", "full");

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getMediaType().toString()).isEqualTo("text/html");
    assertThat(response.getHeaderString("Far-Resolver")).isEqualTo("https://far.example.com");
  }

  @Test
  void shouldReturnPdfRendition() {
    final var urn = new Urn("stub", "TEST-001");
    final var rendition =
        new Rendition(new byte[] {0x25, 0x50, 0x44, 0x46}, "application/pdf", urn, Instant.now());
    when(resolver.rendition(eq(urn), eq("application/pdf"), eq(List.of()), isNull()))
        .thenReturn(rendition);

    final var response =
        resource.resolve(
            "urn:far:stub:TEST-001", null, null, "application/pdf", null, "allow", "full");

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getMediaType().toString()).isEqualTo("application/pdf");
  }

  @Test
  void shouldDefaultToJsonWhenNoAcceptHeader() {
    final var urn = new Urn("stub", "TEST-001");
    final var resolution =
        new Resolution(urn, "stub", "TEST-001", Map.of(), null, "stub", Instant.now());
    when(resolver.resolve(eq(urn), any(), isNull())).thenReturn(resolution);

    final var response =
        resource.resolve("urn:far:stub:TEST-001", null, null, null, null, "allow", "full");

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo(resolution);
  }

  @Test
  void shouldDefaultToJsonForWildcardAccept() {
    final var urn = new Urn("stub", "TEST-001");
    final var resolution =
        new Resolution(urn, "stub", "TEST-001", Map.of(), null, "stub", Instant.now());
    when(resolver.resolve(eq(urn), any(), isNull())).thenReturn(resolution);

    final var response =
        resource.resolve("urn:far:stub:TEST-001", null, null, "*/*", null, "allow", "full");

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo(resolution);
  }

  @Test
  void shouldThrowForUnsupportedRenditionFormat() {
    final var urn = new Urn("stub", "TEST-001");
    when(resolver.rendition(eq(urn), eq("image/png"), eq(List.of()), isNull()))
        .thenThrow(new UnsupportedFormatException("image/png"));

    try {
      resource.resolve("urn:far:stub:TEST-001", null, null, "image/png", null, "allow", "full");
    } catch (final UnsupportedFormatException exception) {
      assertThat(exception.code()).isEqualTo("unsupported_format");
    }
  }

  @Test
  void shouldParseRenditionMediaFromAcceptHeader() {
    assertThat(ResolveResource.media(null)).isNull();
    assertThat(ResolveResource.media("")).isNull();
    assertThat(ResolveResource.media("application/json")).isNull();
    assertThat(ResolveResource.media("*/*")).isNull();
    assertThat(ResolveResource.media("text/html")).isEqualTo("text/html");
    assertThat(ResolveResource.media("application/pdf")).isEqualTo("application/pdf");
    assertThat(ResolveResource.media("image/png")).isEqualTo("image/png");
    assertThat(ResolveResource.media("application/json, text/html")).isNull();
    assertThat(ResolveResource.media("text/html;q=0.9, application/json")).isEqualTo("text/html");
  }

  @Test
  void shouldFilterAttributes() {
    final var urn = new Urn("stub", "TEST-001");
    final var resolution =
        new Resolution(
            urn,
            "stub",
            "TEST-001",
            Map.of(
                "volume", new Attribute("volume", Value.of("1000"), "stub", true, Instant.now()),
                "status", new Attribute("status", Value.of("active"), "stub", true, Instant.now()),
                "vintage", new Attribute("vintage", Value.of(2023), "stub", true, Instant.now())),
            null,
            "stub",
            Instant.now());
    when(resolver.resolve(eq(urn), any(), isNull())).thenReturn(resolution);

    final var response =
        resource.resolve(
            "urn:far:stub:TEST-001", null, null, null, "volume,status", "allow", "full");

    assertThat(response.getStatus()).isEqualTo(200);
    final var filtered = (Resolution) response.getEntity();
    assertThat(filtered.attributes()).containsKey("volume");
    assertThat(filtered.attributes()).containsKey("status");
    assertThat(filtered.attributes()).doesNotContainKey("vintage");
  }

  @Test
  void shouldReturnExistsMode() {
    final var urn = new Urn("stub", "TEST-001");
    final var resolution =
        new Resolution(urn, "stub", "TEST-001", Map.of(), null, "stub", Instant.now());
    when(resolver.resolve(eq(urn), any(), isNull())).thenReturn(resolution);

    final var response =
        resource.resolve("urn:far:stub:TEST-001", null, null, null, null, "allow", "exists");

    assertThat(response.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    final var body = (Map<String, Object>) response.getEntity();
    assertThat(body).containsKey("exists");
    assertThat(body.get("exists")).isEqualTo(true);
    assertThat(body).doesNotContainKey("attributes");
  }
}
