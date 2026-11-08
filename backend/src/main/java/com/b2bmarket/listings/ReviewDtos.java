package com.b2bmarket.listings;

import com.b2bmarket.batches.PhotoDtos.Photo;
import com.b2bmarket.businesses.BusinessDtos.BusinessSummary;
import com.b2bmarket.listings.ListingDtos.LabelCheck;
import com.b2bmarket.listings.ListingDtos.Listing;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Request and response bodies of the admin review API. */
public final class ReviewDtos {

  private ReviewDtos() {}

  /** One listing waiting in the queue, with what the checks say about it now. */
  /** problems block approval; flags need the reviewer's confirmation (see ApproveRequest). */
  public record ReviewItem(
      Listing listing, BusinessSummary seller, List<String> problems, List<String> flags) {

    public ReviewItem {
      problems = List.copyOf(problems);
      flags = List.copyOf(flags);
    }
  }

  public record Decision(String decision, String reason, Instant decidedAt) {}

  public record ReviewDetails(
      Listing listing,
      BusinessSummary seller,
      List<Photo> photos,
      List<String> problems,
      List<String> flags,
      LabelCheck label,
      String ruleSetVersion,
      List<Decision> history) {

    public ReviewDetails {
      photos = List.copyOf(photos);
      problems = List.copyOf(problems);
      flags = List.copyOf(flags);
      history = List.copyOf(history);
    }
  }

  /**
   * confirmedLabelCheck: the reviewer compared the label photo with the typed details themselves.
   * Required when the rules raised any flag.
   */
  public record ApproveRequest(Boolean confirmedLabelCheck) {}

  /** Shown to the seller, so it should say what to fix. */
  public record RejectRequest(@NotBlank @Size(max = 500) String reason) {}
}
