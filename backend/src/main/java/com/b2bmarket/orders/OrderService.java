package com.b2bmarket.orders;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.batches.BatchService;
import com.b2bmarket.businesses.BusinessDtos.BusinessSummary;
import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.businesses.BusinessService;
import com.b2bmarket.businesses.TenantAccess;
import com.b2bmarket.compliance.ComplianceDtos.Stage;
import com.b2bmarket.listings.ListingCompliance;
import com.b2bmarket.orders.OrderDtos.ActionRequest;
import com.b2bmarket.orders.OrderDtos.Item;
import com.b2bmarket.orders.OrderDtos.Order;
import com.b2bmarket.orders.OrderStateMachine.Action;
import com.b2bmarket.orders.OrderStateMachine.Side;
import com.b2bmarket.orders.OrderStateMachine.Status;
import com.b2bmarket.orders.internal.OrderRecords;
import com.b2bmarket.orders.internal.OrderRecords.NewItem;
import com.b2bmarket.orders.internal.OrderRecords.OrderRow;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.TenantScope;
import com.b2bmarket.platform.Text;
import com.b2bmarket.search.OfferDtos.Orderable;
import com.b2bmarket.users.CurrentUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Placing orders (as the ordering database role, with the batch row locked) and moving them on
 * (each party in its own business scope). Prices and totals are fixed when placed.
 */
@Service
public class OrderService {

  static final Duration RESERVATION_HOLD = Duration.ofHours(24);

  /** An online order not paid within this time expires and gives its stock back. */
  static final Duration ONLINE_PAYMENT_WINDOW = Duration.ofHours(1);

  static final String ONLINE = "ONLINE";

  /** Order states after which an online payment is refunded. */
  public static final Set<String> ENDED_UNFULFILLED = Set.of("DECLINED", "CANCELLED", "EXPIRED");

  /** Order states in which the goods have reached the buyer: the seller's share is released. */
  public static final Set<String> FULFILLED = Set.of("DELIVERED", "PICKED_UP", "COMPLETED");

  static final int PAGE_SIZE = 50;

  /** Steps that decide about the order (not just fulfil it): owners and managers only. */
  static final Set<Action> DECISIONS = Set.of(Action.ACCEPT, Action.DECLINE, Action.CANCEL);

  /** A confidential seller's name reaches the buyer only once the seller has accepted. */
  static final Set<Status> SELLER_HIDDEN = Set.of(Status.PLACED, Status.DECLINED, Status.EXPIRED);

  /** One thing to order: the offer as on sale now, how much, and a quoted price if any. */
  public record Wanted(Orderable offer, long quantity, Long quotedUnitPricePaise) {}

  /** How the order is delivered and paid. */
  public record Choice(
      String deliveryMethod,
      String deliveryAddress,
      String deliveryPincode,
      String paymentMethod) {}

  private final OrderRecords records;
  private final TenantAccess tenants;
  private final TenantScope scope;
  private final BusinessService businesses;
  private final BatchService batches;
  private final ListingCompliance compliance;
  private final AuditService audit;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final OnlinePayments onlinePayments;
  private final ApplicationEventPublisher events;

  OrderService(
      OrderRecords records,
      TenantAccess tenants,
      TenantScope scope,
      BusinessService businesses,
      BatchService batches,
      ListingCompliance compliance,
      AuditService audit,
      PlatformTransactionManager transactions,
      Clock clock,
      OnlinePayments onlinePayments,
      ApplicationEventPublisher events) {
    this.onlinePayments = onlinePayments;
    this.events = events;
    this.records = records;
    this.tenants = tenants;
    this.scope = scope;
    this.businesses = businesses;
    this.batches = batches;
    this.compliance = compliance;
    this.audit = audit;
    this.transaction = new TransactionTemplate(transactions);
    this.clock = clock;
  }

  // --- Placing --------------------------------------------------------------------------------

