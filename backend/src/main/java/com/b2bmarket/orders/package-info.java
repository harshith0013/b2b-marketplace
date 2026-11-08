/**
 * Carts, quotes, orders and stock reservations. Placing an order runs as the marketplace_ordering
 * database role (stock on sale only, batch row locked, buyer's own rows written); afterwards each
 * party acts in its own business scope. Prices, GST and totals are fixed when the order is placed.
 *
 * <p>Module boundary: other modules may call only this module's public service API, never its
 * repositories or entities.
 */
package com.b2bmarket.orders;
