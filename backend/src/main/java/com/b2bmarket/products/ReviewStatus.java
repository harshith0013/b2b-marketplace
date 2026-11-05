package com.b2bmarket.products;

/**
 * Whether an admin has checked a product. Unreviewed products are visible only to the business that
 * added them (approval queue: Phase 5).
 */
public enum ReviewStatus {
  UNREVIEWED,
  APPROVED,
  REJECTED
}
