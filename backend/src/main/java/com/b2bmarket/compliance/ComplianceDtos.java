package com.b2bmarket.compliance;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Inputs and results of the compliance rules. */
public final class ComplianceDtos {

  private ComplianceDtos() {}

  /** Where in the stock's life the rules run. */
  public enum Stage {
    SUBMIT,
    APPROVE,
    DAILY,
    CHECKOUT,
    DISPATCH
  }

  /**
   * PASS: fine. FLAG: a person must look (the reviewer confirms). BLOCK: cannot go ahead. SKIPPED:
   * the rule had nothing to check (for example, no label reading).
   */
  public enum Outcome {
    PASS,
    FLAG,
    BLOCK,
    SKIPPED
  }

  public record RuleResult(String rule, Outcome outcome, String code) {}

  /** What was read from the label photo, if anything (see the ai module's LabelReading). */
  public record LabelFacts(
      String outcome,
      Integer expiryYear,
      Integer expiryMonth,
      Integer expiryDay,
      String batchNumber,
      Long mrpPaise) {}

  /** Everything the rules need about one listing and its batch, gathered by the caller. */
  public record Facts(
      LocalDate today,
      LocalDate expiresOn,
      long stock,
      String unit,
      Integer packsPerCase,
      Long mrpPaise,
      long moq,
      int gstRateBp,
      long highestUnitPricePaise,
      Set<String> photoKinds,
      boolean regulatedCategory,
      String batchNumber,
      LabelFacts label) {

    public Facts {
      photoKinds = Set.copyOf(photoKinds);
    }
  }

  public record Evaluation(String ruleSetVersion, List<RuleResult> results) {

    public Evaluation {
      results = List.copyOf(results);
    }

    public List<String> blocks() {
      return codes(Outcome.BLOCK);
    }

    public List<String> flags() {
      return codes(Outcome.FLAG);
    }

    private List<String> codes(Outcome outcome) {
      return results.stream().filter(r -> r.outcome() == outcome).map(RuleResult::code).toList();
    }
  }
}
