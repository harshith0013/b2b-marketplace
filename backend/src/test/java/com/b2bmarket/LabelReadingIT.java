package com.b2bmarket;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2bmarket.FakeAiProvider.Reply;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Label photos read by a fake AI provider: readings are stored, compared by the rules, flagged to
 * seller and reviewer, confirmed by the reviewer, recorded in the Batch Passport and summarised for
 * buyers. Every check is stored with its rule-set version.
 */
class LabelReadingIT extends SellerFixtures {

  static final FakeAiProvider PROVIDER = new FakeAiProvider();

  @DynamicPropertySource
  static void provider(DynamicPropertyRegistry registry) {
    registry.add("app.ai.base-url", PROVIDER::baseUrl);
    registry.add("app.ai.api-key", () -> "test-key-not-real");
    registry.add("app.ai.vision-timeout", () -> "2s");
  }

  @org.springframework.beans.factory.annotation.Autowired
  com.b2bmarket.ai.internal.ProviderPause pause;

  String seller;
  String business;
  String admin;

  @BeforeEach
  void aSellerAndAReviewer() {
    pause.resume();
    jdbc.update("delete from ai_usage_daily");
    seller = newPerson("seller");
    business = createBusiness(seller, "Label Traders");
    admin = newPerson("admin");
    makeAdmin(admin);
  }

  /** The label the fake provider will "see": the batch fixture's dates are today + 45 days. */
  static String label(String date, String batch, Double mrp) {
    return """
        {"legible":true,"printedDate":"EXP %s","dateType":"EXPIRY","expiryDate":"%s",
         "batchNumber":%s,"mrpRupees":%s}
        """
        .formatted(
            date, date, batch == null ? "null" : "\"" + batch + "\"", mrp == null ? "null" : mrp);
  }

  String listingPath(String listingId) {
    return "/api/v1/businesses/" + business + "/listings/" + listingId;
  }

