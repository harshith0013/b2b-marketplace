package com.b2bmarket.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2bmarket.compliance.ComplianceDtos.Evaluation;
import com.b2bmarket.compliance.ComplianceDtos.Facts;
import com.b2bmarket.compliance.ComplianceDtos.LabelFacts;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PilotRulesTest {

  static final LocalDate TODAY = LocalDate.parse("2026-11-08");
  static final LocalDate EXPIRES = LocalDate.parse("2027-03-31");
  static final Set<String> BOTH = Set.of("PACK_FRONT", "LABEL_DATE");

  /** A listing that passes: ₹30 + 18% GST = ₹35.40 under the ₹45 MRP, 143 days left. */
  static Facts facts(LabelFacts label) {
    return new Facts(
        TODAY,
        EXPIRES,
        500,
        "PIECE",
        null,
        4_500L,
        10,
        1800,
        3_000,
        BOTH,
        false,
        "B23-0456",
        label);
  }

  static LabelFacts read(Integer year, Integer month, Integer day, String batch, Long mrp) {
    return new LabelFacts("READ", year, month, day, batch, mrp);
  }

  @Test
  void aCompleteListingPassesUnderTheCurrentVersion() {
    Evaluation e = PilotRules.evaluate(facts(read(2027, 3, 31, "B23-0456", 4_500L)));
    assertThat(e.ruleSetVersion()).isEqualTo("pilot-2026-11");
    assertThat(e.blocks()).isEmpty();
    assertThat(e.flags()).isEmpty();
  }

  @Test
  void blocksWhatCannotBeListed() {
    Facts bad =
        new Facts(
            TODAY,
            TODAY.plusDays(5),
            5,
            "PIECE",
            null,
            null,
            10,
            1800,
            3_000,
            Set.of(),
            true,
            "B1",
            null);
    assertThat(PilotRules.evaluate(bad).blocks())
        .containsExactly(
            "SHELF_LIFE_TOO_SHORT",
            "PACK_PHOTO_MISSING",
            "LABEL_PHOTO_MISSING",
            "MRP_MISSING",
            "REGULATED_CATEGORY",
            "NOT_ENOUGH_STOCK");
  }

  @Test
  void needsThirtyDaysAndComparesPriceWithGstAgainstMrpPerPack() {
    Facts thirty =
        new Facts(
            TODAY,
            TODAY.plusDays(30),
            500,
            "PIECE",
            null,
            4_500L,
            10,
            1800,
            3_813,
            BOTH,
            false,
            "B",
            null);
    assertThat(PilotRules.evaluate(thirty).blocks()).isEmpty(); // ₹38.13 + 18% = ₹44.99
    Facts pricey =
        new Facts(
            TODAY,
            TODAY.plusDays(29),
            500,
            "PIECE",
            null,
            4_500L,
            10,
            1800,
            3_814,
            BOTH,
            false,
            "B",
            null);
    assertThat(PilotRules.evaluate(pricey).blocks())
        .containsExactly("SHELF_LIFE_TOO_SHORT", "PRICE_ABOVE_MRP");
    Facts cases =
        new Facts(TODAY, EXPIRES, 50, "CASE", 24, 4_500L, 2, 1800, 92_000, BOTH, false, "B", null);
    assertThat(PilotRules.evaluate(cases).blocks())
        .containsExactly("PRICE_ABOVE_MRP"); // ₹45.23 a pack
  }

  @Test
  void aLabelShowingOnlyTheMonthMatchesAnyDayInIt() {
    assertThat(PilotRules.evaluate(facts(read(2027, 3, null, null, null))).flags()).isEmpty();
    assertThat(PilotRules.evaluate(facts(read(2027, 4, null, null, null))).flags())
        .containsExactly("LABEL_DATE_MISMATCH");
  }

  @Test
  void flagsDifferencesForAPersonToLookAt() {
    Evaluation e = PilotRules.evaluate(facts(read(2028, 3, 31, "B23-9999", 5_000L)));
    assertThat(e.blocks()).isEmpty();
    assertThat(e.flags())
        .containsExactly(
            "LABEL_DATE_MISMATCH", "LABEL_BATCH_NUMBER_MISMATCH", "LABEL_MRP_MISMATCH");
  }

  @Test
  void batchNumbersCompareWithoutSpacesOrPunctuation() {
    assertThat(PilotRules.evaluate(facts(read(2027, 3, 31, "b23 0456", 4_500L))).flags()).isEmpty();
  }

  @Test
  void unreadableOrDatelessLabelsAreFlaggedButANotCheckedPhotoIsNot() {
    assertThat(
            PilotRules.evaluate(facts(new LabelFacts("UNREADABLE", null, null, null, null, null)))
                .flags())
        .containsExactly("LABEL_UNREADABLE");
    assertThat(PilotRules.evaluate(facts(read(null, null, null, null, null))).flags())
        .containsExactly("LABEL_DATE_NOT_FOUND");
    assertThat(
            PilotRules.evaluate(facts(new LabelFacts("NOT_CHECKED", null, null, null, null, null)))
                .flags())
        .isEmpty();
    assertThat(PilotRules.evaluate(facts(null)).flags()).isEmpty();
  }
}