  /**
   * Places one order with one seller. The caller has fetched each offer as it is on sale now
   * (outside any transaction). A repeat with the same idempotency key returns the first order.
   */
  public Order place(
      CurrentUser caller,
      UUID buyer,
      List<Wanted> wanted,
      Choice choice,
      String idempotencyKey,
      UUID quoteId) {
    transaction.executeWithoutResult(
        s -> tenants.check(caller, buyer, BusinessRole.OWNER_OR_MANAGER));
    Plan plan = plan(buyer, wanted, choice);
    if (ONLINE.equals(choice.paymentMethod()) && !onlinePayments.canTakeOnline(plan.seller())) {
      throw ApiException.conflict("This seller cannot take online payment yet.");
    }
    UUID orderId;
    try {
      orderId =
          transaction.execute(s -> insert(caller, buyer, plan, choice, idempotencyKey, quoteId));
    } catch (DataIntegrityViolationException raced) {
      // The same checkout arrived twice at once and the other one won: return its order.
      orderId =
          transaction.execute(
              s -> {
                scope.enterOrdering(buyer);
                return records.orderForKey(buyer, idempotencyKey).orElseThrow(() -> raced);
              });
    }
    UUID placed = orderId;
    return transaction.execute(
        s -> {
          tenants.enter(caller, buyer, BusinessRole.ANY);
          return view(load(placed, buyer), buyer);
        });
  }

  /** Everything checked before touching the database: one seller, quantities, delivery, payment. */
  record Plan(
      UUID seller,
      boolean confidential,
      String pickupCity,
      String pickupState,
      List<NewItem> items,
      OrderMath.Totals totals) {}

  static Plan plan(UUID buyer, List<Wanted> wanted, Choice choice) {
    if (wanted.isEmpty()) {
      throw ApiException.badRequest("Choose something to order.");
    }
    Set<UUID> sellers =
        wanted.stream().map(w -> w.offer().sellerBusinessId()).collect(Collectors.toSet());
    if (sellers.size() != 1) {
      throw ApiException.badRequest(
          "One order is for one seller. Order from each seller separately.");
    }
    UUID seller = sellers.iterator().next();
    if (seller.equals(buyer)) {
      throw ApiException.badRequest("You cannot order your own stock.");
    }
    boolean pickup = "PICKUP".equals(choice.deliveryMethod());
    boolean delivery = "SELLER_DELIVERY".equals(choice.deliveryMethod());
    if (!pickup && !delivery) {
      throw ApiException.badRequest("Choose pickup or delivery.");
    }
    String address = Text.optional(choice.deliveryAddress());
    if (delivery && (address == null || choice.deliveryPincode() == null)) {
      throw ApiException.badRequest("Give the delivery address and PIN code.");
    }
    boolean online = ONLINE.equals(choice.paymentMethod());
    if (pickup && !online && !"PAY_ON_PICKUP".equals(choice.paymentMethod())) {
      throw ApiException.badRequest("Pickup orders are paid on pickup or online.");
    }
    if (delivery && !online && !"COD".equals(choice.paymentMethod())) {
      throw ApiException.badRequest("Delivered orders are paid in cash on delivery or online.");
    }

    List<NewItem> items = new ArrayList<>();
    List<OrderMath.Line> lines = new ArrayList<>();
    long deliveryCharge = 0;
    for (Wanted w : wanted) {
      Orderable o = w.offer();
      if (pickup && !o.delivery().pickupAvailable()) {
        throw ApiException.badRequest(o.productLine() + ": pickup is not offered.");
      }
      if (delivery
          && (!o.delivery().ownDelivery()
              || !o.delivery().deliveryPincodes().contains(choice.deliveryPincode()))) {
        throw ApiException.badRequest(
            o.productLine() + ": the seller does not deliver to this PIN code.");
      }
      if (online && !o.delivery().onlineAccepted()) {
        throw ApiException.badRequest(
            o.productLine() + ": the seller does not take online payment for this offer.");
      }
      if (delivery && !online && !o.delivery().codAccepted()) {
        throw ApiException.badRequest(
            o.productLine() + ": the seller does not accept cash on delivery.");
      }
      if (w.quotedUnitPricePaise() == null && w.quantity() < o.moq()) {
        throw ApiException.badRequest(o.productLine() + ": order at least the minimum order.");
      }
      if (w.quantity() > o.stock()) {
        throw ApiException.conflict(o.productLine() + ": not enough stock.");
      }
      long unitPrice =
          w.quotedUnitPricePaise() != null
              ? w.quotedUnitPricePaise()
              : OrderMath.unitPrice(o.priceTiers(), w.quantity());
      OrderMath.Line line = OrderMath.line(w.quantity(), unitPrice, o.gstRateBp());
      lines.add(line);
      items.add(
          new NewItem(
              o.listingId(),
              o.batchId(),
              o.productLine(),
              w.quantity(),
              o.unit(),
              unitPrice,
              o.gstRateBp(),
              line.linePaise(),
              line.gstPaise()));
      if (delivery) {
        deliveryCharge = Math.max(deliveryCharge, o.delivery().deliveryChargePaise());
      }
    }
    Orderable first = wanted.getFirst().offer();
    if (pickup && wanted.stream().anyMatch(w -> !w.offer().city().equals(first.city()))) {
      throw ApiException.badRequest("These items are in different cities. Order them separately.");
    }
    boolean confidential = wanted.stream().anyMatch(w -> w.offer().confidential());
    return new Plan(
        seller,
        confidential,
        first.city(),
        first.state(),
        items,
        OrderMath.totals(lines, deliveryCharge));
  }

