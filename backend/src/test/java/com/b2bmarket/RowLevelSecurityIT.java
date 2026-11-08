package com.b2bmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b2bmarket.inventory.internal.WarehouseEntity;
import com.b2bmarket.inventory.internal.WarehouseRepository;
import com.b2bmarket.platform.TenantScope;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The database's own tenant boundary (migration V6), tested beneath the application: even a query
 * with no business filter at all returns only the scoped business's rows.
 */
class RowLevelSecurityIT extends SellerFixtures {

  @Autowired TenantScope tenantScope;
  @Autowired WarehouseRepository warehouses;
  @Autowired PlatformTransactionManager transactionManager;

  TransactionTemplate tx;
  UUID alice;
  UUID bob;

  @BeforeEach
  void twoBusinessesWithStock() {
    tx = new TransactionTemplate(transactionManager);
    String aliceToken = newPerson("alice");
    alice = UUID.fromString(createBusiness(aliceToken, "Alice RLS"));
    createListing(aliceToken, alice.toString(), createBatch(aliceToken, alice.toString()));
    String bobToken = newPerson("bob");
    bob = UUID.fromString(createBusiness(bobToken, "Bob RLS"));
    createListing(bobToken, bob.toString(), createBatch(bobToken, bob.toString()));
  }

  @Test
  void unfilteredQueriesSeeOnlyTheScopedBusiness() {
    for (String table : List.of("warehouses", "inventory_batches", "listings", "price_tiers")) {
      List<UUID> owners =
          tx.execute(
              s -> {
                tenantScope.enter(alice);
                return jdbc.queryForList("select distinct business_id from " + table, UUID.class);
              });
      assertThat(owners).as(table).containsExactly(alice);
    }
  }

  @Test
  void hibernateQueriesInTheSameTransactionAreScopedToo() {
    List<UUID> owners =
        tx.execute(
            s -> {
              tenantScope.enter(bob);
              return warehouses.findAll().stream()
                  .map(WarehouseEntity::businessId)
                  .distinct()
                  .toList();
            });
    assertThat(owners).containsExactly(bob);
  }

  @Test
  void withoutABusinessTheTenantRoleSeesNothing() {
    Integer count =
        tx.execute(
            s -> {
              jdbc.execute("set local role marketplace_tenant");
              return jdbc.queryForObject("select count(*) from inventory_batches", Integer.class);
            });
    assertThat(count).isZero();
  }

