package com.b2bmarket.listings;

import com.b2bmarket.batches.PhotoKind;
import com.b2bmarket.batches.StockUnit;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The baseline checks a listing must pass before review, and again when a reviewer approves it.
 * They only flag problems; they never certify the stock (that is the reviewer's job, and from Phase
 * 8 the compliance engine's).
 */
final class Eligibility {

  static final int MIN_SHELF_LIFE_DAYS = 30;
  private static final long BASIS_POINTS = 10_000L;

  private Eligibility() {}

  /** Everything the checks need, gathered by the caller. */
  record Facts(
      LocalDate today,
      LocalDate expiresOn,
      long stock,
      StockUnit unit,
      Integer packsPerCase,
      Long mrpPaise,
      long moq,
      int gstRateBp,
      long highestUnitPricePaise,
      Set<PhotoKind> photos,
      boolean regulatedCategory) {}

  /** Every failed check, in a stable order; empty when the listing may go ahead. */
  static List<EligibilityProblem> check(Facts facts) {
    List<EligibilityProblem> problems = new ArrayList<>();
    if (ChronoUnit.DAYS.between(facts.today(), facts.expiresOn()) < MIN_SHELF_LIFE_DAYS) {
      problems.add(EligibilityProblem.SHELF_LIFE_TOO_SHORT);
    }
    if (!facts.photos().contains(PhotoKind.PACK_FRONT)) {
      problems.add(EligibilityProblem.PACK_PHOTO_MISSING);
    }
    if (!facts.photos().contains(PhotoKind.LABEL_DATE)) {
      problems.add(EligibilityProblem.LABEL_PHOTO_MISSING);
    }
    if (facts.mrpPaise() == null) {
      problems.add(EligibilityProblem.MRP_MISSING);
    } else if (priceAboveMrp(facts)) {
      problems.add(EligibilityProblem.PRICE_ABOVE_MRP);
    }
    if (facts.regulatedCategory()) {
      problems.add(EligibilityProblem.REGULATED_CATEGORY);
    }
    if (facts.stock() < facts.moq()) {
      problems.add(EligibilityProblem.NOT_ENOUGH_STOCK);
    }
    return List.copyOf(problems);
  }

  /**
   * MRP includes all taxes; listing prices exclude GST. Compares, without rounding, the price of
   * one pack with GST against the MRP: for cases, one pack is the case price over its pack count.
   */
  static boolean priceAboveMrp(Facts facts) {
    long packsPerUnit = facts.unit() == StockUnit.CASE ? facts.packsPerCase() : 1;
    long priceWithGst =
        Math.multiplyExact(facts.highestUnitPricePaise(), BASIS_POINTS + facts.gstRateBp());
    long mrpForSameQuantity =
        Math.multiplyExact(Math.multiplyExact(facts.mrpPaise(), packsPerUnit), BASIS_POINTS);
    return priceWithGst > mrpForSameQuantity;
  }
}
