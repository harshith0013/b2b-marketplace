package com.b2bmarket.businesses;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * GST identification numbers: 2-digit state code, 10-character PAN, entity number, the letter Z and
 * a check character. Checking the format and check character catches typos; it does not prove the
 * GSTIN is registered or belongs to this business (that is business verification, Phase 5).
 */
public final class Gstin {

  private static final Pattern FORMAT =
      Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
  private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final int BASE = CHARS.length();

  private Gstin() {}

  /** Upper-cases and trims user input so "27aapfu0939f1zv " and "27AAPFU0939F1ZV" are the same. */
  public static String normalize(String gstin) {
    return gstin.trim().toUpperCase(Locale.ROOT);
  }

  public static boolean isValid(String gstin) {
    if (gstin == null || !FORMAT.matcher(gstin).matches()) {
      return false;
    }
    return gstin.charAt(14) == checkCharacter(gstin.substring(0, 14));
  }

  /** The 15th character of a GSTIN, computed from the first 14 (a base-36 Luhn variant). */
  static char checkCharacter(String first14) {
    int sum = 0;
    for (int i = 0; i < first14.length(); i++) {
      int product = CHARS.indexOf(first14.charAt(i)) * (i % 2 == 0 ? 1 : 2);
      sum += product / BASE + product % BASE;
    }
    return CHARS.charAt((BASE - sum % BASE) % BASE);
  }
}
