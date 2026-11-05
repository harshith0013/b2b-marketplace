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
@Table(name = "business_invitations")
public class InvitationEntity {

  public enum Status {
    PENDING,
    ACCEPTED,
    REVOKED,
    EXPIRED
  }

  @Id private UUID id;

  @Column(name = "business_id", nullable = false, updatable = false)
  private UUID businessId;

  @Column(nullable = false, updatable = false)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private BusinessRole role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(name = "invited_by", nullable = false, updatable = false)
  private UUID invitedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "responded_at")
  private Instant respondedAt;

  @Column(name = "accepted_by")
  private UUID acceptedBy;

  protected InvitationEntity() {}

  public static InvitationEntity create(
      UUID businessId,
      String email,
      BusinessRole role,
      UUID invitedBy,
      Instant now,
      Instant expiresAt) {
    InvitationEntity invitation = new InvitationEntity();
    invitation.id = UUID.randomUUID();
    invitation.businessId = businessId;
    invitation.email = email;
    invitation.role = role;
    invitation.status = Status.PENDING;
    invitation.invitedBy = invitedBy;
    invitation.createdAt = now;
    invitation.expiresAt = expiresAt;
    return invitation;
  }

  /** Pending and not yet past its expiry time. */
  public boolean isOpen(Instant now) {
    return status == Status.PENDING && now.isBefore(expiresAt);
  }

  public void accept(UUID userId, Instant now) {
    status = Status.ACCEPTED;
    acceptedBy = userId;
    respondedAt = now;
  }

  public void revoke(Instant now) {
    status = Status.REVOKED;
    respondedAt = now;
  }

  public void expire(Instant now) {
    status = Status.EXPIRED;
    respondedAt = now;
  }

  public UUID id() {
    return id;
  }

  public UUID businessId() {
    return businessId;
  }

  public String email() {
    return email;
  }

  public BusinessRole role() {
    return role;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }
}
