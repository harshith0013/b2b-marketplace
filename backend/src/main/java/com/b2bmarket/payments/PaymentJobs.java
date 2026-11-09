package com.b2bmarket.payments;

import com.b2bmarket.orders.OrderEvents;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Moves payments on: right after an online order changes (once that change is committed), and every
 * 10 minutes for anything still open, such as a buyer who closed the browser mid-payment or a step
 * that failed because Razorpay could not be reached.
 */
@Component
public class PaymentJobs {

  static final int BATCH = 100;

  private final PaymentService payments;

  PaymentJobs(PaymentService payments) {
    this.payments = payments;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void onOrderChanged(OrderEvents.OnlineOrderChanged event) {
    payments.settle(event.orderId());
  }

  @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
  public void settleOpenPayments() {
    List<UUID> open = payments.openPayments(BATCH);
    open.forEach(payments::settle);
  }
}
