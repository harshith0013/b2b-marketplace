package com.b2bmarket.businesses;

import com.b2bmarket.businesses.internal.BusinessAccess;
import com.b2bmarket.platform.TenantScope;
import com.b2bmarket.users.CurrentUser;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The entry point for every operation on a business's own data in other modules: checks the
 * caller's membership and role (404 for non-members, 403 for the wrong role), then switches the
 * transaction to that business's rows only (Row-Level Security).
 */
@Component
public class TenantAccess {

  private final BusinessAccess access;
  private final TenantScope scope;

  TenantAccess(BusinessAccess access, TenantScope scope) {
    this.access = access;
    this.scope = scope;
  }

  /**
   * Membership and role check only, for writes to shared tables (such as the product catalogue)
   * that are not scoped to one business.
   */
  public BusinessRole check(CurrentUser caller, UUID businessId, Set<BusinessRole> allowedRoles) {
    return access.require(businessId, caller.userId(), allowedRoles).role();
  }

  /** Must run inside the caller's transaction, so the database scope covers its queries. */
  @Transactional(propagation = Propagation.MANDATORY)
  public BusinessRole enter(CurrentUser caller, UUID businessId, Set<BusinessRole> allowedRoles) {
    BusinessRole role = access.require(businessId, caller.userId(), allowedRoles).role();
    scope.enter(businessId);
    return role;
  }
}
