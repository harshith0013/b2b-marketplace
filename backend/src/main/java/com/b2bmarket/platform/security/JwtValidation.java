package com.b2bmarket.platform.security;

import java.time.Clock;
import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/** The checks every access token must pass after its signature has been verified. */
final class JwtValidation {

  static final String INVALID_TOKEN = "invalid_token";

  private JwtValidation() {}

  static OAuth2TokenValidator<Jwt> validator(String issuerUri, String audience, Clock clock) {
    JwtTimestampValidator timestamps = new JwtTimestampValidator();
    timestamps.setClock(clock);
    return new DelegatingOAuth2TokenValidator<>(
        timestamps, new JwtIssuerValidator(issuerUri), audienceValidator(audience));
  }

  /**
   * Standard tokens name the API in {@code aud}. Cognito access tokens have no {@code aud} and name
   * the app client in {@code client_id} instead, so either is accepted.
   */
  static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
    return jwt -> {
      List<String> aud = jwt.getAudience();
      boolean matches =
          (aud != null && aud.contains(audience))
              || audience.equals(jwt.getClaimAsString("client_id"));
      return matches
          ? OAuth2TokenValidatorResult.success()
          : OAuth2TokenValidatorResult.failure(
              new OAuth2Error(INVALID_TOKEN, "The token was not issued for this API.", null));
    };
  }
}
