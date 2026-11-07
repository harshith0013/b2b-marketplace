package com.b2bmarket.platform;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Printed dates (expiry, manufacture) are judged by the calendar in India. */
public final class IndiaTime {

  public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

  private IndiaTime() {}

  public static LocalDate today(Clock clock) {
    return LocalDate.now(clock.withZone(ZONE));
  }
}
