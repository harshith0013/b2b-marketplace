package com.b2bmarket.compliance;

import com.b2bmarket.compliance.ComplianceDtos.Evaluation;
import com.b2bmarket.compliance.ComplianceDtos.Facts;
import com.b2bmarket.compliance.ComplianceDtos.Outcome;
import com.b2bmarket.compliance.ComplianceDtos.Stage;
import com.b2bmarket.compliance.internal.ComplianceRecords;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the current rule set. {@link #check} also stores the evaluation, inside the caller's
 * transaction (and so its database scope: the seller's business, or review).
 */
@Service
public class ComplianceService {

  private final ComplianceRecords records;
  private final JsonMapper json;
  private final Clock clock;

  ComplianceService(ComplianceRecords records, JsonMapper json, Clock clock) {
    this.records = records;
    this.json = json;
    this.clock = clock;
  }

  /** The current rules' verdict, without storing it (for showing a seller what is left to do). */
  public Evaluation preview(Facts facts) {
    return PilotRules.evaluate(facts);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Evaluation check(Stage stage, UUID businessId, UUID batchId, UUID listingId, Facts facts) {
    Evaluation evaluation = PilotRules.evaluate(stage, facts);
    records.insert(
        new ComplianceRecords.Stored(
            businessId,
            batchId,
            listingId,
            stage.name(),
            evaluation.ruleSetVersion(),
            json.writeValueAsString(evaluation.results()),
            evaluation.results().stream().anyMatch(r -> r.outcome() == Outcome.BLOCK),
            evaluation.results().stream().anyMatch(r -> r.outcome() == Outcome.FLAG),
            Instant.now(clock)));
    return evaluation;
  }

  /** A stored evaluation, for the Batch Passport. */
  public record CheckSummary(
      Instant at, String stage, String ruleSetVersion, List<String> blocks, List<String> flags) {}

  /** Stored evaluations of a batch, newest first. Call in the business's or review scope. */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<CheckSummary> history(UUID businessId, UUID batchId) {
    return records.forBatch(businessId, batchId).stream()
        .map(
            row -> {
              List<ComplianceDtos.RuleResult> results =
                  List.of(json.readValue(row.resultsJson(), ComplianceDtos.RuleResult[].class));
              return new CheckSummary(
                  row.at(),
                  row.stage(),
                  row.ruleSetVersion(),
                  results.stream()
                      .filter(r -> r.outcome() == Outcome.BLOCK)
                      .map(ComplianceDtos.RuleResult::code)
                      .toList(),
                  results.stream()
                      .filter(r -> r.outcome() == Outcome.FLAG)
                      .map(ComplianceDtos.RuleResult::code)
                      .toList());
            })
        .toList();
  }
}
