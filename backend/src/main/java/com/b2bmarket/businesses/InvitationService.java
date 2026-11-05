package com.b2bmarket.businesses;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.businesses.InvitationDtos.InviteRequest;
import com.b2bmarket.businesses.InvitationDtos.ReceivedInvitation;
import com.b2bmarket.businesses.InvitationDtos.SentInvitation;
import com.b2bmarket.businesses.internal.BusinessAccess;
import com.b2bmarket.businesses.internal.BusinessEntity;
import com.b2bmarket.businesses.internal.BusinessRepository;
import com.b2bmarket.businesses.internal.InvitationEntity;
import com.b2bmarket.businesses.internal.InvitationEntity.Status;
import com.b2bmarket.businesses.internal.InvitationRepository;
import com.b2bmarket.businesses.internal.MembershipEntity;
import com.b2bmarket.businesses.internal.MembershipRepository;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.users.CurrentUser;
import com.b2bmarket.users.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inviting people into a business by email. Owners invite and revoke; owners and managers see open
 * invitations; the invitee accepts after signing in with the invited email.
 */
@Service
public class InvitationService {

  static final Duration VALIDITY = Duration.ofDays(14);
  static final String TARGET_TYPE = "invitation";
  static final String NOT_FOUND = "Invitation not found or no longer valid.";

  private final InvitationRepository invitations;
  private final MembershipRepository memberships;
  private final BusinessRepository businesses;
  private final BusinessAccess access;
  private final UserService users;
  private final AuditService audit;
  private final Clock clock;

  InvitationService(
      InvitationRepository invitations,
      MembershipRepository memberships,
      BusinessRepository businesses,
      BusinessAccess access,
      UserService users,
      AuditService audit,
      Clock clock) {
    this.invitations = invitations;
    this.memberships = memberships;
    this.businesses = businesses;
    this.access = access;
    this.users = users;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public SentInvitation invite(CurrentUser caller, UUID businessId, InviteRequest request) {
    access.require(businessId, caller.userId(), BusinessRole.OWNER_ONLY);
    if (request.role() == BusinessRole.OWNER) {
      throw ApiException.badRequest("Invite people as MANAGER or STAFF.");
    }
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    Instant now = Instant.now(clock);
    rejectIfAlreadyMember(businessId, email);
    replaceExpiredOrReject(businessId, email, now);

    InvitationEntity invitation =
        invitations.save(
            InvitationEntity.create(
                businessId, email, request.role(), caller.userId(), now, now.plus(VALIDITY)));
    record("invitation.created", caller, invitation, Map.of("role", request.role().name()));
    return toSent(invitation);
  }

  /** Open invitations of a business. Owners and managers only. */
  @Transactional(readOnly = true)
  public List<SentInvitation> sent(CurrentUser caller, UUID businessId) {
    access.require(businessId, caller.userId(), BusinessRole.OWNER_OR_MANAGER);
    Instant now = Instant.now(clock);
    return invitations
        .findByBusinessIdAndStatusOrderByCreatedAtAsc(businessId, Status.PENDING)
        .stream()
        .filter(i -> i.isOpen(now))
        .map(InvitationService::toSent)
        .toList();
  }

  @Transactional
  public void revoke(CurrentUser caller, UUID businessId, UUID invitationId) {
    access.require(businessId, caller.userId(), BusinessRole.OWNER_ONLY);
    Instant now = Instant.now(clock);
    InvitationEntity invitation =
        invitations
            .findByIdAndBusinessId(invitationId, businessId)
            .filter(i -> i.isOpen(now))
            .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    invitation.revoke(now);
    record("invitation.revoked", caller, invitation, Map.of());
  }

  /** Open invitations addressed to the signed-in person's verified email. */
  @Transactional(readOnly = true)
  public List<ReceivedInvitation> received(CurrentUser caller) {
    Instant now = Instant.now(clock);
    List<InvitationEntity> open =
        invitations.findByEmailAndStatusOrderByCreatedAtAsc(caller.email(), Status.PENDING).stream()
            .filter(i -> i.isOpen(now))
            .toList();
    Map<UUID, BusinessEntity> byId =
        businesses.findAllById(open.stream().map(InvitationEntity::businessId).toList()).stream()
            .collect(Collectors.toMap(BusinessEntity::id, Function.identity()));
    return open.stream()
        .filter(i -> byId.containsKey(i.businessId()))
        .map(
            i ->
                new ReceivedInvitation(
                    i.id(),
                    i.businessId(),
                    byId.get(i.businessId()).displayName(),
                    i.role(),
                    i.expiresAt()))
        .toList();
  }

  /**
   * Accepts an invitation addressed to the caller's own email, making them a member. Anyone else
   * gets 404, as if the invitation did not exist.
   */
  @Transactional
  public void accept(CurrentUser caller, UUID invitationId) {
    Instant now = Instant.now(clock);
    InvitationEntity invitation =
        invitations
            .findByIdAndEmail(invitationId, caller.email())
            .filter(i -> i.isOpen(now))
            .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    if (memberships
        .findByBusinessIdAndUserId(invitation.businessId(), caller.userId())
        .isPresent()) {
      throw ApiException.conflict("You are already a member of this business.");
    }
    memberships.save(
        MembershipEntity.create(caller.userId(), invitation.businessId(), invitation.role(), now));
    invitation.accept(caller.userId(), now);
    record("invitation.accepted", caller, invitation, Map.of("role", invitation.role().name()));
  }

  private void rejectIfAlreadyMember(UUID businessId, String email) {
    users
        .findIdByEmail(email)
        .flatMap(userId -> memberships.findByBusinessIdAndUserId(businessId, userId))
        .ifPresent(
            m -> {
              throw ApiException.conflict("This person is already a member of the business.");
            });
  }

  private void replaceExpiredOrReject(UUID businessId, String email, Instant now) {
    invitations
        .findByBusinessIdAndEmailAndStatus(businessId, email, Status.PENDING)
        .ifPresent(
            existing -> {
              if (existing.isOpen(now)) {
                throw ApiException.conflict("This person already has an open invitation.");
              }
              existing.expire(now);
              // Write now: Hibernate inserts before it updates, and the new invitation would
              // otherwise clash with this one on the "one pending invitation" index.
              invitations.saveAndFlush(existing);
            });
  }

  private void record(
      String action, CurrentUser caller, InvitationEntity invitation, Map<String, Object> details) {
    audit.record(
        new AuditEntry(
            action,
            caller.userId(),
            invitation.businessId(),
            TARGET_TYPE,
            invitation.id(),
            details));
  }

  private static SentInvitation toSent(InvitationEntity i) {
    return new SentInvitation(i.id(), i.email(), i.role(), i.createdAt(), i.expiresAt());
  }
}
