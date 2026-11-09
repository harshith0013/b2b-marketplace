package com.b2bmarket.payments.internal;

import com.b2bmarket.payments.PaymentDtos.Payment;
import com.b2bmarket.payments.PaymentDtos.PaymentConfirm;
import com.b2bmarket.payments.PaymentDtos.PaymentStart;
import com.b2bmarket.payments.PaymentDtos.PayoutAccount;
import com.b2bmarket.payments.PaymentDtos.PayoutRequest;
import com.b2bmarket.payments.PaymentService;
import com.b2bmarket.payments.PayoutService;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.users.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PaymentController {

  /** Razorpay's webhook bodies are small; anything larger is not from Razorpay. */
  static final int MAX_WEBHOOK_BYTES = 256 * 1024;

  private final PaymentService payments;
  private final PayoutService payouts;

  PaymentController(PaymentService payments, PayoutService payouts) {
    this.payments = payments;
    this.payouts = payouts;
  }

  @GetMapping("/api/v1/businesses/{businessId}/payout-account")
  PayoutAccount payoutAccount(CurrentUser caller, @PathVariable UUID businessId) {
    return payouts.get(caller, businessId);
  }

  @PutMapping("/api/v1/businesses/{businessId}/payout-account")
  PayoutAccount setUpPayouts(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @Valid @RequestBody PayoutRequest request) {
    return payouts.setUp(caller, businessId, request);
  }

  @PostMapping("/api/v1/businesses/{businessId}/orders/{orderId}/payment")
  PaymentStart startPayment(
      CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID orderId) {
    return payments.start(caller, businessId, orderId);
  }

  @PostMapping("/api/v1/businesses/{businessId}/orders/{orderId}/payment/confirm")
  Payment confirmPayment(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @PathVariable UUID orderId,
      @Valid @RequestBody PaymentConfirm request) {
    return payments.confirm(caller, businessId, orderId, request);
  }

  @GetMapping("/api/v1/businesses/{businessId}/orders/{orderId}/payment")
  Payment payment(CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID orderId) {
    return payments
        .get(caller, businessId, orderId)
        .orElseThrow(() -> ApiException.notFound("No payment for this order."));
  }

  /** Public, but only accepted with a valid Razorpay signature (see SecurityConfig). */
  @PostMapping("/api/v1/payments/razorpay-webhook")
  @ResponseStatus(HttpStatus.OK)
  void webhook(
      @RequestBody String body,
      @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
      @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
    if (body.length() > MAX_WEBHOOK_BYTES) {
      throw ApiException.badRequest("Too large.");
    }
    payments.webhook(body, signature, eventId);
  }
}
