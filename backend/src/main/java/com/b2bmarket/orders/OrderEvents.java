package com.b2bmarket.orders;

import java.util.UUID;

/** Published inside the transaction that changed an order; listeners act after it commits. */
public final class OrderEvents {

  private OrderEvents() {}

  /**
   * An order paid (or to be paid) online changed status: its payment may need a next step (hold the
   * seller's share, release it on delivery, or refund it if the order ended).
   */
  public record OnlineOrderChanged(UUID orderId, String status) {}
}
