package com.b2bmarket.listings.internal;

import com.b2bmarket.batches.StockUnit;
import com.b2bmarket.listings.ClearanceReason;
import com.b2bmarket.listings.ListingStatus;
import com.b2bmarket.listings.Visibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listings")
public class ListingEntity {

  @Id private UUID id;

  @Column(name = "business_id", nullable = false, updatable = false)
  private UUID businessId;

  @Column(name = "batch_id", nullable = false, updatable = false)
  private UUID batchId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ListingStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "clearance_reason", nullable = false)
  private ClearanceReason clearanceReason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Visibility visibility;

  @Column(nullable = false)
  private long moq;

  @Enumerated(EnumType.STRING)
  @Column(name = "quantity_unit", nullable = false, updatable = false)
  private StockUnit quantityUnit;

  @Column(name = "gst_rate_bp", nullable = false)
  private int gstRateBp;

  private String notes;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ListingEntity() {}

  /** The details that can change on a draft; validated and normalized. */
  public record Fields(
      ClearanceReason clearanceReason,
      Visibility visibility,
      long moq,
      int gstRateBp,
      String notes) {}

  public static ListingEntity createDraft(
      UUID businessId, UUID batchId, StockUnit quantityUnit, Fields fields, Instant now) {
    ListingEntity listing = new ListingEntity();
    listing.id = UUID.randomUUID();
    listing.businessId = businessId;
    listing.batchId = batchId;
    listing.quantityUnit = quantityUnit;
    listing.status = ListingStatus.DRAFT;
    listing.createdAt = now;
    listing.apply(fields, now);
    return listing;
  }

  public void apply(Fields fields, Instant now) {
    clearanceReason = fields.clearanceReason();
    visibility = fields.visibility();
    moq = fields.moq();
    gstRateBp = fields.gstRateBp();
    notes = fields.notes();
    updatedAt = now;
  }

  /** Seller sends a draft for review; an earlier rejection reason is cleared. */
  public void submit(Instant now) {
    status = ListingStatus.PENDING_REVIEW;
    submittedAt = now;
    rejectionReason = null;
    updatedAt = now;
  }

  /** Seller moves the listing to a state that needs no review (withdraw, pause, resume, close). */
  public void moveTo(ListingStatus next, Instant now) {
    status = next;
    updatedAt = now;
  }

  /** Records that something else about the listing (its price tiers) changed. */
  public void touch(Instant now) {
    updatedAt = now;
  }

  public Fields fields() {
    return new Fields(clearanceReason, visibility, moq, gstRateBp, notes);
  }

  public UUID id() {
    return id;
  }

  public UUID businessId() {
    return businessId;
  }

  public Instant submittedAt() {
    return submittedAt;
  }

  public Instant reviewedAt() {
    return reviewedAt;
  }

  public String rejectionReason() {
    return rejectionReason;
  }

  public UUID batchId() {
    return batchId;
  }

  public ListingStatus status() {
    return status;
  }

  public StockUnit quantityUnit() {
    return quantityUnit;
  }
}
