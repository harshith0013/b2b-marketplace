package com.b2bmarket.batches.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every method takes the business id; Row-Level Security is the backstop, not the filter. */
public interface PhotoRepository extends JpaRepository<PhotoEntity, UUID> {

  List<PhotoEntity> findByBusinessIdAndBatchIdOrderByCreatedAtAsc(UUID businessId, UUID batchId);

  Optional<PhotoEntity> findByIdAndBusinessIdAndBatchId(UUID id, UUID businessId, UUID batchId);

  long countByBusinessIdAndBatchId(UUID businessId, UUID batchId);

  boolean existsByBusinessIdAndSha256AndBatchId(UUID businessId, String sha256, UUID batchId);
}
