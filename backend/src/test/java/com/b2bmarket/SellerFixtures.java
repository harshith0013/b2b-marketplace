package com.b2bmarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** Builds a seller's world through the real API: a business, a product, a warehouse, a batch. */
public abstract class SellerFixtures extends IntegrationTestSupport {

  protected static final LocalDate TODAY_IN_INDIA = LocalDate.now(ZoneId.of("Asia/Kolkata"));

  @Autowired protected JdbcTemplate jdbc;

  /** A signed-in person with a fresh, unique identity. */
  protected static String newPerson(String name) {
    String id = name + "-" + UUID.randomUUID();
    return tokenFor(id, id + "@example.com");
  }

  protected String createBusiness(String token, String legalName) {
    return created(
            call(
                "POST",
                "/api/v1/businesses",
                token,
                Map.of(
                    "legalName", legalName,
                    "businessType", "WHOLESALER",
                    "city", "Guntur",
                    "state", "Andhra Pradesh",
                    "pincode", "522001")))
        .get("id")
        .asString();
  }

  protected UUID userIdOf(String token) {
    return UUID.fromString(
        json(call("GET", "/api/v1/me", token, null)).get("profile").get("id").asString());
  }

  /** Adds a membership directly, to set up role tests without the invitation round trip. */
  protected void addMember(String businessId, String token, String role) {
    jdbc.update(
        "insert into business_memberships (id, user_id, business_id, role, created_at)"
            + " values (?, ?, ?::uuid, ?, ?)",
        UUID.randomUUID(),
        userIdOf(token),
        businessId,
        role,
        Timestamp.from(Instant.now()));
  }

  protected String leafCategoryId(String slug) {
    return jdbc.queryForObject(
        "select id::text from categories where slug = ?", String.class, slug);
  }

  /** A product with one 100 g pack; returns the variant id. */
  protected String createProductVariant(String token, String businessId, String name) {
    JsonNode product =
        created(
            call(
                "POST",
                "/api/v1/businesses/" + businessId + "/products",
                token,
                Map.of(
                    "categoryId",
                    leafCategoryId("bath-soaps"),
                    "brand",
                    "Test Brand",
                    "name",
                    name,
                    "variants",
                    List.of(Map.of("packSize", 100, "packUnit", "G")))));
    return product.get("variants").get(0).get("id").asString();
  }

  protected String createWarehouse(String token, String businessId, String name) {
    return created(
            call(
                "POST", "/api/v1/businesses/" + businessId + "/warehouses", token, warehouse(name)))
        .get("id")
        .asString();
  }

  protected static Map<String, Object> warehouse(String name) {
    return Map.of(
        "name", name,
        "addressLine", "12-3 Main Road",
        "city", "Guntur",
        "state", "Andhra Pradesh",
        "pincode", "522002");
  }

  protected static Map<String, Object> batch(String variantId, String warehouseId, long quantity) {
    Map<String, Object> body = new HashMap<>();
    body.put("variantId", variantId);
    body.put("warehouseId", warehouseId);
    body.put("batchNumber", "B-" + UUID.randomUUID().toString().substring(0, 8));
    body.put("manufacturedOn", TODAY_IN_INDIA.minusMonths(10).toString());
    body.put("expiresOn", TODAY_IN_INDIA.plusDays(45).toString());
    body.put("dateType", "EXPIRY");
    body.put("quantity", quantity);
    body.put("quantityUnit", "PIECE");
    body.put("mrpPaise", 4_500);
    return body;
  }

  /** A full seller setup: product, warehouse and a batch of 500 pieces. Returns the batch id. */
  protected String createBatch(String token, String businessId) {
    String variant = createProductVariant(token, businessId, "Soap " + UUID.randomUUID());
    String warehouseId = createWarehouse(token, businessId, "Main " + UUID.randomUUID());
    return created(
            call(
                "POST",
                "/api/v1/businesses/" + businessId + "/batches",
                token,
                batch(variant, warehouseId, 500)))
        .get("id")
        .asString();
  }

  protected static Map<String, Object> listing(String batchId) {
    return Map.of(
        "batchId",
        batchId,
        "clearanceReason",
        "NEAR_EXPIRY",
        "moq",
        10,
        "gstRateBp",
        1800,
        "priceTiers",
        List.of(
            Map.of("minQuantity", 10, "unitPricePaise", 3_000),
            Map.of("minQuantity", 100, "unitPricePaise", 2_700)));
  }

  protected String createListing(String token, String businessId, String batchId) {
    return created(
            call("POST", "/api/v1/businesses/" + businessId + "/listings", token, listing(batchId)))
        .get("id")
        .asString();
  }

  protected static JsonNode created(HttpResponse<String> response) {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response);
  }
}
