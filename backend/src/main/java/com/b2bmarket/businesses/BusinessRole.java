package com.b2bmarket.businesses;

import java.util.EnumSet;
import java.util.Set;

/** A person's role inside one business. Platform-wide roles (admin, reviewer) come later. */
public enum BusinessRole {
  OWNER,
  MANAGER,
  STAFF;

  public static final Set<BusinessRole> ANY = EnumSet.allOf(BusinessRole.class);
  public static final Set<BusinessRole> OWNER_OR_MANAGER = EnumSet.of(OWNER, MANAGER);
  public static final Set<BusinessRole> OWNER_ONLY = EnumSet.of(OWNER);
}