  /** The label is read in the background after upload: wait (up to 10 s) for the reading. */
  JsonNode readinessOnceRead(String listingId) {
    for (int attempt = 0; attempt < 100; attempt++) {
      JsonNode readiness = json(call("GET", listingPath(listingId) + "/eligibility", seller, null));
      if (readiness.hasNonNull("label")) {
        return readiness;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    throw new AssertionError("The label photo was not read within 10 seconds");
  }

  String batchNumberOf(String listingId) {
    return jdbc.queryForObject(
        "select batch_number from inventory_batches where id = ?::uuid",
        String.class,
        batchOfListing(listingId));
  }

  String expiryOf(String listingId) {
    return jdbc.queryForObject(
        "select expires_on::text from inventory_batches where id = ?::uuid",
        String.class,
        batchOfListing(listingId));
  }

  @Test
  void aMatchingLabelRaisesNoFlagAndBuyersSeeItMatched() {
    PROVIDER.answer(new Reply.Content(label(TODAY_IN_INDIA.plusDays(45).toString(), null, 45.0)));
    String listingId = readyListing(seller, business);

    JsonNode readiness = readinessOnceRead(listingId);
    assertThat(readiness.get("label").get("outcome").asString()).isEqualTo("READ");
    assertThat(readiness.get("flags").size()).isZero();

    publish(seller, business, listingId); // approves without needing a confirmation
    JsonNode passport = json(get("/api/v1/offers/" + listingId)).get("passport");
    assertThat(passport.get("labelCheck").asString()).isEqualTo("MATCHES_LABEL");
    assertThat(passport.get("detailsChangedAfterApproval").asBoolean()).isFalse();
    assertThat(passport.get("approvedAt").isNull()).isFalse();
  }

  @Test
  void aDifferentDateIsFlaggedAndTheReviewerMustConfirm() {
    PROVIDER.answer(new Reply.Content(label("2031-01-31", "XX-1", 99.0)));
    String listingId = readyListing(seller, business);

    JsonNode readiness = readinessOnceRead(listingId);
    assertThat(texts(readiness.get("flags")))
        .containsExactly(
            "LABEL_DATE_MISMATCH", "LABEL_BATCH_NUMBER_MISMATCH", "LABEL_MRP_MISMATCH");
    assertThat(readiness.get("problems").size()).as("flags do not block").isZero();
    assertThat(readiness.get("label").get("expiry").asString()).isEqualTo("2031-01-31");

    assertThat(call("POST", listingPath(listingId) + "/submit", seller, null).statusCode())
        .isEqualTo(200);
    JsonNode details = json(call("GET", "/api/v1/admin/reviews/" + listingId, admin, null));
    assertThat(texts(details.get("flags"))).contains("LABEL_DATE_MISMATCH");
    assertThat(details.get("label").get("printedDate").asString()).isEqualTo("EXP 2031-01-31");

    var refused = call("POST", "/api/v1/admin/reviews/" + listingId + "/approve", admin, null);
    assertThat(refused.statusCode()).isEqualTo(422);
    assertThat(texts(json(refused).get("problems")))
        .containsExactly("LABEL_CHECK_CONFIRMATION_REQUIRED");

    var approved =
        call(
            "POST",
            "/api/v1/admin/reviews/" + listingId + "/approve",
            admin,
            Map.of("confirmedLabelCheck", true));
    assertThat(approved.statusCode()).as(approved.body()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select label_check_confirmed from review_decisions where listing_id = ?::uuid",
                Boolean.class,
                listingId))
        .isTrue();
    // Buyers are told a reviewer checked it, not that it matched.
    assertThat(
            json(get("/api/v1/offers/" + listingId)).get("passport").get("labelCheck").asString())
        .isEqualTo("REVIEWER_CHECKED");
  }

  @Test
  void everyCheckIsStoredWithItsStageAndRuleSetVersion() {
    PROVIDER.answer(new Reply.Content(label(TODAY_IN_INDIA.plusDays(45).toString(), null, null)));
    String listingId = readyListing(seller, business);
    readinessOnceRead(listingId);
    publish(seller, business, listingId);

    List<Map<String, Object>> checks =
        jdbc.queryForList(
            "select stage, rule_set_version, blocked, flagged from compliance_checks where listing_id = ?::uuid order by created_at",
            listingId);
    assertThat(checks).extracting(c -> c.get("stage")).containsExactly("SUBMIT", "APPROVE");
    assertThat(checks).allMatch(c -> "pilot-2026-11".equals(c.get("rule_set_version")));
    assertThat(checks).allMatch(c -> Boolean.FALSE.equals(c.get("blocked")));
  }

  @Test
  void theBatchPassportTellsTheStoryToMembersAndReviewersOnly() {
    PROVIDER.answer(new Reply.Content(label(TODAY_IN_INDIA.plusDays(45).toString(), null, null)));
    String listingId = readyListing(seller, business);
    readinessOnceRead(listingId);
    publish(seller, business, listingId);

    JsonNode passport = json(call("GET", listingPath(listingId) + "/passport", seller, null));
    List<String> kinds =
        StreamSupport.stream(passport.get("entries").spliterator(), false)
            .map(e -> e.get("kind").asString())
            .toList();
    assertThat(kinds)
        .contains(
            "batch.created",
            "batch_photo.added",
            "label.read",
            "listing.created",
            "check.SUBMIT",
            "listing.submitted",
            "check.APPROVE",
            "listing.approved");
    assertThat(kinds.indexOf("batch.created")).isLessThan(kinds.indexOf("listing.approved"));
    assertThat(passport.toString()).doesNotContain(admin, "@example.com");

    assertThat(
            call("GET", "/api/v1/admin/reviews/" + listingId + "/passport", admin, null)
                .statusCode())
        .isEqualTo(200);
    String stranger = newPerson("stranger");
    createBusiness(stranger, "Other Co");
    assertThat(call("GET", listingPath(listingId) + "/passport", stranger, null).statusCode())
        .isEqualTo(404);
    assertThat(
            call("GET", "/api/v1/admin/reviews/" + listingId + "/passport", stranger, null)
                .statusCode())
        .isEqualTo(403);
  }

  @Test
  void whenTheProviderCannotReadTheLabelNothingIsBlocked() {
    PROVIDER.answer(new Reply.Error(500));
    String listingId = readyListing(seller, business);

    JsonNode readiness = readinessOnceRead(listingId);
    assertThat(readiness.get("label").get("outcome").asString()).isEqualTo("NOT_CHECKED");
    assertThat(readiness.get("label").get("reason").asString()).isEqualTo("PROVIDER_ERROR");
    assertThat(readiness.get("flags").size()).isZero();
    publish(seller, business, listingId);
  }

  @Test
  void changingApprovedDatesIsShownToBuyers() {
    PROVIDER.answer(new Reply.Content(label(TODAY_IN_INDIA.plusDays(45).toString(), null, null)));
    String listingId = readyListing(seller, business);
    readinessOnceRead(listingId);
    publish(seller, business, listingId);

    call(
        "PATCH",
        "/api/v1/businesses/" + business + "/batches/" + batchOfListing(listingId),
        seller,
        Map.of("expiresOn", TODAY_IN_INDIA.plusDays(60).toString()));
    // The batch is back in review, so the offer is off sale; approve again to see the summary.
    jdbc.update(
        "update inventory_batches set status = 'APPROVED' where id = ?::uuid",
        batchOfListing(listingId));

    JsonNode passport = json(get("/api/v1/offers/" + listingId)).get("passport");
    assertThat(passport.get("detailsChangedAfterApproval").asBoolean()).isTrue();
  }

  static List<String> texts(JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asString).toList();
  }

  static String unique() {
    return UUID.randomUUID().toString().substring(0, 6);
  }
}
