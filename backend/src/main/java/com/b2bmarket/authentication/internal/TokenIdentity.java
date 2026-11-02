package com.b2bmarket.authentication.internal;

import com.b2bmarket.platform.ApiException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who a verified access token says the caller is. People are identified by email for now (email
 * one-time-code sign-in); the email must be verified by the identity provider.
 *
 * <p>Amazon Cognito access tokens carry {@code email} and {@code email_verified} only when a
 * pre-token-generation trigger adds them; see docs/cognito-setup.md.
 */
record TokenIdentity(String subject, String email) {

  static final String UNVERIFIED_EMAIL = "Sign in with a verified email address to continue.";

  static TokenIdentity from(Jwt jwt) {
    String subject = jwt.getSubject();
    String email = jwt.getClaimAsString("email");
    if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
      throw ApiException.forbidden(UNVERIFIED_EMAIL);
    }
    if (!isTrue(jwt.getClaim("email_verified"))) {
      throw ApiException.forbidden(UNVERIFIED_EMAIL);
    }
    return new TokenIdentity(subject, email);
  }

  /** Providers send this claim as a JSON boolean or as the string "true". */
  private static boolean isTrue(Object claim) {
    return Boolean.TRUE.equals(claim) || "true".equals(claim);
  }
}
