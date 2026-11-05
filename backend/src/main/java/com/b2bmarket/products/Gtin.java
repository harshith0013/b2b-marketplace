package com.b2bmarket.products;

import java.util.regex.Pattern;

/** GS1 barcodes (GTIN-8, -12, -13 and -14) as printed under the bars. */
public final class Gtin {

  private static final Pattern FORMAT = Pattern.compile("^([0-9]{8}|[0-9]{12,14})$");

  private Gtin() {}

  /** Removes spaces and hyphens that people type when copying from a pack. */
  public static String normalize(String value) {
    return value.replaceAll("[\\s-]", "");
  }

  /** Right length and a correct GS1 check digit (the last digit). */
  public static boolean isValid(String gtin) {
    if (gtin == null || !FORMAT.matcher(gtin).matches()) {
      return false;
    }
    int checkDigit = gtin.charAt(gtin.length() - 1) - '0';
    return checkDigit == checkDigitFor(gtin.substring(0, gtin.length() - 1));
  }

  /** GS1 rule: from the right, weights 3, 1, 3, 1 …; the check digit rounds the sum up to 10. */
  static int checkDigitFor(String digitsWithoutCheck) {
    int sum = 0;
    for (int i = 0; i < digitsWithoutCheck.length(); i++) {
      int digit = digitsWithoutCheck.charAt(digitsWithoutCheck.length() - 1 - i) - '0';
      sum += i % 2 == 0 ? digit * 3 : digit;
    }
    return (10 - sum % 10) % 10;
  }
}
