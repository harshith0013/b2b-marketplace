package com.b2bmarket.platform.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A single injectable clock so time-dependent rules (expiry, shelf life) are testable. */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
