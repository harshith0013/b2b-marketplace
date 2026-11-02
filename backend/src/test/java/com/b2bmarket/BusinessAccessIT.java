package com.b2bmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Proves the tenant boundary end to end with real signed tokens: one business can never read,
 * change or list the members of another, and roles inside a business are enforced.
 */
class BusinessAccessIT extends IntegrationTestSupport {

  static final String VALID_GSTIN = "27AAPFU0939F1ZV";

  @Autowired JdbcTemplate jdbc;

  /** Two unrelated people, each owning one business. Fresh per test so tests stay independent. */
  String alice;

  String bob;
  String aliceBusiness;
  String bobBusiness;

  @BeforeEach
  void twoBusinessesWithDifferentOwners() {
    alice = tokenFor("alice-" + UUID.randomUUID(), "alice-" + UUID.randomUUID() + "@example.com");
    bob = tokenFor("bob-" + UUID.randomUUID(), "bob-" + UUID.randomUUID() + "@example.com");
    aliceBusiness = createBusiness(alice, "Alice Traders", null);
    bobBusiness = createBusiness(bob, "Bob Distributors", null);
  }

  String createBusiness(String token, String legalName, String gstin) {
    HttpResponse<String> response =
        call("POST", "/api/v1/businesses", token, business(legalName, gstin));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asString();
  }

  static Map<String, Object> business(String legalName, String gstin) {
    Map<String, Object> body =
        new HashMap<>(
            Map.of(
                "legalName", legalName,
                "businessType", "WHOLESALER",
                "city", "Guntur",
                "state", "Andhra Pradesh",
                "pincode", "522001"));
    if (gstin != null) {
      body.put("gstin", gstin);
    }
    return body;
  }

  UUID userIdOf(String token) {
    return UUID.fromString(
        json(call("GET", "/api/v1/me", token, null)).get("profile").get("id").asString());
  }

  // --- Authentication -----------------------------------------------------------------------

  @Test
  void requestsWithoutAValidTokenAreRejected() {
    assertThat(call("GET", "/api/v1/me", null, null).statusCode()).isEqualTo(401);
    assertThat(call("GET", "/api/v1/me", "not-a-jwt", null).statusCode()).isEqualTo(401);
    String tampered = alice.substring(0, alice.length() - 4) + "AAAA";
    assertThat(call("GET", "/api/v1/me", tampered, null).statusCode()).isEqualTo(401);
  }

  @Test
  void tokensFromAnotherIssuerAreRejected() {
    String foreign = tokenFor("other", "mallory", "mallory@example.com", true);
    assertThat(call("GET", "/api/v1/me", foreign, null).statusCode()).isEqualTo(401);
  }

  @Test
  void unverifiedEmailsCannotUseTheApi() {
    String unverified = tokenFor("default", "eve-" + UUID.randomUUID(), "eve@example.com", false);
    assertThat(call("GET", "/api/v1/me", unverified, null).statusCode()).isEqualTo(403);
  }

  @Test
  void firstRequestCreatesTheAccountWithANormalizedEmail() {
    String token = tokenFor("carol-" + UUID.randomUUID(), "Carol.Mixed@Example.COM");

    JsonNode me = json(call("GET", "/api/v1/me", token, null));

    assertThat(me.get("profile").get("email").asString()).isEqualTo("carol.mixed@example.com");
    assertThat(me.get("profile").get("preferredLocale").asString()).isEqualTo("en");
    assertThat(me.get("businesses").size()).isZero();
  }

  @Test
  void profileCanBeUpdatedAndInvalidLanguagesAreRejected() {
    HttpResponse<String> ok =
        call("PATCH", "/api/v1/me", alice, Map.of("displayName", "Alice", "preferredLocale", "te"));
    assertThat(ok.statusCode()).isEqualTo(200);
    assertThat(json(ok).get("preferredLocale").asString()).isEqualTo("te");

    assertThat(call("PATCH", "/api/v1/me", alice, Map.of("preferredLocale", "fr")).statusCode())
        .isEqualTo(400);
  }

  // --- Tenant isolation ---------------------------------------------------------------------

  @Test
  void ownersSeeTheirOwnBusinessAndItIsListedOnMe() {
    JsonNode business = json(call("GET", "/api/v1/businesses/" + aliceBusiness, alice, null));
    assertThat(business.get("legalName").asString()).isEqualTo("Alice Traders");
    assertThat(business.get("yourRole").asString()).isEqualTo("OWNER");

    JsonNode memberships = json(call("GET", "/api/v1/me", alice, null)).get("businesses");
    assertThat(memberships.size()).isEqualTo(1);
    assertThat(memberships.get(0).get("businessId").asString()).isEqualTo(aliceBusiness);
  }

