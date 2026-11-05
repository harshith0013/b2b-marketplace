package com.b2bmarket.businesses.internal;

import com.b2bmarket.businesses.internal.InvitationEntity.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {

  /** Always scoped to a business, so one business can never reach another's invitations. */
  Optional<InvitationEntity> findByIdAndBusinessId(UUID id, UUID businessId);

  Optional<InvitationEntity> findByBusinessIdAndEmailAndStatus(
      UUID businessId, String email, Status status);

  List<InvitationEntity> findByBusinessIdAndStatusOrderByCreatedAtAsc(
      UUID businessId, Status status);

  /** Scoped to the invitee's own verified email. */
  List<InvitationEntity> findByEmailAndStatusOrderByCreatedAtAsc(String email, Status status);

  Optional<InvitationEntity> findByIdAndEmail(UUID id, String email);
}
