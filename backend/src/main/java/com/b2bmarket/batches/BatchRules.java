package com.b2bmarket.batches;

import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.IndiaTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/** Validation of batch details. Dates are judged by the calendar in India. */
final class BatchRules {

  /** Longer than any non-food FMCG shelf life; catches a mistyped year. */
  static final int MAX_YEARS_AHEAD = 10;

  private static final Pattern BATCH_NUMBER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 ./_-]*$");

  private BatchRules() {}

  static LocalDate today(Clock clock) {
    return IndiaTime.today(clock);
  }

  static long daysUntil(LocalDate date, Clock clock) {
    return ChronoUnit.DAYS.between(today(clock), date);
  }

  static String batchNumber(String value) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty() || !BATCH_NUMBER.matcher(trimmed).matches()) {
      throw ApiException.badRequest(
          "Type the batch number as printed on the pack (letters, digits, spaces and . / _ -).");
    }
    return trimmed;
  }

  /** The printed dates make sense and the stock has not already expired. */
  static void requireDates(LocalDate manufacturedOn, LocalDate expiresOn, Clock clock) {
    LocalDate today = today(clock);
    if (expiresOn.isBefore(today)) {
      throw ApiException.badRequest("This stock has already expired and cannot be added.");
    }
    if (expiresOn.isAfter(today.plusYears(MAX_YEARS_AHEAD))) {
      throw ApiException.badRequest("Check the expiry date: it is more than 10 years away.");
    }
    if (manufacturedOn != null && manufacturedOn.isAfter(today)) {
      throw ApiException.badRequest("The manufacturing date cannot be in the future.");
    }
    if (manufacturedOn != null && manufacturedOn.isAfter(expiresOn)) {
      throw ApiException.badRequest("The manufacturing date must be before the expiry date.");
    }
  }

  /** Cases need their size; single pieces must not have one. */
  static Integer packsPerCase(StockUnit unit, Integer packsPerCase) {
    if (unit == StockUnit.CASE && packsPerCase == null) {
      throw ApiException.badRequest("Say how many packs are in one case.");
    }
    if (unit == StockUnit.PIECE && packsPerCase != null) {
      throw ApiException.badRequest("Packs per case applies only when counting in cases.");
    }
    return packsPerCase;
  }
}
