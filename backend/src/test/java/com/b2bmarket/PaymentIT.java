package com.b2bmarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Online payment against a fake Razorpay: a seller sets up payouts, a buyer pays, the seller's
 * share is held and released on handover, ended orders are refunded, and every step survives a lost
 * answer or a closed browser without paying anyone twice.
 */
class PaymentIT extends SellerFixtures {

  static final FakeRazorpay RAZORPAY = new FakeRazorpay();
  static final String WEBHOOK_SECRET = "webhook_secret_for_tests";

  @DynamicPropertySource
  static void razorpay(DynamicPropertyRegistry registry) {
    registry.add("app.razorpay.base-url", RAZORPAY::baseUrl);
    registry.add("app.razorpay.key-id", () -> FakeRazorpay.KEY_ID);
    registry.add("app.razorpay.key-secret", () -> FakeRazorpay.KEY_SECRET);
    registry.add("app.razorpay.webhook-secret", () -> WEBHOOK_SECRET);
  }

  @Autowired com.b2bmarket.payments.PaymentJobs paymentJobs;
  @Autowired com.b2bmarket.orders.OrderJobs orderJobs;

  String seller;
  String sellerBusiness;
  String buyer;
  String buyerBusiness;
  String listingId;

  @BeforeEach
  void anOnlineOfferAndABuyer() {
    seller = newPerson("seller");
    sellerBusiness = createBusiness(seller, "Online Traders");
    listingId = readyListing(seller, sellerBusiness); // 500 pieces, MOQ 10, ₹30 / ₹27 from 100
    publish(seller, sellerBusiness, listingId);
    buyer = newPerson("buyer");
    buyerBusiness = createBusiness(buyer, "Paying Kirana");
    RAZORPAY.productStatus("activated");
  }

  // --- Helpers --------------------------------------------------------------------------------

  String path(String business, String rest) {
    return "/api/v1/businesses/" + business + rest;
  }

  static Map<String, Object> payoutDetails() {
    Map<String, Object> body = new HashMap<>();
    body.put("email", "accounts@onlinetraders.example");
    body.put("phone", "9876543210");
    body.put("contactName", "Ravi Kumar");
    body.put("businessType", "proprietorship");
    body.put("street", "12-3 Main Road");
    body.put("accountNumber", "123456789012");
    body.put("ifsc", "HDFC0000317");
    body.put("beneficiaryName", "Online Traders");
    body.put("acceptsTerms", true);
    return body;
  }

  void setUpPayouts() {
    HttpResponse<String> r =
        call("PUT", path(sellerBusiness, "/payout-account"), seller, payoutDetails());
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
  }

  void acceptOnline() {
    Map<String, Object> delivery = new HashMap<>();
    delivery.put("pickupAvailable", true);
    delivery.put("ownDelivery", false);
    delivery.put("deliveryPincodes", List.of());
    delivery.put("deliveryChargePaise", 0);
    delivery.put("codAccepted", false);
    delivery.put("onlineAccepted", true);
    HttpResponse<String> r =
        call("PUT", path(sellerBusiness, "/listings/" + listingId + "/delivery"), seller, delivery);
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
  }

  HttpResponse<String> checkoutOnline() {
    HttpResponse<String> added =
        call(
            "POST",
            path(buyerBusiness, "/cart"),
            buyer,
            Map.of("listingId", listingId, "quantity", 100));
    assertThat(added.statusCode()).as(added.body()).isEqualTo(200);
    return call(
        "POST",
        path(buyerBusiness, "/orders"),
        buyer,
        Map.of(
            "listingIds",
            List.of(listingId),
            "deliveryMethod",
            "PICKUP",
            "paymentMethod",
            "ONLINE",
            "idempotencyKey",
            UUID.randomUUID().toString()));
  }

