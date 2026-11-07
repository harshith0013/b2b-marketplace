package com.b2bmarket.products.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository
    extends JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {

  /** Touches only review_status and updated_at: all the reviewer database role may change. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update ProductEntity p set p.reviewStatus = com.b2bmarket.products.ReviewStatus.APPROVED,"
          + " p.updatedAt = :now where p.id = :id"
          + " and p.reviewStatus = com.b2bmarket.products.ReviewStatus.UNREVIEWED")
  int approveIfUnreviewed(@Param("id") UUID id, @Param("now") Instant now);
}
