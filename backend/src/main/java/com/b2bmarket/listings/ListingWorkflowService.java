package com.b2bmarket.listings;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.batches.BatchDtos.BatchSummary;
import com.b2bmarket.batches.BatchService;
import com.b2bmarket.batches.BatchStatus;
import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.businesses.TenantAccess;
import com.b2bmarket.listings.ListingDtos.Listing;
import com.b2bmarket.listings.internal.ListingEntity;
import com.b2bmarket.listings.internal.ListingRepository;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.users.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A seller moving a listing through its life: submit for review, withdraw, pause, resume, close.
 * Publishing happens only through review ({@link ReviewService}); the database also refuses it.
 */
@Service
public class ListingWorkflowService {

  static final String PROBLEMS = "problems";

  private final ListingRepository listings;
  private final ListingViews views;
  private final EligibilityEvaluator eligibility;
  private final BatchService batches;
  private final TenantAccess tenants;
  private final AuditService audit;
  private final Clock clock;

  ListingWorkflowService(
      ListingRepository listings,
      ListingViews views,
      EligibilityEvaluator eligibility,
      BatchService batches,
      TenantAccess tenants,
      AuditService audit,
      Clock clock) {
    this.listings = listings;
    this.views = views;
    this.eligibility = eligibility;
    this.batches = batches;
    this.tenants = tenants;
    this.audit = audit;
    this.clock = clock;
  }

  /** What still stops this listing from being submitted. Any member may ask. */
  @Transactional(readOnly = true)
  public List<EligibilityProblem> problems(CurrentUser caller, UUID businessId, UUID listingId) {
    tenants.enter(caller, businessId, BusinessRole.ANY);
    return eligibility.problems(load(businessId, listingId));
  }

  @Transactional
  public Listing submit(CurrentUser caller, UUID businessId, UUID listingId) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    ListingEntity listing = load(businessId, listingId);
    requireStatus(listing, Set.of(ListingStatus.DRAFT), "Only a draft can be submitted.");
    List<EligibilityProblem> problems = eligibility.problems(listing);
    if (!problems.isEmpty()) {
      throw notEligible(problems);
    }
    listing.submit(now());
    batches.markSubmitted(businessId, listing.batchId());
    record(caller, listing, "listing.submitted");
    return views.view(reloaded(businessId, listingId));
  }

  /** Takes a listing out of the review queue, so its batch and photos can be changed. */
  @Transactional
  public Listing withdraw(CurrentUser caller, UUID businessId, UUID listingId) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    ListingEntity listing = load(businessId, listingId);
    requireStatus(
        listing,
        Set.of(ListingStatus.PENDING_REVIEW),
        "Only a listing in review can be withdrawn.");
    listing.moveTo(ListingStatus.DRAFT, now());
    batches.returnToDraft(businessId, listing.batchId());
    record(caller, listing, "listing.withdrawn");
    return views.view(reloaded(businessId, listingId));
  }

  @Transactional
  public Listing pause(CurrentUser caller, UUID businessId, UUID listingId) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    ListingEntity listing = load(businessId, listingId);
    requireStatus(listing, Set.of(ListingStatus.PUBLISHED), "Only a published listing can pause.");
    listing.moveTo(ListingStatus.PAUSED, now());
    record(caller, listing, "listing.paused");
    return views.view(listing);
  }

  /** Back on sale without a new review, if the approved batch still passes every check. */
  @Transactional
  public Listing resume(CurrentUser caller, UUID businessId, UUID listingId) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    ListingEntity listing = load(businessId, listingId);
    requireStatus(listing, Set.of(ListingStatus.PAUSED), "Only a paused listing can resume.");
    BatchSummary batch =
        batches.summaries(businessId, List.of(listing.batchId())).get(listing.batchId());
    if (batch.status() != BatchStatus.APPROVED) {
      throw ApiException.conflict(
          "The batch details changed and need review again. Close this listing and list it anew.");
    }
    List<EligibilityProblem> problems = eligibility.problems(listing);
    if (!problems.isEmpty()) {
      throw notEligible(problems);
    }
    listing.moveTo(ListingStatus.PUBLISHED, now());
    record(caller, listing, "listing.resumed");
    return views.view(listing);
  }

  /** Ends the listing for good; the batch can then be listed again. */
  @Transactional
  public Listing close(CurrentUser caller, UUID businessId, UUID listingId) {
    tenants.enter(caller, businessId, BusinessRole.OWNER_OR_MANAGER);
    ListingEntity listing = load(businessId, listingId);
    if (listing.status() == ListingStatus.CLOSED) {
      throw ApiException.conflict("This listing is already closed.");
    }
    boolean wasInReview = listing.status() == ListingStatus.PENDING_REVIEW;
    listing.moveTo(ListingStatus.CLOSED, now());
    if (wasInReview) {
      batches.returnToDraft(businessId, listing.batchId());
    }
    record(caller, listing, "listing.closed");
    return views.view(reloaded(businessId, listingId));
  }

  static ApiException notEligible(List<EligibilityProblem> problems) {
    return ApiException.unprocessable(
        "This listing is not ready yet. Fix the problems listed and try again.",
        Map.of(PROBLEMS, problems.stream().map(Enum::name).toList()));
  }

  private ListingEntity load(UUID businessId, UUID listingId) {
    return listings
        .findByIdAndBusinessId(listingId, businessId)
        .orElseThrow(() -> ApiException.notFound(ListingService.NOT_FOUND));
  }

  /** After a batch status update, which clears the persistence context. */
  private ListingEntity reloaded(UUID businessId, UUID listingId) {
    return load(businessId, listingId);
  }

  private static void requireStatus(
      ListingEntity listing, Set<ListingStatus> allowed, String message) {
    if (!allowed.contains(listing.status())) {
      throw ApiException.conflict(message);
    }
  }

  private void record(CurrentUser caller, ListingEntity listing, String action) {
    audit.record(
        new AuditEntry(
            action,
            caller.userId(),
            listing.businessId(),
            ListingService.TARGET_TYPE,
            listing.id(),
            Map.of("status", listing.status().name())));
  }

  private Instant now() {
    return Instant.now(clock);
  }
}
