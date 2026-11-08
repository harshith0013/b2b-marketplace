package com.b2bmarket.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class VisitorLimiterTest {

  /** A clock the test moves forward. */
  static final class MovableClock extends Clock {
    Instant now = Instant.parse("2026-11-08T10:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  static AiProperties limits(int perMinute, int perDay) {
    return new AiProperties(
        "http://x",
        "key",
        "m",
        "none",
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        100,
        100,
        perMinute,
        perDay,
        90);
  }

  @Test
  void limitsEachVisitorPerMinute() {
    MovableClock clock = new MovableClock();
    VisitorLimiter limiter = new VisitorLimiter(limits(2, 100), clock);

    assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
    assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
    assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
    assertThat(limiter.tryAcquire("5.6.7.8")).as("another visitor").isTrue();

    clock.now = clock.now.plusSeconds(60);
    assertThat(limiter.tryAcquire("1.2.3.4")).as("next minute").isTrue();
  }

  @Test
  void limitsEachVisitorPerDay() {
    MovableClock clock = new MovableClock();
    VisitorLimiter limiter = new VisitorLimiter(limits(10, 3), clock);
    for (int minute = 0; minute < 3; minute++) {
      clock.now = clock.now.plusSeconds(60);
      assertThat(limiter.tryAcquire("visitor")).isTrue();
    }
    clock.now = clock.now.plusSeconds(60);
    assertThat(limiter.tryAcquire("visitor")).isFalse();

    clock.now = clock.now.plus(Duration.ofDays(1));
    assertThat(limiter.tryAcquire("visitor")).as("next day").isTrue();
  }
}
