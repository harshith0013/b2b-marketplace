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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Public offer search with no sign-in: only offers on sale appear, sellers of confidential offers
 * stay hidden, and search and filters work. Each test uses its own brand, so other tests' data
 * never interferes.
 */
class OfferSearchIT extends SellerFixtures {

  String seller;
  String business;
  String brand;

  @BeforeEach
  void aSeller() {
    seller = newPerson("seller");
    business = createBusiness(seller, "Offer Traders");
    brand = "Brand" + UUID.randomUUID().toString().substring(0, 8);
  }

  /** What to put on sale; defaults give a valid offer. */
  record Spec(
      String name,
      String categorySlug,
      String city,
      String state,
      String unit,
      Integer packsPerCase,
      long quantity,
      long moq,
      long pricePaise,
      long mrpPaise,
      String visibility,
      int daysToExpiry) {

    static Spec of(String name) {
      return new Spec(
          name,
          "bath-soaps",
          "Guntur",
          "Andhra Pradesh",
          "PIECE",
          null,
          500,
          10,
          3_000,
          4_500,
          "PUBLIC",
          60);
    }

    Spec city(String city, String state) {
      return new Spec(
          name,
          categorySlug,
          city,
          state,
          unit,
          packsPerCase,
          quantity,
          moq,
          pricePaise,
          mrpPaise,
          visibility,
          daysToExpiry);
    }

    Spec category(String slug) {
      return new Spec(
          name,
          slug,
          city,
          state,
          unit,
          packsPerCase,
          quantity,
          moq,
          pricePaise,
          mrpPaise,
          visibility,
          daysToExpiry);
    }

    Spec price(long paise) {
      return new Spec(
          name,
          categorySlug,
          city,
          state,
          unit,
          packsPerCase,
          quantity,
          moq,
          paise,
          mrpPaise,
          visibility,
          daysToExpiry);
    }

    Spec cases(int packs, long casePricePaise) {
      return new Spec(
          name,
          categorySlug,
          city,
          state,
          "CASE",
          packs,
          50,
          2,
          casePricePaise,
          mrpPaise,
          visibility,
          daysToExpiry);
    }

    Spec confidential() {
      return new Spec(
          name,
          categorySlug,
          city,
          state,
          unit,
          packsPerCase,
          quantity,
          moq,
          pricePaise,
          mrpPaise,
          "CONFIDENTIAL",
          daysToExpiry);
    }

    Spec expiresIn(int days) {
      return new Spec(
          name,
          categorySlug,
          city,
          state,
          unit,
          packsPerCase,
          quantity,
          moq,
          pricePaise,
          mrpPaise,
          visibility,
          days);
    }
  }

  /** Creates product, warehouse, batch, photos and listing; publishes unless told not to. */
  String offer(Spec spec, boolean publish) {
    String base = "/api/v1/businesses/" + business;
    JsonNode product =
        created(
            call(
                "POST",
                base + "/products",
                seller,
                Map.of(
                    "categoryId", leafCategoryId(spec.categorySlug()),
                    "brand", brand,
                    "name", spec.name(),
                    "variants", List.of(Map.of("packSize", 100, "packUnit", "G")))));
    String warehouse =
        created(
                call(
                    "POST",
                    base + "/warehouses",
                    seller,
                    Map.of(
                        "name",
                        "Store " + UUID.randomUUID(),
                        "addressLine",
                        "Secret Street 7, behind the temple",
                        "city",
                        spec.city(),
                        "state",
                        spec.state(),
                        "pincode",
                        "522002")))
            .get("id")
            .asString();
    Map<String, Object> batch = new HashMap<>();
    batch.put("variantId", product.get("variants").get(0).get("id").asString());
    batch.put("warehouseId", warehouse);
    batch.put("batchNumber", "B-" + UUID.randomUUID().toString().substring(0, 6));
    batch.put("expiresOn", TODAY_IN_INDIA.plusDays(spec.daysToExpiry()).toString());
    batch.put("dateType", "EXPIRY");
    batch.put("quantity", spec.quantity());
    batch.put("quantityUnit", spec.unit());
    batch.put("packsPerCase", spec.packsPerCase());
    batch.put("mrpPaise", spec.mrpPaise());
    String batchId = created(call("POST", base + "/batches", seller, batch)).get("id").asString();
    uploadPhoto(seller, business, batchId, "PACK_FRONT");
    uploadPhoto(seller, business, batchId, "LABEL_DATE");
    String listingId =
        created(
                call(
                    "POST",
                    base + "/listings",
                    seller,
                    Map.of(
                        "batchId",
                        batchId,
                        "clearanceReason",
                        "OVERSTOCK",
                        "visibility",
                        spec.visibility(),
                        "moq",
                        spec.moq(),
                        "gstRateBp",
                        500,
                        "priceTiers",
                        List.of(
                            Map.of(
                                "minQuantity", spec.moq(), "unitPricePaise", spec.pricePaise())))))
            .get("id")
            .asString();
    if (publish) {
      publish(seller, business, listingId);
    }
    return listingId;
  }

