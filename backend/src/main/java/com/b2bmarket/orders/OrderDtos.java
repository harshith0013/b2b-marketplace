package com.b2bmarket.orders;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request and response bodies for carts, orders and quotes. Money in paise. */
public final class OrderDtos {

  static final long MAX_QUANTITY = 10_000_000L;

  private OrderDtos() {}

  public record CartRequest(
      @NotNull UUID listingId, @NotNull @Positive @Max(MAX_QUANTITY) Long quantity) {}

  /** One cart line, with the offer as it is now (available false if it is no longer on sale). */
  public record CartLine(
      UUID listingId,
      long quantity,
      boolean available,
      String productLine,
      String unit,
      long moq,
      long unitPricePaise,
      String sellerKey,
      String sellerName,
      String city,
      boolean pickupAvailable,
      boolean ownDelivery,
      List<String> deliveryPincodes,
      long deliveryChargePaise,
      boolean codAccepted,
      boolean onlineAccepted) {}

  /**
   * Checkout of the cart lines of one seller. idempotencyKey: one per checkout attempt (the website
   * sends a fresh one per form); a repeat with the same key returns the same order.
   */
  public record CheckoutRequest(
      @NotEmpty @Size(max = 20) List<@NotNull UUID> listingIds,
      @NotNull String deliveryMethod,
      @Size(max = 300) String deliveryAddress,
      @Pattern(regexp = "^[1-9][0-9]{5}$") String deliveryPincode,
      @NotNull String paymentMethod,
      @NotBlank @Size(max = 64) String idempotencyKey) {}

  /** For a seller's step: a reason (decline, cancel) or the delivery person (out for delivery). */
  public record ActionRequest(
      @Size(max = 300) String reason,
      @Size(max = 100) String deliveryPersonName,
      @Pattern(regexp = "^[0-9+ ]{10,15}$") String deliveryPersonPhone) {}

  public record Item(
      UUID listingId,
      String productLine,
      long quantity,
      String unit,
      long unitPricePaise,
      int gstRateBp,
      long linePaise,
      long gstPaise) {}

  /**
   * An order as one of its two businesses sees it. sellerName is null for a buyer while a
   * confidential seller has not accepted yet. allowedActions: what the viewer may do now.
   */
  public record Order(
      UUID id,
      String viewerSide,
      String status,
      String paymentMethod,
      String paymentStatus,
      String deliveryMethod,
      String deliveryAddress,
      String deliveryPincode,
      String pickupCity,
      String pickupState,
      String buyerName,
      String sellerName,
      List<Item> items,
      long itemsPaise,
      long gstPaise,
      long deliveryPaise,
      long totalPaise,
      String statusReason,
      String deliveryPersonName,
      String deliveryPersonPhone,
      Instant placedAt,
      Instant updatedAt,
      Instant completedAt,
      Set<String> allowedActions) {

    public Order {
      items = List.copyOf(items);
      allowedActions = Set.copyOf(allowedActions);
    }
  }

  public record QuoteRequest(
      @NotNull UUID listingId,
      @NotNull @Positive @Max(MAX_QUANTITY) Long quantity,
      @Size(max = 500) String note) {}

  public record QuoteOffer(@NotNull @Positive @Max(1_000_000_000L) Long unitPricePaise) {}

  /** Ordering an offered quote: like checkout, for the quoted quantity and price. */
  public record QuoteOrderRequest(
      @NotNull String deliveryMethod,
      @Size(max = 300) String deliveryAddress,
      @Pattern(regexp = "^[1-9][0-9]{5}$") String deliveryPincode,
      @NotNull String paymentMethod,
      @NotBlank @Size(max = 64) String idempotencyKey) {}

  public record Quote(
      UUID id,
      String viewerSide,
      UUID listingId,
      String productLine,
      long quantity,
      String unit,
      String note,
      String status,
      Long offeredUnitPricePaise,
      Instant validUntil,
      String buyerName,
      String sellerName,
      Instant createdAt) {}
}
