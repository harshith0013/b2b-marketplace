package com.b2bmarket.businesses.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<MembershipEntity, UUID> {

  Optional<MembershipEntity> findByBusinessIdAndUserId(UUID businessId, UUID userId);

  List<MembershipEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);

  List<MembershipEntity> findByBusinessIdOrderByCreatedAtAsc(UUID businessId);
}
