package com.b2bmarket.listings;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2bmarket.batches.PhotoKind;
import com.b2bmarket.batches.StockUnit;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EligibilityTest {

  static final LocalDate TODAY = LocalDate.parse("2026-11-05");
  static final Set<PhotoKind> BOTH_PHOTOS = EnumSet.of(PhotoKind.PACK_FRONT, PhotoKind.LABEL_DATE);

  /** A listing that passes: 60 days left, both photos, ₹30 + 18% GST = ₹35.40 under ₹45 MRP. */
  static Eligibility.Facts good() {
    return new Eligibility.Facts(
        TODAY,
        TODAY.plusDays(60),
        500,
        StockUnit.PIECE,
        null,
        4_500L,
        10,
        1800,
        3_000,
        BOTH_PHOTOS,
        false);
  }

  static Eligibility.Facts with(
      LocalDate expiresOn,
      StockUnit unit,
      Integer packsPerCase,
      Long mrp,
      long price,
      Set<PhotoKind> photos) {
    return new Eligibility.Facts(
        TODAY, expiresOn, 500, unit, packsPerCase, mrp, 10, 1800, price, photos, false);
  }

  @Test
  void aCompleteListingHasNoProblems() {
    assertThat(Eligibility.check(good())).isEmpty();
  }

  @Test
  void needsThirtyDaysOfShelfLife() {
    assertThat(
            Eligibility.check(
                with(TODAY.plusDays(30), StockUnit.PIECE, null, 4_500L, 3_000, BOTH_PHOTOS)))
        .isEmpty();
    assertThat(
            Eligibility.check(
                with(TODAY.plusDays(29), StockUnit.PIECE, null, 4_500L, 3_000, BOTH_PHOTOS)))
        .containsExactly(EligibilityProblem.SHELF_LIFE_TOO_SHORT);
  }

  @Test
  void needsThePackAndLabelPhotos() {
    assertThat(
            Eligibility.check(
                with(
                    TODAY.plusDays(60),
                    StockUnit.PIECE,
                    null,
                    4_500L,
                    3_000,
                    Set.of(PhotoKind.OTHER))))
        .containsExactly(
            EligibilityProblem.PACK_PHOTO_MISSING, EligibilityProblem.LABEL_PHOTO_MISSING);
  }

  @Test
  void priceWithGstMustNotExceedTheMrp() {
    // ₹38.13 + 18% = ₹44.99 is fine; ₹38.14 + 18% = ₹45.0052 is above ₹45.
    assertThat(
            Eligibility.check(
                with(TODAY.plusDays(60), StockUnit.PIECE, null, 4_500L, 3_813, BOTH_PHOTOS)))
        .isEmpty();
    assertThat(
            Eligibility.check(
                with(TODAY.plusDays(60), StockUnit.PIECE, null, 4_500L, 3_814, BOTH_PHOTOS)))
        .containsExactly(EligibilityProblem.PRICE_ABOVE_MRP);
  }

  @Test
  void casePricesAreComparedPerPack() {
    // A case of 24 at ₹900 + 18% = ₹1,062, i.e. ₹44.25 a pack: under the ₹45 MRP.
    assertThat(
            Eligibility.check(
                with(TODAY.plusDays(60), StockUnit.CASE, 24, 4_500L, 90_000, BOTH_PHOTOS)))
        .isEmpty();
    // ₹920 a case is ₹45.23 a pack with GST.
    assertThat(
            Eligibility.check(
                with(TODAY.plusDays(60), StockUnit.CASE, 24, 4_500L, 92_000, BOTH_PHOTOS)))
        .containsExactly(EligibilityProblem.PRICE_ABOVE_MRP);
  }

  @Test
  void theMrpMustBeEntered() {
    assertThat(
            Eligibility.check(
                with(TODAY.plusDays(60), StockUnit.PIECE, null, null, 3_000, BOTH_PHOTOS)))
        .containsExactly(EligibilityProblem.MRP_MISSING);
  }

  @Test
  void reportsEveryProblemAtOnce() {
    Eligibility.Facts bad =
        new Eligibility.Facts(
            TODAY,
            TODAY.plusDays(5),
            5,
            StockUnit.PIECE,
            null,
            null,
            10,
            1800,
            3_000,
            Set.of(),
            true);
    assertThat(Eligibility.check(bad))
        .containsExactly(
            EligibilityProblem.SHELF_LIFE_TOO_SHORT,
            EligibilityProblem.PACK_PHOTO_MISSING,
            EligibilityProblem.LABEL_PHOTO_MISSING,
            EligibilityProblem.MRP_MISSING,
            EligibilityProblem.REGULATED_CATEGORY,
            EligibilityProblem.NOT_ENOUGH_STOCK);
  }
}
