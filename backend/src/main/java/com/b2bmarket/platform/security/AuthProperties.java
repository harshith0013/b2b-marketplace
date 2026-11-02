package com.b2bmarket.platform.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Which access tokens the API accepts.
 *
 * @param issuerUri the exact {@code iss} claim expected; also used to discover the signing keys
 *     unless {@code jwkSetUri} is set
 * @param jwkSetUri optional direct address of the signing keys, for when the issuer's public
 *     address is not reachable from the server (for example inside Docker Compose)
 * @param audience the API identifier the token must be issued for ({@code aud} or Cognito's {@code
 *     client_id})
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
    @NotBlank String issuerUri, String jwkSetUri, @NotBlank String audience) {}
