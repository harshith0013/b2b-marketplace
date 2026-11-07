package com.b2bmarket.listings;

import com.b2bmarket.batches.BatchDtos.BatchSummary;
import com.b2bmarket.batches.BatchPhotoService;
import com.b2bmarket.batches.BatchService;
import com.b2bmarket.catalog.CategoryService;
import com.b2bmarket.listings.ListingDtos.PriceTier;
import com.b2bmarket.listings.internal.ListingEntity;
import com.b2bmarket.platform.IndiaTime;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

/** Gathers the facts about a listing and runs the {@link Eligibility} checks. */
@Component
class EligibilityEvaluator {

  private final ListingViews views;
  private final BatchService batches;
  private final BatchPhotoService photos;
  private final CategoryService categories;
  private final Clock clock;

  EligibilityEvaluator(
      ListingViews views,
      BatchService batches,
      BatchPhotoService photos,
      CategoryService categories,
      Clock clock) {
    this.views = views;
    this.batches = batches;
    this.photos = photos;
    this.categories = categories;
    this.clock = clock;
  }

  /** Call inside a transaction scoped to the listing's business or to review. */
  List<EligibilityProblem> problems(ListingEntity listing) {
    BatchSummary batch =
        batches.summaries(listing.businessId(), List.of(listing.batchId())).get(listing.batchId());
    List<PriceTier> tiers =
        views
            .tiersOf(listing.businessId(), List.of(listing.id()))
            .getOrDefault(listing.id(), List.of());
    long highestPrice = tiers.stream().mapToLong(PriceTier::unitPricePaise).max().orElse(0);
    return Eligibility.check(
        new Eligibility.Facts(
            IndiaTime.today(clock),
            batch.expiresOn(),
            batch.quantity(),
            batch.quantityUnit(),
            batch.packsPerCase(),
            batch.mrpPaise(),
            listing.fields().moq(),
            listing.fields().gstRateBp(),
            highestPrice,
            photos.kindsOf(listing.businessId(), listing.batchId()),
            categories.isRegulated(batch.variant().categoryId())));
  }
}
