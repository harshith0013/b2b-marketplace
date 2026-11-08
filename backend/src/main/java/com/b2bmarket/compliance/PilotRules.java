package com.b2bmarket.compliance;

import com.b2bmarket.compliance.ComplianceDtos.Evaluation;
import com.b2bmarket.compliance.ComplianceDtos.Facts;
import com.b2bmarket.compliance.ComplianceDtos.LabelFacts;
import com.b2bmarket.compliance.ComplianceDtos.Outcome;
import com.b2bmarket.compliance.ComplianceDtos.RuleResult;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * The rules for the pilot (non-food FMCG), version {@value #VERSION}. Each rule has an id and a
 * plain description; change a rule only together with a new version, so every stored evaluation
 * still says which rules it used. To be reviewed by counsel before the pilot.
 *
 * <ul>
 *   <li>SHELF_LIFE: at least 30 days before the printed date (at dispatch of an accepted order: not
 *       past the printed date). Block.
 *   <li>PACK_PHOTO, LABEL_PHOTO: a photo of the pack front and of the label with the date. Block.
 *   <li>MRP_GIVEN: the MRP printed on one pack is entered. Block.
 *   <li>PRICE_WITHIN_MRP: one pack, with GST, costs no more than its MRP. Block.
 *   <li>UNREGULATED_CATEGORY: categories needing licences (food, medicines) are not open yet.
 *       Block.
 *   <li>STOCK_FOR_MOQ: the batch holds at least the minimum order. Block.
 *   <li>LABEL_DATE: the date read from the label photo matches the typed date. Flag if not, or if
 *       the label could not be read; a reviewer then compares them and confirms.
 *   <li>LABEL_BATCH_NUMBER, LABEL_MRP: the batch number and MRP read from the label match. Flag.
 * </ul>
 */
public final class PilotRules {

  /** .2: at dispatch, an accepted order may go out until the printed date (not 30 days before). */
  public static final String VERSION = "pilot-2026-11.2";

  static final int MIN_SHELF_LIFE_DAYS = 30;
  private static final long BASIS_POINTS = 10_000L;

  private PilotRules() {}

  public static Evaluation evaluate(Facts f) {
    return evaluate(ComplianceDtos.Stage.SUBMIT, f);
  }

  public static Evaluation evaluate(ComplianceDtos.Stage stage, Facts f) {
    return new Evaluation(
        VERSION,
        List.of(
            stage == ComplianceDtos.Stage.DISPATCH ? notExpired(f) : shelfLife(f),
            photo("PACK_PHOTO", f, "PACK_FRONT", "PACK_PHOTO_MISSING"),
            photo("LABEL_PHOTO", f, "LABEL_DATE", "LABEL_PHOTO_MISSING"),
            f.mrpPaise() == null ? block("MRP_GIVEN", "MRP_MISSING") : pass("MRP_GIVEN"),
            priceWithinMrp(f),
            f.regulatedCategory()
                ? block("UNREGULATED_CATEGORY", "REGULATED_CATEGORY")
                : pass("UNREGULATED_CATEGORY"),
            f.stock() < f.moq()
                ? block("STOCK_FOR_MOQ", "NOT_ENOUGH_STOCK")
                : pass("STOCK_FOR_MOQ"),
            labelDate(f),
            labelBatchNumber(f),
            labelMrp(f)));
  }

  static RuleResult shelfLife(Facts f) {
    return ChronoUnit.DAYS.between(f.today(), f.expiresOn()) < MIN_SHELF_LIFE_DAYS
        ? block("SHELF_LIFE", "SHELF_LIFE_TOO_SHORT")
        : pass("SHELF_LIFE");
  }

  static RuleResult notExpired(Facts f) {
    return f.expiresOn().isBefore(f.today()) ? block("SHELF_LIFE", "EXPIRED") : pass("SHELF_LIFE");
  }

  static RuleResult photo(String rule, Facts f, String kind, String missing) {
    return f.photoKinds().contains(kind) ? pass(rule) : block(rule, missing);
  }

  /** MRP includes all taxes; prices exclude GST. Compares one pack, with GST, exactly. */
  static RuleResult priceWithinMrp(Facts f) {
    if (f.mrpPaise() == null) {
      return new RuleResult("PRICE_WITHIN_MRP", Outcome.SKIPPED, "MRP_MISSING");
    }
    long packsPerUnit = "CASE".equals(f.unit()) ? f.packsPerCase() : 1;
    long priceWithGst = Math.multiplyExact(f.highestUnitPricePaise(), BASIS_POINTS + f.gstRateBp());
    long mrpForUnit =
        Math.multiplyExact(Math.multiplyExact(f.mrpPaise(), packsPerUnit), BASIS_POINTS);
    return priceWithGst > mrpForUnit
        ? block("PRICE_WITHIN_MRP", "PRICE_ABOVE_MRP")
        : pass("PRICE_WITHIN_MRP");
  }

  static RuleResult labelDate(Facts f) {
    LabelFacts label = f.label();
    if (label == null || "NOT_CHECKED".equals(label.outcome())) {
      return new RuleResult("LABEL_DATE", Outcome.SKIPPED, "LABEL_NOT_CHECKED");
    }
    if ("UNREADABLE".equals(label.outcome())) {
      return flag("LABEL_DATE", "LABEL_UNREADABLE");
    }
    if (label.expiryYear() == null || label.expiryMonth() == null) {
      return flag("LABEL_DATE", "LABEL_DATE_NOT_FOUND");
    }
    YearMonth printed = YearMonth.of(label.expiryYear(), label.expiryMonth());
    boolean sameMonth = printed.equals(YearMonth.from(f.expiresOn()));
    boolean sameDay =
        label.expiryDay() == null || label.expiryDay() == f.expiresOn().getDayOfMonth();
    return sameMonth && sameDay ? pass("LABEL_DATE") : flag("LABEL_DATE", "LABEL_DATE_MISMATCH");
  }

  static RuleResult labelBatchNumber(Facts f) {
    LabelFacts label = f.label();
    if (label == null || !"READ".equals(label.outcome()) || label.batchNumber() == null) {
      return new RuleResult("LABEL_BATCH_NUMBER", Outcome.SKIPPED, "LABEL_NOT_CHECKED");
    }
    return normalize(label.batchNumber()).equals(normalize(f.batchNumber()))
        ? pass("LABEL_BATCH_NUMBER")
        : flag("LABEL_BATCH_NUMBER", "LABEL_BATCH_NUMBER_MISMATCH");
  }

  static RuleResult labelMrp(Facts f) {
    LabelFacts label = f.label();
    if (label == null
        || !"READ".equals(label.outcome())
        || label.mrpPaise() == null
        || f.mrpPaise() == null) {
      return new RuleResult("LABEL_MRP", Outcome.SKIPPED, "LABEL_NOT_CHECKED");
    }
    return label.mrpPaise().equals(f.mrpPaise())
        ? pass("LABEL_MRP")
        : flag("LABEL_MRP", "LABEL_MRP_MISMATCH");
  }

  /** "B23-0456", "b23 0456" and "B23/0456" are the same batch number. */
  static String normalize(String batchNumber) {
    return batchNumber == null
        ? ""
        : batchNumber.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
  }

  private static RuleResult pass(String rule) {
    return new RuleResult(rule, Outcome.PASS, null);
  }

  private static RuleResult flag(String rule, String code) {
    return new RuleResult(rule, Outcome.FLAG, code);
  }

  private static RuleResult block(String rule, String code) {
    return new RuleResult(rule, Outcome.BLOCK, code);
  }
}