  /**
   * In the ordering scope: lock each batch, check what is left, write order, items, reservations.
   */
  private UUID insert(
      CurrentUser caller, UUID buyer, Plan plan, Choice choice, String key, UUID quoteId) {
    scope.enterOrdering(buyer);
    var existing = records.orderForKey(buyer, key);
    if (existing.isPresent()) {
      return existing.get();
    }
    Instant now = clock.instant();
    // Lock in a fixed order (by batch id) so two orders never wait on each other.
    List<NewItem> byBatch =
        plan.items().stream().sorted(Comparator.comparing(NewItem::batchId)).toList();
    for (NewItem item : byBatch) {
      long stock =
          records
              .lockBatch(item.batchId())
              .orElseThrow(
                  () -> ApiException.conflict(item.productLine() + ": no longer on sale."));
      long left = stock - records.reserved(item.batchId());
      if (item.quantity() > left) {
        throw ApiException.conflict(item.productLine() + ": only " + Math.max(left, 0) + " left.");
      }
    }
    UUID orderId = UUID.randomUUID();
    boolean delivery = "SELLER_DELIVERY".equals(choice.deliveryMethod());
    records.insertOrder(
        new OrderRecords.NewOrder(
            orderId,
            buyer,
            plan.seller(),
            plan.confidential(),
            choice.paymentMethod(),
            choice.deliveryMethod(),
            delivery ? Text.optional(choice.deliveryAddress()) : null,
            delivery ? choice.deliveryPincode() : null,
            plan.pickupCity(),
            plan.pickupState(),
            plan.totals().itemsPaise(),
            plan.totals().gstPaise(),
            plan.totals().deliveryPaise(),
            plan.totals().totalPaise(),
            quoteId,
            key,
            now));
    for (NewItem item : plan.items()) {
      records.insertItem(orderId, item);
      records.reserve(
          orderId,
          item.batchId(),
          plan.seller(),
          buyer,
          item.quantity(),
          now.plus(RESERVATION_HOLD),
          now);
    }
    records.removeFromCart(buyer, plan.items().stream().map(NewItem::listingId).toList());
    if (quoteId != null && records.quoteStatus(quoteId, "OFFERED", "ORDERED", now) == 0) {
      throw ApiException.conflict("This quote can no longer be ordered.");
    }
    audit.record(
        new AuditEntry(
            "order.placed",
            caller.userId(),
            buyer,
            "order",
            orderId,
            Map.of("items", plan.items().size(), "totalPaise", plan.totals().totalPaise())));
    return orderId;
  }

