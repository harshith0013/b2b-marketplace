package com.b2bmarket.listings;

/**
 * A reason a listing cannot be submitted or approved yet. Codes, not sentences: the website shows
 * them in the reader's language.
 */
public enum EligibilityProblem {
  /** Less than 30 days left before the printed date (or already past it). */
  SHELF_LIFE_TOO_SHORT,
  PACK_PHOTO_MISSING,
  LABEL_PHOTO_MISSING,
  /** The MRP printed on one pack was not entered on the batch. */
  MRP_MISSING,
  /** One pack would cost the buyer more than its printed MRP (GST included). */
  PRICE_ABOVE_MRP,
  /** The product's category needs compliance checks that the pilot does not have. */
  REGULATED_CATEGORY,
  /** The batch has less stock than the minimum order. */
  NOT_ENOUGH_STOCK
}
