package com.b2bmarket.auditing;

import com.b2bmarket.auditing.internal.AuditRepository;
import com.b2bmarket.platform.RequestIds;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records audit events. Rows can never be changed or deleted (enforced by the database). */
@Service
public class AuditService {

  private final AuditRepository repository;
  private final Clock clock;

  AuditService(AuditRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * Must run inside the caller's transaction, so the action and its audit event are saved together
   * or not at all.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(AuditEntry entry) {
    repository.insert(entry, Instant.now(clock), RequestIds.current().orElse(null));
  }

  /** A recorded event: when, what, about which target, and its (non-sensitive) details. */
  public record Recorded(Instant at, String action, UUID targetId, String detailsJson) {}

  /**
   * Events of a business about these targets (or naming one of them in details under detailsKey),
   * oldest first. Call inside a transaction scoped to the business or to review: the database shows
   * sellers only their own business's events.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<Recorded> about(
      UUID businessId, Collection<UUID> targetIds, String detailsKey, int limit) {
    return repository.about(businessId, targetIds, detailsKey, limit).stream()
        .map(e -> new Recorded(e.at(), e.action(), e.targetId(), e.detailsJson()))
        .toList();
  }
}
