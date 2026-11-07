package com.b2bmarket.users;

import com.b2bmarket.platform.ApiException;
import com.b2bmarket.users.internal.PlatformRoleEntity;
import com.b2bmarket.users.internal.PlatformRoleRepository;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Who holds platform-wide roles (admins and reviewers). */
@Service
public class PlatformRoles {

  static final String NOT_A_REVIEWER = "Only platform reviewers can do this.";

  private final PlatformRoleRepository roles;

  PlatformRoles(PlatformRoleRepository roles) {
    this.roles = roles;
  }

  @Transactional(readOnly = true)
  public Set<PlatformRole> of(UUID userId) {
    Set<PlatformRole> held =
        roles.findByKeyUserId(userId).stream()
            .map(PlatformRoleEntity::role)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(PlatformRole.class)));
    return Set.copyOf(held);
  }

  /** 403 unless the person may review listings. */
  @Transactional(readOnly = true)
  public void requireReviewer(CurrentUser caller) {
    if (of(caller.userId()).stream().noneMatch(PlatformRole.CAN_REVIEW::contains)) {
      throw ApiException.forbidden(NOT_A_REVIEWER);
    }
  }
}
