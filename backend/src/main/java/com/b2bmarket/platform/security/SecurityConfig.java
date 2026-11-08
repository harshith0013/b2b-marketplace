package com.b2bmarket.platform.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deny-by-default security. Health, system information, the category tree and published offers are
 * public (read-only); every other {@code /api/v1/**} endpoint needs a valid access token (see
 * {@link JwtDecoderConfig}); everything else is refused. Which business a caller may act on is
 * checked in the services, per request.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

  static final String[] PUBLIC_GET_ENDPOINTS = {
    "/actuator/health",
    "/actuator/health/**",
    "/actuator/info",
    "/api/v1/system/info",
    // Buyers browse without signing in (Phase 6). Only GET; queries run as the read-only
    // marketplace_public database role.
    "/api/v1/categories",
    "/api/v1/offers",
    "/api/v1/offers/**"
  };

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable) // Stateless bearer-token API; no cookies are read.
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .headers(
            h ->
                h.contentSecurityPolicy(
                    csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS)
                    .permitAll()
                    // The search assistant: public like search, limited per visitor and per day.
                    .requestMatchers(HttpMethod.POST, "/api/v1/offers/interpret")
                    .permitAll()
                    .requestMatchers("/api/v1/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll());
    return http.build();
  }
}
