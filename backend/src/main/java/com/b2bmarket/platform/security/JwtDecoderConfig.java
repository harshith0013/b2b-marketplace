package com.b2bmarket.platform.security;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

/**
 * Verifies tokens from the configured sign-in provider: Google in deployed environments, the mock
 * OAuth2 server locally and in tests.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class JwtDecoderConfig {

  @Bean
  JwtDecoder jwtDecoder(AuthProperties auth, Clock clock) {
    // Built on first use, so the API still starts (and reports health) if the provider is briefly
    // unreachable.
    return new SupplierJwtDecoder(
        () -> {
          NimbusJwtDecoder decoder =
              auth.jwkSetUri() == null || auth.jwkSetUri().isBlank()
                  ? NimbusJwtDecoder.withIssuerLocation(auth.issuerUri()).build()
                  : NimbusJwtDecoder.withJwkSetUri(auth.jwkSetUri()).build();
          decoder.setJwtValidator(
              JwtValidation.validator(auth.acceptedIssuers(), auth.audience(), clock));
          return decoder;
        });
  }
}
