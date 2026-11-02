package com.b2bmarket.auditing;

import com.b2bmarket.auditing.internal.AuditRepository;
import com.b2bmarket.platform.RequestIds;
import java.time.Clock;
import java.time.Instant;
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
}
