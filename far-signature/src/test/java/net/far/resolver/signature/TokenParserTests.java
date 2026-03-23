package net.far.resolver.signature;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenParserTests {

  private static String token(final String payload) {
    final var header =
        """
        {"alg":"EdDSA","typ":"JWT"}\
        """;
    return encode(header) + "." + encode(payload) + "." + encode("fake-signature");
  }

  private static String encode(final String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes());
  }

  @Test
  void parse() {
    final var payload =
        """
        {"sub":"user@example.com","iss":"auth.example.com","iat":1700000000}\
        """;
    final var token = token(payload);

    final var result = TokenParser.parse(token);

    assertThat(result).isPresent();
    assertThat(result.get().subject()).isEqualTo("user@example.com");
    assertThat(result.get().issuer()).isEqualTo("auth.example.com");
    assertThat(result.get().issued()).isEqualTo(1700000000L);
  }

  @Test
  void missing() {
    final var payload =
        """
        {"sub":"user@example.com"}\
        """;
    final var token = token(payload);

    final var result = TokenParser.parse(token);

    assertThat(result).isEmpty();
  }

  @Test
  void blank() {
    assertThat(TokenParser.parse(null)).isEmpty();
    assertThat(TokenParser.parse("")).isEmpty();
    assertThat(TokenParser.parse("   ")).isEmpty();
  }

  @Test
  void malformed() {
    assertThat(TokenParser.parse("not-a-token")).isEmpty();
    assertThat(TokenParser.parse("one.two")).isEmpty();
    assertThat(TokenParser.parse("a.b.c.d")).isEmpty();
  }

  @Test
  void invalid() {
    final var token = encode("{}") + "." + encode("not-json") + "." + encode("sig");

    assertThat(TokenParser.parse(token)).isEmpty();
  }

  @Test
  void subject() {
    final var payload =
        """
        {"iss":"auth.example.com","iat":1700000000}\
        """;
    final var token = token(payload);

    assertThat(TokenParser.parse(token)).isEmpty();
  }
}
