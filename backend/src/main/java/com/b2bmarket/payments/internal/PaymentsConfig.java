package com.b2bmarket.payments.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Checks the Razorpay settings at startup: no live keys while the platform runs in test mode. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RazorpayProperties.class)
class PaymentsConfig {

  private static final Logger log = LoggerFactory.getLogger(PaymentsConfig.class);

  PaymentsConfig(RazorpayProperties properties) {
    check(properties);
  }

  static void check(RazorpayProperties properties) {
    if (properties.platformFeeBp() < 0 || properties.platformFeeBp() >= 10_000) {
      throw new IllegalStateException("app.razorpay.platform-fee-bp must be 0 to 9999");
    }
    if (!properties.enabled()) {
      log.info("Razorpay keys not set: online payment is off");
      return;
    }
    if (!properties.keyId().startsWith(RazorpayProperties.TEST_KEY_PREFIX)
        && !properties.allowLiveKeys()) {
      throw new IllegalStateException(
          "Refusing to start with a Razorpay key that is not a test key (rzp_test_...)");
    }
    log.info(
        "Online payment on (Razorpay {} mode)",
        properties.keyId().startsWith(RazorpayProperties.TEST_KEY_PREFIX) ? "test" : "LIVE");
  }
}
