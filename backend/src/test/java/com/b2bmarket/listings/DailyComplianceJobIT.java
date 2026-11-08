package com.b2bmarket.listings;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2bmarket.SellerFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The morning re-check stores a DAILY evaluation for every published offer, failing or not. */
class DailyComplianceJobIT extends SellerFixtures {

  @Autowired DailyComplianceJob job;

  @Test
  void recordsWhetherEachPublishedOfferStillPasses() {
    String seller = newPerson("seller");
    String business = createBusiness(seller, "Daily Traders");
    String fine = readyListing(seller, business);
    publish(seller, business, fine);
    String expiring = readyListing(seller, business);
    publish(seller, business, expiring);
    // Time passes for the second offer: 10 days left (an approved date changes only via review).
    String batch = batchOfListing(expiring);
    jdbc.update(
        "update inventory_batches set expires_on = ?::date, status = 'PENDING_REVIEW' where id = ?::uuid",
        TODAY_IN_INDIA.plusDays(10).toString(),
        batch);
    jdbc.update("update inventory_batches set status = 'APPROVED' where id = ?::uuid", batch);

    job.recheckPublishedOffers();

    assertThat(daily(fine)).isEqualTo("false");
    assertThat(daily(expiring)).isEqualTo("true");
  }

  String daily(String listingId) {
    return jdbc.queryForObject(
        "select blocked::text from compliance_checks where listing_id = ?::uuid and stage = 'DAILY'"
            + " order by created_at desc limit 1",
        String.class,
        listingId);
  }
}
