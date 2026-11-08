package com.b2bmarket.search.internal;

import com.b2bmarket.search.AssistedSearch;
import com.b2bmarket.search.OfferDtos.OfferDetails;
import com.b2bmarket.search.OfferDtos.OfferPage;
import com.b2bmarket.search.OfferDtos.OfferSummary;
import com.b2bmarket.search.OfferDtos.Place;
import com.b2bmarket.search.OfferFilters;
import com.b2bmarket.search.OfferSearchService;
import com.b2bmarket.search.OfferSearchService.PhotoContent;
import com.b2bmarket.search.OfferSort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  private final AssistedSearch assisted;

  OfferController(OfferSearchService offers, AssistedSearch assisted) {
    this.offers = offers;
    this.assisted = assisted;
  }

  /** A question in the shopper's words (any language); at most 300 characters are used. */
  record InterpretRequest(@NotBlank @Size(max = 1000) String question) {}

  /**
   * Turns a question into checked search filters (or says to use normal search). Public, limited
   * per visitor and per day; the website forwards the visitor's address in X-Forwarded-For.
   */
  @PostMapping("/interpret")
  AssistedSearch.Result interpret(
      @Valid @RequestBody InterpretRequest request, HttpServletRequest http) {
    return assisted.interpret(request.question(), visitor(http));
  }

  @GetMapping("/{listingId}/similar")
  List<OfferSummary> similar(@PathVariable UUID listingId) {
    return offers.similar(listingId);
  }

  /**
   * The first X-Forwarded-For address, else the connection's. Only the website reaches the API in
   * production (it is not exposed to the internet), so the header is the website's to set.
   */
  static String visitor(HttpServletRequest http) {
    String forwarded = http.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].strip();
    }
    return http.getRemoteAddr();
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
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) Long quantity,
      @RequestParam(required = false) String unit) {
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
                size)
            .wanting(quantity, unit));
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
