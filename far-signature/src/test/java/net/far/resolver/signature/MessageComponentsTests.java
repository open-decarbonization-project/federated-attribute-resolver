package net.far.resolver.signature;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessageComponentsTests {

  @Test
  void shouldNormalizeContentType() {
    assertThat(MessageComponents.normalize("application/json;charset=UTF-8"))
        .isEqualTo("application/json;charset=utf-8");
  }

  @Test
  void shouldNormalizeWithSpace() {
    assertThat(MessageComponents.normalize("application/json; charset=UTF-8"))
        .isEqualTo("application/json;charset=utf-8");
  }

  @Test
  void shouldNormalizeMultipleSpaces() {
    assertThat(MessageComponents.normalize("application/json ; charset=UTF-8 ; boundary=something"))
        .isEqualTo("application/json;boundary=something;charset=utf-8");
  }

  @Test
  void shouldNormalizeCasing() {
    assertThat(MessageComponents.normalize("Application/JSON;Charset=utf-8"))
        .isEqualTo("application/json;charset=utf-8");
  }

  @Test
  void shouldHandlePlainType() {
    assertThat(MessageComponents.normalize("application/json"))
        .isEqualTo("application/json");
  }

  @Test
  void shouldHandleNull() {
    assertThat(MessageComponents.normalize(null)).isNull();
  }

  @Test
  void shouldHandleBlank() {
    assertThat(MessageComponents.normalize("  ")).isEqualTo("  ");
  }

  @Test
  void shouldProduceIdenticalComponentsFromVariants() {
    final var first =
        new MessageComponents(200, null, null, null, "application/json;charset=UTF-8", null, null);
    final var second =
        new MessageComponents(
            200, null, null, null, "application/json; charset=utf-8", null, null);

    assertThat(first.type()).isEqualTo(second.type());
  }
}
