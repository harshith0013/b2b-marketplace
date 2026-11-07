package com.b2bmarket.batches;

import com.b2bmarket.products.ProductDtos.VariantSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Request and response bodies of the batches API. Money is in paise. */
public final class BatchDtos {

  static final long MAX_QUANTITY = 10_000_000L;
  static final int MAX_PACKS_PER_CASE = 10_000;

  /** ₹1,00,000 for one pack. */
  static final long MAX_MRP_PAISE = 10_000_000L;

  private BatchDtos() {}

  public record CreateBatchRequest(
      @NotNull UUID variantId,
      @NotNull UUID warehouseId,
      @NotBlank @Size(max = 50) String batchNumber,
      LocalDate manufacturedOn,
      @NotNull LocalDate expiresOn,
      @NotNull DateType dateType,
      @NotNull @Positive @Max(MAX_QUANTITY) Long quantity,
      @NotNull StockUnit quantityUnit,
      @Positive @Max(MAX_PACKS_PER_CASE) Integer packsPerCase,
      @Positive @Max(MAX_MRP_PAISE) Long mrpPaise) {}

  /**
   * Every field is optional; only the fields that are present are changed. The product and the
   * stock unit cannot change: add a new batch instead.
   */
  public record UpdateBatchRequest(
      UUID warehouseId,
      @Size(min = 1, max = 50) String batchNumber,
      LocalDate manufacturedOn,
      LocalDate expiresOn,
      DateType dateType,
      @PositiveOrZero @Max(MAX_QUANTITY) Long quantity,
      @Positive @Max(MAX_MRP_PAISE) Long mrpPaise) {}

  public record Batch(
      UUID id,
      VariantSummary variant,
      UUID warehouseId,
      String batchNumber,
      LocalDate manufacturedOn,
      LocalDate expiresOn,
      DateType dateType,
      long daysUntilExpiry,
      long quantity,
      StockUnit quantityUnit,
      Integer packsPerCase,
      Long mrpPaise,
      BatchStatus status) {}

  /** What the listings module needs to know about a batch. */
  public record BatchSummary(
      UUID id,
      VariantSummary variant,
      LocalDate expiresOn,
      DateType dateType,
      long quantity,
      StockUnit quantityUnit,
      Integer packsPerCase,
      Long mrpPaise,
      BatchStatus status) {}
}
