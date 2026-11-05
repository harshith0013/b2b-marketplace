package com.b2bmarket.products.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VariantRepository extends JpaRepository<VariantEntity, UUID> {

  List<VariantEntity> findByProductIdInOrderByPackUnitAscPackSizeAsc(Collection<UUID> productIds);

  boolean existsByGtinIn(Collection<String> gtins);
}
