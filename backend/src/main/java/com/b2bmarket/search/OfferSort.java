package com.b2bmarket.search;

/** Result order. RELEVANCE needs search text; without it, results come NEWEST first. */
public enum OfferSort {
  RELEVANCE,
  EXPIRY,
  PRICE,
  NEWEST
}
