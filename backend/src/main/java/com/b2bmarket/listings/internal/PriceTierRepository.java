package com.b2bmarket.listings.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceTierRepository extends JpaRepository<PriceTierEntity, UUID> {

  List<PriceTierEntity> findByBusinessIdAndListingIdInOrderByMinQuantityAsc(
      UUID businessId, Collection<UUID> listingIds);

  /**
   * Runs immediately (not at flush), so replacement tiers with the same quantities can be inserted
   * afterwards in the same transaction.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "delete from PriceTierEntity t where t.businessId = :businessId and t.listingId = :listingId")
  int deleteAllOf(@Param("businessId") UUID businessId, @Param("listingId") UUID listingId);
}
