package net.far.resolver.signature;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DigestCalculatorTests {

  @Test
  void compute() {
    final var content = "hello world".getBytes(StandardCharsets.UTF_8);

    final var result = DigestCalculator.compute(content);

    assertThat(result).startsWith("sha-256=:");
    assertThat(result).endsWith(":");
    assertThat(result).isEqualTo("sha-256=:uU0nuZNNPgilLlLX2n2r+sSE7+N6U4DukIj3rOLvzek=:");
  }

  @Test
  void validate() {
    final var content = "hello world".getBytes(StandardCharsets.UTF_8);
    final var expected = DigestCalculator.compute(content);

    assertThat(DigestCalculator.validate(content, expected)).isTrue();
  }

  @Test
  void reject() {
    final var content = "hello world".getBytes(StandardCharsets.UTF_8);

    assertThat(DigestCalculator.validate(content, "sha-256=:invalid:")).isFalse();
  }

  @Test
  void deterministic() {
    final var content = "test content".getBytes(StandardCharsets.UTF_8);

    final var first = DigestCalculator.compute(content);
    final var second = DigestCalculator.compute(content);

    assertThat(first).isEqualTo(second);
  }

  @Test
  void empty() {
    final var content = new byte[0];

    final var result = DigestCalculator.compute(content);

    assertThat(result).startsWith("sha-256=:");
    assertThat(result).endsWith(":");
  }
}
