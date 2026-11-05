package com.b2bmarket.inventory.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every method takes the business id, even though Row-Level Security would also filter by it: the
 * application check comes first, the database policy is the backstop.
 */
public interface WarehouseRepository extends JpaRepository<WarehouseEntity, UUID> {

  List<WarehouseEntity> findByBusinessIdOrderByNameAsc(UUID businessId);

  Optional<WarehouseEntity> findByIdAndBusinessId(UUID id, UUID businessId);

  long countByBusinessId(UUID businessId);

  boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

  boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(UUID businessId, String name, UUID id);
}
