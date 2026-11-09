package com.b2bmarket.listings;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.batches.BatchDtos.BatchSummary;
import com.b2bmarket.batches.BatchService;
import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.businesses.TenantAccess;
import com.b2bmarket.listings.ListingDtos.CreateListingRequest;
import com.b2bmarket.listings.ListingDtos.Listing;
import com.b2bmarket.listings.ListingDtos.PriceTier;
import com.b2bmarket.listings.ListingDtos.PriceTierRequest;
import com.b2bmarket.listings.ListingDtos.UpdateListingRequest;
import com.b2bmarket.listings.internal.ListingEntity;
import com.b2bmarket.listings.internal.ListingEntity.Fields;
import com.b2bmarket.listings.internal.ListingRepository;
import com.b2bmarket.listings.internal.PriceTierEntity;
import com.b2bmarket.listings.internal.PriceTierRepository;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.PageResult;
import com.b2bmarket.platform.Text;
import com.b2bmarket.users.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A business's listings of its stock batches. Members can see them; owners and managers can create
 * and change drafts. Publishing (after review) arrives in Phase 5.
 */
@Service
public class ListingService {

  static final String TARGET_TYPE = "listing";
  static final String NOT_FOUND = "Listing not found.";
  static final Sort LIST_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

  private final ListingRepository listings;
  private final PriceTierRepository tiers;
  private final ListingViews views;
  private final BatchService batches;
  private final TenantAccess tenants;
  private final AuditService audit;
  private final Clock clock;

  ListingService(
      ListingRepository listings,
      PriceTierRepository tiers,
      ListingViews views,
      BatchService batches,
      TenantAccess tenants,
      AuditService audit,
      Clock clock) {
    this.listings = listings;
    this.tiers = tiers;
    this.views = views;
    this.batches = batches;
    this.tenants = tenants;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PageResult<Listing> list(CurrentUser caller, UUID businessId, Integer page, Integer size) {
    tenants.enter(caller, businessId, BusinessRole.ANY);
    Page<ListingEntity> found =
        listings.findByBusinessId(businessId, PageResult.request(page, size, LIST_ORDER));
    List<Listing> items = views.views(found.getContent());
    return new PageResult<>(items, found.getNumber(), found.getSize(), found.hasNext());
  }

  @Transactional(readOnly = true)
  public Listing get(CurrentUser caller, UUID businessId, UUID listingId) {
    tenants.enter(caller, businessId, BusinessRole.ANY);
    return views.view(load(businessId, listingId));
  }

  @Transactional
  public Listing create(CurrentUser caller, UUID businessId, CreateListingRequest request) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    BatchSummary batch = batches.requireSellable(businessId, request.batchId());
    if (listings.existsByBusinessIdAndBatchIdAndStatusNot(
        businessId, batch.id(), ListingStatus.CLOSED)) {
      throw ApiException.conflict("This batch already has a listing. Change that one instead.");
    }
    Fields fields =
        new Fields(
            request.clearanceReason(),
            request.visibility() == null ? Visibility.PUBLIC : request.visibility(),
            request.moq(),
            request.gstRateBp(),
            Text.optional(request.notes()));
    List<PriceTier> priceTiers =
        PriceTiers.validate(fields.moq(), batch.quantity(), request.priceTiers());

    Instant now = Instant.now(clock);
    ListingEntity listing =
        listings.save(
            ListingEntity.createDraft(businessId, batch.id(), batch.quantityUnit(), fields, now));
    saveTiers(listing, businessId, priceTiers, now);
    audit.record(
        new AuditEntry(
            "listing.created",
            caller.userId(),
            businessId,
            TARGET_TYPE,
            listing.id(),
            Map.of(
                "batchId", batch.id().toString(),
                "visibility", fields.visibility().name(),
                "priceTiers", priceTiers.size())));
    return ListingViews.toDto(listing, priceTiers, batch);
  }

  @Transactional
  public Listing update(
      CurrentUser caller, UUID businessId, UUID listingId, UpdateListingRequest request) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    ListingEntity listing = load(businessId, listingId);
    if (listing.status() != ListingStatus.DRAFT) {
      throw ApiException.conflict("Only draft listings can be changed for now.");
    }
    Fields before = listing.fields();
    Fields after = merge(before, request);
    List<String> changed = changedFields(before, after);

