package com.b2bmarket.ai;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * What was read from a label photo. Not trusted, never a certificate: it is compared with what the
 * seller typed, and any difference is a flag for a person to look at.
 *
 * @param outcome READ, UNREADABLE (the label could not be read) or NOT_CHECKED (the photo was not
 *     looked at; reason says why)
 * @param expiryDay null when only a month and year are printed
 */
public record LabelReading(
    String outcome,
    String reason,
    String printedDate,
    String dateType,
    Integer expiryYear,
    Integer expiryMonth,
    Integer expiryDay,
    String batchNumber,
    Long mrpPaise,
    String model) {

  public static final String READ = "READ";
  public static final String UNREADABLE = "UNREADABLE";
  public static final String NOT_CHECKED = "NOT_CHECKED";

  public static LabelReading notChecked(String reason) {
    return new LabelReading(NOT_CHECKED, reason, null, null, null, null, null, null, null, null);
  }

  /** The printed month, when a valid year and month were read. */
  public YearMonth expiryMonthOfYear() {
    if (expiryYear == null || expiryMonth == null) {
      return null;
    }
    return YearMonth.of(expiryYear, expiryMonth);
  }

  /** The full printed date, when a day was printed too (and the date exists). */
  public LocalDate expiryDate() {
    YearMonth month = expiryMonthOfYear();
    if (month == null || expiryDay == null || !month.isValidDay(expiryDay)) {
      return null;
    }
    return month.atDay(expiryDay);
  }
}
