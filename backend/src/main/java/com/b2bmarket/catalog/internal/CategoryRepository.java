package com.b2bmarket.catalog.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

  List<CategoryEntity> findByActiveTrueOrderBySortOrderAscSlugAsc();

  boolean existsByParentIdAndActiveTrue(UUID parentId);
}
