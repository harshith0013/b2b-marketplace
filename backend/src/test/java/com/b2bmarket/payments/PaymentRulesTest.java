package com.b2bmarket.payments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentRulesTest {

  @Test
  void checkoutSignatureIsHmacOfOrderAndPaymentWithTheKeySecret() {
    String sig = Signatures.hmacHex("secret", "order_1|pay_1");
    assertThat(sig).hasSize(64).matches("[0-9a-f]+");
    assertThat(Signatures.checkoutValid("order_1", "pay_1", sig, "secret")).isTrue();
    assertThat(Signatures.checkoutValid("order_2", "pay_1", sig, "secret")).isFalse();
    assertThat(Signatures.checkoutValid("order_1", "pay_1", sig, "other")).isFalse();
    assertThat(Signatures.checkoutValid("order_1", "pay_1", null, "secret")).isFalse();
  }

  @Test
  void webhookSignatureCoversTheExactBody() {
    String body = "{\"event\":\"payment.captured\"}";
    String sig = Signatures.hmacHex("whsec", body);
    assertThat(Signatures.webhookValid(body, sig, "whsec")).isTrue();
    assertThat(Signatures.webhookValid(body + " ", sig, "whsec")).isFalse();
  }

  @Test
  void theFeeIsRoundedDownSoTheSellerNeverGetsLess() {
    assertThat(PaymentService.platformFee(318_600, 0)).isZero();
    assertThat(PaymentService.platformFee(318_600, 150)).isEqualTo(4_779); // 1.5% of ₹3,186
    assertThat(PaymentService.platformFee(99, 100)).isZero();
  }

  @Test
  void razorpayActivationStatusesMapToOurs() {
    assertThat(PayoutService.status("activated")).isEqualTo("ACTIVATED");
    assertThat(PayoutService.status("needs_clarification")).isEqualTo("NEEDS_CLARIFICATION");
    assertThat(PayoutService.status("under_review")).isEqualTo("UNDER_REVIEW");
    assertThat(PayoutService.status("suspended")).isEqualTo("SUSPENDED");
    assertThat(PayoutService.status("requested")).isEqualTo("REQUESTED");
    assertThat(PayoutService.status(null)).isEqualTo("REQUESTED");
  }
}
