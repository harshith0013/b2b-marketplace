package com.b2bmarket.batches.internal;

import com.b2bmarket.batches.BatchStatus;
import com.b2bmarket.batches.DateType;
import com.b2bmarket.batches.StockUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "inventory_batches")
public class BatchEntity {

  @Id private UUID id;

  @Column(name = "business_id", nullable = false, updatable = false)
  private UUID businessId;

  @Column(name = "variant_id", nullable = false, updatable = false)
  private UUID variantId;

  @Column(name = "warehouse_id", nullable = false)
  private UUID warehouseId;

  @Column(name = "batch_number", nullable = false)
  private String batchNumber;

  @Column(name = "manufactured_on")
  private LocalDate manufacturedOn;

  @Column(name = "expires_on", nullable = false)
  private LocalDate expiresOn;

  @Enumerated(EnumType.STRING)
  @Column(name = "date_type", nullable = false)
  private DateType dateType;

  @Column(nullable = false)
  private long quantity;

  @Enumerated(EnumType.STRING)
  @Column(name = "quantity_unit", nullable = false, updatable = false)
  private StockUnit quantityUnit;

  @Column(name = "packs_per_case", updatable = false)
  private Integer packsPerCase;

  @Column(name = "mrp_paise")
  private Long mrpPaise;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BatchStatus status;

  @Column(name = "details_changed_after_approval_at")
  private Instant detailsChangedAfterApprovalAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected BatchEntity() {}

  /** The details that can change after creation; validated and normalized. */
  public record Fields(
      UUID warehouseId,
      String batchNumber,
      LocalDate manufacturedOn,
      LocalDate expiresOn,
      DateType dateType,
      long quantity,
      Long mrpPaise) {

    /** Whether the printed identity of the stock (batch number and dates) differs. */
    public boolean printedDetailsDifferFrom(Fields other) {
      return !Objects.equals(batchNumber, other.batchNumber)
          || !Objects.equals(manufacturedOn, other.manufacturedOn)
          || !Objects.equals(expiresOn, other.expiresOn)
          || dateType != other.dateType;
    }
  }

  public static BatchEntity create(
      UUID businessId,
      UUID variantId,
      StockUnit quantityUnit,
      Integer packsPerCase,
      Fields fields,
      Instant now) {
    BatchEntity batch = new BatchEntity();
    batch.id = UUID.randomUUID();
    batch.businessId = businessId;
    batch.variantId = variantId;
    batch.quantityUnit = quantityUnit;
    batch.packsPerCase = packsPerCase;
    batch.status = BatchStatus.DRAFT;
    batch.createdAt = now;
    batch.apply(fields, now);
    return batch;
  }

  public void apply(Fields fields, Instant now) {
    warehouseId = fields.warehouseId();
    batchNumber = fields.batchNumber();
    manufacturedOn = fields.manufacturedOn();
    expiresOn = fields.expiresOn();
    dateType = fields.dateType();
    quantity = fields.quantity();
    mrpPaise = fields.mrpPaise();
    updatedAt = now;
  }

  /** Approved details changed: a reviewer must look again, and buyers are told it happened. */
  public void sendBackForReview(Instant now) {
    status = BatchStatus.PENDING_REVIEW;
    detailsChangedAfterApprovalAt = now;
  }

  public Instant detailsChangedAfterApprovalAt() {
    return detailsChangedAfterApprovalAt;
  }

  public Fields fields() {
    return new Fields(
        warehouseId, batchNumber, manufacturedOn, expiresOn, dateType, quantity, mrpPaise);
  }

  public UUID id() {
    return id;
  }

  public UUID variantId() {
    return variantId;
  }

  public StockUnit quantityUnit() {
    return quantityUnit;
  }

  public Integer packsPerCase() {
    return packsPerCase;
  }

  public BatchStatus status() {
    return status;
  }
}
