package net.far.resolver.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeyManagerTests {

  @Test
  void generate() {
    final var manager = KeyManager.generate("test-server");

    assertThat(manager.id()).isEqualTo("test-server");
    assertThat(manager.signing()).isNotNull();
    assertThat(manager.verifying()).isNotNull();
    assertThat(manager.encoded()).isNotBlank();
  }

  @Test
  void roundtrip() {
    final var original = KeyManager.generate("roundtrip-test");
    final var signing = original.signing().getEncoded();
    final var verifying = original.verifying().getEncoded();

    final var restored = new KeyManager(signing, verifying, "roundtrip-test");

    assertThat(restored.id()).isEqualTo(original.id());
    assertThat(restored.signing().getEncoded()).isEqualTo(signing);
    assertThat(restored.verifying().getEncoded()).isEqualTo(verifying);
    assertThat(restored.encoded()).isEqualTo(original.encoded());
  }

  @Test
  void encoded() {
    final var manager = KeyManager.generate("encode-test");
    final var encoded = manager.encoded();

    final var decoded = Base64.getDecoder().decode(encoded);
    assertThat(decoded).hasSize(32);
    assertThat(decoded).isEqualTo(manager.verifying().getEncoded());
  }

  @Test
  void unique() {
    final var first = KeyManager.generate("key-1");
    final var second = KeyManager.generate("key-2");

    assertThat(first.encoded()).isNotEqualTo(second.encoded());
    assertThat(first.signing().getEncoded()).isNotEqualTo(second.signing().getEncoded());
  }

  @Test
  void pem(@TempDir final Path directory) {
    final var manager = KeyManager.generate("pem-test");
    final var result = manager.pem();

    assertThat(result).startsWith("-----BEGIN PUBLIC KEY-----");
    assertThat(result).endsWith("-----END PUBLIC KEY-----");
  }

  @Test
  void write(@TempDir final Path directory) {
    final var original = KeyManager.generate("write-test");
    original.write(directory);

    assertThat(directory.resolve("private.pem")).exists();
    assertThat(directory.resolve("public.pem")).exists();
  }

  @Test
  void load(@TempDir final Path directory) {
    final var original = KeyManager.generate("load-test");
    original.write(directory);

    final var loaded = KeyManager.load(directory, "load-test");

    assertThat(loaded.id()).isEqualTo("load-test");
    assertThat(loaded.encoded()).isEqualTo(original.encoded());
    assertThat(loaded.signing().getEncoded()).isEqualTo(original.signing().getEncoded());
    assertThat(loaded.verifying().getEncoded()).isEqualTo(original.verifying().getEncoded());
  }

  @Test
  void pemRoundtrip(@TempDir final Path directory) {
    final var original = KeyManager.generate("pem-roundtrip");
    original.write(directory);
    final var loaded = KeyManager.load(directory, "pem-roundtrip");

    assertThat(loaded.pem()).isEqualTo(original.pem());
  }

  @Test
  void parsePem() {
    final var manager = KeyManager.generate("parse-pem-test");
    final var pem = manager.pem();

    final var parsed = KeyManager.parse(pem);

    assertThat(parsed.getEncoded()).isEqualTo(manager.verifying().getEncoded());
  }

  @Test
  void parseBase64() {
    final var manager = KeyManager.generate("parse-raw-test");
    final var encoded = manager.encoded();

    final var parsed = KeyManager.parse(encoded);

    assertThat(parsed.getEncoded()).isEqualTo(manager.verifying().getEncoded());
  }

  @Test
  void loadMissing(@TempDir final Path directory) {
    final var missing = directory.resolve("nonexistent");

    assertThatThrownBy(() -> KeyManager.load(missing, "missing"))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("Failed to load keys");
  }
}
