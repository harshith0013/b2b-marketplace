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

  private final JdbcTemplate jdbc;

  TenantScope(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void enter(UUID businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("businessId is required");
    }
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      // Outside a transaction the settings would apply to one statement and be lost.
      throw new IllegalStateException("TenantScope.enter must run inside a transaction");
    }
    // is_local = true: both settings end with the transaction, so pooled connections stay clean.
    jdbc.queryForObject(
        "select set_config('app.business_id', ?, true)", String.class, businessId.toString());
    jdbc.execute("set local role " + TENANT_ROLE);
  }
}
