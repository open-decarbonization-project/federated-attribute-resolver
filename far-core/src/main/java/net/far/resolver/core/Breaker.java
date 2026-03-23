package net.far.resolver.core;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Simple circuit breaker for peer delegation. */
public class Breaker {

  private static final int THRESHOLD = 5;
  private static final long WINDOW = 60;

  private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

  /** Returns true if the circuit for the given identity is open (peer should be skipped). */
  public boolean open(final String identity) {
    final var state = states.get(identity);
    if (state == null) {
      return false;
    }
    if (state.failures.get() >= THRESHOLD) {
      if (Instant.now().getEpochSecond() - state.opened > WINDOW) {
        states.remove(identity);
        return false;
      }
      return true;
    }
    return false;
  }

  public void success(final String identity) {
    states.remove(identity);
  }

  public void failure(final String identity) {
    final var state =
        states.computeIfAbsent(
            identity, k -> new State(new AtomicInteger(0), Instant.now().getEpochSecond()));
    if (state.failures.incrementAndGet() >= THRESHOLD) {
      state.opened = Instant.now().getEpochSecond();
    }
  }

  private static class State {
    final AtomicInteger failures;
    volatile long opened;

    State(final AtomicInteger failures, final long opened) {
      this.failures = failures;
      this.opened = opened;
    }
  }
}