  // --- Moving on ------------------------------------------------------------------------------

  public Order act(
      CurrentUser caller, UUID business, UUID orderId, Action action, ActionRequest request) {
    return transaction.execute(
        s -> {
          BusinessRole role = tenants.enter(caller, business, BusinessRole.ANY);
          OrderRow order = load(orderId, business);
          Side side = order.seller().equals(business) ? Side.SELLER : Side.BUYER;
          if (DECISIONS.contains(action) && role == BusinessRole.STAFF) {
            throw ApiException.forbidden(
                "Only owners and managers can accept, decline or cancel orders.");
          }
          Status current = Status.valueOf(order.status());
          Status next = OrderStateMachine.next(current, action, side, order.deliveryMethod());
          boolean online = ONLINE.equals(order.paymentMethod());
          if (online && action == Action.PAID) {
            throw ApiException.badRequest("Online orders are marked paid by the payment itself.");
          }
          if (online && action == Action.ACCEPT && !"PAID".equals(order.paymentStatus())) {
            throw ApiException.conflict("The buyer has not paid yet.");
          }
          if (online
              && "PAID".equals(order.paymentStatus())
              && (next == Status.DELIVERED || next == Status.PICKED_UP)) {
            next = Status.COMPLETED; // Already paid: handing over the goods completes the order.
          }
          String reason = request == null ? null : Text.optional(request.reason());
          if ((action == Action.DECLINE || (action == Action.CANCEL && side == Side.SELLER))
              && reason == null) {
            throw ApiException.badRequest("Give a reason for the buyer.");
          }
          String person = request == null ? null : Text.optional(request.deliveryPersonName());
          String phone = request == null ? null : Text.optional(request.deliveryPersonPhone());
          if (action == Action.OUT_FOR_DELIVERY && (person == null || phone == null)) {
            throw ApiException.badRequest("Give the delivery person's name and phone number.");
          }
          List<NewItem> items = records.items(orderId);
          if (action == Action.READY || action == Action.OUT_FOR_DELIVERY) {
            for (NewItem item : items) {
              List<String> blocks =
                  compliance.check(order.seller(), item.listingId(), Stage.DISPATCH).blocks();
              if (!blocks.isEmpty()) {
                throw ApiException.unprocessable(
                    "This stock cannot be sent: it no longer passes the rules.",
                    Map.of("problems", blocks));
              }
            }
          }
          Instant now = clock.instant();
          if (action == Action.DELIVERED || action == Action.PICKED_UP) {
            for (NewItem item : items) {
              batches.deductForOrder(order.seller(), item.batchId(), item.quantity());
            }
            records.setReservations(orderId, "CONSUMED");
          }
          if (next == Status.DECLINED || next == Status.CANCELLED) {
            records.setReservations(orderId, "RELEASED");
          }
          int changed =
              records.change(
                  orderId,
                  current.name(),
                  new OrderRecords.Change(
                      next.name(),
                      reason,
                      person,
                      phone,
                      action == Action.PAID ? "PAID" : null,
                      next == Status.COMPLETED ? now : null),
                  now);
          if (changed == 0) {
            throw ApiException.conflict(
                "This order was changed at the same time. Refresh and try again.");
          }
          if (online) {
            events.publishEvent(new OrderEvents.OnlineOrderChanged(orderId, next.name()));
          }
          audit.record(
              new AuditEntry(
                  "order." + action.name().toLowerCase(Locale.ROOT),
                  caller.userId(),
                  business,
                  "order",
                  orderId,
                  Map.of("status", next.name())));
          return view(load(orderId, business), business);
        });
  }

  public Order get(CurrentUser caller, UUID business, UUID orderId) {
    return transaction.execute(
        s -> {
          tenants.enter(caller, business, BusinessRole.ANY);
          return view(load(orderId, business), business);
        });
  }

