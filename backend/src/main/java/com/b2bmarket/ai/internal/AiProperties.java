package com.b2bmarket.ai.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the search assistant. A blank api key turns the assistant off (questions go to
 * normal search).
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    String baseUrl,
    String apiKey,
    String model,
    String reasoningEffort,
    Duration timeout,
    int dailyRequestLimit,
    int visitorPerMinute,
    int visitorPerDay,
    int logRetentionDays) {

  public boolean enabled() {
    return apiKey != null && !apiKey.isBlank();
  }
}
