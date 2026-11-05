package com.b2bmarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Warehouses, batches and listings through the API: the full seller flow, role rules, and proof
 * that another business can neither read nor change any of it.
 */
class SellerDataIT extends SellerFixtures {

  String alice;
  String aliceBusiness;
  String bob;
  String bobBusiness;

  @BeforeEach
  void twoSellers() {
    alice = newPerson("alice");
    aliceBusiness = createBusiness(alice, "Alice Traders");
    bob = newPerson("bob");
    bobBusiness = createBusiness(bob, "Bob Distributors");
  }

  String base(String businessId) {
    return "/api/v1/businesses/" + businessId;
  }

  @Test
  void aSellerAddsStockAndDraftsAListing() {
    String batchId = createBatch(alice, aliceBusiness);

    JsonNode batch = json(call("GET", base(aliceBusiness) + "/batches/" + batchId, alice, null));
    assertThat(batch.get("status").asString()).isEqualTo("DRAFT");
    assertThat(batch.get("quantityUnit").asString()).isEqualTo("PIECE");
    assertThat(batch.get("daysUntilExpiry").asLong()).isEqualTo(45);
    assertThat(batch.get("variant").get("brand").asString()).isEqualTo("Test Brand");

    String listingId = createListing(alice, aliceBusiness, batchId);
    JsonNode listing =
        json(call("GET", base(aliceBusiness) + "/listings/" + listingId, alice, null));
    assertThat(listing.get("status").asString()).isEqualTo("DRAFT");
    assertThat(listing.get("visibility").asString()).isEqualTo("PUBLIC");
    assertThat(listing.get("quantityUnit").asString()).isEqualTo("PIECE");
    assertThat(listing.get("priceTiers").size()).isEqualTo(2);
    assertThat(listing.get("priceTiers").get(0).get("unitPricePaise").asLong()).isEqualTo(3_000);
    assertThat(listing.get("batch").get("id").asString()).isEqualTo(batchId);

    JsonNode page = json(call("GET", base(aliceBusiness) + "/listings", alice, null));
    assertThat(page.get("items").size()).isEqualTo(1);

    assertThat(auditActions(aliceBusiness))
        .contains("warehouse.created", "product.created", "batch.created", "listing.created");
  }

  @Test
  void priceTiersCanBeReplacedWithTheSameQuantities() {
    String listingId = createListing(alice, aliceBusiness, createBatch(alice, aliceBusiness));
    Map<String, Object> change =
        Map.of(
            "priceTiers",
            List.of(
                Map.of("minQuantity", 10, "unitPricePaise", 2_900),
                Map.of("minQuantity", 100, "unitPricePaise", 2_500),
                Map.of("minQuantity", 300, "unitPricePaise", 2_200)));

    HttpResponse<String> updated =
        call("PATCH", base(aliceBusiness) + "/listings/" + listingId, alice, change);

    assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
    assertThat(json(updated).get("priceTiers").size()).isEqualTo(3);
    assertThat(json(updated).get("priceTiers").get(0).get("unitPricePaise").asLong())
        .isEqualTo(2_900);
  }

  @Test
  void listingRulesAreEnforced() {
    String batchId = createBatch(alice, aliceBusiness);
    String path = base(aliceBusiness) + "/listings";

    Map<String, Object> tooBig = new HashMap<>(listing(batchId));
    tooBig.put("moq", 600);
    tooBig.put("priceTiers", List.of(Map.of("minQuantity", 600, "unitPricePaise", 100)));
    assertThat(call("POST", path, alice, tooBig).statusCode()).isEqualTo(400);

    Map<String, Object> pricierInBulk = new HashMap<>(listing(batchId));
    pricierInBulk.put(
        "priceTiers",
        List.of(
            Map.of("minQuantity", 10, "unitPricePaise", 100),
            Map.of("minQuantity", 50, "unitPricePaise", 200)));
    assertThat(call("POST", path, alice, pricierInBulk).statusCode()).isEqualTo(400);

    Map<String, Object> badGst = new HashMap<>(listing(batchId));
    badGst.put("gstRateBp", 4001);
    assertThat(call("POST", path, alice, badGst).statusCode()).isEqualTo(400);

    createListing(alice, aliceBusiness, batchId);
    assertThat(call("POST", path, alice, listing(batchId)).statusCode()).isEqualTo(409);
  }

