package com.b2bmarket.listings;

import com.b2bmarket.ai.LabelReading;
import com.b2bmarket.batches.BatchDtos.BatchSummary;
import com.b2bmarket.batches.BatchPhotoService;
import com.b2bmarket.batches.BatchService;
import com.b2bmarket.batches.LabelReadings;
import com.b2bmarket.batches.PhotoKind;
import com.b2bmarket.catalog.CategoryService;
import com.b2bmarket.compliance.ComplianceDtos.Evaluation;
import com.b2bmarket.compliance.ComplianceDtos.Facts;
import com.b2bmarket.compliance.ComplianceDtos.LabelFacts;
import com.b2bmarket.compliance.ComplianceDtos.Stage;
import com.b2bmarket.compliance.ComplianceService;
import com.b2bmarket.listings.ListingDtos.LabelCheck;
import com.b2bmarket.listings.ListingDtos.PriceTier;
import com.b2bmarket.listings.ListingDtos.Readiness;
import com.b2bmarket.listings.internal.ListingEntity;
import com.b2bmarket.platform.IndiaTime;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Gathers the facts about a listing (its batch, prices, photos, category and the latest label
 * reading) and runs the compliance rules. Call inside a transaction scoped to the listing's
 * business or to review.
 */
@Component
class EligibilityEvaluator {

  private final ListingViews views;
  private final BatchService batches;
  private final BatchPhotoService photos;
  private final LabelReadings labels;
  private final CategoryService categories;
  private final ComplianceService compliance;
  private final Clock clock;

  EligibilityEvaluator(
      ListingViews views,
      BatchService batches,
      BatchPhotoService photos,
      LabelReadings labels,
      CategoryService categories,
      ComplianceService compliance,
      Clock clock) {
    this.views = views;
    this.batches = batches;
    this.photos = photos;
    this.labels = labels;
    this.categories = categories;
    this.compliance = compliance;
    this.clock = clock;
  }

  /** What still stops the listing (blocks), what a person must look at (flags), and the label. */
  Readiness readiness(ListingEntity listing) {
    Optional<LabelReadings.Reading> label = labels.latest(listing.businessId(), listing.batchId());
    Evaluation evaluation = compliance.preview(facts(listing, label));
    return new Readiness(
        evaluation.blocks(),
        evaluation.flags(),
        evaluation.ruleSetVersion(),
        label.map(EligibilityEvaluator::view).orElse(null));
  }

  /** Runs the rules for a stage and stores the evaluation. */
  Evaluation check(ListingEntity listing, Stage stage) {
    Optional<LabelReadings.Reading> label = labels.latest(listing.businessId(), listing.batchId());
    return compliance.check(
        stage, listing.businessId(), listing.batchId(), listing.id(), facts(listing, label));
  }

  private Facts facts(ListingEntity listing, Optional<LabelReadings.Reading> label) {
    BatchSummary batch =
        batches.summaries(listing.businessId(), List.of(listing.batchId())).get(listing.batchId());
    List<PriceTier> tiers =
        views
            .tiersOf(listing.businessId(), List.of(listing.id()))
            .getOrDefault(listing.id(), List.of());
    long highestPrice = tiers.stream().mapToLong(PriceTier::unitPricePaise).max().orElse(0);
    return new Facts(
        IndiaTime.today(clock),
        batch.expiresOn(),
        batch.quantity(),
        batch.quantityUnit().name(),
        batch.packsPerCase(),
        batch.mrpPaise(),
        listing.fields().moq(),
        listing.fields().gstRateBp(),
        highestPrice,
        photos.kindsOf(listing.businessId(), listing.batchId()).stream()
            .map(PhotoKind::name)
            .collect(Collectors.toSet()),
        categories.isRegulated(batch.variant().categoryId()),
        batch.batchNumber(),
        label.map(r -> facts(r.reading())).orElse(null));
  }

  private static LabelFacts facts(LabelReading r) {
    return new LabelFacts(
        r.outcome(), r.expiryYear(), r.expiryMonth(), r.expiryDay(), r.batchNumber(), r.mrpPaise());
  }

  static LabelCheck view(LabelReadings.Reading stored) {
    LabelReading r = stored.reading();
    String expiry =
        r.expiryYear() == null || r.expiryMonth() == null
            ? null
            : r.expiryDay() == null
                ? "%04d-%02d".formatted(r.expiryYear(), r.expiryMonth())
                : "%04d-%02d-%02d".formatted(r.expiryYear(), r.expiryMonth(), r.expiryDay());
    return new LabelCheck(
        r.outcome(),
        r.reason(),
        r.printedDate(),
        r.dateType(),
        expiry,
        r.batchNumber(),
        r.mrpPaise(),
        stored.readAt());
  }
}
