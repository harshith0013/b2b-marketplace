package com.b2bmarket.platform.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deny-by-default security. Phase 1 exposes only health and system information. Phase 3 adds the
 * OAuth 2.0 resource server (Amazon Cognito JWTs) and per-endpoint authorization rules.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

  static final String[] PUBLIC_GET_ENDPOINTS = {
    "/actuator/health", "/actuator/health/**", "/actuator/info", "/api/v1/system/info"
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
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS)
                    .permitAll()
                    .anyRequest()
                    .denyAll());
    return http.build();
  }

  /**
   * An empty user store. Prevents Spring Boot from generating a default user and logging its
   * password. Removed in Phase 3 when JWT authentication is configured.
   */
  @Bean
  UserDetailsService noLocalUsers() {
    return new InMemoryUserDetailsManager();
  }
}
