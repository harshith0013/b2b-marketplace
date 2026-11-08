package com.b2bmarket.search.internal;

import com.b2bmarket.search.OfferDtos.OfferDetails;
import com.b2bmarket.search.OfferDtos.OfferPage;
import com.b2bmarket.search.OfferDtos.Place;
import com.b2bmarket.search.OfferFilters;
import com.b2bmarket.search.OfferSearchService;
import com.b2bmarket.search.OfferSearchService.PhotoContent;
import com.b2bmarket.search.OfferSort;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only offer search (no sign-in; see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/offers")
class OfferController {

  /** Photos of a published offer rarely change; a short public cache spares the storage. */
  static final CacheControl PHOTO_CACHE = CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();

  private final OfferSearchService offers;

  OfferController(OfferSearchService offers) {
    this.offers = offers;
  }

  @GetMapping
  OfferPage search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String brand,
      @RequestParam(required = false) Long maxPricePerPackPaise,
      @RequestParam(required = false) Long maxOrderValuePaise,
      @RequestParam(required = false) Integer minDaysLeft,
      @RequestParam(required = false) OfferSort sort,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return offers.search(
        OfferFilters.of(
            q,
            categoryId,
            state,
            city,
            brand,
            maxPricePerPackPaise,
            maxOrderValuePaise,
            minDaysLeft,
            sort,
            page,
            size));
  }

  @GetMapping("/locations")
  List<Place> locations() {
    return offers.places();
  }

  @GetMapping("/brands")
  List<String> brands() {
    return offers.brands();
  }

  @GetMapping("/{listingId}")
  OfferDetails details(@PathVariable UUID listingId) {
    return offers.details(listingId);
  }

  @GetMapping("/{listingId}/photos/{photoId}")
  ResponseEntity<byte[]> photo(@PathVariable UUID listingId, @PathVariable UUID photoId) {
    PhotoContent content = offers.photo(listingId, photoId);
    return ResponseEntity.ok()
        .cacheControl(PHOTO_CACHE)
        .contentType(MediaType.parseMediaType(content.contentType()))
        .header("Content-Disposition", "inline")
        .body(content.bytes());
  }
}
