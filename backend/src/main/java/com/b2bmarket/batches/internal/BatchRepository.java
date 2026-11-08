package com.b2bmarket.batches.internal;

import com.b2bmarket.batches.BatchStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every method takes the business id; Row-Level Security is the backstop, not the filter. */
public interface BatchRepository extends JpaRepository<BatchEntity, UUID> {

  Page<BatchEntity> findByBusinessId(UUID businessId, Pageable pageable);

  Optional<BatchEntity> findByIdAndBusinessId(UUID id, UUID businessId);

  List<BatchEntity> findByBusinessIdAndIdIn(UUID businessId, Collection<UUID> ids);

  boolean existsByBusinessIdAndVariantIdAndBatchNumberIgnoreCase(
      UUID businessId, UUID variantId, String batchNumber);

  boolean existsByBusinessIdAndVariantIdAndBatchNumberIgnoreCaseAndIdNot(
      UUID businessId, UUID variantId, String batchNumber, UUID id);

  /** Takes delivered stock off the batch; 0 when there is not enough left. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update BatchEntity b set b.quantity = b.quantity - :quantity, b.updatedAt = :now"
          + " where b.id = :id and b.businessId = :businessId and b.quantity >= :quantity")
  int deduct(
      @Param("businessId") UUID businessId,
      @Param("id") UUID id,
      @Param("quantity") long quantity,
      @Param("now") Instant now);

  /**
   * Moves a batch between review states, touching only status and updated_at (all the reviewer
   * database role may change). Returns 0 when the batch was not in one of the expected states.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update BatchEntity b set b.status = :to, b.updatedAt = :now"
          + " where b.id = :id and b.businessId = :businessId and b.status in :from")
  int changeStatus(
      @Param("businessId") UUID businessId,
      @Param("id") UUID id,
      @Param("from") Collection<BatchStatus> from,
      @Param("to") BatchStatus to,
      @Param("now") Instant now);
}
