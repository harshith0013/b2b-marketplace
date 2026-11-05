package com.b2bmarket.listings.internal;

import com.b2bmarket.listings.ListingStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every method takes the business id; Row-Level Security is the backstop, not the filter. */
public interface ListingRepository extends JpaRepository<ListingEntity, UUID> {

  Page<ListingEntity> findByBusinessId(UUID businessId, Pageable pageable);

  Optional<ListingEntity> findByIdAndBusinessId(UUID id, UUID businessId);

  boolean existsByBusinessIdAndBatchIdAndStatusNot(
      UUID businessId, UUID batchId, ListingStatus status);
}
