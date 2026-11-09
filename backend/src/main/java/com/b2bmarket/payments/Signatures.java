package com.b2bmarket.payments;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Razorpay's HMAC-SHA256 signatures, compared in constant time. */
final class Signatures {

  private Signatures() {}

  static String hmacHex(String secret, String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Checkout: HMAC of "our Razorpay order id|payment id" with the key secret. */
  static boolean checkoutValid(
      String razorpayOrderId, String paymentId, String signature, String keySecret) {
    return matches(hmacHex(keySecret, razorpayOrderId + "|" + paymentId), signature);
  }

  /** Webhook: HMAC of the raw request body with the webhook secret. */
  static boolean webhookValid(String rawBody, String signature, String webhookSecret) {
    return matches(hmacHex(webhookSecret, rawBody), signature);
  }

  private static boolean matches(String expected, String given) {
    if (given == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
  }
}
