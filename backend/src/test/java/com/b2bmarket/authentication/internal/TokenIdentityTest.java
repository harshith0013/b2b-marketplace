package com.b2bmarket.authentication.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b2bmarket.platform.ApiException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenIdentityTest {

  static Jwt token(Consumer<Jwt.Builder> customize) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("sub-1")
            .claim("email", "asha@example.com")
            .claim("email_verified", true);
    customize.accept(builder);
    return builder.build();
  }

  @Test
  void readsSubjectAndVerifiedEmail() {
    assertThat(TokenIdentity.from(token(b -> {})))
        .isEqualTo(new TokenIdentity("sub-1", "asha@example.com"));
  }

  @Test
  void acceptsEmailVerifiedSentAsAString() {
    assertThat(TokenIdentity.from(token(b -> b.claim("email_verified", "true"))).email())
        .isEqualTo("asha@example.com");
  }

  @Test
  void refusesAnUnverifiedEmail() {
    assertForbidden(token(b -> b.claim("email_verified", false)));
    assertForbidden(token(b -> b.claims(c -> c.remove("email_verified"))));
  }

  @Test
  void refusesATokenWithoutEmail() {
    assertForbidden(token(b -> b.claims(c -> c.remove("email"))));
  }

  private static void assertForbidden(Jwt jwt) {
    assertThatThrownBy(() -> TokenIdentity.from(jwt))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
  }
}
