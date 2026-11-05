package com.b2bmarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Categories and products: who can see and add what. */
class CatalogueIT extends SellerFixtures {

  @Test
  void categoriesNeedSignInAndComeInThreeLanguages() {
    assertThat(call("GET", "/api/v1/categories", null, null).statusCode()).isEqualTo(401);

    JsonNode categories = json(call("GET", "/api/v1/categories", newPerson("reader"), null));

    JsonNode soaps =
        StreamSupport.stream(categories.spliterator(), false)
            .filter(c -> c.get("slug").asString().equals("bath-soaps"))
            .findFirst()
            .orElseThrow();
    assertThat(soaps.get("leaf").asBoolean()).isTrue();
    assertThat(soaps.get("regulated").asBoolean()).isFalse();
    assertThat(soaps.get("name").get("te").asString()).isNotBlank();
    assertThat(soaps.get("name").get("hi").asString()).isNotBlank();
    assertThat(categories.get(0).get("parentId").isNull()).isTrue(); // parents first
  }

  @Test
  void newProductsAreVisibleOnlyToTheBusinessThatAddedThem() {
    String owner = newPerson("owner");
    String business = createBusiness(owner, "Soap Wholesale");
    String name = "Neem Soap " + UUID.randomUUID();
    createProductVariant(owner, business, name);

    JsonNode mine = json(call("GET", search(name.substring(0, 20)), owner, null));
    assertThat(mine.get("items").size()).isEqualTo(1);
    JsonNode product = mine.get("items").get(0);
    assertThat(product.get("reviewStatus").asString()).isEqualTo("UNREVIEWED");
    assertThat(product.get("variants").get(0).get("packUnit").asString()).isEqualTo("G");

    String stranger = newPerson("stranger");
    createBusiness(stranger, "Other Traders");
    assertThat(json(call("GET", search(name.substring(0, 20)), stranger, null)).get("items").size())
        .isZero();
    String productPath = "/api/v1/products/" + product.get("id").asString();
    assertThat(call("GET", productPath, stranger, null).statusCode()).isEqualTo(404);
    assertThat(call("GET", productPath, owner, null).statusCode()).isEqualTo(200);
  }

  @Test
  void approvedProductsAreVisibleToEveryone() {
    String owner = newPerson("owner");
    String business = createBusiness(owner, "Approved Goods");
    String name = "Approved Detergent " + UUID.randomUUID();
    createProductVariant(owner, business, name);
    jdbc.update("update products set review_status = 'APPROVED' where name = ?", name);

    JsonNode found = json(call("GET", search(name), newPerson("buyer"), null));
    assertThat(found.get("items").size()).isEqualTo(1);
  }

  @Test
  void searchTreatsWildcardsLiterallyAndIsPaged() {
    String owner = newPerson("owner");
    String business = createBusiness(owner, "Wildcards");
    createProductVariant(owner, business, "Plain " + UUID.randomUUID());

    assertThat(json(call("GET", "/api/v1/products?q=%25", owner, null)).get("items").size())
        .isZero();
    assertThat(call("GET", "/api/v1/products?size=51", owner, null).statusCode()).isEqualTo(400);
    JsonNode page = json(call("GET", "/api/v1/products?size=1", owner, null));
    assertThat(page.get("size").asInt()).isEqualTo(1);
    assertThat(page.get("items").size()).isLessThanOrEqualTo(1);
  }

  @Test
  void onlyOwnersAndManagersOfTheBusinessCanAddProducts() {
    String owner = newPerson("owner");
    String business = createBusiness(owner, "Role Check");
    String staff = newPerson("staff");
    addMember(business, staff, "STAFF");
    String outsider = newPerson("outsider");
    Map<String, Object> body =
        Map.of(
            "categoryId",
            leafCategoryId("laundry"),
            "brand",
            "Brand",
            "name",
            "Bar",
            "variants",
            List.of(Map.of("packSize", 250, "packUnit", "G")));
    String path = "/api/v1/businesses/" + business + "/products";

    assertThat(call("POST", path, staff, body).statusCode()).isEqualTo(403);
    assertThat(call("POST", path, outsider, body).statusCode()).isEqualTo(404);
    assertThat(call("POST", path, owner, body).statusCode()).isEqualTo(201);
  }

  @Test
  void productsMustGoInAnActiveLeafCategoryAndBarcodesAreUnique() {
    String owner = newPerson("owner");
    String business = createBusiness(owner, "Validation");
    String path = "/api/v1/businesses/" + business + "/products";
    String barcode = uniqueGtin();

    HttpResponse<String> parentCategory =
        call("POST", path, owner, product(leafCategoryId("home-care"), null));
    assertThat(parentCategory.statusCode()).isEqualTo(400);
    assertThat(call("POST", path, owner, product(UUID.randomUUID().toString(), null)).statusCode())
        .isEqualTo(400);
    assertThat(
            call("POST", path, owner, product(leafCategoryId("dishwash"), "4006381333932"))
                .statusCode())
        .isEqualTo(400);

    assertThat(call("POST", path, owner, product(leafCategoryId("dishwash"), barcode)).statusCode())
        .isEqualTo(201);
    assertThat(call("POST", path, owner, product(leafCategoryId("dishwash"), barcode)).statusCode())
        .isEqualTo(409);
  }

  static String search(String text) {
    return "/api/v1/products?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
  }

  static Map<String, Object> product(String categoryId, String gtin) {
    Map<String, Object> variant = new HashMap<>(Map.of("packSize", 500, "packUnit", "ML"));
    if (gtin != null) {
      variant.put("gtin", gtin);
    }
    return Map.of(
        "categoryId", categoryId, "brand", "Brand", "name", "Liquid", "variants", List.of(variant));
  }

  /** A random, valid GTIN-13, so test runs never collide. */
  static String uniqueGtin() {
    String digits =
        String.format(
            "%012d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000_000L));
    int sum = 0;
    for (int i = 0; i < 12; i++) {
      int digit = digits.charAt(11 - i) - '0';
      sum += i % 2 == 0 ? digit * 3 : digit;
    }
    return digits + (10 - sum % 10) % 10;
  }
}
