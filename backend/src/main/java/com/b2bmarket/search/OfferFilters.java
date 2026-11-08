package com.b2bmarket.search;

import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.Text;
import java.util.Set;
import java.util.UUID;

/**
 * A validated search. Every field except page and size is optional.
 *
 * @param maxPricePerPackPaise highest price for one pack (before GST)
 * @param maxOrderValuePaise highest cost of the minimum order (minimum order × its unit price)
 * @param minDaysLeft offers with fewer days before the printed date are left out (never fewer than
 *     {@link #MIN_SHELF_LIFE_DAYS})
 * @param wantQuantity with wantUnit: how much the buyer wants. Only offers sold in that unit, whose
 *     minimum order is at most this and whose stock is at least this
 */
public record OfferFilters(
    String query,
    UUID categoryId,
    String state,
    String city,
    String brand,
    Long maxPricePerPackPaise,
    Long maxOrderValuePaise,
    int minDaysLeft,
    OfferSort sort,
    int page,
    int size,
    Long wantQuantity,
    String wantUnit) {

  /** Offers stop showing this many days before the date on the pack. */
  public static final int MIN_SHELF_LIFE_DAYS = 30;

  public static final int DEFAULT_SIZE = 24;
  public static final int MAX_SIZE = 24;
  static final int MAX_TEXT = 100;
  static final int MAX_DAYS = 3650;
  static final int MAX_PAGE = 400;
  static final long MAX_QUANTITY = 10_000_000L;
  static final Set<String> UNITS = Set.of("PIECE", "CASE");

  /** Checks and normalizes raw request parameters; 400 for anything out of range. */
  public static OfferFilters of(
      String query,
      UUID categoryId,
      String state,
      String city,
      String brand,
      Long maxPricePerPackPaise,
      Long maxOrderValuePaise,
      Integer minDaysLeft,
      OfferSort sort,
      Integer page,
      Integer size) {
    String text = limited(query, "The search text");
    int days = minDaysLeft == null ? MIN_SHELF_LIFE_DAYS : minDaysLeft;
    if (days < 0 || days > MAX_DAYS) {
      throw ApiException.badRequest("Days left must be between 0 and " + MAX_DAYS + ".");
    }
    int pageNumber = page == null ? 0 : page;
    int pageSize = size == null ? DEFAULT_SIZE : size;
    if (pageNumber < 0 || pageNumber > MAX_PAGE) {
      throw ApiException.badRequest("The page number is out of range.");
    }
    if (pageSize < 1 || pageSize > MAX_SIZE) {
      throw ApiException.badRequest("The page size must be between 1 and " + MAX_SIZE + ".");
    }
    OfferSort order = sort == null ? (text == null ? OfferSort.NEWEST : OfferSort.RELEVANCE) : sort;
    if (order == OfferSort.RELEVANCE && text == null) {
      order = OfferSort.NEWEST;
    }
    return new OfferFilters(
        text,
        categoryId,
        limited(state, "The state"),
        limited(city, "The city"),
        limited(brand, "The brand"),
        positive(maxPricePerPackPaise, "The highest price"),
        positive(maxOrderValuePaise, "The highest order value"),
        Math.max(days, MIN_SHELF_LIFE_DAYS),
        order,
        pageNumber,
        pageSize,
        null,
        null);
  }

  /** The same search for a wanted quantity: both or neither; 400 when out of range. */
  public OfferFilters wanting(Long quantity, String unit) {
    if (quantity == null && unit == null) {
      return this;
    }
    if (quantity == null || unit == null) {
      throw ApiException.badRequest("Give both the quantity and its unit.");
    }
    if (quantity < 1 || quantity > MAX_QUANTITY || !UNITS.contains(unit)) {
      throw ApiException.badRequest("The quantity or its unit is not valid.");
    }
    return new OfferFilters(
        query,
        categoryId,
        state,
        city,
        brand,
        maxPricePerPackPaise,
        maxOrderValuePaise,
        minDaysLeft,
        sort,
        page,
        size,
        quantity,
        unit);
  }

  private static String limited(String value, String label) {
    String trimmed = Text.optional(value);
    if (trimmed != null && trimmed.length() > MAX_TEXT) {
      throw ApiException.badRequest(label + " is too long.");
    }
    return trimmed;
  }

  private static Long positive(Long value, String label) {
    if (value != null && value <= 0) {
      throw ApiException.badRequest(label + " must be more than zero.");
    }
    return value;
  }
}
