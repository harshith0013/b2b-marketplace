package com.b2bmarket.listings;

import com.b2bmarket.compliance.ComplianceDtos.Stage;
import com.b2bmarket.listings.internal.ListingEntity;
import com.b2bmarket.listings.internal.ListingRepository;
import com.b2bmarket.platform.TenantScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every morning, the rules run again for every published offer and the results are stored, so the
 * Batch Passport shows when an offer stopped passing (for example as it neared expiry). Buyer
 * search already hides offers that fail; this records it. Runs as the reviewer database role, one
 * page per transaction.
 */
@Component
class DailyComplianceJob {

  private static final Logger log = LoggerFactory.getLogger(DailyComplianceJob.class);
  static final int PAGE_SIZE = 100;

  private final ListingRepository listings;
  private final EligibilityEvaluator eligibility;
  private final TenantScope scope;
  private final TransactionTemplate transaction;

  DailyComplianceJob(
      ListingRepository listings,
      EligibilityEvaluator eligibility,
      TenantScope scope,
      PlatformTransactionManager transactions) {
    this.listings = listings;
    this.eligibility = eligibility;
    this.scope = scope;
    this.transaction = new TransactionTemplate(transactions);
  }

  @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Kolkata")
  void recheckPublishedOffers() {
    int checked = 0;
    int failing = 0;
    for (int page = 0; ; page++) {
      int current = page;
      int[] counts =
          transaction.execute(
              status -> {
                scope.enterReview();
                Page<ListingEntity> found =
                    listings.findByStatus(
                        ListingStatus.PUBLISHED, PageRequest.of(current, PAGE_SIZE, Sort.by("id")));
                int failed = 0;
                for (ListingEntity listing : found.getContent()) {
                  if (!eligibility.check(listing, Stage.DAILY).blocks().isEmpty()) {
                    failed++;
                  }
                }
                return new int[] {found.getNumberOfElements(), failed, found.hasNext() ? 1 : 0};
              });
      checked += counts[0];
      failing += counts[1];
      if (counts[2] == 0) {
        break;
      }
    }
    log.info(
        "Daily compliance re-check: {} published offers, {} no longer passing", checked, failing);
  }
}
