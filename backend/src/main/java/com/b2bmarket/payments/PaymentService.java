package com.b2bmarket.payments;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.businesses.TenantAccess;
import com.b2bmarket.orders.OrderService;
import com.b2bmarket.orders.OrderService.PaymentFacts;
import com.b2bmarket.payments.PaymentDtos.Payment;
import com.b2bmarket.payments.PaymentDtos.PaymentConfirm;
import com.b2bmarket.payments.PaymentDtos.PaymentStart;
import com.b2bmarket.payments.internal.PaymentRecords;
import com.b2bmarket.payments.internal.PaymentRecords.PaymentRow;
import com.b2bmarket.payments.internal.RazorpayClient;
import com.b2bmarket.payments.internal.RazorpayClient.RazorpayException;
import com.b2bmarket.payments.internal.RazorpayClient.RzPayment;
import com.b2bmarket.payments.internal.RazorpayClient.RzTransfer;
import com.b2bmarket.payments.internal.RazorpayProperties;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.TenantScope;
import com.b2bmarket.users.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Online payment of an order through Razorpay. The buyer pays the platform; once captured, the
 * seller's share is transferred on hold to their linked account and released when the goods are
 * handed over. If the order ends without that, the transfer is reversed and the buyer refunded.
 *
 * <p>{@link #settle} moves a payment on by whatever steps are due. Each step runs in its own
 * transaction with the payment row locked, and looks at Razorpay first, so repeating a step after a
 * lost answer never charges, transfers or refunds twice. The webhook, the confirm call, order
 * changes and a job all call it.
 */
@Service
public class PaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
  static final int BASIS_POINTS = 10_000;

  private final PaymentRecords records;
  private final RazorpayClient razorpay;
  private final RazorpayProperties properties;
  private final PayoutService payouts;
  private final OrderService orders;
  private final TenantAccess tenants;
  private final TenantScope scope;
  private final AuditService audit;
  private final TransactionTemplate transaction;
  private final TransactionTemplate ownTransaction;
  private final Clock clock;
  private final JsonMapper json = JsonMapper.builder().build();

  PaymentService(
      PaymentRecords records,
      RazorpayClient razorpay,
      RazorpayProperties properties,
      PayoutService payouts,
      OrderService orders,
      TenantAccess tenants,
      TenantScope scope,
      AuditService audit,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.records = records;
    this.razorpay = razorpay;
    this.properties = properties;
    this.payouts = payouts;
    this.orders = orders;
    this.tenants = tenants;
    this.scope = scope;
    this.audit = audit;
    this.transaction = new TransactionTemplate(transactions);
    this.ownTransaction = new TransactionTemplate(transactions);
    this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  /** The platform's share; the rest goes to the seller. Rounded down, in the seller's favour. */
  static long platformFee(long amountPaise, int feeBp) {
    return amountPaise * feeBp / BASIS_POINTS;
  }

  // --- The buyer pays -------------------------------------------------------------------------

  /** Opens (or reopens) payment for an online order waiting to be paid. */
  public PaymentStart start(CurrentUser caller, UUID buyer, UUID orderId) {
    requireEnabled();
    PaymentFacts facts = buyersOrder(caller, buyer, orderId);
    if (!"PLACED".equals(facts.status()) || !"PENDING".equals(facts.paymentStatus())) {
      throw ApiException.conflict("This order is not waiting for payment.");
    }
    Optional<PaymentRow> existing = inPayments(s -> records.paymentForOrder(orderId));
    if (existing.isPresent()) {
      return startOf(existing.get());
    }
    UUID paymentId = UUID.randomUUID();
    String razorpayOrderId;
    try {
      razorpayOrderId =
          razorpay.createOrder(
              facts.totalPaise(), paymentId.toString(), Map.of("order", orderId.toString()));
    } catch (RazorpayException e) {
      throw ApiException.unavailable("Online payment is not available right now. Try again.");
    }
    long fee = platformFee(facts.totalPaise(), properties.platformFeeBp());
    PaymentRow row =
        inPayments(
            s -> {
              records.insertPayment(
                  paymentId,
                  orderId,
                  facts.buyer(),
                  facts.seller(),
                  facts.totalPaise(),
                  fee,
                  razorpayOrderId,
                  clock.instant());
              // A second click may have won the race: use whichever payment was stored.
              return records.paymentForOrder(orderId).orElseThrow();
            });
    return startOf(row);
  }

  /**
   * After Razorpay's checkout: checks its signature, then asks Razorpay itself whether the money
   * was captured. The browser's word alone is never enough.
   */
  public Payment confirm(CurrentUser caller, UUID buyer, UUID orderId, PaymentConfirm confirm) {
    requireEnabled();
    buyersOrder(caller, buyer, orderId);
    PaymentRow row =
        inPayments(s -> records.paymentForOrder(orderId))
            .orElseThrow(() -> ApiException.notFound("No payment was started for this order."));
    boolean valid =
        row.razorpayOrderId().equals(confirm.razorpayOrderId())
            && Signatures.checkoutValid(
                row.razorpayOrderId(),
                confirm.razorpayPaymentId(),
                confirm.razorpaySignature(),
                properties.keySecret());
    if (!valid) {
      log.warn("Payment signature did not match for order {}", orderId);
      throw ApiException.badRequest("The payment could not be verified.");
    }
    settle(orderId);
    PaymentRow now = inPayments(s -> records.paymentForOrder(orderId)).orElseThrow();
    if ("CREATED".equals(now.status())) {
      throw ApiException.conflict("Razorpay has not confirmed the payment yet. Refresh shortly.");
    }
    return view(now);
  }

  /** The order's payment, for either business on it. Empty if it was never started. */
  public Optional<Payment> get(CurrentUser caller, UUID business, UUID orderId) {
    return transaction.execute(
        s -> {
          tenants.enter(caller, business, BusinessRole.ANY);
          return records.paymentForOrder(orderId).map(PaymentService::view);
        });
  }

  // --- Moving a payment on --------------------------------------------------------------------

  /** Takes every step that is due for the order's payment. Never throws: the job retries. */
  public void settle(UUID orderId) {
    try {
      boolean more = step(orderId, this::capture);
      if (more) {
        more = step(orderId, this::refundIfEnded);
      }
      if (more) {
        more = step(orderId, this::holdSellerShare);
      }
      if (more) {
        step(orderId, this::releaseIfHandedOver);
      }
    } catch (RazorpayException e) {
      log.warn("Payment for order {} not settled yet: {}", orderId, e.getMessage());
    } catch (RuntimeException e) {
      log.error("Payment for order {} could not be settled", orderId, e);
    }
  }

  /** One step with the payment row locked; false when there is nothing more to do now. */
  private boolean step(UUID orderId, Step work) {
    return Boolean.TRUE.equals(
        ownTransaction.execute(
            s -> {
              scope.enterPayments();
              Optional<PaymentRow> row = records.lockPaymentForOrder(orderId);
              Optional<PaymentFacts> facts = orders.paymentFacts(orderId);
              if (row.isEmpty() || facts.isEmpty()) {
                return false;
              }
              return work.apply(row.get(), facts.get(), clock.instant());
            }));
  }

  @FunctionalInterface
  private interface Step {
    boolean apply(PaymentRow payment, PaymentFacts order, Instant now);
  }

  /** CREATED → CAPTURED, if Razorpay has the money (capturing an authorised payment if needed). */
  private boolean capture(PaymentRow p, PaymentFacts order, Instant now) {
    if (!"CREATED".equals(p.status())) {
      return true;
    }
    Optional<RzPayment> paid =
        razorpay.paymentsOfOrder(p.razorpayOrderId()).stream()
            .filter(r -> "captured".equals(r.status()) || "authorized".equals(r.status()))
            .findFirst();
    if (paid.isEmpty()) {
      return false;
    }
    RzPayment found = paid.get();
    if (found.amount() != p.amountPaise() || !p.razorpayOrderId().equals(found.orderId())) {
      log.error("Payment {} does not match order {}: not accepted", found.id(), order.orderId());
      return false;
    }
    if ("authorized".equals(found.status())) {
      razorpay.capture(found.id(), p.amountPaise());
    }
    records.markCaptured(p.id(), found.id(), now);
    systemAudit("payment.captured", p, Map.of("amountPaise", p.amountPaise()));
    return true;
  }

  /** The order ended without delivery: take back the seller's share and refund the buyer. */
  private boolean refundIfEnded(PaymentRow p, PaymentFacts order, Instant now) {
    if ("REFUNDED".equals(p.status())) {
      return false;
    }
    if (!OrderService.ENDED_UNFULFILLED.contains(order.status())) {
      return true;
    }
    List<RzTransfer> transfers = razorpay.transfersOf(p.razorpayPaymentId());
    for (RzTransfer t : transfers) {
      if (t.amountReversed() < t.amount()) {
        razorpay.reverseTransfer(t.id(), t.amount() - t.amountReversed());
      }
    }
    if (!transfers.isEmpty()) {
      records.setTransfer(p.id(), transfers.getFirst().id(), "REVERSED", now);
    }
    List<String> refunds = razorpay.refundsOf(p.razorpayPaymentId());
    String refundId =
        refunds.isEmpty()
            ? razorpay.refund(p.razorpayPaymentId(), p.id().toString().substring(0, 36))
            : refunds.getFirst();
    records.markRefunded(p.id(), refundId, now);
    orders.recordOnlinePayment(order.orderId(), "REFUNDED");
    systemAudit("payment.refunded", p, Map.of("orderStatus", order.status()));
    return false;
  }

  /** Paid and still going: mark the order paid and send the seller's share, on hold. */
  private boolean holdSellerShare(PaymentRow p, PaymentFacts order, Instant now) {
    if (!"PAID".equals(order.paymentStatus())) {
      orders.recordOnlinePayment(order.orderId(), "PAID");
    }
    if (!"NONE".equals(p.transferStatus())) {
      return true;
    }
    List<RzTransfer> made = razorpay.transfersOf(p.razorpayPaymentId());
    RzTransfer transfer;
    if (!made.isEmpty()) {
      transfer = made.getFirst(); // made before, but the answer was lost
    } else {
      Optional<String> account = payouts.linkedAccount(p.seller());
      if (account.isEmpty()) {
        log.warn("Seller of order {} has no active payout account yet", order.orderId());
        return false;
      }
      transfer =
          razorpay.transferOnHold(
              p.razorpayPaymentId(),
              account.get(),
              p.sellerSharePaise(),
              order.orderId().toString());
    }
    records.setTransfer(p.id(), transfer.id(), "HELD", now);
    systemAudit("payment.share_held", p, Map.of("sharePaise", p.sellerSharePaise()));
    return true;
  }

  /** The goods were handed over: release the seller's share. */
  private boolean releaseIfHandedOver(PaymentRow p, PaymentFacts order, Instant now) {
    if (!"HELD".equals(p.transferStatus()) || !OrderService.FULFILLED.contains(order.status())) {
      return false;
    }
    razorpay.releaseTransfer(p.transferId());
    records.setTransfer(p.id(), null, "RELEASED", now);
    systemAudit("payment.share_released", p, Map.of("sharePaise", p.sellerSharePaise()));
    return false;
  }

  /** Payments with a step possibly due, oldest first. For the job. */
  List<UUID> openPayments(int limit) {
    return inPayments(s -> records.openPaymentOrders(limit));
  }

  // --- Webhook --------------------------------------------------------------------------------

  /**
   * A Razorpay webhook delivery: checked against the webhook secret, handled once per event id, and
   * used only as a nudge: settle() asks Razorpay itself what happened.
   */
  public void webhook(String rawBody, String signature, String eventId) {
    if (!properties.webhookEnabled()) {
      throw ApiException.notFound("Not found.");
    }
    if (!Signatures.webhookValid(rawBody, signature, properties.webhookSecret())) {
      log.warn("Rejected a webhook with a wrong signature");
      throw ApiException.badRequest("Invalid signature.");
    }
    JsonNode event;
    try {
      event = json.readTree(rawBody);
    } catch (RuntimeException e) {
      throw ApiException.badRequest("Unreadable event.");
    }
    String type = event.path("event").asString("unknown");
    String id =
        eventId == null || eventId.isBlank()
            ? Signatures.hmacHex(properties.webhookSecret(), rawBody).substring(0, 64)
            : eventId.substring(0, Math.min(eventId.length(), 64));
    String razorpayOrderId =
        event.path("payload").path("payment").path("entity").path("order_id").asString(null);
    if (razorpayOrderId == null) {
      razorpayOrderId =
          event.path("payload").path("order").path("entity").path("id").asString(null);
    }
    String rzOrder = razorpayOrderId;
    Optional<UUID> orderId =
        inPayments(
            s -> {
              if (!records.firstDelivery(
                  id, type.substring(0, Math.min(type.length(), 64)), clock.instant())) {
                return Optional.<UUID>empty();
              }
              return rzOrder == null
                  ? Optional.<UUID>empty()
                  : records.orderForRazorpayOrder(rzOrder);
            });
    orderId.ifPresent(this::settle);
  }

  // --- Helpers --------------------------------------------------------------------------------

  private PaymentFacts buyersOrder(CurrentUser caller, UUID buyer, UUID orderId) {
    transaction.executeWithoutResult(
        s -> tenants.check(caller, buyer, BusinessRole.OWNER_OR_MANAGER));
    return orders
        .paymentFacts(orderId)
        .filter(f -> f.buyer().equals(buyer))
        .orElseThrow(() -> ApiException.notFound("Order not found."));
  }

  private PaymentStart startOf(PaymentRow row) {
    if (!"CREATED".equals(row.status())) {
      throw ApiException.conflict("This order is already paid.");
    }
    return new PaymentStart(
        properties.keyId(), row.razorpayOrderId(), row.amountPaise(), RazorpayClient.CURRENCY);
  }

  private void requireEnabled() {
    if (!properties.enabled()) {
      throw ApiException.unavailable("Online payment is not switched on yet.");
    }
  }

  private <T> T inPayments(Function<org.springframework.transaction.TransactionStatus, T> work) {
    return transaction.execute(
        s -> {
          scope.enterPayments();
          return work.apply(s);
        });
  }

  private void systemAudit(String action, PaymentRow p, Map<String, Object> details) {
    audit.record(new AuditEntry(action, null, p.seller(), "order", p.orderId(), details));
  }

  static Payment view(PaymentRow p) {
    return new Payment(
        p.status(), p.transferStatus(), p.amountPaise(), p.platformFeePaise(), p.capturedAt());
  }
}
