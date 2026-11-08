package com.b2bmarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Submitting listings and the admin review queue, end to end. */
class ReviewIT extends SellerFixtures {

  String seller;
  String business;
  String admin;

  @BeforeEach
  void aSellerAndAnAdmin() {
    seller = newPerson("seller");
    business = createBusiness(seller, "Review Traders");
    admin = newPerson("admin");
    makeAdmin(admin);
  }

  String listingPath(String listingId) {
    return "/api/v1/businesses/" + business + "/listings/" + listingId;
  }

  @Test
  void submitNeedsEveryCheckToPassAndSaysWhatIsMissing() {
    String listingId = createListing(seller, business, createBatch(seller, business));

    JsonNode readiness = json(call("GET", listingPath(listingId) + "/eligibility", seller, null));
    assertThat(texts(readiness.get("problems")))
        .containsExactly("PACK_PHOTO_MISSING", "LABEL_PHOTO_MISSING");
    assertThat(readiness.get("flags").size()).isZero();
    assertThat(readiness.get("ruleSetVersion").asString()).isEqualTo("pilot-2026-11.2");

    HttpResponse<String> refused = call("POST", listingPath(listingId) + "/submit", seller, null);
    assertThat(refused.statusCode()).isEqualTo(422);
    assertThat(texts(json(refused).get("problems")))
        .containsExactly("PACK_PHOTO_MISSING", "LABEL_PHOTO_MISSING");
  }

  @Test
  void approvalPublishesTheListingAndApprovesItsBatchAndProduct() {
    String listingId = readyListing(seller, business);

    JsonNode submitted = json(call("POST", listingPath(listingId) + "/submit", seller, null));
    assertThat(submitted.get("status").asString()).isEqualTo("PENDING_REVIEW");
    assertThat(submitted.get("batch").get("status").asString()).isEqualTo("PENDING_REVIEW");

    JsonNode queue = json(call("GET", "/api/v1/admin/reviews?size=50", admin, null));
    JsonNode item = findListing(queue, listingId);
    assertThat(item.get("seller").get("name").asString()).isEqualTo("Review Traders");
    assertThat(item.get("problems").size()).isZero();

    JsonNode details = json(call("GET", "/api/v1/admin/reviews/" + listingId, admin, null));
    assertThat(details.get("photos").size()).isEqualTo(2);
    String photoId = details.get("photos").get(0).get("id").asString();
    assertThat(
            getBytes("/api/v1/admin/reviews/" + listingId + "/photos/" + photoId, admin)
                .statusCode())
        .isEqualTo(200);

    JsonNode approved =
        json(call("POST", "/api/v1/admin/reviews/" + listingId + "/approve", admin, null));
    assertThat(approved.get("status").asString()).isEqualTo("PUBLISHED");
    assertThat(approved.get("batch").get("status").asString()).isEqualTo("APPROVED");
    String productId = approved.get("batch").get("variant").get("productId").asString();
    assertThat(
            jdbc.queryForObject(
                "select review_status from products where id = ?::uuid", String.class, productId))
        .isEqualTo("APPROVED");
    assertThat(
            call("POST", "/api/v1/admin/reviews/" + listingId + "/approve", admin, null)
                .statusCode())
        .as("already decided")
        .isEqualTo(409);
    assertThat(decisions(listingId)).containsExactly("APPROVED");

    // The seller can pause and resume a published listing without another review.
    assertThat(
            json(call("POST", listingPath(listingId) + "/pause", seller, null))
                .get("status")
                .asString())
        .isEqualTo("PAUSED");
    assertThat(
            json(call("POST", listingPath(listingId) + "/resume", seller, null))
                .get("status")
                .asString())
        .isEqualTo("PUBLISHED");
  }

  @Test
  void rejectionReturnsADraftWithTheReasonForTheSeller() {
    String listingId = readyListing(seller, business);
    call("POST", listingPath(listingId) + "/submit", seller, null);

    assertThat(
            call(
                    "POST",
                    "/api/v1/admin/reviews/" + listingId + "/reject",
                    admin,
                    Map.of("reason", " "))
                .statusCode())
        .isEqualTo(400);
    JsonNode rejected =
        json(
            call(
                "POST",
                "/api/v1/admin/reviews/" + listingId + "/reject",
                admin,
                Map.of("reason", "The label photo is blurred; the expiry date cannot be read.")));

    assertThat(rejected.get("status").asString()).isEqualTo("DRAFT");
    assertThat(rejected.get("batch").get("status").asString()).isEqualTo("DRAFT");
    JsonNode seen = json(call("GET", listingPath(listingId), seller, null));
    assertThat(seen.get("rejectionReason").asString()).contains("blurred");

    // Fixed and resubmitted: the old reason is cleared.
    JsonNode again = json(call("POST", listingPath(listingId) + "/submit", seller, null));
    assertThat(again.get("rejectionReason").isNull()).isTrue();
    assertThat(decisions(listingId)).containsExactly("REJECTED");
  }