  @Test
  void expiredOrImpossibleBatchesAreRefused() {
    String variant = createProductVariant(alice, aliceBusiness, "Expiry Soap");
    String warehouse = createWarehouse(alice, aliceBusiness, "Expiry Store");
    String path = base(aliceBusiness) + "/batches";

    Map<String, Object> expired = new HashMap<>(batch(variant, warehouse, 10));
    expired.put("expiresOn", TODAY_IN_INDIA.minusDays(1).toString());
    assertThat(call("POST", path, alice, expired).statusCode()).isEqualTo(400);

    Map<String, Object> caseWithoutSize = new HashMap<>(batch(variant, warehouse, 10));
    caseWithoutSize.put("quantityUnit", "CASE");
    assertThat(call("POST", path, alice, caseWithoutSize).statusCode()).isEqualTo(400);

    Map<String, Object> noUnit = new HashMap<>(batch(variant, warehouse, 10));
    noUnit.remove("quantityUnit");
    assertThat(call("POST", path, alice, noUnit).statusCode()).isEqualTo(400);

    Map<String, Object> ok = batch(variant, warehouse, 10);
    assertThat(call("POST", path, alice, ok).statusCode()).isEqualTo(201);
    assertThat(call("POST", path, alice, ok).statusCode()).isEqualTo(409); // same batch number
  }

  @Test
  void changingTheDatesOfAnApprovedBatchSendsItBackForReview() {
    String batchId = createBatch(alice, aliceBusiness);
    jdbc.update("update inventory_batches set status = 'APPROVED' where id = ?::uuid", batchId);
    String path = base(aliceBusiness) + "/batches/" + batchId;

    JsonNode quantityOnly = json(call("PATCH", path, alice, Map.of("quantity", 400)));
    assertThat(quantityOnly.get("status").asString()).isEqualTo("APPROVED");

    String later = TODAY_IN_INDIA.plusDays(90).toString();
    JsonNode redated = json(call("PATCH", path, alice, Map.of("expiresOn", later)));

    assertThat(redated.get("expiresOn").asString()).isEqualTo(later);
    assertThat(redated.get("status").asString()).isEqualTo("PENDING_REVIEW");
    Map<String, Object> event =
        jdbc.queryForMap(
            "select details::text as details from audit_events"
                + " where action = 'batch.approved_details_changed' and target_id = ?::uuid",
            batchId);
    assertThat(event.get("details").toString()).contains("expiresOnBefore", later);
  }

  @Test
  void staffCanSeeStockButNotChangeIt() {
    String batchId = createBatch(alice, aliceBusiness);
    String staff = newPerson("staff");
    addMember(aliceBusiness, staff, "STAFF");

    assertThat(call("GET", base(aliceBusiness) + "/batches", staff, null).statusCode())
        .isEqualTo(200);
    assertThat(call("GET", base(aliceBusiness) + "/warehouses", staff, null).statusCode())
        .isEqualTo(200);
    assertThat(
            call("PATCH", base(aliceBusiness) + "/batches/" + batchId, staff, Map.of("quantity", 1))
                .statusCode())
        .isEqualTo(403);
    assertThat(
            call("POST", base(aliceBusiness) + "/warehouses", staff, warehouse("Staff Store"))
                .statusCode())
        .isEqualTo(403);
    assertThat(
            call("POST", base(aliceBusiness) + "/listings", staff, listing(batchId)).statusCode())
        .isEqualTo(403);
  }

