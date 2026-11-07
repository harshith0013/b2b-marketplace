package com.b2bmarket.listings.internal;

import com.b2bmarket.listings.ReviewDtos.Decision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Append-only (enforced by the database). */
@Entity
@Immutable
@Table(name = "review_decisions")
public class ReviewDecisionEntity {

  public static final String APPROVED = "APPROVED";
  public static final String REJECTED = "REJECTED";

  @Id private UUID id;

  @Column(name = "listing_id", nullable = false)
  private UUID listingId;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(name = "reviewer_user_id", nullable = false)
  private UUID reviewerUserId;

  @Column(nullable = false)
  private String decision;

  private String reason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ReviewDecisionEntity() {}

  public static ReviewDecisionEntity create(
      UUID listingId,
      UUID businessId,
      UUID reviewerUserId,
      String decision,
      String reason,
      Instant now) {
    ReviewDecisionEntity entity = new ReviewDecisionEntity();
    entity.id = UUID.randomUUID();
    entity.listingId = listingId;
    entity.businessId = businessId;
    entity.reviewerUserId = reviewerUserId;
    entity.decision = decision;
    entity.reason = reason;
    entity.createdAt = now;
    return entity;
  }

  public Decision toDto() {
    return new Decision(decision, reason, createdAt);
  }
}
