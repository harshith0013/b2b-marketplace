package com.b2bmarket.auditing.internal;

import com.b2bmarket.auditing.AuditEntry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Insert-only access to {@code audit_events}; there are deliberately no update or delete methods.
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
}
