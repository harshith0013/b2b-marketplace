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

  /** Sees only offers that are on sale, read-only. See migration V8. */
  static final String PUBLIC_ROLE = "marketplace_public";

  /** Anonymous searches must stay cheap: the database cancels anything slower. */
  static final String PUBLIC_STATEMENT_TIMEOUT = "2s";

  /** Word similarity for typo-tolerant search; pg_trgm's default of 0.6 misses common typos. */
  static final String PUBLIC_WORD_SIMILARITY = "0.4";

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

  /**
   * Switches the rest of the transaction to the read-only public role, with a short statement time
   * limit. For anonymous offer search.
   */
  public void enterPublic() {
    requireTransaction();
    jdbc.execute("set local role " + PUBLIC_ROLE);
    jdbc.execute("set local statement_timeout = '" + PUBLIC_STATEMENT_TIMEOUT + "'");
    jdbc.execute("set local pg_trgm.word_similarity_threshold = " + PUBLIC_WORD_SIMILARITY);
  }

  /** Places orders and quotes for this buyer business (see migration V11). */
  static final String ORDERING_ROLE = "marketplace_ordering";

  /**
   * Switches to the ordering role for a buyer: reads only stock on sale, locks its batch, writes
   * orders, reservations and quotes for this buyer. Callers check the buyer's membership first.
   */
  public void enterOrdering(UUID buyerBusinessId) {
    if (buyerBusinessId == null) {
      throw new IllegalArgumentException("buyerBusinessId is required");
    }
    requireTransaction();
    jdbc.queryForObject(
        "select set_config('app.business_id', ?, true)", String.class, buyerBusinessId.toString());
    jdbc.execute("set local role " + ORDERING_ROLE);
  }

  /** The ordering role with no business: only for the order-expiry and clean-up jobs. */
  public void enterOrderingSweep() {
    requireTransaction();
    jdbc.execute("set local role " + ORDERING_ROLE);
  }

  private static void requireTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      // Outside a transaction the settings would apply to one statement and be lost.
      throw new IllegalStateException("TenantScope must be entered inside a transaction");
    }
  }
}
