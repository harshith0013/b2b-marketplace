package com.b2bmarket.listings.internal;

import com.b2bmarket.listings.EligibilityProblem;
import com.b2bmarket.listings.ListingDtos.CreateListingRequest;
import com.b2bmarket.listings.ListingDtos.Listing;
import com.b2bmarket.listings.ListingDtos.UpdateListingRequest;
import com.b2bmarket.listings.ListingService;
import com.b2bmarket.listings.ListingWorkflowService;
import com.b2bmarket.platform.PageResult;
import com.b2bmarket.users.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A business's own listings. Membership and role checks happen in the service. */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/listings")
class ListingController {

  private final ListingService listings;
  private final ListingWorkflowService workflow;

  ListingController(ListingService listings, ListingWorkflowService workflow) {
    this.listings = listings;
    this.workflow = workflow;
  }

  @GetMapping
  PageResult<Listing> list(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return listings.list(caller, businessId, page, size);
  }

  @PostMapping
  ResponseEntity<Listing> create(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @Valid @RequestBody CreateListingRequest request) {
    Listing created = listings.create(caller, businessId, request);
    return ResponseEntity.created(
            URI.create("/api/v1/businesses/" + businessId + "/listings/" + created.id()))
        .body(created);
  }

  @GetMapping("/{listingId}")
  Listing get(CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID listingId) {
    return listings.get(caller, businessId, listingId);
  }

  @PatchMapping("/{listingId}")
  Listing update(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @PathVariable UUID listingId,
      @Valid @RequestBody UpdateListingRequest request) {
    return listings.update(caller, businessId, listingId, request);
  }

  /** What still stops the listing from being submitted (codes; empty when ready). */
  @GetMapping("/{listingId}/eligibility")
  List<EligibilityProblem> eligibility(
      CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID listingId) {
    return workflow.problems(caller, businessId, listingId);
  }

  @PostMapping("/{listingId}/submit")
  Listing submit(CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID listingId) {
    return workflow.submit(caller, businessId, listingId);
  }

  @PostMapping("/{listingId}/withdraw")
  Listing withdraw(
      CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID listingId) {
    return workflow.withdraw(caller, businessId, listingId);
  }

  @PostMapping("/{listingId}/pause")
  Listing pause(CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID listingId) {
    return workflow.pause(caller, businessId, listingId);
  }

  @PostMapping("/{listingId}/resume")
  Listing resume(CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID listingId) {
    return workflow.resume(caller, businessId, listingId);
  }

  @PostMapping("/{listingId}/close")
  Listing close(CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID listingId) {
    return workflow.close(caller, businessId, listingId);
  }
}