  @Test
  void approvalRechecksTheRulesInCaseThingsChangedWhileWaiting() {
    String listingId = readyListing(seller, business);
    call("POST", listingPath(listingId) + "/submit", seller, null);
    // Time passed: now only 10 days of shelf life are left.
    jdbc.update(
        "update inventory_batches set expires_on = ?::date where id = ?::uuid",
        TODAY_IN_INDIA.plusDays(10).toString(),
        batchOfListing(listingId));

    HttpResponse<String> refused =
        call("POST", "/api/v1/admin/reviews/" + listingId + "/approve", admin, null);

    assertThat(refused.statusCode()).isEqualTo(422);
    assertThat(texts(json(refused).get("problems"))).containsExactly("SHELF_LIFE_TOO_SHORT");
  }

  @Test
  void onlyPlatformReviewersCanUseTheQueue() {
    String listingId = readyListing(seller, business);
    call("POST", listingPath(listingId) + "/submit", seller, null);

    for (String token : List.of(seller, newPerson("someone"))) {
      assertThat(call("GET", "/api/v1/admin/reviews", token, null).statusCode()).isEqualTo(403);
      assertThat(call("GET", "/api/v1/admin/reviews/" + listingId, token, null).statusCode())
          .isEqualTo(403);
      assertThat(
              call("POST", "/api/v1/admin/reviews/" + listingId + "/approve", token, null)
                  .statusCode())
          .isEqualTo(403);
    }
    assertThat(call("GET", "/api/v1/admin/reviews", null, null).statusCode()).isEqualTo(401);
  }

  @Test
  void reviewersCannotDecideOnTheirOwnBusiness() {
    makeAdmin(seller);
    String listingId = readyListing(seller, business);
    call("POST", listingPath(listingId) + "/submit", seller, null);

    assertThat(
            call("POST", "/api/v1/admin/reviews/" + listingId + "/approve", seller, null)
                .statusCode())
        .isEqualTo(403);
  }

  @Test
  void inReviewTheBatchIsFrozenUntilWithdrawn() {
    String listingId = readyListing(seller, business);
    String batchId = batchOfListing(listingId);
    call("POST", listingPath(listingId) + "/submit", seller, null);
    String batchPath = "/api/v1/businesses/" + business + "/batches/" + batchId;

    String later = TODAY_IN_INDIA.plusDays(80).toString();
    assertThat(call("PATCH", batchPath, seller, Map.of("expiresOn", later)).statusCode())
        .isEqualTo(409);
    assertThat(call("PATCH", listingPath(listingId), seller, Map.of("moq", 20)).statusCode())
        .isEqualTo(409);

    JsonNode withdrawn = json(call("POST", listingPath(listingId) + "/withdraw", seller, null));
    assertThat(withdrawn.get("status").asString()).isEqualTo("DRAFT");
    assertThat(call("PATCH", batchPath, seller, Map.of("expiresOn", later)).statusCode())
        .isEqualTo(200);
  }

  @Test
  void anotherBusinessCannotSubmitOrSeeEligibility() {
    String listingId = readyListing(seller, business);
    String bob = newPerson("bob");
    createBusiness(bob, "Bob Review");

    assertThat(call("POST", listingPath(listingId) + "/submit", bob, null).statusCode())
        .isEqualTo(404);
    assertThat(call("GET", listingPath(listingId) + "/eligibility", bob, null).statusCode())
        .isEqualTo(404);
    assertThat(call("POST", listingPath(listingId) + "/close", bob, null).statusCode())
        .isEqualTo(404);
  }

  @Test
  void meListsPlatformRoles() {
    assertThat(texts(json(call("GET", "/api/v1/me", admin, null)).get("platformRoles")))
        .containsExactly("ADMIN");
    assertThat(json(call("GET", "/api/v1/me", seller, null)).get("platformRoles").size()).isZero();
  }

  @Test
  void theBootstrapEmailIsNotConfiguredInTests() {
    // Nobody becomes an admin by signing in unless the local-only setting names them.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where details->>'source' = 'bootstrap-email'",
                Integer.class))
        .isZero();
  }

  List<String> decisions(String listingId) {
    return jdbc.queryForList(
        "select decision from review_decisions where listing_id = ?::uuid order by created_at",
        String.class,
        listingId);
  }

  static JsonNode findListing(JsonNode queue, String listingId) {
    return StreamSupport.stream(queue.get("items").spliterator(), false)
        .filter(i -> i.get("listing").get("id").asString().equals(listingId))
        .findFirst()
        .orElseThrow();
  }

  static List<String> texts(JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asString).toList();
  }
}
