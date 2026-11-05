package com.b2bmarket.platform.security;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/** The checks every token must pass after its signature has been verified. */
final class JwtValidation {

  static final String INVALID_TOKEN = "invalid_token";

  private JwtValidation() {}

  static OAuth2TokenValidator<Jwt> validator(
      Collection<String> acceptedIssuers, String audience, Clock clock) {
    JwtTimestampValidator timestamps = new JwtTimestampValidator();
    timestamps.setClock(clock);
    return new DelegatingOAuth2TokenValidator<>(
        timestamps, issuerValidator(acceptedIssuers), audienceValidator(audience));
  }

  static OAuth2TokenValidator<Jwt> issuerValidator(Collection<String> acceptedIssuers) {
    Set<String> accepted = Set.copyOf(acceptedIssuers);
    return jwt ->
        accepted.contains(jwt.getClaimAsString("iss"))
            ? OAuth2TokenValidatorResult.success()
            : failure("The token was not issued by a trusted sign-in provider.");
  }

  static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
    return jwt -> {
      List<String> aud = jwt.getAudience();
      return aud != null && aud.contains(audience)
          ? OAuth2TokenValidatorResult.success()
          : failure("The token was not issued for this application.");
    };
  }

  private static OAuth2TokenValidatorResult failure(String description) {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error(INVALID_TOKEN, description, null));
  }
}
