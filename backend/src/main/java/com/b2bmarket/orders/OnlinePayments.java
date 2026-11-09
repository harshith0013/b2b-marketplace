package com.b2bmarket.orders;

import java.util.UUID;

/**
 * What ordering needs to know about online payment, implemented by the payments module (which
 * depends on orders, not the other way round).
 */
public interface OnlinePayments {

  /** The seller has an active payout account, so money paid online can reach them. */
  boolean canTakeOnline(UUID sellerBusinessId);
}
