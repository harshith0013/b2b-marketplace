package com.b2bmarket.listings.internal;

import com.b2bmarket.listings.ListingDtos.CreateListingRequest;
import com.b2bmarket.listings.ListingDtos.Listing;
import com.b2bmarket.listings.ListingDtos.UpdateListingRequest;
import com.b2bmarket.listings.ListingService;
import com.b2bmarket.platform.PageResult;
import com.b2bmarket.users.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
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

  ListingController(ListingService listings) {
    this.listings = listings;
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
}
