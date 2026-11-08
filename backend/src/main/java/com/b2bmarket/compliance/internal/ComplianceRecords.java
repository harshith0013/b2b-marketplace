package com.b2bmarket.compliance.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Stored evaluations (append-only). Runs in the caller's transaction and database scope. */
@Repository
public class ComplianceRecords {

  private final JdbcClient jdbc;

  ComplianceRecords(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public record Stored(
      UUID businessId,
      UUID batchId,
      UUID listingId,
      String stage,
      String ruleSetVersion,
      String resultsJson,
      boolean blocked,
      boolean flagged,
      Instant at) {}

  public void insert(Stored check) {
    jdbc.sql(
            """
            insert into compliance_checks (id, business_id, batch_id, listing_id, stage,
                rule_set_version, results, blocked, flagged, created_at)
            values (:id, :business, :batch, :listing, :stage, :version, cast(:results as jsonb),
                :blocked, :flagged, :at)
            """)
        .param("id", UUID.randomUUID())
        .param("business", check.businessId())
        .param("batch", check.batchId())
        .param("listing", check.listingId())
        .param("stage", check.stage())
        .param("version", check.ruleSetVersion())
        .param("results", check.resultsJson())
        .param("blocked", check.blocked())
        .param("flagged", check.flagged())
        .param("at", Timestamp.from(check.at()))
        .update();
  }

  /** A stored evaluation, newest first, for the Batch Passport. */
  public record Row(
      Instant at,
      String stage,
      String ruleSetVersion,
      boolean blocked,
      boolean flagged,
      String resultsJson) {}

  public java.util.List<Row> forBatch(UUID businessId, UUID batchId) {
    return jdbc.sql(
            "select created_at, stage, rule_set_version, blocked, flagged, results::text"
                + " from compliance_checks where business_id = :business and batch_id = :batch"
                + " order by created_at desc limit 50")
        .param("business", businessId)
        .param("batch", batchId)
        .query(
            (rs, i) ->
                new Row(
                    rs.getTimestamp(1).toInstant(),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getBoolean(4),
                    rs.getBoolean(5),
                    rs.getString(6)))
        .list();
  }
}
