package com.b2bmarket.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b2bmarket.platform.ApiException;
import com.b2bmarket.search.internal.OfferQueries.Row;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfferFiltersTest {

  static OfferFilters filters(String q, OfferSort sort, Integer minDays, Integer size) {
    return OfferFilters.of(q, null, " ", null, null, null, null, minDays, sort, null, size);
  }

  @Test
  void defaultsToNewestFirstWithoutTextAndBestMatchWithIt() {
    assertThat(filters(null, null, null, null).sort()).isEqualTo(OfferSort.NEWEST);
    assertThat(filters(" soap ", null, null, null).sort()).isEqualTo(OfferSort.RELEVANCE);
    assertThat(filters(" soap ", null, null, null).query()).isEqualTo("soap");
    assertThat(filters(null, OfferSort.RELEVANCE, null, null).sort()).isEqualTo(OfferSort.NEWEST);
    assertThat(filters(null, null, null, null).state()).isNull(); // blank means no filter
  }

  @Test
  void neverShowsOffersWithFewerThanThirtyDaysLeft() {
    assertThat(filters(null, null, null, null).minDaysLeft()).isEqualTo(30);
    assertThat(filters(null, null, 5, null).minDaysLeft()).isEqualTo(30);
    assertThat(filters(null, null, 90, null).minDaysLeft()).isEqualTo(90);
  }

  @Test
  void refusesOutOfRangeRequests() {
    assertThatThrownBy(() -> filters("x".repeat(101), null, null, null))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> filters(null, null, null, 25)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> filters(null, null, -1, null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () -> OfferFilters.of(null, null, null, null, null, 0L, null, null, null, null, null))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () -> OfferFilters.of(null, null, null, null, null, null, null, null, null, 401, null))
        .isInstanceOf(ApiException.class);
  }

  static Row row(String unit, Integer packsPerCase, long unitPrice, boolean confidential) {
    return new Row(
        UUID.randomUUID(),
        "Brand",
        "Soap",
        new BigDecimal("100.000"),
        "G",
        UUID.randomUUID(),
        "EXPIRY",
        LocalDate.parse("2027-01-31"),
        500,
        unit,
        packsPerCase,
        4_500L,
        "B1",
        10,
        1800,
        "NEAR_EXPIRY",
        confidential,
        null,
        Instant.parse("2026-11-01T00:00:00Z"),
        "Guntur",
        "Andhra Pradesh",
        "522002",
        "Asha Traders",
        "UNVERIFIED",
        unitPrice,
        null);
  }

  @Test
  void pricesCasesPerPackSoTheyCompareWithPieces() {
    LocalDate today = LocalDate.parse("2026-11-01");
    var piece = OfferSearchService.summary(row("PIECE", null, 3_000, false), today);
    var kase = OfferSearchService.summary(row("CASE", 24, 70_000, false), today);

    assertThat(piece.pricePerPackPaise()).isEqualTo(3_000);
    assertThat(kase.pricePerPackPaise()).isEqualTo(2_917); // ₹700 / 24 = ₹29.1667, rounded
    assertThat(kase.minimumOrderValuePaise()).isEqualTo(700_000); // 10 cases
    assertThat(piece.daysLeft()).isEqualTo(91);
    assertThat(piece.packSize()).isEqualByComparingTo("100");
  }

  @Test
  void confidentialOffersHideTheSellerName() {
    var hidden =
        OfferSearchService.summary(row("PIECE", null, 3_000, true), LocalDate.parse("2026-11-01"));
    assertThat(hidden.seller().name()).isNull();
    assertThat(hidden.seller().confidential()).isTrue();
    assertThat(hidden.seller().verificationStatus()).isEqualTo("UNVERIFIED");
  }
}
