package com.b2bmarket.listings;

import com.b2bmarket.compliance.ComplianceDtos.Evaluation;
import com.b2bmarket.compliance.ComplianceDtos.Stage;
import com.b2bmarket.listings.internal.ListingRepository;
import com.b2bmarket.platform.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Runs (and stores) the compliance rules for a listing at a later stage, such as dispatch. */
@Service
public class ListingCompliance {

  private final ListingRepository listings;
  private final EligibilityEvaluator eligibility;

  ListingCompliance(ListingRepository listings, EligibilityEvaluator eligibility) {
    this.listings = listings;
    this.eligibility = eligibility;
  }

  /** Call inside a transaction scoped to the seller's business. */
  @Transactional(propagation = Propagation.MANDATORY)
  public Evaluation check(UUID sellerBusinessId, UUID listingId, Stage stage) {
    return eligibility.check(
        listings
            .findByIdAndBusinessId(listingId, sellerBusinessId)
            .orElseThrow(() -> ApiException.notFound("Listing not found.")),
        stage);
  }
}
