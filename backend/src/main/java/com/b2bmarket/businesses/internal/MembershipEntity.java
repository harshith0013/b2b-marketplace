package com.b2bmarket.businesses.internal;

import com.b2bmarket.businesses.BusinessRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_memberships")
public class MembershipEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "business_id", nullable = false, updatable = false)
  private UUID businessId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BusinessRole role;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MembershipEntity() {}

  public static MembershipEntity create(
      UUID userId, UUID businessId, BusinessRole role, Instant now) {
    MembershipEntity membership = new MembershipEntity();
    membership.id = UUID.randomUUID();
    membership.userId = userId;
    membership.businessId = businessId;
    membership.role = role;
    membership.createdAt = now;
    return membership;
  }

  public UUID userId() {
    return userId;
  }

  public UUID businessId() {
    return businessId;
  }

  public BusinessRole role() {
    return role;
  }
}