  /** Orders of the business, newest first: as buyer, or as seller. */
  public List<Order> list(CurrentUser caller, UUID business, boolean asSeller, int page) {
    if (page < 0 || page > 400) {
      throw ApiException.badRequest("The page number is out of range.");
    }
    return transaction.execute(
        s -> {
          tenants.enter(caller, business, BusinessRole.ANY);
          return records.orders(business, asSeller, PAGE_SIZE, (long) page * PAGE_SIZE).stream()
              .map(o -> view(o, business))
              .toList();
        });
  }

  private OrderRow load(UUID orderId, UUID business) {
    return records
        .order(orderId, business)
        .orElseThrow(() -> ApiException.notFound("Order not found."));
  }

  /** Call in the viewer's business scope. */
  private Order view(OrderRow o, UUID viewer) {
    Side side = o.seller().equals(viewer) ? Side.SELLER : Side.BUYER;
    Map<UUID, BusinessSummary> names = businesses.summaries(List.of(o.buyer(), o.seller()));
    Status status = Status.valueOf(o.status());
    boolean hideSeller =
        side == Side.BUYER && o.sellerConfidential() && SELLER_HIDDEN.contains(status);
    List<Item> items =
        records.items(o.id()).stream()
            .map(
                i ->
                    new Item(
                        i.listingId(),
                        i.productLine(),
                        i.quantity(),
                        i.unit(),
                        i.unitPricePaise(),
                        i.gstRateBp(),
                        i.linePaise(),
                        i.gstPaise()))
            .toList();
    return new Order(
        o.id(),
        side.name(),
        o.status(),
        o.paymentMethod(),
        o.paymentStatus(),
        o.deliveryMethod(),
        o.deliveryAddress(),
        o.deliveryPincode(),
        o.pickupCity(),
        o.pickupState(),
        names.get(o.buyer()) == null ? null : names.get(o.buyer()).name(),
        hideSeller || names.get(o.seller()) == null ? null : names.get(o.seller()).name(),
        items,
        o.itemsPaise(),
        o.gstPaise(),
        o.deliveryPaise(),
        o.totalPaise(),
        o.statusReason(),
        o.deliveryPersonName(),
        o.deliveryPersonPhone(),
        o.placedAt(),
        o.updatedAt(),
        o.completedAt(),
        allowedActions(o, status, side));
  }

  /** The state machine's steps, less those that online payment takes care of. */
  private static Set<String> allowedActions(OrderRow o, Status status, Side side) {
    boolean online = ONLINE.equals(o.paymentMethod());
    boolean paid = "PAID".equals(o.paymentStatus());
    return OrderStateMachine.allowed(status, side, o.deliveryMethod()).stream()
        .filter(a -> !(online && a == Action.PAID))
        .filter(a -> !(online && !paid && a == Action.ACCEPT))
        .map(Enum::name)
        .collect(Collectors.toSet());
  }

  // --- For the payments module ----------------------------------------------------------------

  /** What a payment needs to know about an online order. */
  public record PaymentFacts(
      UUID orderId,
      UUID buyer,
      UUID seller,
      String status,
      String paymentStatus,
      long totalPaise,
      Instant placedAt) {}

  /**
   * An online order's payment facts, read as the payments role (online orders only). The caller has
   * checked the person's membership, or is a system job; joins the caller's transaction.
   */
  public java.util.Optional<PaymentFacts> paymentFacts(UUID orderId) {
    return transaction.execute(
        s -> {
          scope.enterPayments();
          return records.paymentFacts(orderId);
        });
  }

  /** Records that an online order was paid, or refunded. Joins the caller's transaction. */
  public void recordOnlinePayment(UUID orderId, String paymentStatus) {
    if (!Set.of("PAID", "REFUNDED").contains(paymentStatus)) {
      throw new IllegalArgumentException("paymentStatus must be PAID or REFUNDED");
    }
    transaction.executeWithoutResult(
        s -> {
          scope.enterPayments();
          records.setOnlinePaymentStatus(orderId, paymentStatus, clock.instant());
        });
  }
}
