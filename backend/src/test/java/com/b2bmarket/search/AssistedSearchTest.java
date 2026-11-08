package com.b2bmarket.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2bmarket.ai.AssistantDtos.ProposedFilters;
import com.b2bmarket.catalog.CategoryDtos.Category;
import com.b2bmarket.catalog.CategoryDtos.Names;
import com.b2bmarket.search.AssistedSearch.Understood;
import com.b2bmarket.search.OfferDtos.Place;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssistedSearchTest {

  static final UUID LAUNDRY = UUID.randomUUID();
  static final List<Category> CATEGORIES =
      List.of(new Category(LAUNDRY, null, "laundry", new Names("Laundry", "", ""), false, true));
  static final List<Place> PLACES =
      List.of(
          new Place("Andhra Pradesh", List.of("Guntur", "Vijayawada")),
          new Place("Telangana", List.of("Hyderabad")));

  static ProposedFilters proposed(
      String category,
      String city,
      String state,
      Integer quantity,
      String unit,
      String price,
      String sort) {
    return new ProposedFilters(
        " detergent ",
        category,
        city,
        state,
        quantity,
        unit,
        price == null ? null : new BigDecimal(price),
        null,
        null,
        sort);
  }

  @Test
  void keepsKnownValuesAndFillsTheStateFromTheCity() {
    Understood understood =
        AssistedSearch.check(
            proposed("laundry", "vijayawada", null, 20, "CASE", "30.50", "PRICE"),
            CATEGORIES,
            PLACES);

    assertThat(understood.query()).isEqualTo("detergent");
    assertThat(understood.categoryId()).isEqualTo(LAUNDRY);
    assertThat(understood.city()).isEqualTo("Vijayawada"); // spelled as stored
    assertThat(understood.state()).isEqualTo("Andhra Pradesh");
    assertThat(understood.quantity()).isEqualTo(20);
    assertThat(understood.unit()).isEqualTo("CASE");
    assertThat(understood.maxPricePerPackPaise()).isEqualTo(3_050);
    assertThat(understood.sort()).isEqualTo(OfferSort.PRICE);
    assertThat(understood.placeNotFound()).isNull();
  }

  @Test
  void dropsWhatDoesNotExistAndSaysWhichPlaceHasNoOffers() {
    Understood understood =
        AssistedSearch.check(
            proposed("made-up", "Chennai", "Tamil Nadu", 20, null, "-5", "CHEAPEST"),
            CATEGORIES,
            PLACES);

    assertThat(understood.categoryId()).isNull();
    assertThat(understood.city()).isNull();
    assertThat(understood.state()).isNull();
    assertThat(understood.placeNotFound()).isEqualTo("Chennai");
    assertThat(understood.quantity()).as("no unit, no quantity").isNull();
    assertThat(understood.maxPricePerPackPaise()).isNull();
    assertThat(understood.sort()).isNull();
  }

  @Test
  void acceptsAStateWithoutACity() {
    Understood understood =
        AssistedSearch.check(
            proposed(null, null, "telangana", null, null, null, null), CATEGORIES, PLACES);
    assertThat(understood.state()).isEqualTo("Telangana");
    assertThat(understood.city()).isNull();
  }

  @Test
  void refusesOutOfRangeNumbers() {
    ProposedFilters huge =
        new ProposedFilters(
            "x".repeat(500),
            null,
            null,
            null,
            0,
            "CASE",
            new BigDecimal("99999999"),
            new BigDecimal("0"),
            99999,
            "EXPIRY");
    Understood understood = AssistedSearch.check(huge, CATEGORIES, PLACES);

    assertThat(understood.query()).hasSize(AssistedSearch.MAX_KEYWORDS);
    assertThat(understood.quantity()).isNull();
    assertThat(understood.maxPricePerPackPaise()).isNull();
    assertThat(understood.maxOrderValuePaise()).isNull();
    assertThat(understood.minDaysLeft()).isNull();
    assertThat(understood.sort()).isEqualTo(OfferSort.EXPIRY);
  }
}
