package com.b2bmarket.payments.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay settings. Blank keys turn online payment off (sellers cannot offer it). Live keys are
 * refused unless allowLiveKeys is set: development and the pilot run in test mode.
 *
 * @param platformFeeBp the platform's share of each online order, in basis points (100 = 1%)
 */
@ConfigurationProperties(prefix = "app.razorpay")
public record RazorpayProperties(
    String baseUrl,
    String keyId,
    String keySecret,
    String webhookSecret,
    Duration timeout,
    int platformFeeBp,
    boolean allowLiveKeys) {

  public static final String TEST_KEY_PREFIX = "rzp_test_";

  public boolean enabled() {
    return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
  }

  public boolean webhookEnabled() {
    return webhookSecret != null && !webhookSecret.isBlank();
  }

  @Override
  public String toString() {
    // Never print the secrets, even by accident.
    return "RazorpayProperties[baseUrl=" + baseUrl + ", keyId=" + keyId + "]";
  }
}
