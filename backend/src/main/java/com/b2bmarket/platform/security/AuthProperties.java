package com.b2bmarket.platform.security;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Which tokens the API accepts: Google ID tokens in deployed environments, tokens from the mock
 * OAuth2 server locally and in tests.
 *
 * @param issuerUri the {@code iss} claim expected; also used to discover the signing keys unless
 *     {@code jwkSetUri} is set
 * @param issuerAliases other spellings of the same issuer that are also accepted (Google issues
 *     both {@code https://accounts.google.com} and {@code accounts.google.com})
 * @param jwkSetUri optional direct address of the signing keys, for when the issuer's public
 *     address is not reachable from the server
 * @param audience the OAuth client id the token must be issued for ({@code aud})
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
    @NotBlank String issuerUri,
    List<String> issuerAliases,
    String jwkSetUri,
    @NotBlank String audience) {

  public AuthProperties {
    issuerAliases = issuerAliases == null ? List.of() : List.copyOf(issuerAliases);
  }

  /** The issuer and all its aliases. */
  public List<String> acceptedIssuers() {
    return Stream.concat(Stream.of(issuerUri), issuerAliases.stream()).toList();
  }
}
