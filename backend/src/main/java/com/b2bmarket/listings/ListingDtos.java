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
import java.time.Instant;
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
      BatchSummary batch,
      Instant submittedAt,
      Instant reviewedAt,
      /** Why a reviewer sent it back; null unless the last review was a rejection. */
      String rejectionReason,
      DeliveryOptions delivery) {

    public Listing {
      priceTiers = List.copyOf(priceTiers);
    }
  }

  /**
   * What the label photo seemed to show, for the seller and reviewer to compare. Never a
   * certificate. expiry is "2027-03" when only a month was printed.
   */
  public record LabelCheck(
      String outcome,
      String reason,
      String printedDate,
      String dateType,
      String expiry,
      String batchNumber,
      Long mrpPaise,
      Instant readAt) {}

  /** problems block the listing; flags need a person to look; label is the latest reading. */
  public record Readiness(
      List<String> problems, List<String> flags, String ruleSetVersion, LabelCheck label) {

    public Readiness {
      problems = List.copyOf(problems);
      flags = List.copyOf(flags);
    }
  }

  /**
   * How goods reach the buyer: pickup from the warehouse city and/or the seller's own delivery to
   * the listed PIN codes, for a charge (paise). Cash on delivery when the seller accepts it.
   */
  public record DeliveryOptions(
      @jakarta.validation.constraints.NotNull Boolean pickupAvailable,
      @jakarta.validation.constraints.NotNull Boolean ownDelivery,
      @Size(max = 50)
          List<@jakarta.validation.constraints.Pattern(regexp = "^[1-9][0-9]{5}$") String>
              deliveryPincodes,
      @jakarta.validation.constraints.PositiveOrZero @Max(1_000_000_00L) Long deliveryChargePaise,
      @jakarta.validation.constraints.NotNull Boolean codAccepted,
      /** Online payment (Razorpay); null keeps it off. Needs an active payout account to use. */
      Boolean onlineAccepted) {}
}
