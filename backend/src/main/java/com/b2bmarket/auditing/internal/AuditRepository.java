package com.b2bmarket.auditing.internal;

import com.b2bmarket.auditing.AuditEntry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Access to {@code audit_events}: insert, and read for histories. There are deliberately no update
 * or delete methods (the database refuses them anyway).
 */
@Repository
public class AuditRepository {

  private final JdbcClient jdbc;
  private final JsonMapper json;

  AuditRepository(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public void insert(AuditEntry entry, Instant occurredAt, String requestId) {
    jdbc.sql(
            """
            INSERT INTO audit_events
              (id, occurred_at, actor_user_id, business_id, action, target_type, target_id,
               request_id, details)
            VALUES
              (:id, :occurredAt, :actor, :business, :action, :targetType, :targetId,
               :requestId, CAST(:details AS jsonb))
            """)
        .param("id", UUID.randomUUID())
        .param("occurredAt", Timestamp.from(occurredAt))
        .param("actor", entry.actorUserId())
        .param("business", entry.businessId())
        .param("action", entry.action())
        .param("targetType", entry.targetType())
        .param("targetId", entry.targetId())
        .param("requestId", requestId)
        .param("details", json.writeValueAsString(entry.details()))
        .update();
  }

  /** One recorded event, as read back. */
  public record Event(Instant at, String action, UUID targetId, String detailsJson) {}

  /**
   * A business's events about the given targets, or whose details name one of them under the given
   * key (for example photos naming their "batchId"), oldest first.
   */
  public List<Event> about(
      UUID businessId, Collection<UUID> targetIds, String detailsKey, int limit) {
    if (targetIds.isEmpty()) {
      return List.of();
    }
    List<String> ids = targetIds.stream().map(UUID::toString).toList();
    return jdbc.sql(
            """
            SELECT occurred_at, action, target_id, details::text FROM audit_events
            WHERE business_id = :business
              AND (target_id IN (:ids) OR details ->> :key IN (:idTexts))
            ORDER BY occurred_at, id LIMIT :limit
            """)
        .param("business", businessId)
        .param("ids", List.copyOf(targetIds))
        .param("key", detailsKey)
        .param("idTexts", ids)
        .param("limit", limit)
        .query(
            (rs, i) ->
                new Event(
                    rs.getTimestamp(1).toInstant(),
                    rs.getString(2),
                    rs.getObject(3, UUID.class),
                    rs.getString(4)))
        .list();
  }
}
