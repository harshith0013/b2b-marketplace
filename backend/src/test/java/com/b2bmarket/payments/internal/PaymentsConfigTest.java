package com.b2bmarket.payments.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PaymentsConfigTest {

  static RazorpayProperties keys(String keyId, int feeBp, boolean allowLive) {
    return new RazorpayProperties(
        "https://api.razorpay.com", keyId, "secret", "", Duration.ofSeconds(5), feeBp, allowLive);
  }

  @Test
  void liveKeysAreRefusedUnlessExplicitlyAllowed() {
    assertThatThrownBy(() -> PaymentsConfig.check(keys("rzp_live_abc", 0, false)))
        .hasMessageContaining("not a test key");
    assertThatCode(() -> PaymentsConfig.check(keys("rzp_live_abc", 0, true)))
        .doesNotThrowAnyException();
    assertThatCode(() -> PaymentsConfig.check(keys("rzp_test_abc", 0, false)))
        .doesNotThrowAnyException();
  }

  @Test
  void noKeysMeansOffAndTheFeeMustBeAShare() {
    assertThat(keys("", 0, false).enabled()).isFalse();
    assertThatCode(() -> PaymentsConfig.check(keys("", 0, false))).doesNotThrowAnyException();
    assertThatThrownBy(() -> PaymentsConfig.check(keys("rzp_test_abc", 10_000, false)))
        .hasMessageContaining("platform-fee-bp");
  }

  @Test
  void secretsNeverAppearInToString() {
    assertThat(keys("rzp_test_abc", 0, false).toString()).doesNotContain("secret");
  }
}
