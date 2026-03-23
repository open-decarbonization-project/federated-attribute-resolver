package net.far.resolver.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeyRingTests {

  @Test
  void current() {
    final var manager = KeyManager.generate("current-key");
    final var ring = new KeyRing(manager, List.of());

    assertThat(ring.current()).isSameAs(manager);
    assertThat(ring.previous()).isEmpty();
  }

  @Test
  void active() {
    final var current = KeyManager.generate("current");
    final var valid = KeyManager.generate("valid-previous");
    final var expired = KeyManager.generate("expired-previous");

    final var ring =
        new KeyRing(
            current,
            List.of(
                new KeyRing.Retired(valid, Instant.now().plusSeconds(3600)),
                new KeyRing.Retired(expired, Instant.now().minusSeconds(3600))));

    final var active = ring.active();
    assertThat(active).hasSize(2);
    assertThat(active.get(0)).isSameAs(current);
    assertThat(active.get(1)).isSameAs(valid);
  }

  @Test
  void empty() {
    final var current = KeyManager.generate("only");
    final var ring = new KeyRing(current, List.of());

    assertThat(ring.active()).hasSize(1);
    assertThat(ring.active().get(0)).isSameAs(current);
  }

  @Test
  void expired() {
    final var current = KeyManager.generate("current");
    final var first = KeyManager.generate("expired-1");
    final var second = KeyManager.generate("expired-2");

    final var ring =
        new KeyRing(
            current,
            List.of(
                new KeyRing.Retired(first, Instant.now().minusSeconds(100)),
                new KeyRing.Retired(second, Instant.now().minusSeconds(200))));

    assertThat(ring.active()).hasSize(1);
    assertThat(ring.active().get(0)).isSameAs(current);
  }

  @Test
  void required() {
    assertThatThrownBy(() -> new KeyRing(null, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullable() {
    final var manager = KeyManager.generate("test");
    final var ring = new KeyRing(manager, null);

    assertThat(ring.previous()).isEmpty();
  }
}
