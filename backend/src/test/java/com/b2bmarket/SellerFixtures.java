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

  protected String uploadPhoto(String token, String businessId, String batchId, String kind) {
    return created(
            upload(
                "/api/v1/businesses/" + businessId + "/batches/" + batchId + "/photos",
                token,
                Map.of("kind", kind),
                com.b2bmarket.batches.TestImages.encode(tinted(kind.hashCode()), "jpeg"),
                "image/jpeg"))
        .get("id")
        .asString();
  }

  private static final java.util.concurrent.atomic.AtomicInteger PHOTO_COUNTER =
      new java.util.concurrent.atomic.AtomicInteger();

  /**
   * A distinct image per call, so the same-photo-twice check does not trip: a large block whose
   * colour comes from a counter survives JPEG compression, unlike a changed pixel or two.
   */
  private static java.awt.image.BufferedImage tinted(int seed) {
    java.awt.image.BufferedImage image = com.b2bmarket.batches.TestImages.halves();
    int n = PHOTO_COUNTER.incrementAndGet();
    java.awt.Graphics2D g = image.createGraphics();
    g.setColor(new java.awt.Color((n * 37 + seed) & 0xFF, (n * 91) & 0xFF, (n * 53) & 0xFF));
    g.fillRect(0, 0, 120, 120);
    g.setColor(java.awt.Color.WHITE);
    g.fillRect((n % 20) * 10, 150, 60, 60);
    g.dispose();
    return image;
  }

  /** Makes this person a platform admin directly (the documented SQL, as in admin-setup.md). */
  protected void makeAdmin(String token) {
    jdbc.update(
        "insert into platform_roles (user_id, role, granted_at) values (?, 'ADMIN', now())",
        userIdOf(token));
  }

  /** A batch with both required photos and a draft listing that passes every check. */
  protected String readyListing(String token, String businessId) {
    String batchId = createBatch(token, businessId);
    uploadPhoto(token, businessId, batchId, "PACK_FRONT");
    uploadPhoto(token, businessId, batchId, "LABEL_DATE");
    return createListing(token, businessId, batchId);
  }

  private String publisher;

  /** Submits a ready listing and approves it as a platform admin, so it is on sale. */
  protected void publish(String sellerToken, String businessId, String listingId) {
    HttpResponse<String> submitted =
        call(
            "POST",
            "/api/v1/businesses/" + businessId + "/listings/" + listingId + "/submit",
            sellerToken,
            null);
    assertThat(submitted.statusCode()).as(submitted.body()).isEqualTo(200);
    if (publisher == null) {
      publisher = newPerson("publisher");
      makeAdmin(publisher);
    }
    HttpResponse<String> approved =
        call("POST", "/api/v1/admin/reviews/" + listingId + "/approve", publisher, null);
    assertThat(approved.statusCode()).as(approved.body()).isEqualTo(200);
  }

  protected String batchOfListing(String listingId) {
    return jdbc.queryForObject(
        "select batch_id::text from listings where id = ?::uuid", String.class, listingId);
  }

  protected static JsonNode created(HttpResponse<String> response) {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response);
  }
}
