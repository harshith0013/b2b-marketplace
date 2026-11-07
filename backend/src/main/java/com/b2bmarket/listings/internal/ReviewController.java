package com.b2bmarket.listings.internal;

import com.b2bmarket.batches.PhotoDtos.PhotoContent;
import com.b2bmarket.listings.ListingDtos.Listing;
import com.b2bmarket.listings.ReviewDtos.RejectRequest;
import com.b2bmarket.listings.ReviewDtos.ReviewDetails;
import com.b2bmarket.listings.ReviewDtos.ReviewItem;
import com.b2bmarket.listings.ReviewService;
import com.b2bmarket.platform.PageResult;
import com.b2bmarket.users.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform reviewers only; the service checks the platform role (403 for everyone else). */
@RestController
@RequestMapping("/api/v1/admin/reviews")
class ReviewController {

  private final ReviewService reviews;

  ReviewController(ReviewService reviews) {
    this.reviews = reviews;
  }

  @GetMapping
  PageResult<ReviewItem> queue(
      CurrentUser caller,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return reviews.queue(caller, page, size);
  }

  @GetMapping("/{listingId}")
  ReviewDetails details(CurrentUser caller, @PathVariable UUID listingId) {
    return reviews.details(caller, listingId);
  }

  @GetMapping("/{listingId}/photos/{photoId}")
  ResponseEntity<byte[]> photo(
      CurrentUser caller, @PathVariable UUID listingId, @PathVariable UUID photoId) {
    PhotoContent content = reviews.photo(caller, listingId, photoId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .header("Content-Disposition", "inline")
        .body(content.bytes());
  }

  @PostMapping("/{listingId}/approve")
  Listing approve(CurrentUser caller, @PathVariable UUID listingId) {
    return reviews.approve(caller, listingId);
  }

  @PostMapping("/{listingId}/reject")
  Listing reject(
      CurrentUser caller, @PathVariable UUID listingId, @Valid @RequestBody RejectRequest request) {
    return reviews.reject(caller, listingId, request.reason());
  }
}
