package com.b2bmarket.listings;

/** Phase 4 creates drafts only; review and publishing arrive in Phase 5. */
public enum ListingStatus {
  DRAFT,
  PENDING_REVIEW,
  PUBLISHED,
  PAUSED,
  CLOSED
}
