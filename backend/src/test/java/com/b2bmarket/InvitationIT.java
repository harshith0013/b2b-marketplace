package com.b2bmarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Invitations end to end with real signed tokens: only the owner invites, only the invited email
 * can see and accept, and other businesses can neither see nor touch them.
 */
class InvitationIT extends IntegrationTestSupport {

  @Autowired JdbcTemplate jdbc;

  String owner;
  String inviteeEmail;
  String invitee;
  String stranger;
  String business;

  @BeforeEach
  void anOwnerWithABusinessAndTwoOtherPeople() {
    String run = UUID.randomUUID().toString();
    owner = tokenFor("owner-" + run, "owner-" + run + "@example.com");
    inviteeEmail = "invitee-" + run + "@example.com";
    invitee = tokenFor("invitee-" + run, inviteeEmail);
    stranger = tokenFor("stranger-" + run, "stranger-" + run + "@example.com");
    business = createBusiness(owner, "Owner Traders");
  }

  String createBusiness(String token, String name) {
    Map<String, Object> body = new HashMap<>();
    body.put("legalName", name);
    body.put("businessType", "WHOLESALER");
    body.put("city", "Guntur");
    body.put("state", "Andhra Pradesh");
    body.put("pincode", "522001");
    HttpResponse<String> response = call("POST", "/api/v1/businesses", token, body);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asString();
  }

  HttpResponse<String> invite(String token, String businessId, String email, String role) {
    return call(
        "POST",
        "/api/v1/businesses/" + businessId + "/invitations",
        token,
        Map.of("email", email, "role", role));
  }

  String inviteId(HttpResponse<String> response) {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asString();
  }

  @Test
  void theInvitedPersonSeesAndAcceptsTheInvitationAndBecomesAMember() {
    String id = inviteId(invite(owner, business, inviteeEmail.toUpperCase(), "STAFF"));

    JsonNode received = json(call("GET", "/api/v1/me/invitations", invitee, null));
    assertThat(received.size()).isEqualTo(1);
    assertThat(received.get(0).get("businessName").asString()).isEqualTo("Owner Traders");
    assertThat(received.get(0).get("role").asString()).isEqualTo("STAFF");

    assertThat(call("POST", "/api/v1/me/invitations/" + id + "/accept", invitee, null).statusCode())
        .isEqualTo(204);

    JsonNode asMember = json(call("GET", "/api/v1/businesses/" + business, invitee, null));
    assertThat(asMember.get("yourRole").asString()).isEqualTo("STAFF");
    assertThat(json(call("GET", "/api/v1/me/invitations", invitee, null)).size()).isZero();
    assertThat(
            jdbc.queryForList(
                "select action from audit_events where business_id = ?::uuid order by occurred_at",
                String.class,
                business))
        .containsExactly("business.created", "invitation.created", "invitation.accepted");
  }

  @Test
  void someoneElseCannotSeeOrAcceptAnInvitationMeantForAnotherEmail() {
    String id = inviteId(invite(owner, business, inviteeEmail, "MANAGER"));

    assertThat(json(call("GET", "/api/v1/me/invitations", stranger, null)).size()).isZero();
    assertThat(
            call("POST", "/api/v1/me/invitations/" + id + "/accept", stranger, null).statusCode())
        .isEqualTo(404);
    assertThat(call("GET", "/api/v1/businesses/" + business, stranger, null).statusCode())
        .isEqualTo(404);
  }

  @Test
  void anotherBusinessCannotInviteIntoListOrRevokeThisBusinessesInvitations() {
    String id = inviteId(invite(owner, business, inviteeEmail, "STAFF"));
    createBusiness(stranger, "Stranger Co");
    String base = "/api/v1/businesses/" + business + "/invitations";

    assertThat(invite(stranger, business, "x@example.com", "STAFF").statusCode()).isEqualTo(404);
    assertThat(call("GET", base, stranger, null).statusCode()).isEqualTo(404);
    assertThat(call("DELETE", base + "/" + id, stranger, null).statusCode()).isEqualTo(404);
  }

  @Test
  void onlyOwnersInviteAndRevokeWhileManagersCanSeeOpenInvitations() {
    inviteId(invite(owner, business, inviteeEmail, "MANAGER"));
    String acceptId =
        json(call("GET", "/api/v1/me/invitations", invitee, null)).get(0).get("id").asString();
    call("POST", "/api/v1/me/invitations/" + acceptId + "/accept", invitee, null);
    String pending = inviteId(invite(owner, business, "someone@example.com", "STAFF"));

    String base = "/api/v1/businesses/" + business + "/invitations";
    assertThat(json(call("GET", base, invitee, null)).size()).isEqualTo(1); // manager may look
    assertThat(invite(invitee, business, "other@example.com", "STAFF").statusCode()).isEqualTo(403);
    assertThat(call("DELETE", base + "/" + pending, invitee, null).statusCode()).isEqualTo(403);
  }

  @Test
  void aRevokedInvitationCanNoLongerBeAccepted() {
    String id = inviteId(invite(owner, business, inviteeEmail, "STAFF"));

    assertThat(
            call("DELETE", "/api/v1/businesses/" + business + "/invitations/" + id, owner, null)
                .statusCode())
        .isEqualTo(204);
    assertThat(call("POST", "/api/v1/me/invitations/" + id + "/accept", invitee, null).statusCode())
        .isEqualTo(404);
  }

  @Test
  void duplicateInvitationsMembersAndOwnerRoleAreRejected() {
    inviteId(invite(owner, business, inviteeEmail, "STAFF"));

    assertThat(invite(owner, business, inviteeEmail, "MANAGER").statusCode()).isEqualTo(409);
    assertThat(invite(owner, business, "new@example.com", "OWNER").statusCode()).isEqualTo(400);
    assertThat(invite(owner, business, "not-an-email", "STAFF").statusCode()).isEqualTo(400);

    String ownerEmail =
        json(call("GET", "/api/v1/me", owner, null)).get("profile").get("email").asString();
    assertThat(invite(owner, business, ownerEmail, "STAFF").statusCode()).isEqualTo(409);
  }

  @Test
  void anExpiredInvitationCannotBeAcceptedAndCanBeReplaced() {
    String id = inviteId(invite(owner, business, inviteeEmail, "STAFF"));
    jdbc.update(
        "update business_invitations set created_at = now() - interval '20 days',"
            + " expires_at = now() - interval '6 days' where id = ?::uuid",
        id);

    assertThat(call("POST", "/api/v1/me/invitations/" + id + "/accept", invitee, null).statusCode())
        .isEqualTo(404);
    assertThat(json(call("GET", "/api/v1/me/invitations", invitee, null)).size()).isZero();
    inviteId(invite(owner, business, inviteeEmail, "STAFF"));
  }
}
