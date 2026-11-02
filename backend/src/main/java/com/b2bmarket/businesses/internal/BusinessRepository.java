package com.b2bmarket.businesses.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Unscoped by design: callers must first pass {@link BusinessAccess}, which ties every lookup to a
 * business the caller belongs to.
 */
public interface BusinessRepository extends JpaRepository<BusinessEntity, UUID> {

  boolean existsByGstin(String gstin);

  boolean existsByGstinAndIdNot(String gstin, UUID id);
}