  /** A placed online order, ready to pay. */
  String onlineOrder() {
    setUpPayouts();
    acceptOnline();
    HttpResponse<String> placed = checkoutOnline();
    assertThat(placed.statusCode()).as(placed.body()).isEqualTo(201);
    return json(placed).get("id").asString();
  }

  JsonNode start(String orderId) {
    HttpResponse<String> r =
        call("POST", path(buyerBusiness, "/orders/" + orderId + "/payment"), buyer, null);
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    return json(r);
  }

  static String hmac(String secret, String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  HttpResponse<String> confirm(String orderId, String rzOrder, String paymentId, String sig) {
    return call(
        "POST",
        path(buyerBusiness, "/orders/" + orderId + "/payment/confirm"),
        buyer,
        Map.of(
            "razorpayOrderId", rzOrder, "razorpayPaymentId", paymentId, "razorpaySignature", sig));
  }

  /** Pays through the fake checkout and confirms with a valid signature. */
  String payAndConfirm(String orderId) {
    String rzOrder = start(orderId).get("razorpayOrderId").asString();
    String paymentId = RAZORPAY.pay(rzOrder, "captured");
    HttpResponse<String> r =
        confirm(
            orderId, rzOrder, paymentId, hmac(FakeRazorpay.KEY_SECRET, rzOrder + "|" + paymentId));
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    return paymentId;
  }

  JsonNode order(String token, String business, String orderId) {
    return json(call("GET", path(business, "/orders/" + orderId), token, null));
  }

  JsonNode payment(String orderId) {
    return json(call("GET", path(buyerBusiness, "/orders/" + orderId + "/payment"), buyer, null));
  }

  JsonNode act(String orderId, String action, Map<String, Object> body) {
    HttpResponse<String> r =
        call("POST", path(sellerBusiness, "/orders/" + orderId + "/" + action), seller, body);
    assertThat(r.statusCode()).as(action + ": " + r.body()).isEqualTo(200);
    return json(r);
  }

  int callsFor(String method, String pathPattern) {
    return RAZORPAY.calls(method, pathPattern).size();
  }

  // --- Payouts --------------------------------------------------------------------------------

  @Test
  void payoutSetUpCreatesALinkedAccountAndKeepsOnlyTheLastFourDigits() {
    HttpResponse<String> before =
        call("GET", path(sellerBusiness, "/payout-account"), seller, null);
    assertThat(json(before).get("status").asString()).isEqualTo("NOT_SET_UP");
    assertThat(json(before).get("available").asBoolean()).isTrue();

    int accounts = callsFor("POST", "/v2/accounts");
    setUpPayouts();
    JsonNode account = json(call("GET", path(sellerBusiness, "/payout-account"), seller, null));
    assertThat(account.get("status").asString()).isEqualTo("ACTIVATED");
    assertThat(account.get("canTakeOnline").asBoolean()).isTrue();
    assertThat(account.get("bankLast4").asString()).isEqualTo("9012");
    assertThat(callsFor("POST", "/v2/accounts")).isEqualTo(accounts + 1);
    JsonNode sent = RAZORPAY.calls("POST", "/v2/accounts").getLast().body();
    assertThat(sent.path("profile").path("subcategory").asString()).isEqualTo("wholesale");
    assertThat(sent.path("legal_business_name").asString()).isEqualTo("Online Traders");

    // The full account number went to Razorpay only.
    String stored =
        jdbc.queryForObject(
            "select row_to_json(p)::text from payout_accounts p where business_id = ?::uuid",
            String.class,
            sellerBusiness);
    assertThat(stored).doesNotContain("123456789012").contains("9012");

    // A second set-up is refused once active.
    assertThat(
            call("PUT", path(sellerBusiness, "/payout-account"), seller, payoutDetails())
                .statusCode())
        .isEqualTo(409);
    // Another business sees nothing, and a bad IFSC is refused before reaching Razorpay.
    assertThat(call("GET", path(sellerBusiness, "/payout-account"), buyer, null).statusCode())
        .isEqualTo(404);
    Map<String, Object> bad = payoutDetails();
    bad.put("ifsc", "HDFC317");
    assertThat(call("PUT", path(buyerBusiness, "/payout-account"), buyer, bad).statusCode())
        .isEqualTo(400);
  }

  @Test
  void aPayoutSetUpThatStopsPartWayResumesWithoutASecondAccount() {
    RAZORPAY.failNext("/v2/accounts/[^/]+/products", 500);
    assertThat(
            call("PUT", path(sellerBusiness, "/payout-account"), seller, payoutDetails())
                .statusCode())
        .isEqualTo(503);
    int accounts = callsFor("POST", "/v2/accounts");
    setUpPayouts();
    assertThat(callsFor("POST", "/v2/accounts"))
        .as("the saved account is reused")
        .isEqualTo(accounts);
    assertThat(
            json(call("GET", path(sellerBusiness, "/payout-account"), seller, null))
                .get("status")
                .asString())
        .isEqualTo("ACTIVATED");
  }

  @Test
  void whenRazorpayHasNotEnabledRouteTheSellerIsToldItIsNotTheirDetails() {
    RAZORPAY.failNext("POST", "/v2/accounts", 400, "Route feature not enabled for the merchant");
    HttpResponse<String> r =
        call("PUT", path(sellerBusiness, "/payout-account"), seller, payoutDetails());
    assertThat(r.statusCode()).as(r.body()).isEqualTo(422);
    assertThat(r.body()).contains("ROUTE_NOT_ENABLED");
    assertThat(
            json(call("GET", path(sellerBusiness, "/payout-account"), seller, null))
                .get("canTakeOnline")
                .asBoolean())
        .isFalse();
  }

  // --- Paying ---------------------------------------------------------------------------------

  @Test
  void onlineCheckoutNeedsTheListingSettingAndAPayoutAccount() {
    HttpResponse<String> notOffered = checkoutOnline();
    assertThat(notOffered.statusCode()).as(notOffered.body()).isEqualTo(400);

    acceptOnline();
    HttpResponse<String> noPayouts = checkoutOnline();
    assertThat(noPayouts.statusCode()).as(noPayouts.body()).isEqualTo(409);

    RAZORPAY.productStatus("under_review");
    setUpPayouts();
    assertThat(checkoutOnline().statusCode()).as("not active yet").isEqualTo(409);
  }

  @Test
  void aPaidOrderHoldsTheSellersShareAndReleasesItOnHandover() {
    String orderId = onlineOrder();
    JsonNode placed = order(seller, sellerBusiness, orderId);
    assertThat(placed.get("paymentMethod").asString()).isEqualTo("ONLINE");
    assertThat(placed.get("allowedActions").toString()).doesNotContain("ACCEPT", "PAID");
    assertThat(
            call("POST", path(sellerBusiness, "/orders/" + orderId + "/accept"), seller, null)
                .statusCode())
        .as("not before payment")
        .isEqualTo(409);

    JsonNode started = start(orderId);
    assertThat(started.get("keyId").asString()).isEqualTo(FakeRazorpay.KEY_ID);
    String rzOrder = started.get("razorpayOrderId").asString();
    assertThat(started.get("amountPaise").asLong()).isEqualTo(318_600);
    assertThat(RAZORPAY.amountOf(rzOrder)).isEqualTo(318_600);
    assertThat(start(orderId).get("razorpayOrderId").asString())
        .as("a second click reopens the same payment")
        .isEqualTo(rzOrder);

    String paymentId = RAZORPAY.pay(rzOrder, "captured");
    assertThat(confirm(orderId, rzOrder, paymentId, "forged").statusCode()).isEqualTo(400);
    assertThat(
            confirm(orderId, rzOrder, paymentId, hmac("wrong", rzOrder + "|" + paymentId))
                .statusCode())
        .isEqualTo(400);
    HttpResponse<String> ok =
        confirm(
            orderId, rzOrder, paymentId, hmac(FakeRazorpay.KEY_SECRET, rzOrder + "|" + paymentId));
    assertThat(ok.statusCode()).as(ok.body()).isEqualTo(200);
    assertThat(json(ok).get("status").asString()).isEqualTo("CAPTURED");
    assertThat(json(ok).get("transferStatus").asString()).isEqualTo("HELD");

    JsonNode transfer =
        RAZORPAY.calls("POST", "/v1/payments/" + paymentId + "/transfers").getFirst().body();
    assertThat(transfer.path("transfers").path(0).path("amount").asLong()).isEqualTo(318_600);
    assertThat(transfer.path("transfers").path(0).path("on_hold").asBoolean()).isTrue();

    JsonNode paid = order(seller, sellerBusiness, orderId);
    assertThat(paid.get("paymentStatus").asString()).isEqualTo("PAID");
    assertThat(paid.get("allowedActions").toString()).contains("ACCEPT");
    act(orderId, "accept", null);
    act(orderId, "ready", null);
    JsonNode done = act(orderId, "picked-up", null);
    assertThat(done.get("status").asString()).as("paid already").isEqualTo("COMPLETED");

    assertThat(payment(orderId).get("transferStatus").asString()).isEqualTo("RELEASED");
    String transferId =
        jdbc.queryForObject(
            "select transfer_id from payments where order_id = ?::uuid", String.class, orderId);
    assertThat(RAZORPAY.transfer(transferId).path("on_hold").asBoolean()).isFalse();
    // The seller sees the payment too; another business does not.
    assertThat(
            call("GET", path(sellerBusiness, "/orders/" + orderId + "/payment"), seller, null)
                .statusCode())
        .isEqualTo(200);
    String stranger = newPerson("stranger");
    String strangerBusiness = createBusiness(stranger, "Elsewhere");
    assertThat(
            call("GET", path(strangerBusiness, "/orders/" + orderId + "/payment"), stranger, null)
                .statusCode())
        .isEqualTo(404);
    assertThat(
            call("POST", path(strangerBusiness, "/orders/" + orderId + "/payment"), stranger, null)
                .statusCode())
        .isEqualTo(404);
  }

  @Test
  void aDeclinedPaidOrderIsRefundedOnceAndTheShareTakenBack() {
    String orderId = onlineOrder();
    String paymentId = payAndConfirm(orderId);

    act(orderId, "decline", Map.of("reason", "Stock is damaged"));

    JsonNode p = payment(orderId);
    assertThat(p.get("status").asString()).isEqualTo("REFUNDED");
    assertThat(p.get("transferStatus").asString()).isEqualTo("REVERSED");
    assertThat(order(buyer, buyerBusiness, orderId).get("paymentStatus").asString())
        .isEqualTo("REFUNDED");
    assertThat(callsFor("POST", "/v1/transfers/[^/]+/reversals")).isGreaterThanOrEqualTo(1);
    assertThat(RAZORPAY.calls("POST", "/v1/payments/" + paymentId + "/refund")).hasSize(1);

    paymentJobs.settleOpenPayments();
    assertThat(RAZORPAY.calls("POST", "/v1/payments/" + paymentId + "/refund"))
        .as("never twice")
        .hasSize(1);
  }

  @Test
  void theJobCatchesAPaymentWhoseBrowserClosedAndCapturesAnAuthorisedOne() {
    String orderId = onlineOrder();
    String rzOrder = start(orderId).get("razorpayOrderId").asString();
    String paymentId = RAZORPAY.pay(rzOrder, "authorized"); // no confirm: the tab was closed

    paymentJobs.settleOpenPayments();

    assertThat(RAZORPAY.calls("POST", "/v1/payments/" + paymentId + "/capture")).hasSize(1);
    assertThat(payment(orderId).get("status").asString()).isEqualTo("CAPTURED");
    assertThat(order(buyer, buyerBusiness, orderId).get("paymentStatus").asString())
        .isEqualTo("PAID");
  }

  @Test
  void aFailedTransferIsRetriedLaterAndMadeOnlyOnce() {
    String orderId = onlineOrder();
    String rzOrder = start(orderId).get("razorpayOrderId").asString();
    String paymentId = RAZORPAY.pay(rzOrder, "captured");
    RAZORPAY.failNext("POST", "/v1/payments/" + paymentId + "/transfers", 500);
    HttpResponse<String> r =
        confirm(
            orderId, rzOrder, paymentId, hmac(FakeRazorpay.KEY_SECRET, rzOrder + "|" + paymentId));
    assertThat(r.statusCode()).as("the buyer's payment still counts").isEqualTo(200);
    assertThat(json(r).get("transferStatus").asString()).isEqualTo("NONE");

    paymentJobs.settleOpenPayments();
    paymentJobs.settleOpenPayments();

    assertThat(payment(orderId).get("transferStatus").asString()).isEqualTo("HELD");
    assertThat(RAZORPAY.calls("POST", "/v1/payments/" + paymentId + "/transfers")).hasSize(2);
    assertThat(
            jdbc.queryForObject(
                "select count(distinct transfer_id) from payments where order_id = ?::uuid",
                Long.class,
                orderId))
        .isEqualTo(1);
  }

  @Test
  void anUnpaidOnlineOrderExpiresAfterAnHourAndGivesItsStockBack() {
    String orderId = onlineOrder();
    jdbc.update(
        "update orders set placed_at = now() - interval '2 hours' where id = ?::uuid", orderId);

    orderJobs.expireUnacceptedOrders();

    assertThat(order(buyer, buyerBusiness, orderId).get("status").asString()).isEqualTo("EXPIRED");
    assertThat(
            jdbc.queryForObject(
                "select status from inventory_reservations where order_id = ?::uuid",
                String.class,
                orderId))
        .isEqualTo("RELEASED");
    assertThat(
            call("POST", path(buyerBusiness, "/orders/" + orderId + "/payment"), buyer, null)
                .statusCode())
        .isEqualTo(409);
  }

  // --- Webhook --------------------------------------------------------------------------------

  HttpResponse<String> webhook(String body, String signature, String eventId) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + environment.getRequiredProperty("local.server.port")
                        + "/api/v1/payments/razorpay-webhook"))
            .header("Content-Type", "application/json")
            .header("X-Razorpay-Event-Id", eventId)
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (signature != null) {
      request.header("X-Razorpay-Signature", signature);
    }
    try {
      return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  @Test
  void theWebhookNeedsItsSignatureAndHandlesEachEventOnce() {
    String orderId = onlineOrder();
    String rzOrder = start(orderId).get("razorpayOrderId").asString();
    String paymentId = RAZORPAY.pay(rzOrder, "captured");
    String body =
        "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\""
            + paymentId
            + "\",\"order_id\":\""
            + rzOrder
            + "\"}}}}";
    String eventId = "evt_" + UUID.randomUUID().toString().substring(0, 8);

    assertThat(webhook(body, null, eventId).statusCode()).isEqualTo(400);
    assertThat(webhook(body, hmac("not-the-secret", body), eventId).statusCode()).isEqualTo(400);
    assertThat(payment(orderId).get("status").asString()).isEqualTo("CREATED");

    assertThat(webhook(body, hmac(WEBHOOK_SECRET, body), eventId).statusCode()).isEqualTo(200);
    assertThat(payment(orderId).get("status").asString()).isEqualTo("CAPTURED");
    int lookups = callsFor("GET", "/v1/orders/" + rzOrder + "/payments");

    assertThat(webhook(body, hmac(WEBHOOK_SECRET, body), eventId).statusCode()).isEqualTo(200);
    assertThat(callsFor("GET", "/v1/orders/" + rzOrder + "/payments"))
        .as("a repeated delivery is ignored")
        .isEqualTo(lookups);
  }
}
