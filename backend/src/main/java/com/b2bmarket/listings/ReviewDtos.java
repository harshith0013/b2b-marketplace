package com.b2bmarket.listings;

import com.b2bmarket.batches.PhotoDtos.Photo;
import com.b2bmarket.businesses.BusinessDtos.BusinessSummary;
import com.b2bmarket.listings.ListingDtos.Listing;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Request and response bodies of the admin review API. */
public final class ReviewDtos {

  private ReviewDtos() {}

  /** One listing waiting in the queue, with what the checks say about it now. */
  public record ReviewItem(
      Listing listing, BusinessSummary seller, List<EligibilityProblem> problems) {

    public ReviewItem {
      problems = List.copyOf(problems);
    }
  }

  public record Decision(String decision, String reason, Instant decidedAt) {}

  public record ReviewDetails(
      Listing listing,
      BusinessSummary seller,
      List<Photo> photos,
      List<EligibilityProblem> problems,
      List<Decision> history) {

    public ReviewDetails {
      photos = List.copyOf(photos);
      problems = List.copyOf(problems);
      history = List.copyOf(history);
    }
  }

  /** Shown to the seller, so it should say what to fix. */
  public record RejectRequest(@NotBlank @Size(max = 500) String reason) {}
}
