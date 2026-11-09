package com.b2bmarket.payments.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The payment tables. Writes run as the payments role; reads for a business's own pages also work
 * under the tenant role, whose row policies show only that business's rows.
 */
@Repository
public class PaymentRecords {

  private final JdbcClient jdbc;

  PaymentRecords(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static Timestamp ts(Instant at) {
    return at == null ? null : Timestamp.from(at);
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp t = rs.getTimestamp(column);
    return t == null ? null : t.toInstant();
  }

  // --- Payout accounts ------------------------------------------------------------------------

  public record PayoutRow(
      UUID businessId,
      String razorpayAccountId,
      String stakeholderId,
      String productId,
      String status,
      String bankLast4,
      String ifscCode,
      String beneficiaryName,
      Instant updatedAt) {}

  private static PayoutRow payout(ResultSet rs, int i) throws SQLException {
    return new PayoutRow(
        rs.getObject("business_id", UUID.class),
        rs.getString("razorpay_account_id"),
        rs.getString("stakeholder_id"),
        rs.getString("product_id"),
        rs.getString("status"),
        rs.getString("bank_last4"),
        rs.getString("ifsc_code"),
        rs.getString("beneficiary_name"),
        instant(rs, "updated_at"));
  }

  public Optional<PayoutRow> payout(UUID businessId) {
    return jdbc.sql("select * from payout_accounts where business_id = :id")
        .param("id", businessId)
        .query(PaymentRecords::payout)
        .optional();
  }

  /** Locks the row so two set-ups of the same account never run at once. */
  public Optional<PayoutRow> lockPayout(UUID businessId) {
    return jdbc.sql("select * from payout_accounts where business_id = :id for update")
        .param("id", businessId)
        .query(PaymentRecords::payout)
        .optional();
  }

  /** Creates the row, or replaces the bank details of an unfinished one. */
  public void savePayoutBank(
      UUID businessId, String last4, String ifsc, String beneficiary, Instant now) {
    jdbc.sql(
            """
            insert into payout_accounts (business_id, bank_last4, ifsc_code, beneficiary_name,
                created_at, updated_at)
            values (:id, :last4, :ifsc, :name, :now, :now)
            on conflict (business_id) do update set bank_last4 = excluded.bank_last4,
                ifsc_code = excluded.ifsc_code, beneficiary_name = excluded.beneficiary_name,
                updated_at = excluded.updated_at
            """)
        .param("id", businessId)
        .param("last4", last4)
        .param("ifsc", ifsc)
        .param("name", beneficiary)
        .param("now", ts(now))
        .update();
  }

  public void setPayoutStep(
      UUID businessId,
      String accountId,
      String stakeholderId,
      String productId,
      String status,
      Instant now) {
    jdbc.sql(
            """
            update payout_accounts set razorpay_account_id = coalesce(:account, razorpay_account_id),
                stakeholder_id = coalesce(:stakeholder, stakeholder_id),
                product_id = coalesce(:product, product_id),
                status = :status, updated_at = :now
            where business_id = :id
            """)
        .param("account", accountId)
        .param("stakeholder", stakeholderId)
        .param("product", productId)
        .param("status", status)
        .param("now", ts(now))
        .param("id", businessId)
        .update();
  }

  // --- Payments -------------------------------------------------------------------------------

  public record PaymentRow(
      UUID id,
      UUID orderId,
      UUID buyer,
      UUID seller,
      long amountPaise,
      long platformFeePaise,
      String razorpayOrderId,
      String razorpayPaymentId,
      String status,
      String transferId,
      String transferStatus,
      String refundId,
      Instant createdAt,
      Instant capturedAt) {

    public long sellerSharePaise() {
      return amountPaise - platformFeePaise;
    }
  }

  private static PaymentRow paymentRow(ResultSet rs, int i) throws SQLException {
    return new PaymentRow(
        rs.getObject("id", UUID.class),
        rs.getObject("order_id", UUID.class),
        rs.getObject("buyer_business_id", UUID.class),
        rs.getObject("seller_business_id", UUID.class),
        rs.getLong("amount_paise"),
        rs.getLong("platform_fee_paise"),
        rs.getString("razorpay_order_id"),
        rs.getString("razorpay_payment_id"),
        rs.getString("status"),
        rs.getString("transfer_id"),
        rs.getString("transfer_status"),
        rs.getString("refund_id"),
        instant(rs, "created_at"),
        instant(rs, "captured_at"));
  }

  public Optional<PaymentRow> paymentForOrder(UUID orderId) {
    return jdbc.sql("select * from payments where order_id = :id")
        .param("id", orderId)
        .query(PaymentRecords::paymentRow)
        .optional();
  }

  public Optional<PaymentRow> lockPaymentForOrder(UUID orderId) {
    return jdbc.sql("select * from payments where order_id = :id for update")
        .param("id", orderId)
        .query(PaymentRecords::paymentRow)
        .optional();
  }

  public Optional<UUID> orderForRazorpayOrder(String razorpayOrderId) {
    return jdbc.sql("select order_id from payments where razorpay_order_id = :id")
        .param("id", razorpayOrderId)
        .query(UUID.class)
        .optional();
  }

  /** False if the order already has a payment (a second click raced the first). */
  public boolean insertPayment(
      UUID id,
      UUID orderId,
      UUID buyer,
      UUID seller,
      long amount,
      long fee,
      String razorpayOrderId,
      Instant now) {
    return jdbc.sql(
                """
                insert into payments (id, order_id, buyer_business_id, seller_business_id,
                    amount_paise, platform_fee_paise, razorpay_order_id, created_at, updated_at)
                values (:id, :order, :buyer, :seller, :amount, :fee, :rzOrder, :now, :now)
                on conflict (order_id) do nothing
                """)
            .param("id", id)
            .param("order", orderId)
            .param("buyer", buyer)
            .param("seller", seller)
            .param("amount", amount)
            .param("fee", fee)
            .param("rzOrder", razorpayOrderId)
            .param("now", ts(now))
            .update()
        == 1;
  }

  public void markCaptured(UUID id, String razorpayPaymentId, Instant now) {
    jdbc.sql(
            "update payments set status = 'CAPTURED', razorpay_payment_id = :pay,"
                + " captured_at = :now, updated_at = :now where id = :id and status = 'CREATED'")
        .param("pay", razorpayPaymentId)
        .param("now", ts(now))
        .param("id", id)
        .update();
  }

  public void setTransfer(UUID id, String transferId, String transferStatus, Instant now) {
    jdbc.sql(
            "update payments set transfer_id = coalesce(:trf, transfer_id),"
                + " transfer_status = :status, updated_at = :now where id = :id")
        .param("trf", transferId)
        .param("status", transferStatus)
        .param("now", ts(now))
        .param("id", id)
        .update();
  }

  public void markRefunded(UUID id, String refundId, Instant now) {
    jdbc.sql(
            "update payments set status = 'REFUNDED', refund_id = :refund, updated_at = :now"
                + " where id = :id")
        .param("refund", refundId)
        .param("now", ts(now))
        .param("id", id)
        .update();
  }

  /** Payments that may still need a step: not yet captured, or the seller's share not released. */
  public List<UUID> openPaymentOrders(int limit) {
    return jdbc.sql(
            "select order_id from payments where status = 'CREATED'"
                + " or (status = 'CAPTURED' and transfer_status <> 'RELEASED')"
                + " order by updated_at limit :limit")
        .param("limit", limit)
        .query(UUID.class)
        .list();
  }

  // --- Webhook deliveries ---------------------------------------------------------------------

  /** True the first time an event id is seen. */
  public boolean firstDelivery(String eventId, String eventType, Instant now) {
    return jdbc.sql(
                "insert into payment_events (event_id, event_type, received_at)"
                    + " values (:id, :type, :now) on conflict (event_id) do nothing")
            .param("id", eventId)
            .param("type", eventType)
            .param("now", ts(now))
            .update()
        == 1;
  }
}