  @Test
  void anotherBusinessCannotReadChangeOrListMembersAndCannotTellItExists() {
    String path = "/api/v1/businesses/" + aliceBusiness;

    assertThat(call("GET", path, bob, null).statusCode()).isEqualTo(404);
    assertThat(call("PATCH", path, bob, Map.of("tradeName", "Hijacked")).statusCode())
        .isEqualTo(404);
    assertThat(call("GET", path + "/members", bob, null).statusCode()).isEqualTo(404);

    // Same answer as for an id that does not exist at all.
    assertThat(call("GET", "/api/v1/businesses/" + UUID.randomUUID(), bob, null).statusCode())
        .isEqualTo(404);
    // And nothing was changed.
    assertThat(json(call("GET", path, alice, null)).get("tradeName").isNull()).isTrue();
  }

  @Test
  void isolationHoldsInBothDirections() {
    assertThat(call("GET", "/api/v1/businesses/" + bobBusiness, alice, null).statusCode())
        .isEqualTo(404);
  }

  // --- Roles inside a business ----------------------------------------------------------------

  @Test
  void staffCanViewButCannotEditOrListMembers() {
    addMember(aliceBusiness, userIdOf(bob), "STAFF");
    String path = "/api/v1/businesses/" + aliceBusiness;

    JsonNode asStaff = json(call("GET", path, bob, null));
    assertThat(asStaff.get("yourRole").asString()).isEqualTo("STAFF");
    assertThat(call("PATCH", path, bob, Map.of("tradeName", "Nope")).statusCode()).isEqualTo(403);
    assertThat(call("GET", path + "/members", bob, null).statusCode()).isEqualTo(403);
  }

  @Test
  void managersCanListMembersButOnlyOwnersCanEdit() {
    addMember(aliceBusiness, userIdOf(bob), "MANAGER");
    String path = "/api/v1/businesses/" + aliceBusiness;

    JsonNode members = json(call("GET", path + "/members", bob, null));
    assertThat(members.size()).isEqualTo(2);
    assertThat(call("PATCH", path, bob, Map.of("tradeName", "Nope")).statusCode()).isEqualTo(403);
    assertThat(call("PATCH", path, alice, Map.of("tradeName", "Alice Wholesale")).statusCode())
        .isEqualTo(200);
  }

  // --- Validation ---------------------------------------------------------------------------

  @Test
  void invalidInputIsRejectedWithProblemDetails() {
    HttpResponse<String> badPincode =
        call(
            "POST",
            "/api/v1/businesses",
            alice,
            Map.of(
                "legalName",
                "X",
                "businessType",
                "KIRANA",
                "city",
                "A",
                "state",
                "B",
                "pincode",
                "012345"));
    assertThat(badPincode.statusCode()).isEqualTo(400);
    assertThat(badPincode.headers().firstValue("Content-Type"))
        .hasValueSatisfying(t -> assertThat(t).contains("problem+json"));

    assertThat(
            call("POST", "/api/v1/businesses", alice, business("X", "27AAPFU0939F1ZW"))
                .statusCode())
        .isEqualTo(400);
  }

  @Test
  void aGstinCanBelongToOnlyOneBusiness() {
    String gstinOwner =
        tokenFor("dev-" + UUID.randomUUID(), "dev-" + UUID.randomUUID() + "@example.com");
    jdbc.update(
        "delete from business_memberships where business_id in (select id from businesses where gstin = ?)",
        VALID_GSTIN);
    jdbc.update("delete from businesses where gstin = ?", VALID_GSTIN);
    createBusiness(gstinOwner, "First Holder", VALID_GSTIN);

    assertThat(
            call("POST", "/api/v1/businesses", alice, business("Second", VALID_GSTIN)).statusCode())
        .isEqualTo(409);
  }

  // --- Audit ----------------------------------------------------------------------------------

  @Test
  void creationAndUpdatesAreAuditedAndTheAuditLogCannotBeChanged() {
    call("PATCH", "/api/v1/businesses/" + aliceBusiness, alice, Map.of("city", "Vijayawada"));

    var actions =
        jdbc.queryForList(
            "select action from audit_events where business_id = ?::uuid order by occurred_at",
            String.class,
            aliceBusiness);
    assertThat(actions).containsExactly("business.created", "business.updated");
    String details =
        jdbc.queryForObject(
            "select details::text from audit_events where business_id = ?::uuid and action = 'business.updated'",
            String.class,
            aliceBusiness);
    assertThat(details).contains("city").doesNotContain("Vijayawada");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update audit_events set action = 'x' where business_id = ?::uuid",
                    aliceBusiness))
        .hasMessageContaining("append-only");
    assertThatThrownBy(
            () ->
                jdbc.update("delete from audit_events where business_id = ?::uuid", aliceBusiness))
        .hasMessageContaining("append-only");
  }

  /** There is no invite API until Phase 3b, so memberships are added directly for these tests. */
  void addMember(String businessId, UUID userId, String role) {
    jdbc.update(
        "insert into business_memberships (id, user_id, business_id, role, created_at) values (?, ?, ?::uuid, ?, ?)",
        UUID.randomUUID(),
        userId,
        businessId,
        role,
        Timestamp.from(Instant.now()));
  }
}
