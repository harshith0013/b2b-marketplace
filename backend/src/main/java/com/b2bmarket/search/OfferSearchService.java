package com.b2bmarket.search;

import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.IndiaTime;
import com.b2bmarket.platform.ObjectStorage;
import com.b2bmarket.platform.TenantScope;
import com.b2bmarket.search.OfferDtos.Location;
import com.b2bmarket.search.OfferDtos.OfferDetails;
import com.b2bmarket.search.OfferDtos.OfferPage;
import com.b2bmarket.search.OfferDtos.OfferPhoto;
import com.b2bmarket.search.OfferDtos.OfferSummary;
import com.b2bmarket.search.OfferDtos.Place;
import com.b2bmarket.search.OfferDtos.PriceTier;
import com.b2bmarket.search.OfferDtos.Seller;
import com.b2bmarket.search.internal.OfferQueries;
import com.b2bmarket.search.internal.OfferQueries.PhotoRow;
import com.b2bmarket.search.internal.OfferQueries.Row;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Offers anyone may browse: published listings whose batch is approved, with at least 30 days
 * before the printed date and enough stock for the minimum order. Every method runs as the
 * read-only public database role with a short time limit.
 */
@Service
public class OfferSearchService {

  static final String NOT_FOUND = "Offer not found.";
  static final int MAX_BRANDS = 100;

  /** Photo bytes for the public, with their type. */
  public record PhotoContent(byte[] bytes, String contentType) {}

  private final OfferQueries queries;
  private final ObjectStorage storage;
  private final TenantScope scope;
  private final Clock clock;

  OfferSearchService(OfferQueries queries, ObjectStorage storage, TenantScope scope, Clock clock) {
    this.queries = queries;
    this.storage = storage;
    this.scope = scope;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public OfferPage search(OfferFilters filters) {
    scope.enterPublic();
    LocalDate today = IndiaTime.today(clock);
    List<Row> rows = queries.search(filters, today.plusDays(filters.minDaysLeft()));
    boolean hasNext = rows.size() > filters.size();
    List<OfferSummary> items =
        rows.stream().limit(filters.size()).map(row -> summary(row, today)).toList();
    return new OfferPage(items, filters.page(), filters.size(), hasNext);
  }

  @Transactional(readOnly = true)
  public OfferDetails details(UUID listingId) {
    scope.enterPublic();
    LocalDate today = IndiaTime.today(clock);
    Row row = load(listingId, today);
    return new OfferDetails(
        summary(row, today),
        row.batchNumber(),
        row.notes(),
        queries.priceTiers(listingId).stream().map(t -> new PriceTier(t[0], t[1])).toList(),
        queries.photos(listingId).stream()
            .map(p -> new OfferPhoto(p.id(), p.kind(), p.width(), p.height()))
            .toList(),
        passport(row.publishedAt(), queries.passportFacts(listingId)));
  }

  static OfferDtos.PassportSummary passport(
      java.time.Instant approvedAt, OfferQueries.PassportFacts f) {
    boolean matches =
        "READ".equals(f.labelOutcome())
            && f.labelYear() != null
            && f.labelMonth() != null
            && f.labelYear() == f.expiresOn().getYear()
            && f.labelMonth() == f.expiresOn().getMonthValue()
            && (f.labelDay() == null || f.labelDay() == f.expiresOn().getDayOfMonth());
    return new OfferDtos.PassportSummary(
        approvedAt,
        matches ? "MATCHES_LABEL" : "REVIEWER_CHECKED",
        f.detailsChangedAt() != null,
        f.detailsChangedAt());
  }

  @Transactional(readOnly = true)
  public PhotoContent photo(UUID listingId, UUID photoId) {
    scope.enterPublic();
    load(listingId, IndiaTime.today(clock));
    PhotoRow photo =
        queries.photos(listingId).stream()
            .filter(p -> p.id().equals(photoId))
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("Photo not found."));
    return new PhotoContent(storage.get(photo.storageKey()), photo.contentType());
  }

  static final int SIMILAR_COUNT = 6;

  /**
   * Up to six other offers like this one (plain rules, no AI): same or sibling category, price per
   * pack within half to one and a half times this one's.
   */
  @Transactional(readOnly = true)
  public List<OfferSummary> similar(UUID listingId) {
    scope.enterPublic();
    LocalDate today = IndiaTime.today(clock);
    Row target = load(listingId, today);
    long pricePerPack = summary(target, today).pricePerPackPaise();
    UUID parent = queries.parentCategory(target.categoryId()).orElse(null);
    return queries
        .similar(
            listingId,
            target.categoryId(),
            parent,
            target.state(),
            pricePerPack,
            pricePerPack / 2,
            pricePerPack * 3 / 2,
            today.plusDays(OfferFilters.MIN_SHELF_LIFE_DAYS),
            SIMILAR_COUNT)
        .stream()
        .map(row -> summary(row, today))
        .toList();
  }

  /** States, each with its cities, that have offers now. */
  @Transactional(readOnly = true)
  public List<Place> places() {
    scope.enterPublic();
    Map<String, List<String>> byState = new LinkedHashMap<>();
    for (String[] place : queries.places(minExpiry())) {
      byState.computeIfAbsent(place[0], s -> new ArrayList<>()).add(place[1]);
    }
    return byState.entrySet().stream().map(e -> new Place(e.getKey(), e.getValue())).toList();
  }

  @Transactional(readOnly = true)
  public List<String> brands() {
    scope.enterPublic();
    return queries.brands(minExpiry(), MAX_BRANDS);
  }

  private Row load(UUID listingId, LocalDate today) {
    return queries
        .find(listingId, today.plusDays(OfferFilters.MIN_SHELF_LIFE_DAYS))
        .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
  }

  private LocalDate minExpiry() {
    return IndiaTime.today(clock).plusDays(OfferFilters.MIN_SHELF_LIFE_DAYS);
  }

  static OfferSummary summary(Row row, LocalDate today) {
    long packsPerUnit = "CASE".equals(row.quantityUnit()) ? row.packsPerCase() : 1;
    long pricePerPack =
        BigDecimal.valueOf(row.unitPricePaise())
            .divide(BigDecimal.valueOf(packsPerUnit), 0, RoundingMode.HALF_UP)
            .longValueExact();
    return new OfferSummary(
        row.id(),
        row.brand(),
        row.productName(),
        row.packSize().stripTrailingZeros(),
        row.packUnit(),
        row.categoryId(),
        row.dateType(),
        row.expiresOn(),
        ChronoUnit.DAYS.between(today, row.expiresOn()),
        row.quantity(),
        row.quantityUnit(),
        row.packsPerCase(),
        row.moq(),
        row.unitPricePaise(),
        pricePerPack,
        Math.multiplyExact(row.moq(), row.unitPricePaise()),
        row.mrpPaise(),
        row.gstRateBp(),
        row.clearanceReason(),
        new Location(row.city(), row.state(), row.pincode()),
        new Seller(
            row.confidential() ? null : row.sellerName(),
            row.confidential(),
            row.verificationStatus()),
        row.coverPhotoId(),
        row.publishedAt());
  }
}
