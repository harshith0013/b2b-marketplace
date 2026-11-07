package com.b2bmarket.platform;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Turns on PostgreSQL Row-Level Security for the rest of the current transaction: from here on the
 * database returns and accepts only the given business's rows in the seller tables (warehouses,
 * batches, listings, price tiers). See migration V6.
 *
 * <p>This is a second line of defence. Callers must already have checked that the signed-in person
 * belongs to the business; use {@code businesses.TenantAccess}, which does both.
 */
@Component
public class TenantScope {

  /** The database role whose row policies apply; it has no login of its own. */
  static final String TENANT_ROLE = "marketplace_tenant";

  /** Reads every business's seller data, changes only review results. See migration V7. */
  static final String REVIEWER_ROLE = "marketplace_reviewer";

  private final JdbcTemplate jdbc;

  TenantScope(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void enter(UUID businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("businessId is required");
    }
    requireTransaction();
    // is_local = true: both settings end with the transaction, so pooled connections stay clean.
    jdbc.queryForObject(
        "select set_config('app.business_id', ?, true)", String.class, businessId.toString());
    jdbc.execute("set local role " + TENANT_ROLE);
  }

  /**
   * Switches the rest of the transaction to the reviewer role. Callers must already have checked
   * the person's platform role ({@code users.PlatformRoles}).
   */
  public void enterReview() {
    requireTransaction();
    jdbc.execute("set local role " + REVIEWER_ROLE);
  }

  private static void requireTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      // Outside a transaction the settings would apply to one statement and be lost.
      throw new IllegalStateException("TenantScope must be entered inside a transaction");
    }
  }
}
