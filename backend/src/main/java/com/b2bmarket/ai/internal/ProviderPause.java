package com.b2bmarket.ai.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * After the provider says its quota is used up, nothing (questions or label photos) is sent to it
 * for a while. One server, so in memory.
 */
@Component
public class ProviderPause {

  public static final Duration QUOTA_PAUSE = Duration.ofMinutes(15);

  private final Clock clock;
  private volatile Instant pausedUntil = Instant.MIN;

  ProviderPause(Clock clock) {
    this.clock = clock;
  }

  public boolean paused() {
    return clock.instant().isBefore(pausedUntil);
  }

  public void pause() {
    pausedUntil = clock.instant().plus(QUOTA_PAUSE);
  }

  /** For tests. */
  public void resume() {
    pausedUntil = Instant.MIN;
  }
}