  JsonNode search(String query) {
    HttpResponse<String> response = get("/api/v1/offers?brand=" + brand + query);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return json(response);
  }

  static List<String> ids(JsonNode page) {
    return StreamSupport.stream(page.get("items").spliterator(), false)
        .map(i -> i.get("id").asString())
        .toList();
  }

  static String enc(String text) {
    return URLEncoder.encode(text, StandardCharsets.UTF_8);
  }

  // --- What is on sale --------------------------------------------------------------------------

  @Test
  void onlyPublishedApprovedOffersAppearToAnonymousBuyers() {
    String onSale = offer(Spec.of("Neem Soap"), true);
    String draft = offer(Spec.of("Draft Soap"), false);
    String inReview = offer(Spec.of("Review Soap"), false);
    call(
        "POST",
        "/api/v1/businesses/" + business + "/listings/" + inReview + "/submit",
        seller,
        null);
    String paused = offer(Spec.of("Paused Soap"), true);
    call("POST", "/api/v1/businesses/" + business + "/listings/" + paused + "/pause", seller, null);

    assertThat(ids(search(""))).containsExactly(onSale);

    for (String hidden : List.of(draft, inReview, paused)) {
      assertThat(get("/api/v1/offers/" + hidden).statusCode()).as(hidden).isEqualTo(404);
    }
  }

  @Test
  void offersDisappearThirtyDaysBeforeExpiryOrWhenStockRunsShort() {
    String soon = offer(Spec.of("Soon Soap"), true);
    String short_ = offer(Spec.of("Short Soap"), true);
    String fine = offer(Spec.of("Fine Soap"), true);
    // Time passes: 20 days left. (An approved date changes only via review; then back.)
    jdbc.update(
        "update inventory_batches set expires_on = ?::date, status = 'PENDING_REVIEW' where id = ?::uuid",
        TODAY_IN_INDIA.plusDays(20).toString(),
        batchOfListing(soon));
    jdbc.update(
        "update inventory_batches set status = 'APPROVED' where id = ?::uuid",
        batchOfListing(soon));
    jdbc.update(
        "update inventory_batches set quantity = 5 where id = ?::uuid", batchOfListing(short_));

    assertThat(ids(search(""))).containsExactly(fine);
    assertThat(get("/api/v1/offers/" + soon).statusCode()).isEqualTo(404);
  }

  @Test
  void detailsShowPricesPhotosAndPlaceButNeverTheStreetAddress() {
    String listingId = offer(Spec.of("Detail Soap"), true);

    HttpResponse<String> response = get("/api/v1/offers/" + listingId);

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode details = json(response);
    JsonNode offer = details.get("offer");
    assertThat(offer.get("productName").asString()).isEqualTo("Detail Soap");
    assertThat(offer.get("location").get("city").asString()).isEqualTo("Guntur");
    assertThat(offer.get("seller").get("name").asString()).isEqualTo("Offer Traders");
    assertThat(offer.get("pricePerPackPaise").asLong()).isEqualTo(3_000);
    assertThat(offer.get("minimumOrderValuePaise").asLong()).isEqualTo(30_000);
    assertThat(offer.get("daysLeft").asLong()).isEqualTo(60);
    assertThat(details.get("priceTiers").size()).isEqualTo(1);
    assertThat(details.get("photos").size()).isEqualTo(2);
    assertThat(response.body()).doesNotContain("Secret Street", business, "storage", "businesses/");

    String photoId = details.get("photos").get(0).get("id").asString();
    HttpResponse<byte[]> photo =
        getBytes("/api/v1/offers/" + listingId + "/photos/" + photoId, null);
    assertThat(photo.statusCode()).isEqualTo(200);
    assertThat(photo.headers().firstValue("Content-Type")).hasValue("image/jpeg");
    assertThat(photo.body().length).isGreaterThan(100);
  }

  @Test
  void confidentialOffersHideTheSellerEntirely() {
    String listingId = offer(Spec.of("Quiet Soap").confidential(), true);

    String body = get("/api/v1/offers/" + listingId).body();
    JsonNode seller = json(get("/api/v1/offers/" + listingId)).get("offer").get("seller");

    assertThat(seller.get("name").isNull()).isTrue();
    assertThat(seller.get("confidential").asBoolean()).isTrue();
    assertThat(body).doesNotContain("Offer Traders", business);
    assertThat(search("").toString()).doesNotContain("Offer Traders", business);
  }