    List<PriceTier> currentTiers =
        views.tiersOf(businessId, List.of(listingId)).getOrDefault(listingId, List.of());
    boolean tiersGiven = request.priceTiers() != null;
    List<PriceTierRequest> wantedTiers =
        tiersGiven
            ? request.priceTiers()
            : currentTiers.stream()
                .map(t -> new PriceTierRequest(t.minQuantity(), t.unitPricePaise()))
                .toList();
    if (!changed.contains("moq") && !tiersGiven) {
      return finish(caller, businessId, listing, before, after, changed, currentTiers, false);
    }
    BatchSummary batch = batches.requireSellable(businessId, listing.batchId());
    List<PriceTier> newTiers = PriceTiers.validate(after.moq(), batch.quantity(), wantedTiers);
    boolean tiersChanged = !newTiers.equals(currentTiers);
    if (tiersChanged) {
      changed = new ArrayList<>(changed);
      changed.add("priceTiers");
    }
    return finish(
        caller, businessId, listing, before, after, List.copyOf(changed), newTiers, tiersChanged);
  }

  private Listing finish(
      CurrentUser caller,
      UUID businessId,
      ListingEntity listing,
      Fields before,
      Fields after,
      List<String> changed,
      List<PriceTier> priceTiers,
      boolean replaceTiers) {
    if (changed.isEmpty()) {
      return views.view(listing);
    }
    Instant now = Instant.now(clock);
    listing.apply(after, now);
    if (replaceTiers) {
      tiers.deleteAllOf(businessId, listing.id());
      saveTiers(listing, businessId, priceTiers, now);
    }
    audit.record(
        new AuditEntry(
            "listing.updated",
            caller.userId(),
            businessId,
            TARGET_TYPE,
            listing.id(),
            Map.of("changedFields", changed)));
    return ListingViews.toDto(
        listing,
        priceTiers,
        batches.summaries(businessId, List.of(listing.batchId())).get(listing.batchId()));
  }

  /** Delivery and cash-on-delivery settings; may change at any time until the listing closes. */
  @Transactional
  public Listing setDelivery(
      CurrentUser caller, UUID businessId, UUID listingId, ListingDtos.DeliveryOptions request) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    ListingEntity listing = load(businessId, listingId);
    if (listing.status() == ListingStatus.CLOSED) {
      throw ApiException.conflict("This listing is closed.");
    }
    List<String> pincodes =
        request.deliveryPincodes() == null
            ? List.of()
            : request.deliveryPincodes().stream().distinct().sorted().toList();
    if (!request.pickupAvailable() && !request.ownDelivery()) {
      throw ApiException.badRequest("Offer pickup, your own delivery, or both.");
    }
    if (request.ownDelivery() && pincodes.isEmpty()) {
      throw ApiException.badRequest("List the PIN codes you deliver to.");
    }
    ListingEntity.Delivery delivery =
        new ListingEntity.Delivery(
            request.pickupAvailable(),
            request.ownDelivery(),
            request.ownDelivery() ? pincodes : List.of(),
            request.ownDelivery() && request.deliveryChargePaise() != null
                ? request.deliveryChargePaise()
                : 0,
            request.codAccepted(),
            Boolean.TRUE.equals(request.onlineAccepted()));
    listing.setDelivery(delivery, Instant.now(clock));
    audit.record(
        new AuditEntry(
            "listing.delivery_changed",
            caller.userId(),
            businessId,
            TARGET_TYPE,
            listingId,
            Map.of(
                "ownDelivery",
                delivery.ownDelivery(),
                "cod",
                delivery.codAccepted(),
                "online",
                delivery.onlineAccepted())));
    return views.view(listing);
  }

  private void saveTiers(
      ListingEntity listing, UUID businessId, List<PriceTier> priceTiers, Instant now) {
    tiers.saveAll(
        priceTiers.stream()
            .map(t -> PriceTierEntity.create(listing.id(), businessId, t, now))
            .toList());
  }

  private ListingEntity load(UUID businessId, UUID listingId) {
    return listings
        .findByIdAndBusinessId(listingId, businessId)
        .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
  }

  private static Fields merge(Fields current, UpdateListingRequest request) {
    return new Fields(
        request.clearanceReason() == null ? current.clearanceReason() : request.clearanceReason(),
        request.visibility() == null ? current.visibility() : request.visibility(),
        request.moq() == null ? current.moq() : request.moq(),
        request.gstRateBp() == null ? current.gstRateBp() : request.gstRateBp(),
        request.notes() == null ? current.notes() : Text.optional(request.notes()));
  }

  static List<String> changedFields(Fields before, Fields after) {
    List<String> changed = new ArrayList<>();
    addIfChanged(changed, "clearanceReason", before.clearanceReason(), after.clearanceReason());
    addIfChanged(changed, "visibility", before.visibility(), after.visibility());
    addIfChanged(changed, "moq", before.moq(), after.moq());
    addIfChanged(changed, "gstRateBp", before.gstRateBp(), after.gstRateBp());
    addIfChanged(changed, "notes", before.notes(), after.notes());
    return List.copyOf(changed);
  }

  private static void addIfChanged(List<String> changed, String name, Object before, Object after) {
    if (!Objects.equals(before, after)) {
      changed.add(name);
    }
  }
}