  @Test
  void anotherBusinessCannotReadOrChangeAnyOfIt() {
    String batchId = createBatch(alice, aliceBusiness);
    String listingId = createListing(alice, aliceBusiness, batchId);
    String warehouseId =
        json(call("GET", base(aliceBusiness) + "/warehouses", alice, null))
            .get(0)
            .get("id")
            .asString();

    // Bob, through Alice's business path: not a member, so everything is 404.
    for (String path :
        List.of(
            base(aliceBusiness) + "/warehouses",
            base(aliceBusiness) + "/warehouses/" + warehouseId,
            base(aliceBusiness) + "/batches",
            base(aliceBusiness) + "/batches/" + batchId,
            base(aliceBusiness) + "/listings",
            base(aliceBusiness) + "/listings/" + listingId)) {
      assertThat(call("GET", path, bob, null).statusCode()).as(path).isEqualTo(404);
    }
    assertThat(
            call("PATCH", base(aliceBusiness) + "/batches/" + batchId, bob, Map.of("quantity", 1))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            call(
                    "PATCH",
                    base(aliceBusiness) + "/warehouses/" + warehouseId,
                    bob,
                    Map.of("active", false))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            call("PATCH", base(aliceBusiness) + "/listings/" + listingId, bob, Map.of("moq", 20))
                .statusCode())
        .isEqualTo(404);

    // Bob, through his own business path with Alice's ids: they do not exist for him.
    assertThat(call("GET", base(bobBusiness) + "/batches/" + batchId, bob, null).statusCode())
        .isEqualTo(404);
    assertThat(call("GET", base(bobBusiness) + "/listings/" + listingId, bob, null).statusCode())
        .isEqualTo(404);
    assertThat(
            call("GET", base(bobBusiness) + "/warehouses/" + warehouseId, bob, null).statusCode())
        .isEqualTo(404);
    assertThat(json(call("GET", base(bobBusiness) + "/batches", bob, null)).get("items").size())
        .isZero();

    // Bob cannot list Alice's batch, or stock his own batch in Alice's warehouse.
    assertThat(call("POST", base(bobBusiness) + "/listings", bob, listing(batchId)).statusCode())
        .isEqualTo(400);
    String bobVariant = createProductVariant(bob, bobBusiness, "Bob Soap");
    assertThat(
            call("POST", base(bobBusiness) + "/batches", bob, batch(bobVariant, warehouseId, 5))
                .statusCode())
        .isEqualTo(400);

    // Nor use Alice's unreviewed product in his own batch.
    String aliceVariant = createProductVariant(alice, aliceBusiness, "Alice Only Soap");
    String bobWarehouse = createWarehouse(bob, bobBusiness, "Bob Store");
    assertThat(
            call("POST", base(bobBusiness) + "/batches", bob, batch(aliceVariant, bobWarehouse, 5))
                .statusCode())
        .isEqualTo(400);

    // Nothing of Alice's changed.
    JsonNode batch = json(call("GET", base(aliceBusiness) + "/batches/" + batchId, alice, null));
    assertThat(batch.get("quantity").asLong()).isEqualTo(500);
  }

  @Test
  void warehousesCanBeRenamedAndDeactivatedButNamesStayUnique() {
    String first = createWarehouse(alice, aliceBusiness, "North Godown");
    createWarehouse(alice, aliceBusiness, "South Godown");
    String path = base(aliceBusiness) + "/warehouses/" + first;

    assertThat(call("PATCH", path, alice, Map.of("name", "south godown")).statusCode())
        .isEqualTo(409);
    assertThat(
            call("POST", base(aliceBusiness) + "/warehouses", alice, warehouse("North Godown"))
                .statusCode())
        .isEqualTo(409);
    JsonNode off = json(call("PATCH", path, alice, Map.of("active", false)));
    assertThat(off.get("active").asBoolean()).isFalse();

    String variant = createProductVariant(alice, aliceBusiness, "Inactive Store Soap");
    assertThat(
            call("POST", base(aliceBusiness) + "/batches", alice, batch(variant, first, 5))
                .statusCode())
        .isEqualTo(400);
  }

  List<String> auditActions(String businessId) {
    return jdbc.queryForList(
        "select action from audit_events where business_id = ?::uuid", String.class, businessId);
  }
}
