package com.b2bmarket.listings;

import com.b2bmarket.batches.BatchDtos.BatchSummary;
import com.b2bmarket.batches.BatchService;
import com.b2bmarket.listings.ListingDtos.Listing;
import com.b2bmarket.listings.ListingDtos.PriceTier;
import com.b2bmarket.listings.internal.ListingEntity;
import com.b2bmarket.listings.internal.ListingEntity.Fields;
import com.b2bmarket.listings.internal.PriceTierEntity;
import com.b2bmarket.listings.internal.PriceTierRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds listing responses (with price tiers and batch details) for sellers and reviewers. Call
 * inside a transaction scoped to the business or to review.
 */
@Component
class ListingViews {

  private final PriceTierRepository tiers;
  private final BatchService batches;

  ListingViews(PriceTierRepository tiers, BatchService batches) {
    this.tiers = tiers;
    this.batches = batches;
  }

  Listing view(ListingEntity listing) {
    return views(List.of(listing)).getFirst();
  }

  /** In the given order; listings may belong to different businesses (the review queue). */
  List<Listing> views(List<ListingEntity> listings) {
    Map<UUID, List<ListingEntity>> byBusiness =
        listings.stream().collect(Collectors.groupingBy(ListingEntity::businessId));
    Map<UUID, List<PriceTier>> tiersById = new HashMap<>();
    Map<UUID, BatchSummary> batchesById = new HashMap<>();
    byBusiness.forEach(
        (businessId, ofBusiness) -> {
          tiersById.putAll(
              tiersOf(businessId, ofBusiness.stream().map(ListingEntity::id).toList()));
          batchesById.putAll(
              batches.summaries(
                  businessId, ofBusiness.stream().map(ListingEntity::batchId).toList()));
        });
    return listings.stream()
        .map(l -> toDto(l, tiersById.getOrDefault(l.id(), List.of()), batchesById.get(l.batchId())))
        .toList();
  }

  Map<UUID, List<PriceTier>> tiersOf(UUID businessId, Collection<UUID> listingIds) {
    if (listingIds.isEmpty()) {
      return Map.of();
    }
    return tiers
        .findByBusinessIdAndListingIdInOrderByMinQuantityAsc(businessId, listingIds)
        .stream()
        .collect(
            Collectors.groupingBy(
                PriceTierEntity::listingId,
                Collectors.mapping(PriceTierEntity::toDto, Collectors.toList())));
  }

  static Listing toDto(ListingEntity listing, List<PriceTier> priceTiers, BatchSummary batch) {
    Fields f = listing.fields();
    return new Listing(
        listing.id(),
        listing.status(),
        f.clearanceReason(),
        f.visibility(),
        f.moq(),
        listing.quantityUnit(),
        f.gstRateBp(),
        f.notes(),
        priceTiers,
        batch,
        listing.submittedAt(),
        listing.reviewedAt(),
        listing.rejectionReason(),
        deliveryOf(listing));
  }

  static ListingDtos.DeliveryOptions deliveryOf(ListingEntity listing) {
    ListingEntity.Delivery d = listing.delivery();
    return new ListingDtos.DeliveryOptions(
        d.pickupAvailable(),
        d.ownDelivery(),
        d.deliveryPincodes(),
        d.deliveryChargePaise(),
        d.codAccepted());
  }
}
