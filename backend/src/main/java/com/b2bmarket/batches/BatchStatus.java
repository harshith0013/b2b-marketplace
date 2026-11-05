package com.b2bmarket.batches;

/** Review state of a batch's details. Review itself arrives in Phase 5. */
public enum BatchStatus {
  DRAFT,
  PENDING_REVIEW,
  APPROVED,
  REJECTED
}
