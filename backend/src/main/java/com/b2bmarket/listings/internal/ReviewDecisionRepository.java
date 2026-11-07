package com.b2bmarket.listings.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewDecisionRepository extends JpaRepository<ReviewDecisionEntity, UUID> {

  List<ReviewDecisionEntity> findByListingIdOrderByCreatedAtAsc(UUID listingId);
}
