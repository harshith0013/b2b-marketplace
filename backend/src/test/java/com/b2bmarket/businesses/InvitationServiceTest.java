package com.b2bmarket.businesses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.businesses.InvitationDtos.InviteRequest;
import com.b2bmarket.businesses.internal.BusinessAccess;
import com.b2bmarket.businesses.internal.BusinessRepository;
import com.b2bmarket.businesses.internal.InvitationEntity;
import com.b2bmarket.businesses.internal.InvitationEntity.Status;
import com.b2bmarket.businesses.internal.InvitationRepository;
import com.b2bmarket.businesses.internal.MembershipRepository;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.users.CurrentUser;
import com.b2bmarket.users.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

class InvitationServiceTest {

  static final Instant NOW = Instant.parse("2026-11-02T06:00:00Z");

  final InvitationRepository invitations = mock(InvitationRepository.class);
  final MembershipRepository memberships = mock(MembershipRepository.class);
  final UserService users = mock(UserService.class);
  final InvitationService service =
      new InvitationService(
          invitations,
          memberships,
          mock(BusinessRepository.class),
          mock(BusinessAccess.class),
          users,
          mock(AuditService.class),
          Clock.fixed(NOW, ZoneOffset.UTC));
  final CurrentUser owner = new CurrentUser(UUID.randomUUID(), "owner@example.com");
  final UUID business = UUID.randomUUID();

  @BeforeEach
  void defaults() {
    when(invitations.save(any())).thenAnswer(returnsFirstArg());
    when(users.findIdByEmail(any())).thenReturn(Optional.empty());
  }

  static void assertStatus(Runnable action, HttpStatus status) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(status));
  }

  @Test
  void normalizesTheEmailAndExpiresAfterFourteenDays() {
    var sent =
        service.invite(owner, business, new InviteRequest(" Ravi@Gmail.com ", BusinessRole.STAFF));

    assertThat(sent.email()).isEqualTo("ravi@gmail.com");
    assertThat(sent.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
  }

  @Test
  void nobodyCanBeInvitedAsOwner() {
    assertStatus(
        () -> service.invite(owner, business, new InviteRequest("a@b.com", BusinessRole.OWNER)),
        HttpStatus.BAD_REQUEST);
    verify(invitations, never()).save(any());
  }

  @Test
  void anOpenInvitationBlocksASecondOne() {
    InvitationEntity open =
        InvitationEntity.create(
            business, "a@b.com", BusinessRole.STAFF, owner.userId(), NOW, NOW.plusSeconds(60));
    when(invitations.findByBusinessIdAndEmailAndStatus(business, "a@b.com", Status.PENDING))
        .thenReturn(Optional.of(open));

    assertStatus(
        () -> service.invite(owner, business, new InviteRequest("a@b.com", BusinessRole.STAFF)),
        HttpStatus.CONFLICT);
  }

  @Test
  void anExpiredInvitationIsClosedAndWrittenBeforeTheReplacementIsInserted() {
    InvitationEntity expired =
        InvitationEntity.create(
            business,
            "a@b.com",
            BusinessRole.STAFF,
            owner.userId(),
            NOW.minus(Duration.ofDays(20)),
            NOW.minus(Duration.ofDays(6)));
    when(invitations.findByBusinessIdAndEmailAndStatus(business, "a@b.com", Status.PENDING))
        .thenReturn(Optional.of(expired));

    service.invite(owner, business, new InviteRequest("a@b.com", BusinessRole.STAFF));

    assertThat(expired.isOpen(NOW)).isFalse();
    InOrder order = inOrder(invitations);
    order.verify(invitations).saveAndFlush(expired);
    order.verify(invitations).save(any());
  }

  @Test
  void acceptingSomeoneElsesInvitationLooksLikeItDoesNotExist() {
    UUID id = UUID.randomUUID();
    when(invitations.findByIdAndEmail(id, owner.email())).thenReturn(Optional.empty());

    assertStatus(() -> service.accept(owner, id), HttpStatus.NOT_FOUND);
    verify(memberships, never()).save(any());
  }
}
