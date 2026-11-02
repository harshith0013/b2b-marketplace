package com.b2bmarket.businesses.internal;

import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.platform.ApiException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The tenant boundary. Every operation on a business goes through here first.
 *
 * <ul>
 *   <li>Not a member: 404, exactly as if the business did not exist, so ids cannot be probed.
 *   <li>Member without a permitted role: 403.
 * </ul>
 */
@Component
public class BusinessAccess {

  static final String NOT_FOUND = "Business not found.";
  static final String NOT_PERMITTED = "Your role in this business does not allow this.";

  private final MembershipRepository memberships;

  BusinessAccess(MembershipRepository memberships) {
    this.memberships = memberships;
  }

  public MembershipEntity require(UUID businessId, UUID userId, Set<BusinessRole> allowedRoles) {
    MembershipEntity membership =
        memberships
            .findByBusinessIdAndUserId(businessId, userId)
            .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    if (!allowedRoles.contains(membership.role())) {
      throw ApiException.forbidden(NOT_PERMITTED);
    }
    return membership;
  }
}
