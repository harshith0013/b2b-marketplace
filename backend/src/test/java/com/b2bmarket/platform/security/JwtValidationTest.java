package com.b2bmarket.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtValidationTest {

  static final String ISSUER = "https://issuer.example/pool";
  static final String AUDIENCE = "b2b-marketplace-web";
  static final Instant NOW = Instant.parse("2026-11-02T06:00:00Z");

  final OAuth2TokenValidator<Jwt> validator =
      JwtValidation.validator(List.of(ISSUER), AUDIENCE, Clock.fixed(NOW, ZoneOffset.UTC));

  static Jwt token(Consumer<Jwt.Builder> customize) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-1")
            .issuer(ISSUER)
            .audience(List.of(AUDIENCE))
            .issuedAt(NOW.minusSeconds(60))
            .expiresAt(NOW.plus(Duration.ofMinutes(30)));
    customize.accept(builder);
    return builder.build();
  }

  @Test
  void acceptsAValidToken() {
    assertThat(validator.validate(token(b -> {})).hasErrors()).isFalse();
  }

  @Test
  void rejectsAnExpiredTokenUsingTheInjectedClock() {
    Jwt expired =
        token(
            b ->
                b.issuedAt(NOW.minus(Duration.ofHours(2)))
                    .expiresAt(NOW.minus(Duration.ofHours(1))));
    assertThat(validator.validate(expired).hasErrors()).isTrue();
  }

  @Test
  void rejectsATokenFromAnotherIssuer() {
    assertThat(validator.validate(token(b -> b.issuer("https://evil.example"))).hasErrors())
        .isTrue();
  }

  @Test
  void rejectsATokenForAnotherAudience() {
    assertThat(validator.validate(token(b -> b.audience(List.of("another-api")))).hasErrors())
        .isTrue();
  }

  @Test
  void acceptsAnyConfiguredSpellingOfTheIssuer() {
    OAuth2TokenValidator<Jwt> google =
        JwtValidation.validator(
            List.of("https://accounts.google.com", "accounts.google.com"),
            AUDIENCE,
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(google.validate(token(b -> b.issuer("accounts.google.com"))).hasErrors()).isFalse();
    assertThat(google.validate(token(b -> b.issuer("https://accounts.google.com"))).hasErrors())
        .isFalse();
    assertThat(google.validate(token(b -> b.issuer("https://accounts.google.co"))).hasErrors())
        .isTrue();
  }

  @Test
  void rejectsATokenWithoutAudience() {
    Jwt none = token(b -> b.claims(c -> c.remove("aud")));
    assertThat(validator.validate(none).hasErrors()).isTrue();
  }
}
