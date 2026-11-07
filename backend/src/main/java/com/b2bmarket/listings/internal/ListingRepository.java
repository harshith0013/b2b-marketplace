package com.b2bmarket.listings.internal;

import com.b2bmarket.listings.ListingStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every seller method takes the business id; Row-Level Security is the backstop, not the filter.
 */
public interface ListingRepository extends JpaRepository<ListingEntity, UUID> {

  Page<ListingEntity> findByBusinessId(UUID businessId, Pageable pageable);

  Optional<ListingEntity> findByIdAndBusinessId(UUID id, UUID businessId);

  boolean existsByBusinessIdAndBatchIdAndStatusNot(
      UUID businessId, UUID batchId, ListingStatus status);

  /** The review queue (review scope). */
  Page<ListingEntity> findByStatus(ListingStatus status, Pageable pageable);

  /**
   * Reviewer approves: touches only the columns the reviewer database role may change. 0 when the
   * listing was no longer waiting for review (someone else decided first).
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update ListingEntity l set l.status = com.b2bmarket.listings.ListingStatus.PUBLISHED,"
          + " l.reviewedAt = :now, l.updatedAt = :now, l.rejectionReason = null"
          + " where l.id = :id and l.status = com.b2bmarket.listings.ListingStatus.PENDING_REVIEW")
  int publishPending(@Param("id") UUID id, @Param("now") Instant now);

  /** Reviewer rejects: back to draft with the reason, so the seller can fix and resubmit. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update ListingEntity l set l.status = com.b2bmarket.listings.ListingStatus.DRAFT,"
          + " l.reviewedAt = :now, l.updatedAt = :now, l.rejectionReason = :reason"
          + " where l.id = :id and l.status = com.b2bmarket.listings.ListingStatus.PENDING_REVIEW")
  int rejectPending(
      @Param("id") UUID id, @Param("reason") String reason, @Param("now") Instant now);
}
