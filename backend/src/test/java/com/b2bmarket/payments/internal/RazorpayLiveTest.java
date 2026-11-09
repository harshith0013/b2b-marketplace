package com.b2bmarket.payments.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Talks to Razorpay's real API with the test keys from the environment: creates a ₹1 order and
 * reads it back. Test mode only; no money moves and nothing is paid. Run with -Prazorpay-live.
 */
@Tag("razorpay-live")
class RazorpayLiveTest {

  @Test
  void theTestKeysWorkAgainstRazorpay() {
    String keyId = System.getenv("RAZORPAY_KEY_ID");
    String secret = System.getenv("RAZORPAY_KEY_SECRET");
    assumeTrue(keyId != null && secret != null, "No Razorpay keys: skipping");
    assertThat(keyId).as("only test keys here").startsWith(RazorpayProperties.TEST_KEY_PREFIX);
    RazorpayClient client =
        new RazorpayClient(
            new RazorpayProperties(
                "https://api.razorpay.com", keyId, secret, "", Duration.ofSeconds(15), 0, false));

    String receipt = UUID.randomUUID().toString();
    String orderId = client.createOrder(100, receipt, Map.of("purpose", "key check"));

    assertThat(orderId).startsWith("order_");
    assertThat(client.paymentsOfOrder(orderId)).isEmpty();
  }
}
