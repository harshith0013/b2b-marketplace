package com.b2bmarket.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SystemInfoControllerTest {

  @Test
  void reportsNameVersionAndServerTimeFromTheClock() {
    Instant now = Instant.parse("2026-11-02T06:00:00Z");
    var controller =
        new SystemInfoController(Clock.fixed(now, ZoneOffset.UTC), "b2b-marketplace", "0.1.0");

    SystemInfoResponse info = controller.info();

    assertThat(info.name()).isEqualTo("b2b-marketplace");
    assertThat(info.version()).isEqualTo("0.1.0");
    assertThat(info.serverTime()).isEqualTo(now);
  }
}
