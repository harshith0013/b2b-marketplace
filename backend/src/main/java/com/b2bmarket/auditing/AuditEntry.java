package com.b2bmarket.auditing;

import java.util.Map;
import java.util.UUID;

/**
 * One security-relevant action to record.
 *
 * @param action what happened, as {@code <noun>.<past-tense verb>}, e.g. {@code business.created}
 * @param details small, non-sensitive facts about the change (field names, not secrets or tokens)
 */
public record AuditEntry(
    String action,
    UUID actorUserId,
    UUID businessId,
    String targetType,
    UUID targetId,
    Map<String, Object> details) {

  public AuditEntry {
    details = details == null ? Map.of() : Map.copyOf(details);
  }
}
