package com.b2bmarket.search;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What anyone may see about an offer. No warehouse street address, and for confidential offers no
 * seller name and no seller id (so different confidential offers cannot be linked to one seller).
 * Money in paise, prices before GST.
 */
public final class OfferDtos {

  private OfferDtos() {}

  public record Location(String city, String state, String pincode) {}

  /** name is null when the offer is confidential. */
  public record Seller(String name, boolean confidential, String verificationStatus) {}

  public record OfferSummary(
      UUID id,
      String brand,
      String productName,
      BigDecimal packSize,
      String packUnit,
      UUID categoryId,
      String dateType,
      LocalDate expiresOn,
      long daysLeft,
      long availableQuantity,
      String quantityUnit,
      Integer packsPerCase,
      long moq,
      /** Price per unit (piece or case) at the minimum order. */
      long unitPricePaise,
      /** The same price for one pack, so pieces and cases compare fairly. */
      long pricePerPackPaise,
      long minimumOrderValuePaise,
      Long mrpPaise,
      int gstRateBp,
      String clearanceReason,
      Location location,
      Seller seller,
      UUID coverPhotoId,
      Instant publishedAt) {}

  public record PriceTier(long minQuantity, long unitPricePaise) {}

  public record OfferPhoto(UUID id, String kind, int width, int height) {}

  public record OfferDetails(
      OfferSummary offer,
      String batchNumber,
      String notes,
      List<PriceTier> priceTiers,
      List<OfferPhoto> photos) {

    public OfferDetails {
      priceTiers = List.copyOf(priceTiers);
      photos = List.copyOf(photos);
    }
  }

  /** A state with the cities in it that have offers, for the location filter. */
  public record Place(String state, List<String> cities) {

    public Place {
      cities = List.copyOf(cities);
    }
  }

  public record OfferPage(List<OfferSummary> items, int page, int size, boolean hasNext) {

    public OfferPage {
      items = List.copyOf(items);
    }
  }
}
