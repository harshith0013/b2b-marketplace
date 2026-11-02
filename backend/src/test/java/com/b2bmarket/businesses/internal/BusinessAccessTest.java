package com.b2bmarket.businesses.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.platform.ApiException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BusinessAccessTest {

  final MembershipRepository memberships = mock(MembershipRepository.class);
  final BusinessAccess access = new BusinessAccess(memberships);
  final UUID business = UUID.randomUUID();
  final UUID user = UUID.randomUUID();

  void memberAs(BusinessRole role) {
    when(memberships.findByBusinessIdAndUserId(business, user))
        .thenReturn(Optional.of(MembershipEntity.create(user, business, role, Instant.EPOCH)));
  }

  @Test
  void nonMembersGetNotFoundSoBusinessIdsCannotBeProbed() {
    when(memberships.findByBusinessIdAndUserId(business, user)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> access.require(business, user, BusinessRole.ANY))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void membersWithoutAPermittedRoleGetForbidden() {
    memberAs(BusinessRole.STAFF);

    assertThatThrownBy(() -> access.require(business, user, BusinessRole.OWNER_OR_MANAGER))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void membersWithAPermittedRolePass() {
    memberAs(BusinessRole.MANAGER);

    assertThat(access.require(business, user, BusinessRole.OWNER_OR_MANAGER).role())
        .isEqualTo(BusinessRole.MANAGER);
  }
}
