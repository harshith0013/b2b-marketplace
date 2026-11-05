package com.b2bmarket.batches.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every method takes the business id; Row-Level Security is the backstop, not the filter. */
public interface BatchRepository extends JpaRepository<BatchEntity, UUID> {

  Page<BatchEntity> findByBusinessId(UUID businessId, Pageable pageable);

  Optional<BatchEntity> findByIdAndBusinessId(UUID id, UUID businessId);

  List<BatchEntity> findByBusinessIdAndIdIn(UUID businessId, Collection<UUID> ids);

  boolean existsByBusinessIdAndVariantIdAndBatchNumberIgnoreCase(
      UUID businessId, UUID variantId, String batchNumber);

  boolean existsByBusinessIdAndVariantIdAndBatchNumberIgnoreCaseAndIdNot(
      UUID businessId, UUID variantId, String batchNumber, UUID id);
}
