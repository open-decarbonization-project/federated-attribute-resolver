package net.far.resolver.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.far.resolver.model.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerifierTests {

  private KeyManager keys;
  private Signer signer;
  private Verifier verifier;

  @BeforeEach
  void setup() {
    keys = KeyManager.generate("test-server");
    signer = new Signer(keys);
    verifier = new Verifier();
  }

  @Test
  void roundtrip() {
    final var components =
        new MessageComponents(
            "GET",
            "/v1/resources/urn:far:verra:VCS-1234",
            "far.example.com",
            "application/json",
            "sha-256=:abc123=:",
            null);

    final var signature = signer.sign(components);

    final var result =
        verifier.verify(components, signature.value(), signature.input(), keys.verifying());

    assertThat(result).isTrue();
  }

  @Test
  void invalid() {
    final var components =
        new MessageComponents(
            "GET",
            "/v1/resources/urn:far:verra:VCS-1234",
            "far.example.com",
            "application/json",
            null,
            null);

    final var signature = signer.sign(components);
    final var other = KeyManager.generate("other-server");

    assertThatThrownBy(
            () ->
                verifier.verify(
                    components, signature.value(), signature.input(), other.verifying()))
        .isInstanceOf(SignatureException.class)
        .hasMessageContaining("Signature verification failed");
  }

  @Test
  void tampered() {
    final var components =
        new MessageComponents(
            "GET",
            "/v1/resources/urn:far:verra:VCS-1234",
            "far.example.com",
            "application/json",
            null,
            null);

    final var signature = signer.sign(components);

    final var altered =
        new MessageComponents(
            "POST",
            "/v1/resources/urn:far:verra:VCS-1234",
            "far.example.com",
            "application/json",
            null,
            null);

    assertThatThrownBy(
            () -> verifier.verify(altered, signature.value(), signature.input(), keys.verifying()))
        .isInstanceOf(SignatureException.class);
  }

  @Test
  void keyid() {
    final var input =
        "sig1=(\"@method\" \"@target-uri\");keyid=\"my-server\";alg=\"ed25519\";created=1234567890";

    final var result = verifier.keyid(input);

    assertThat(result).isEqualTo("my-server");
  }

  @Test
  void keyidMissing() {
    assertThatThrownBy(() -> verifier.keyid("sig1=(\"@method\");alg=\"ed25519\""))
        .isInstanceOf(SignatureException.class)
        .hasMessageContaining("No keyid found");
  }
}