  @Test
  void photosOfUnpublishedListingsAreNotServed() {
    String draft = offer(Spec.of("Hidden Photo Soap"), false);
    String photoId =
        jdbc.queryForObject(
            "select id::text from batch_photos where batch_id = ?::uuid limit 1",
            String.class,
            batchOfListing(draft));

    assertThat(getBytes("/api/v1/offers/" + draft + "/photos/" + photoId, null).statusCode())
        .isEqualTo(404);
    String published = offer(Spec.of("Shown Photo Soap"), true);
    // Another listing's photo id through a published listing's path: still not served.
    assertThat(getBytes("/api/v1/offers/" + published + "/photos/" + photoId, null).statusCode())
        .isEqualTo(404);
  }

  // --- Search --------------------------------------------------------------------------------

  @Test
  void findsByWordsTyposAndCategoryNamesInEveryLanguage() {
    String detergent = offer(Spec.of("Ultra Detergent Powder").category("laundry"), true);
    String soap = offer(Spec.of("Sandal Bar"), true);

    assertThat(ids(search("&q=detergent"))).containsExactly(detergent);
    assertThat(ids(search("&q=" + enc("detrgent")))).as("typo").containsExactly(detergent);
    assertThat(ids(search("&q=" + enc("సబ్బు")))).as("Telugu: soap").containsExactly(soap);
    assertThat(ids(search("&q=" + enc("साबुन")))).as("Hindi: soap").containsExactly(soap);
    assertThat(ids(search("&q=" + enc("%")))).as("wildcards are literal").isEmpty();
  }

  @Test
  void filtersByPlaceCategoryPriceAndOrderValue() {
    String guntur = offer(Spec.of("Guntur Soap").price(3_000), true);
    String hyderabad = offer(Spec.of("Hyd Soap").city("Hyderabad", "Telangana").price(2_000), true);
    String laundry = offer(Spec.of("Bar Detergent").category("laundry").price(4_000), true);
    // 24 packs a case at ₹600 = ₹25 a pack; minimum 2 cases = ₹1,200.
    String cases = offer(Spec.of("Case Soap").cases(24, 60_000), true);

    assertThat(ids(search("&state=telangana"))).containsExactly(hyderabad);
    assertThat(ids(search("&city=Hyderabad&state=Telangana"))).containsExactly(hyderabad);
    String homeCare =
        jdbc.queryForObject(
            "select id::text from categories where slug = 'home-care'", String.class);
    assertThat(ids(search("&categoryId=" + homeCare)))
        .as("parent includes children")
        .containsExactly(laundry);
    assertThat(ids(search("&maxPricePerPackPaise=2600")))
        .containsExactlyInAnyOrder(hyderabad, cases);
    assertThat(ids(search("&maxOrderValuePaise=30000")))
        .as("₹300 minimum order or less")
        .containsExactlyInAnyOrder(guntur, hyderabad);
    assertThat(ids(search("&sort=PRICE"))).containsExactly(hyderabad, cases, guntur, laundry);
  }

  @Test
  void sortsBySoonestExpiryAndPages() {
    String later = offer(Spec.of("Later Soap").expiresIn(90), true);
    String sooner = offer(Spec.of("Sooner Soap").expiresIn(45), true);

    assertThat(ids(search("&sort=EXPIRY"))).containsExactly(sooner, later);
    JsonNode first = search("&sort=EXPIRY&size=1");
    assertThat(ids(first)).containsExactly(sooner);
    assertThat(first.get("hasNext").asBoolean()).isTrue();
    assertThat(ids(search("&sort=EXPIRY&size=1&page=1"))).containsExactly(later);
    assertThat(ids(search("&minDaysLeft=60"))).containsExactly(later);
    assertThat(get("/api/v1/offers?size=25").statusCode()).isEqualTo(400);
    assertThat(get("/api/v1/offers?q=" + "x".repeat(101)).statusCode()).isEqualTo(400);
  }

  @Test
  void listsPlacesAndBrandsThatHaveOffers() {
    offer(Spec.of("Vizag Soap").city("Visakhapatnam", "Andhra Pradesh"), true);

    JsonNode places = json(get("/api/v1/offers/locations"));
    JsonNode andhra =
        StreamSupport.stream(places.spliterator(), false)
            .filter(p -> p.get("state").asString().equals("Andhra Pradesh"))
            .findFirst()
            .orElseThrow();
    assertThat(andhra.get("cities").toString()).contains("Visakhapatnam");
    assertThat(get("/api/v1/offers/brands").body()).contains(brand);
  }

  // --- Access ----------------------------------------------------------------------------------

  @Test
  void onlyOffersAndCategoriesArePublic() {
    assertThat(get("/api/v1/offers").statusCode()).isEqualTo(200);
    assertThat(get("/api/v1/categories").statusCode()).isEqualTo(200);
    for (String path :
        List.of(
            "/api/v1/products",
            "/api/v1/me",
            "/api/v1/admin/reviews",
            "/api/v1/businesses/" + business,
            "/api/v1/businesses/" + business + "/listings")) {
      assertThat(get(path).statusCode()).as(path).isEqualTo(401);
    }
    assertThat(call("POST", "/api/v1/offers", null, Map.of()).statusCode()).isIn(401, 403);
  }
}
