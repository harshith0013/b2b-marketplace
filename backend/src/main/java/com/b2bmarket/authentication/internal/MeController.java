package com.b2bmarket.authentication.internal;

import com.b2bmarket.businesses.BusinessDtos.Membership;
import com.b2bmarket.businesses.BusinessService;
import com.b2bmarket.businesses.InvitationDtos.ReceivedInvitation;
import com.b2bmarket.businesses.InvitationService;
import com.b2bmarket.users.CurrentUser;
import com.b2bmarket.users.UserProfile;
import com.b2bmarket.users.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person: their profile, their businesses and invitations addressed to them. */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

  record MeResponse(UserProfile profile, List<Membership> businesses) {}

  /** Both fields optional; only the fields present are changed. A blank name clears it. */
  record UpdateProfileRequest(
      @Size(max = 100) String displayName,
      @Pattern(regexp = "en|te|hi", message = "must be en, te or hi") String preferredLocale) {}

  private final UserService users;
  private final BusinessService businesses;
  private final InvitationService invitations;

  MeController(UserService users, BusinessService businesses, InvitationService invitations) {
    this.users = users;
    this.businesses = businesses;
    this.invitations = invitations;
  }

  @GetMapping
  MeResponse me(CurrentUser caller) {
    return new MeResponse(
        users.profile(caller.userId()), businesses.membershipsOf(caller.userId()));
  }

  @PatchMapping
  UserProfile updateProfile(CurrentUser caller, @Valid @RequestBody UpdateProfileRequest request) {
    return users.updateProfile(caller.userId(), request.displayName(), request.preferredLocale());
  }

  @GetMapping("/invitations")
  List<ReceivedInvitation> invitations(CurrentUser caller) {
    return invitations.received(caller);
  }

  @PostMapping("/invitations/{invitationId}/accept")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void accept(CurrentUser caller, @PathVariable UUID invitationId) {
    invitations.accept(caller, invitationId);
  }
}
