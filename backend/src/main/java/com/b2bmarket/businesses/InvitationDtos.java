package com.b2bmarket.businesses;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response bodies for business invitations. */
public final class InvitationDtos {

  private InvitationDtos() {}

  /** Invite someone by the email they sign in with. Role must be MANAGER or STAFF. */
  public record InviteRequest(
      @NotBlank @Email @Size(max = 320) String email, @NotNull BusinessRole role) {}

  /** An open invitation, as its business's owners and managers see it. */
  public record SentInvitation(
      UUID id, String email, BusinessRole role, Instant invitedAt, Instant expiresAt) {}

  /** An open invitation addressed to the signed-in person. */
  public record ReceivedInvitation(
      UUID id, UUID businessId, String businessName, BusinessRole role, Instant expiresAt) {}
}
