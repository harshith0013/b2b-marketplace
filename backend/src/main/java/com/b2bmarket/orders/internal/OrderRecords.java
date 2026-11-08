package com.b2bmarket.orders.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Carts, orders, reservations and quotes. Every statement runs in the caller's transaction and
 * database scope (ordering role for the buyer, or tenant role for either party), so row policies
 * decide what each statement can see.
 */
@Repository
public class OrderRecords {

  private final JdbcClient jdbc;

  OrderRecords(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static Timestamp ts(Instant at) {
    return at == null ? null : Timestamp.from(at);
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp t = rs.getTimestamp(column);
    return t == null ? null : t.toInstant();
  }

  // --- Cart -----------------------------------------------------------------------------------

  public record CartRow(UUID listingId, long quantity) {}

  public void upsertCart(UUID buyer, UUID listingId, long quantity, Instant now) {
    jdbc.sql(
            """
            insert into cart_items (id, buyer_business_id, listing_id, quantity, created_at, updated_at)
            values (:id, :buyer, :listing, :quantity, :now, :now)
            on conflict (buyer_business_id, listing_id)
            do update set quantity = excluded.quantity, updated_at = excluded.updated_at
            """)
        .param("id", UUID.randomUUID())
        .param("buyer", buyer)
        .param("listing", listingId)
        .param("quantity", quantity)
        .param("now", ts(now))
        .update();
  }

  public void removeFromCart(UUID buyer, Collection<UUID> listingIds) {
    if (listingIds.isEmpty()) {
      return;
    }
    jdbc.sql("delete from cart_items where buyer_business_id = :buyer and listing_id in (:ids)")
        .param("buyer", buyer)
        .param("ids", List.copyOf(listingIds))
        .update();
  }

  public List<CartRow> cart(UUID buyer) {
    return jdbc.sql(
            "select listing_id, quantity from cart_items where buyer_business_id = :buyer"
                + " order by created_at, listing_id limit 100")
        .param("buyer", buyer)
        .query((rs, i) -> new CartRow(rs.getObject(1, UUID.class), rs.getLong(2)))
        .list();
  }

  // --- Placing --------------------------------------------------------------------------------

  public Optional<UUID> orderForKey(UUID buyer, String key) {
    return jdbc.sql(
            "select id from orders where buyer_business_id = :buyer and idempotency_key = :key")
        .param("buyer", buyer)
        .param("key", key)
        .query(UUID.class)
        .optional();
  }

  /** Locks the batch row for this transaction and returns its stock (empty if not on sale). */
  public Optional<Long> lockBatch(UUID batchId) {
    return jdbc.sql("select quantity from inventory_batches where id = :id for update")
        .param("id", batchId)
        .query(Long.class)
        .optional();
  }

  public long reserved(UUID batchId) {
    return jdbc.sql(
            "select coalesce(sum(quantity), 0) from inventory_reservations"
                + " where batch_id = :batch and status = 'ACTIVE'")
        .param("batch", batchId)
        .query(Long.class)
        .single();
  }

  public record NewOrder(
      UUID id,
      UUID buyer,
      UUID seller,
      boolean sellerConfidential,
      String paymentMethod,
      String deliveryMethod,
      String deliveryAddress,
      String deliveryPincode,
      String pickupCity,
      String pickupState,
      long itemsPaise,
      long gstPaise,
      long deliveryPaise,
      long totalPaise,
      UUID quoteId,
      String idempotencyKey,
      Instant now) {}

  public void insertOrder(NewOrder o) {
    jdbc.sql(
            """
            insert into orders (id, buyer_business_id, seller_business_id, seller_confidential, status,
                payment_method, delivery_method, delivery_address, delivery_pincode, pickup_city,
                pickup_state, items_total_paise, gst_total_paise, delivery_charge_paise, total_paise,
                quote_id, idempotency_key, placed_at, updated_at)
            values (:id, :buyer, :seller, :confidential, 'PLACED', :payment, :delivery, :address,
                :pincode, :city, :state, :items, :gst, :deliveryPaise, :total, :quote, :key, :now, :now)
            """)
        .param("id", o.id())
        .param("buyer", o.buyer())
        .param("seller", o.seller())
        .param("confidential", o.sellerConfidential())
        .param("payment", o.paymentMethod())
        .param("delivery", o.deliveryMethod())
        .param("address", o.deliveryAddress())
        .param("pincode", o.deliveryPincode())
        .param("city", o.pickupCity())
        .param("state", o.pickupState())
        .param("items", o.itemsPaise())
        .param("gst", o.gstPaise())
        .param("deliveryPaise", o.deliveryPaise())
        .param("total", o.totalPaise())
        .param("quote", o.quoteId())
        .param("key", o.idempotencyKey())
        .param("now", ts(o.now()))
        .update();
  }

  public record NewItem(
      UUID listingId,
      UUID batchId,
      String productLine,
      long quantity,
      String unit,
      long unitPricePaise,
      int gstRateBp,
      long linePaise,
      long gstPaise) {}

  public void insertItem(UUID orderId, NewItem item) {
    jdbc.sql(
            """
            insert into order_items (id, order_id, listing_id, batch_id, product_line, quantity,
                quantity_unit, unit_price_paise, gst_rate_bp, line_total_paise, gst_paise)
            values (:id, :order, :listing, :batch, :line, :quantity, :unit, :price, :gstRate,
                :lineTotal, :gst)
            """)
        .param("id", UUID.randomUUID())
        .param("order", orderId)
        .param("listing", item.listingId())
        .param("batch", item.batchId())
        .param("line", item.productLine())
        .param("quantity", item.quantity())
        .param("unit", item.unit())
        .param("price", item.unitPricePaise())
        .param("gstRate", item.gstRateBp())
        .param("lineTotal", item.linePaise())
        .param("gst", item.gstPaise())
        .update();
  }

  public void reserve(
      UUID orderId,
      UUID batchId,
      UUID seller,
      UUID buyer,
      long quantity,
      Instant expires,
      Instant now) {
    jdbc.sql(
            """
            insert into inventory_reservations (id, order_id, batch_id, seller_business_id,
                buyer_business_id, quantity, expires_at, created_at)
            values (:id, :order, :batch, :seller, :buyer, :quantity, :expires, :now)
            """)
        .param("id", UUID.randomUUID())
        .param("order", orderId)
        .param("batch", batchId)
        .param("seller", seller)
        .param("buyer", buyer)
        .param("quantity", quantity)
        .param("expires", ts(expires))
        .param("now", ts(now))
        .update();
  }

  // --- Reading and changing orders ------------------------------------------------------------

  public record OrderRow(
      UUID id,
      UUID buyer,
      UUID seller,
      boolean sellerConfidential,
      String status,
      String paymentMethod,
      String paymentStatus,
      String deliveryMethod,
      String deliveryAddress,
      String deliveryPincode,
      String pickupCity,
      String pickupState,
      long itemsPaise,
      long gstPaise,
      long deliveryPaise,
      long totalPaise,
      String statusReason,
      String deliveryPersonName,
      String deliveryPersonPhone,
      Instant placedAt,
      Instant updatedAt,
      Instant completedAt) {}

  private static final String ORDER_COLUMNS =
      "id, buyer_business_id, seller_business_id, seller_confidential, status, payment_method,"
          + " payment_status, delivery_method, delivery_address, delivery_pincode, pickup_city,"
          + " pickup_state, items_total_paise, gst_total_paise, delivery_charge_paise, total_paise,"
          + " status_reason, delivery_person_name, delivery_person_phone, placed_at, updated_at,"
          + " completed_at";

  private static OrderRow orderRow(ResultSet rs, int i) throws SQLException {
    return new OrderRow(
        rs.getObject("id", UUID.class),
        rs.getObject("buyer_business_id", UUID.class),
        rs.getObject("seller_business_id", UUID.class),
        rs.getBoolean("seller_confidential"),
        rs.getString("status"),
        rs.getString("payment_method"),
        rs.getString("payment_status"),
        rs.getString("delivery_method"),
        rs.getString("delivery_address"),
        rs.getString("delivery_pincode"),
        rs.getString("pickup_city"),
        rs.getString("pickup_state"),
        rs.getLong("items_total_paise"),
        rs.getLong("gst_total_paise"),
        rs.getLong("delivery_charge_paise"),
        rs.getLong("total_paise"),
        rs.getString("status_reason"),
        rs.getString("delivery_person_name"),
        rs.getString("delivery_person_phone"),
        instant(rs, "placed_at"),
        instant(rs, "updated_at"),
        instant(rs, "completed_at"));
  }

  /** The order if this business is one of its two parties (the caller passes its business id). */
  public Optional<OrderRow> order(UUID orderId, UUID business) {
    return jdbc.sql(
            "select "
                + ORDER_COLUMNS
                + " from orders where id = :id"
                + " and (buyer_business_id = :business or seller_business_id = :business)")
        .param("id", orderId)
        .param("business", business)
        .query(OrderRecords::orderRow)
        .optional();
  }

  /** Orders of a business, newest first: as buyer or as seller. */
  public List<OrderRow> orders(UUID business, boolean asSeller, int limit, long offset) {
    return jdbc.sql(
            "select "
                + ORDER_COLUMNS
                + " from orders where "
                + (asSeller ? "seller_business_id" : "buyer_business_id")
                + " = :business order by placed_at desc, id limit :limit offset :offset")
        .param("business", business)
        .param("limit", limit)
        .param("offset", offset)
        .query(OrderRecords::orderRow)
        .list();
  }

  public List<NewItem> items(UUID orderId) {
    return jdbc.sql(
            "select listing_id, batch_id, product_line, quantity, quantity_unit, unit_price_paise,"
                + " gst_rate_bp, line_total_paise, gst_paise from order_items where order_id = :id"
                + " order by product_line")
        .param("id", orderId)
        .query(
            (rs, i) ->
                new NewItem(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getString(3),
                    rs.getLong(4),
                    rs.getString(5),
                    rs.getLong(6),
                    rs.getInt(7),
                    rs.getLong(8),
                    rs.getLong(9)))
        .list();
  }

  public record Change(
      String status,
      String statusReason,
      String deliveryPersonName,
      String deliveryPersonPhone,
      String paymentStatus,
      Instant completedAt) {}

  /** Moves an order on, only from the expected state (0 rows: someone else changed it first). */
  public int change(UUID orderId, String from, Change c, Instant now) {
    return jdbc.sql(
            """
            update orders set status = :status,
                status_reason = coalesce(:reason, status_reason),
                delivery_person_name = coalesce(:person, delivery_person_name),
                delivery_person_phone = coalesce(:phone, delivery_person_phone),
                payment_status = coalesce(:payment, payment_status),
                completed_at = coalesce(:completed, completed_at),
                updated_at = :now
            where id = :id and status = :from
            """)
        .param("status", c.status())
        .param("reason", c.statusReason())
        .param("person", c.deliveryPersonName())
        .param("phone", c.deliveryPersonPhone())
        .param("payment", c.paymentStatus())
        .param("completed", ts(c.completedAt()))
        .param("now", ts(now))
        .param("id", orderId)
        .param("from", from)
        .update();
  }

  public void setReservations(UUID orderId, String status) {
    jdbc.sql(
            "update inventory_reservations set status = :status where order_id = :order and status = 'ACTIVE'")
        .param("status", status)
        .param("order", orderId)
        .update();
  }

  // --- Jobs (ordering role, no business) -----------------------------------------------------

  public List<UUID> expirePlaced(Instant cutoff, Instant now) {
    return jdbc.sql(
            "update orders set status = 'EXPIRED', updated_at = :now"
                + " where status = 'PLACED' and placed_at < :cutoff returning id")
        .param("now", ts(now))
        .param("cutoff", ts(cutoff))
        .query(UUID.class)
        .list();
  }

  public int releaseFor(Collection<UUID> orderIds) {
    if (orderIds.isEmpty()) {
      return 0;
    }
    return jdbc.sql(
            "update inventory_reservations set status = 'RELEASED'"
                + " where order_id in (:ids) and status = 'ACTIVE'")
        .param("ids", List.copyOf(orderIds))
        .update();
  }

  public int forgetDeliveryPeople(Instant cutoff) {
    return jdbc.sql(
            "update orders set delivery_person_name = null, delivery_person_phone = null"
                + " where status = 'COMPLETED' and completed_at < :cutoff"
                + " and (delivery_person_name is not null or delivery_person_phone is not null)")
        .param("cutoff", ts(cutoff))
        .update();
  }

  // --- Quotes ---------------------------------------------------------------------------------

  public record QuoteRow(
      UUID id,
      UUID buyer,
      UUID seller,
      UUID listingId,
      boolean sellerConfidential,
      String productLine,
      String unit,
      long quantity,
      String note,
      String status,
      Long offeredUnitPricePaise,
      Instant validUntil,
      Instant createdAt) {}

  private static QuoteRow quoteRow(ResultSet rs, int i) throws SQLException {
    return new QuoteRow(
        rs.getObject("id", UUID.class),
        rs.getObject("buyer_business_id", UUID.class),
        rs.getObject("seller_business_id", UUID.class),
        rs.getObject("listing_id", UUID.class),
        rs.getBoolean("seller_confidential"),
        rs.getString("product_line"),
        rs.getString("quantity_unit"),
        rs.getLong("quantity"),
        rs.getString("note"),
        rs.getString("status"),
        (Long) rs.getObject("offered_unit_price_paise"),
        instant(rs, "valid_until"),
        instant(rs, "created_at"));
  }

  private static final String QUOTE_COLUMNS =
      "id, buyer_business_id, seller_business_id, listing_id, seller_confidential, product_line,"
          + " quantity_unit, quantity, note, status,"
          + " offered_unit_price_paise, valid_until, created_at";

  public void insertQuote(QuoteRow q, Instant now) {
    jdbc.sql(
            """
            insert into quotes (id, buyer_business_id, seller_business_id, listing_id,
                seller_confidential, product_line, quantity_unit, quantity, note, status, created_at,
                updated_at)
            values (:id, :buyer, :seller, :listing, :confidential, :line, :unit, :quantity, :note,
                'REQUESTED', :now, :now)
            """)
        .param("id", q.id())
        .param("buyer", q.buyer())
        .param("seller", q.seller())
        .param("listing", q.listingId())
        .param("confidential", q.sellerConfidential())
        .param("line", q.productLine())
        .param("unit", q.unit())
        .param("quantity", q.quantity())
        .param("note", q.note())
        .param("now", ts(now))
        .update();
  }

  public Optional<QuoteRow> quote(UUID quoteId, UUID business) {
    return jdbc.sql(
            "select "
                + QUOTE_COLUMNS
                + " from quotes where id = :id"
                + " and (buyer_business_id = :business or seller_business_id = :business)")
        .param("id", quoteId)
        .param("business", business)
        .query(OrderRecords::quoteRow)
        .optional();
  }

  public List<QuoteRow> quotes(UUID business, boolean asSeller, int limit) {
    return jdbc.sql(
            "select "
                + QUOTE_COLUMNS
                + " from quotes where "
                + (asSeller ? "seller_business_id" : "buyer_business_id")
                + " = :business order by created_at desc, id limit :limit")
        .param("business", business)
        .param("limit", limit)
        .query(OrderRecords::quoteRow)
        .list();
  }

  public int offerQuote(UUID quoteId, long unitPricePaise, Instant validUntil, Instant now) {
    return jdbc.sql(
            "update quotes set status = 'OFFERED', offered_unit_price_paise = :price,"
                + " valid_until = :until, updated_at = :now where id = :id and status = 'REQUESTED'")
        .param("price", unitPricePaise)
        .param("until", ts(validUntil))
        .param("now", ts(now))
        .param("id", quoteId)
        .update();
  }

  public int quoteStatus(UUID quoteId, String from, String to, Instant now) {
    return jdbc.sql(
            "update quotes set status = :to, updated_at = :now where id = :id and status = :from")
        .param("to", to)
        .param("now", ts(now))
        .param("id", quoteId)
        .param("from", from)
        .update();
  }
}
