package com.b2bmarket.listings;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.batches.BatchDtos.BatchSummary;
import com.b2bmarket.batches.BatchPhotoService;
import com.b2bmarket.batches.BatchService;
import com.b2bmarket.batches.PhotoDtos.PhotoContent;
import com.b2bmarket.businesses.BusinessDtos.BusinessSummary;
import com.b2bmarket.businesses.BusinessDtos.Membership;
import com.b2bmarket.businesses.BusinessService;
import com.b2bmarket.listings.ListingDtos.Listing;
import com.b2bmarket.listings.ReviewDtos.ReviewDetails;
import com.b2bmarket.listings.ReviewDtos.ReviewItem;
import com.b2bmarket.listings.internal.ListingEntity;
import com.b2bmarket.listings.internal.ListingRepository;
import com.b2bmarket.listings.internal.ReviewDecisionEntity;
import com.b2bmarket.listings.internal.ReviewDecisionRepository;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.PageResult;
import com.b2bmarket.platform.TenantScope;
import com.b2bmarket.platform.Text;
import com.b2bmarket.products.ProductService;
import com.b2bmarket.users.CurrentUser;
import com.b2bmarket.users.PlatformRoles;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin review queue. Every method checks the caller's platform role first, then switches the
 * transaction to the reviewer database role, which can read every business's listings but change
 * only review results. Reviewers cannot decide on listings of a business they belong to.
 */
@Service
public class ReviewService {

  static final String NOT_FOUND = "Listing not found.";

  /** Oldest first: fair to sellers. */
  static final Sort QUEUE_ORDER = Sort.by("submittedAt", "id");

  private final ListingRepository listings;
  private final ReviewDecisionRepository decisions;
  private final ListingViews views;
  private final EligibilityEvaluator eligibility;
  private final BatchService batches;
  private final BatchPhotoService photos;
  private final ProductService products;
  private final BusinessService businesses;
  private final PlatformRoles roles;
  private final TenantScope scope;
  private final AuditService audit;
  private final Clock clock;

  ReviewService(
      ListingRepository listings,
      ReviewDecisionRepository decisions,
      ListingViews views,
      EligibilityEvaluator eligibility,
      BatchService batches,
      BatchPhotoService photos,
      ProductService products,
      BusinessService businesses,
      PlatformRoles roles,
      TenantScope scope,
      AuditService audit,
      Clock clock) {
    this.listings = listings;
    this.decisions = decisions;
    this.views = views;
    this.eligibility = eligibility;
    this.batches = batches;
    this.photos = photos;
    this.products = products;
    this.businesses = businesses;
    this.roles = roles;
    this.scope = scope;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PageResult<ReviewItem> queue(CurrentUser caller, Integer page, Integer size) {
    enter(caller);
    Page<ListingEntity> found =
        listings.findByStatus(
            ListingStatus.PENDING_REVIEW, PageResult.request(page, size, QUEUE_ORDER));
    List<ListingEntity> pending = found.getContent();
    List<Listing> listed = views.views(pending);
    Map<UUID, BusinessSummary> sellers =
        businesses.summaries(pending.stream().map(ListingEntity::businessId).distinct().toList());
    List<ReviewItem> items =
        IntStream.range(0, pending.size())
            .mapToObj(
                i ->
                    new ReviewItem(
                        listed.get(i),
                        sellers.get(pending.get(i).businessId()),
                        eligibility.problems(pending.get(i))))
            .toList();
    return new PageResult<>(items, found.getNumber(), found.getSize(), found.hasNext());
  }

  @Transactional(readOnly = true)
  public ReviewDetails details(CurrentUser caller, UUID listingId) {
    enter(caller);
    ListingEntity listing = load(listingId);
    return new ReviewDetails(
        views.view(listing),
        businesses.summaries(List.of(listing.businessId())).get(listing.businessId()),
        photos.photosOf(listing.businessId(), listing.batchId()),
        eligibility.problems(listing),
        decisions.findByListingIdOrderByCreatedAtAsc(listingId).stream()
            .map(ReviewDecisionEntity::toDto)
            .toList());
  }

  @Transactional(readOnly = true)
  public PhotoContent photo(CurrentUser caller, UUID listingId, UUID photoId) {
    enter(caller);
    ListingEntity listing = load(listingId);
    return photos.contentOf(listing.businessId(), listing.batchId(), photoId);
  }

  /** Publishes the listing and approves its batch and, if new, its product. */
  @Transactional
  public Listing approve(CurrentUser caller, UUID listingId) {
    enter(caller);
    ListingEntity listing = loadPending(caller, listingId);
    List<EligibilityProblem> problems = eligibility.problems(listing);
    if (!problems.isEmpty()) {
      throw ListingWorkflowService.notEligible(problems);
    }
    BatchSummary batch =
        batches.summaries(listing.businessId(), List.of(listing.batchId())).get(listing.batchId());
    Instant now = Instant.now(clock);
    if (listings.publishPending(listingId, now) == 0) {
      throw alreadyDecided();
    }
    batches.approve(listing.businessId(), listing.batchId());
    products.approveIfUnreviewed(batch.variant().productId());
    decide(caller, listing, ReviewDecisionEntity.APPROVED, null, now);
    return views.view(load(listingId));
  }

  /** Sends the listing back to the seller as a draft, with the reason. */
  @Transactional
  public Listing reject(CurrentUser caller, UUID listingId, String reason) {
    enter(caller);
    ListingEntity listing = loadPending(caller, listingId);
    String why = Text.required(reason, "A reason");
    Instant now = Instant.now(clock);
    if (listings.rejectPending(listingId, why, now) == 0) {
      throw alreadyDecided();
    }
    batches.returnToDraft(listing.businessId(), listing.batchId());
    decide(caller, listing, ReviewDecisionEntity.REJECTED, why, now);
    return views.view(load(listingId));
  }

  private void enter(CurrentUser caller) {
    roles.requireReviewer(caller);
    scope.enterReview();
  }

  private ListingEntity load(UUID listingId) {
    return listings.findById(listingId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
  }

  private ListingEntity loadPending(CurrentUser caller, UUID listingId) {
    ListingEntity listing = load(listingId);
    boolean ownBusiness =
        businesses.membershipsOf(caller.userId()).stream()
            .map(Membership::businessId)
            .anyMatch(listing.businessId()::equals);
    if (ownBusiness) {
      throw ApiException.forbidden("You cannot review listings of a business you belong to.");
    }
    if (listing.status() != ListingStatus.PENDING_REVIEW) {
      throw alreadyDecided();
    }
    return listing;
  }

  private void decide(
      CurrentUser caller, ListingEntity listing, String decision, String reason, Instant now) {
    decisions.save(
        ReviewDecisionEntity.create(
            listing.id(), listing.businessId(), caller.userId(), decision, reason, now));
    audit.record(
        new AuditEntry(
            ReviewDecisionEntity.APPROVED.equals(decision)
                ? "listing.approved"
                : "listing.rejected",
            caller.userId(),
            listing.businessId(),
            ListingService.TARGET_TYPE,
            listing.id(),
            Map.of("decision", decision)));
  }

  private static ApiException alreadyDecided() {
    return ApiException.conflict("This listing is no longer waiting for review.");
  }
}
