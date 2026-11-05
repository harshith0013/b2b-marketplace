package com.b2bmarket.listings;

import com.b2bmarket.batches.BatchDtos.BatchSummary;
import com.b2bmarket.batches.StockUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Request and response bodies of the listings API. Quantities are in the batch's stock unit; prices
 * are per unit, in paise, excluding GST.
 */
public final class ListingDtos {

  static final int MAX_TIERS = 5;
  static final long MAX_QUANTITY = 10_000_000L;

  /** ₹1,00,00,000 per unit: far above any FMCG case, catches a slipped decimal. */
  static final long MAX_UNIT_PRICE_PAISE = 1_000_000_000L;

  /** 40%, the highest GST slab. */
  static final int MAX_GST_RATE_BP = 4000;

  private ListingDtos() {}

  public record PriceTierRequest(
      @NotNull @Positive @Max(MAX_QUANTITY) Long minQuantity,
      @NotNull @Positive @Max(MAX_UNIT_PRICE_PAISE) Long unitPricePaise) {}

  public record CreateListingRequest(
      @NotNull UUID batchId,
      @NotNull ClearanceReason clearanceReason,
      Visibility visibility,
      @NotNull @Positive @Max(MAX_QUANTITY) Long moq,
      @NotNull @Min(0) @Max(MAX_GST_RATE_BP) Integer gstRateBp,
      @Size(max = 1000) String notes,
      @NotEmpty @Size(max = MAX_TIERS) List<@Valid @NotNull PriceTierRequest> priceTiers) {}

  /** Every field is optional; price tiers, when present, replace all existing tiers. */
  public record UpdateListingRequest(
      ClearanceReason clearanceReason,
      Visibility visibility,
      @Positive @Max(MAX_QUANTITY) Long moq,
      @Min(0) @Max(MAX_GST_RATE_BP) Integer gstRateBp,
      @Size(max = 1000) String notes,
      @Size(min = 1, max = MAX_TIERS) List<@Valid @NotNull PriceTierRequest> priceTiers) {}

  public record PriceTier(long minQuantity, long unitPricePaise) {}

  public record Listing(
      UUID id,
      ListingStatus status,
      ClearanceReason clearanceReason,
      Visibility visibility,
      long moq,
      StockUnit quantityUnit,
      int gstRateBp,
      String notes,
      List<PriceTier> priceTiers,
      BatchSummary batch) {

    public Listing {
      priceTiers = List.copyOf(priceTiers);
    }
  }
}