  @Test
  void rowsCannotBeWrittenForOrMovedToAnotherBusiness() {
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      tenantScope.enter(alice);
                      jdbc.update(
                          "insert into warehouses (id, business_id, name, address_line, city, state,"
                              + " pincode, created_at, updated_at) values (?, ?, 'Sneaky', 'x', 'x',"
                              + " 'x', '522001', ?, ?)",
                          UUID.randomUUID(),
                          bob,
                          Timestamp.from(Instant.now()),
                          Timestamp.from(Instant.now()));
                    }))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("violates row-level security policy");

    Integer changed =
        tx.execute(
            s -> {
              tenantScope.enter(alice);
              return jdbc.update(
                  "update inventory_batches set quantity = 0 where business_id = ?", bob);
            });
    assertThat(changed).isZero();

    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      tenantScope.enter(alice);
                      jdbc.update("update listings set business_id = ?", bob);
                    }))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void theScopeEndsWithTheTransaction() {
    tx.executeWithoutResult(s -> tenantScope.enter(alice));

    for (int i = 0; i < 5; i++) {
      String role = tx.execute(s -> jdbc.queryForObject("select current_user", String.class));
      String business =
          tx.execute(
              s ->
                  jdbc.queryForObject(
                      "select coalesce(current_setting('app.business_id', true), '')",
                      String.class));
      assertThat(role).isNotEqualTo("marketplace_tenant");
      assertThat(business).isEmpty();
    }
  }

  @Test
  void approvedDatesCannotBeChangedWithoutReReview() {
    UUID batch =
        jdbc.queryForObject(
            "select id from inventory_batches where business_id = ?", UUID.class, alice);
    jdbc.update("update inventory_batches set status = 'APPROVED' where id = ?", batch);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update inventory_batches set expires_on = expires_on + 30 where id = ?",
                    batch))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("re-review");

    jdbc.update(
        "update inventory_batches set expires_on = expires_on + 30, status = 'PENDING_REVIEW'"
            + " where id = ?",
        batch);
    jdbc.update("update inventory_batches set quantity = 1 where id = ?", batch); // not a date
  }

  @Test
  void theReviewerRoleReadsEveryBusinessButCannotCreateSellerData() {
    List<UUID> owners =
        tx.execute(
            s -> {
              tenantScope.enterReview();
              return jdbc.queryForList("select distinct business_id from listings", UUID.class);
            });
    assertThat(owners).contains(alice, bob);

    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      tenantScope.enterReview();
                      jdbc.update(
                          "insert into warehouses (id, business_id, name, address_line, city, state,"
                              + " pincode, created_at, updated_at) values (?, ?, 'Reviewer', 'x', 'x',"
                              + " 'x', '522001', now(), now())",
                          UUID.randomUUID(),
                          alice);
                    }))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied");

    // Only review columns can change: not a price, not a quantity.
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      tenantScope.enterReview();
                      jdbc.update(
                          "update inventory_batches set quantity = 1 where business_id = ?", alice);
                    }))
        .rootCause()
        .hasMessageContaining("permission denied");
  }

  @Test
  void aSellerTransactionCannotPublishItsOwnListing() {
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      tenantScope.enter(alice);
                      jdbc.update(
                          "update listings set status = 'PUBLISHED' where business_id = ?", alice);
                    }))
        .rootCause()
        .hasMessageContaining("cannot publish");
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      tenantScope.enter(alice);
                      jdbc.update(
                          "update inventory_batches set status = 'APPROVED' where business_id = ?",
                          alice);
                    }))
        .rootCause()
        .hasMessageContaining("review result");
  }

  @Test
  void photosAreScopedToo() {
    String token = newPerson("photos");
    UUID business = UUID.fromString(createBusiness(token, "Photo RLS"));
    String batchId = createBatch(token, business.toString());
    uploadPhoto(token, business.toString(), batchId, "PACK_FRONT");

    Integer seenByAlice =
        tx.execute(
            s -> {
              tenantScope.enter(alice);
              return jdbc.queryForObject(
                  "select count(*) from batch_photos where business_id = ?",
                  Integer.class,
                  business);
            });
    assertThat(seenByAlice).isZero();
  }

  @Test
  void thePublicRoleSeesOnlyWhatIsOnSaleAndNoPrivateColumns() {
    // Alice's and Bob's listings from setUp are drafts: the public sees none of them.
    Integer visibleDrafts =
        tx.execute(
            s -> {
              tenantScope.enterPublic();
              return jdbc.queryForObject(
                  "select count(*) from listings where business_id in (?, ?)",
                  Integer.class,
                  alice,
                  bob);
            });
    assertThat(visibleDrafts).isZero();

    for (String query :
        List.of(
            "select address_line from warehouses limit 1",
            "select email from users limit 1",
            "select count(*) from business_memberships",
            "select count(*) from audit_events",
            "select gstin from businesses limit 1")) {
      assertThatThrownBy(
              () ->
                  tx.executeWithoutResult(
                      s -> {
                        tenantScope.enterPublic();
                        jdbc.queryForList(query);
                      }))
          .as(query)
          .rootCause()
          .hasMessageContaining("permission denied");
    }

    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      tenantScope.enterPublic();
                      jdbc.update("update listings set moq = 1");
                    }))
        .rootCause()
        .hasMessageContaining("permission denied");
  }

  @Test
  void publicQueriesHaveAShortTimeLimit() {
    String limit =
        tx.execute(
            s -> {
              tenantScope.enterPublic();
              return jdbc.queryForObject("show statement_timeout", String.class);
            });
    assertThat(limit).isEqualTo("2s");
  }

  @Test
  void labelReadingsAndChecksAreScopedAndTheAuditLogToo() {
    String token = newPerson("labels");
    UUID business = UUID.fromString(createBusiness(token, "Label RLS"));
    String batchId = createBatch(token, business.toString());
    jdbc.update(
        "insert into label_readings (id, business_id, batch_id, photo_id, created_at, outcome)"
            + " values (?, ?, ?::uuid, ?, now(), 'UNREADABLE')",
        UUID.randomUUID(),
        business,
        batchId,
        UUID.randomUUID());

    for (String table : List.of("label_readings", "audit_events")) {
      Integer seenByAlice =
          tx.execute(
              s -> {
                tenantScope.enter(alice);
                return jdbc.queryForObject(
                    "select count(*) from " + table + " where business_id = ?",
                    Integer.class,
                    business);
              });
      assertThat(seenByAlice).as(table).isZero();
    }
    Integer ownAudit =
        tx.execute(
            s -> {
              tenantScope.enter(business);
              return jdbc.queryForObject(
                  "select count(*) from audit_events where business_id = ?",
                  Integer.class,
                  business);
            });
    assertThat(ownAudit).isPositive();

    // The public sees no readings of batches that are not on sale.
    Integer publicReadings =
        tx.execute(
            s -> {
              tenantScope.enterPublic();
              return jdbc.queryForObject(
                  "select count(*) from label_readings where batch_id = ?::uuid",
                  Integer.class,
                  batchId);
            });
    assertThat(publicReadings).isZero();

    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      tenantScope.enter(business);
                      jdbc.update("delete from compliance_checks");
                      jdbc.update(
                          "insert into compliance_checks (id, business_id, batch_id, stage,"
                              + " rule_set_version, results, blocked, flagged, created_at)"
                              + " values (?, ?, ?::uuid, 'SUBMIT', 'v', '[]', false, false, now())",
                          UUID.randomUUID(),
                          business,
                          batchId);
                      jdbc.update("update compliance_checks set blocked = true");
                    }))
        .as("compliance checks are append-only")
        .isInstanceOf(DataAccessException.class);
  }
}
