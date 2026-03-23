package net.far.resolver.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RenditionTests {

  @Test
  void shouldCreateRendition() {
    final var urn = new Urn("stub", "TEST-001");
    final var bytes = "<html>hello</html>".getBytes(StandardCharsets.UTF_8);
    final var rendition = new Rendition(bytes, "text/html", urn, Instant.now());

    assertThat(rendition.media()).isEqualTo("text/html");
    assertThat(rendition.urn()).isEqualTo(urn);
    assertThat(rendition.content()).isEqualTo(bytes);
  }

  @Test
  void shouldDefaultTimestamp() {
    final var urn = new Urn("stub", "TEST-001");
    final var bytes = "data".getBytes(StandardCharsets.UTF_8);
    final var rendition = new Rendition(bytes, "application/pdf", urn, null);

    assertThat(rendition.timestamp()).isNotNull();
  }

  @Test
  void shouldRejectEmptyContent() {
    final var urn = new Urn("stub", "TEST-001");
    assertThatThrownBy(() -> new Rendition(new byte[0], "text/html", urn, Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNullContent() {
    final var urn = new Urn("stub", "TEST-001");
    assertThatThrownBy(() -> new Rendition(null, "text/html", urn, Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectBlankMedia() {
    final var urn = new Urn("stub", "TEST-001");
    final var bytes = "data".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> new Rendition(bytes, " ", urn, Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNullUrn() {
    final var bytes = "data".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> new Rendition(bytes, "text/html", null, Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldDefensivelyCopyContent() {
    final var urn = new Urn("stub", "TEST-001");
    final var bytes = "original".getBytes(StandardCharsets.UTF_8);
    final var rendition = new Rendition(bytes, "text/html", urn, Instant.now());
    bytes[0] = 'X';

    assertThat(rendition.content()[0]).isEqualTo((byte) 'o');
  }
}
