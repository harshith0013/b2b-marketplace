package com.b2bmarket.platform;

/** Normalizing free-text input from requests. */
public final class Text {

  private Text() {}

  /** Trimmed value; a 400 with "{label} is required." when missing or blank. */
  public static String required(String value, String label) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      throw ApiException.badRequest(label + " is required.");
    }
    return trimmed;
  }

  /** Trimmed value, or null when missing or blank ("none"). */
  public static String optional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
