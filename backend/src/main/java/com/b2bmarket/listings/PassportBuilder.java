package com.b2bmarket.listings;

import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.batches.LabelReadings;
import com.b2bmarket.compliance.ComplianceService;
import com.b2bmarket.listings.PassportDtos.Entry;
import com.b2bmarket.listings.PassportDtos.Passport;
import com.b2bmarket.listings.internal.ListingEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Builds a batch's history from records already kept: audit events (batch, its photos and its
 * listing), label readings and stored rule checks. Call inside a scoped transaction. Shows codes,
 * never people: no reviewer names, no addresses.
 */
@Component
class PassportBuilder {

  static final int MAX_EVENTS = 200;

  /** Audit actions worth showing; others (if any are added later) stay out until worded. */
  static final Set<String> SHOWN =
      Set.of(
          "batch.created",
          "batch.updated",
          "batch.approved_details_changed",
          "batch_photo.added",
          "batch_photo.deleted",
          "listing.created",
          "listing.updated",
          "listing.submitted",
          "listing.withdrawn",
          "listing.approved",
          "listing.rejected",
          "listing.paused",
          "listing.resumed",
          "listing.closed");

  private final AuditService audit;
  private final LabelReadings labels;
  private final ComplianceService compliance;

  PassportBuilder(AuditService audit, LabelReadings labels, ComplianceService compliance) {
    this.audit = audit;
    this.labels = labels;
    this.compliance = compliance;
  }

  Passport passport(ListingEntity listing) {
    List<Entry> entries = new ArrayList<>();
    for (AuditService.Recorded event :
        audit.about(
            listing.businessId(),
            List.of(listing.batchId(), listing.id()),
            "batchId",
            MAX_EVENTS)) {
      if (SHOWN.contains(event.action())) {
        entries.add(new Entry(event.at(), event.action(), List.of()));
      }
    }
    labels
        .latest(listing.businessId(), listing.batchId())
        .ifPresent(
            r -> entries.add(new Entry(r.readAt(), "label.read", List.of(r.reading().outcome()))));
    for (ComplianceService.CheckSummary check :
        compliance.history(listing.businessId(), listing.batchId())) {
      List<String> codes = new ArrayList<>(check.blocks());
      codes.addAll(check.flags());
      entries.add(new Entry(check.at(), "check." + check.stage(), codes));
    }
    entries.sort(Comparator.comparing(Entry::at));
    return new Passport(entries);
  }
}
