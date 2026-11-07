package com.b2bmarket.users;

import java.util.EnumSet;
import java.util.Set;

/** Platform-wide roles, separate from a person's role inside a business. */
public enum PlatformRole {
  /** Reviews listings and, later, manages reviewers and disputes. */
  ADMIN,
  /** Reviews listings only. */
  REVIEWER;

  public static final Set<PlatformRole> CAN_REVIEW = EnumSet.of(ADMIN, REVIEWER);
}
