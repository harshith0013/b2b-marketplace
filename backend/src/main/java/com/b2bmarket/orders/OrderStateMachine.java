package com.b2bmarket.orders;

import com.b2bmarket.platform.ApiException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which side may do what, in which state. Anything not listed here is refused (409).
 *
 * <pre>
 * PLACED --accept--> ACCEPTED --ready--> READY_FOR_PICKUP --picked-up--> PICKED_UP --paid--> COMPLETED
 *        --decline-> DECLINED   --out-for-delivery--> OUT_FOR_DELIVERY --delivered--> DELIVERED --paid--> COMPLETED
 *        --cancel--> CANCELLED (buyer)       --cancel--> CANCELLED (seller, with reason)
 *        (24 h)----> EXPIRED
 * </pre>
 */
public final class OrderStateMachine {

  public enum Status {
    PLACED,
    ACCEPTED,
    DECLINED,
    CANCELLED,
    EXPIRED,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    PICKED_UP,
    COMPLETED
  }

  public enum Side {
    BUYER,
    SELLER
  }

  public enum Action {
    ACCEPT,
    DECLINE,
    CANCEL,
    READY,
    OUT_FOR_DELIVERY,
    DELIVERED,
    PICKED_UP,
    PAID
  }

  /** States that end an order: its reservation is released (or was consumed). */
  public static final Set<Status> CLOSED =
      EnumSet.of(Status.DECLINED, Status.CANCELLED, Status.EXPIRED, Status.COMPLETED);

  private OrderStateMachine() {}

  /** The next state, or 409 when this side may not do this now. */
  public static Status next(Status current, Action action, Side side, String deliveryMethod) {
    boolean pickup = "PICKUP".equals(deliveryMethod);
    Status next =
        switch (action) {
          case ACCEPT -> side == Side.SELLER && current == Status.PLACED ? Status.ACCEPTED : null;
          case DECLINE -> side == Side.SELLER && current == Status.PLACED ? Status.DECLINED : null;
          case CANCEL ->
              (side == Side.BUYER && current == Status.PLACED)
                      || (side == Side.SELLER && current == Status.ACCEPTED)
                  ? Status.CANCELLED
                  : null;
          case READY ->
              side == Side.SELLER && current == Status.ACCEPTED && pickup
                  ? Status.READY_FOR_PICKUP
                  : null;
          case OUT_FOR_DELIVERY ->
              side == Side.SELLER && current == Status.ACCEPTED && !pickup
                  ? Status.OUT_FOR_DELIVERY
                  : null;
          case DELIVERED ->
              side == Side.SELLER && current == Status.OUT_FOR_DELIVERY ? Status.DELIVERED : null;
          case PICKED_UP ->
              side == Side.SELLER && current == Status.READY_FOR_PICKUP ? Status.PICKED_UP : null;
          case PAID ->
              side == Side.SELLER && (current == Status.DELIVERED || current == Status.PICKED_UP)
                  ? Status.COMPLETED
                  : null;
        };
    if (next == null) {
      throw ApiException.conflict("This step is not possible for this order now.");
    }
    return next;
  }

  /** What this side may do now (for the screens). */
  public static Set<Action> allowed(Status current, Side side, String deliveryMethod) {
    Set<Action> allowed = EnumSet.noneOf(Action.class);
    for (Action action : Action.values()) {
      try {
        next(current, action, side, deliveryMethod);
        allowed.add(action);
      } catch (ApiException refused) {
        // Not allowed now.
      }
    }
    return allowed;
  }
}
