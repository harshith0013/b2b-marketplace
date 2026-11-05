package com.b2bmarket.businesses.internal;

import com.b2bmarket.businesses.BusinessDtos.BusinessDetails;
import com.b2bmarket.businesses.BusinessDtos.CreateBusinessRequest;
import com.b2bmarket.businesses.BusinessDtos.Member;
import com.b2bmarket.businesses.BusinessDtos.UpdateBusinessRequest;
import com.b2bmarket.businesses.BusinessService;
import com.b2bmarket.businesses.InvitationDtos.InviteRequest;
import com.b2bmarket.businesses.InvitationDtos.SentInvitation;
import com.b2bmarket.businesses.InvitationService;
import com.b2bmarket.users.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Business accounts and their invitations. Membership and role checks happen in the services. */
@RestController
@RequestMapping("/api/v1/businesses")
class BusinessController {

  private final BusinessService businesses;
  private final InvitationService invitations;

  BusinessController(BusinessService businesses, InvitationService invitations) {
    this.businesses = businesses;
    this.invitations = invitations;
  }

  @PostMapping
  ResponseEntity<BusinessDetails> create(
      CurrentUser caller, @Valid @RequestBody CreateBusinessRequest request) {
    BusinessDetails created = businesses.create(caller, request);
    return ResponseEntity.created(URI.create("/api/v1/businesses/" + created.id())).body(created);
  }

  @GetMapping("/{businessId}")
  BusinessDetails get(CurrentUser caller, @PathVariable UUID businessId) {
    return businesses.get(caller, businessId);
  }

  @PatchMapping("/{businessId}")
  BusinessDetails update(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @Valid @RequestBody UpdateBusinessRequest request) {
    return businesses.update(caller, businessId, request);
  }

  @GetMapping("/{businessId}/members")
  List<Member> members(CurrentUser caller, @PathVariable UUID businessId) {
    return businesses.members(caller, businessId);
  }

  @PostMapping("/{businessId}/invitations")
  ResponseEntity<SentInvitation> invite(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @Valid @RequestBody InviteRequest request) {
    SentInvitation sent = invitations.invite(caller, businessId, request);
    return ResponseEntity.created(
            URI.create("/api/v1/businesses/" + businessId + "/invitations/" + sent.id()))
        .body(sent);
  }

  @GetMapping("/{businessId}/invitations")
  List<SentInvitation> invitations(CurrentUser caller, @PathVariable UUID businessId) {
    return invitations.sent(caller, businessId);
  }

  @DeleteMapping("/{businessId}/invitations/{invitationId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revoke(CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID invitationId) {
    invitations.revoke(caller, businessId, invitationId);
  }
}
