package com.b2bmarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Carts, orders and quotes end to end: totals fixed at placement, stock reserved so two buyers can
 * never take the same units, each party limited to its own steps, and nobody else sees it.
 */
class OrderIT extends SellerFixtures {

  @Autowired com.b2bmarket.orders.OrderJobs jobs;

  String seller;
  String sellerBusiness;
  String buyer;
  String buyerBusiness;
  String listingId;

  @BeforeEach
  void anOfferAndABuyer() {
    seller = newPerson("seller");
    sellerBusiness = createBusiness(seller, "Stock Traders");
    listingId = readyListing(seller, sellerBusiness); // 500 pieces, MOQ 10, ₹30 / ₹27 from 100
    publish(seller, sellerBusiness, listingId);
    buyer = newPerson("buyer");
    buyerBusiness = createBusiness(buyer, "Corner Kirana");
  }

  String buyerPath(String rest) {
    return "/api/v1/businesses/" + buyerBusiness + rest;
  }

  String sellerPath(String rest) {
    return "/api/v1/businesses/" + sellerBusiness + rest;
  }

  void addToCart(String token, String business, long quantity) {
    HttpResponse<String> added =
        call(
            "POST",
            "/api/v1/businesses/" + business + "/cart",
            token,
            Map.of("listingId", listingId, "quantity", quantity));
    assertThat(added.statusCode()).as(added.body()).isEqualTo(200);
  }

  static Map<String, Object> pickup(String key) {
    return Map.of(
        "listingIds",
        List.of(),
        "deliveryMethod",
        "PICKUP",
        "paymentMethod",
        "PAY_ON_PICKUP",
        "idempotencyKey",
        key);
  }

  HttpResponse<String> checkout(String token, String business, String key) {
    Map<String, Object> body = new HashMap<>(pickup(key));
    body.put("listingIds", List.of(listingId));
    return call("POST", "/api/v1/businesses/" + business + "/orders", token, body);
  }

  JsonNode act(
      String token, String business, String orderId, String action, Map<String, Object> body) {
    HttpResponse<String> r =
        call(
            "POST",
            "/api/v1/businesses/" + business + "/orders/" + orderId + "/" + action,
            token,
            body);
    assertThat(r.statusCode()).as(action + ": " + r.body()).isEqualTo(200);
    return json(r);
  }

  long stock() {
    return jdbc.queryForObject(
        "select quantity from inventory_batches where id = ?::uuid",
        Long.class,
        batchOfListing(listingId));
  }

  @Test
  void aPickupOrderGoesFromCartToCompletedAndTakesTheStock() {
    addToCart(buyer, buyerBusiness, 100);
    JsonNode cart = json(call("GET", buyerPath("/cart"), buyer, null));
    assertThat(cart.get(0).get("unitPricePaise").asLong()).isEqualTo(2_700); // the 100+ tier

    HttpResponse<String> placed = checkout(buyer, buyerBusiness, UUID.randomUUID().toString());
    assertThat(placed.statusCode()).as(placed.body()).isEqualTo(201);
    JsonNode order = json(placed);
    String orderId = order.get("id").asString();
    assertThat(order.get("status").asString()).isEqualTo("PLACED");
    assertThat(order.get("itemsPaise").asLong()).isEqualTo(270_000);
    assertThat(order.get("gstPaise").asLong()).isEqualTo(48_600); // 18%
    assertThat(order.get("totalPaise").asLong()).isEqualTo(318_600);
    assertThat(order.get("pickupCity").asString()).isEqualTo("Guntur");
    assertThat(json(call("GET", buyerPath("/cart"), buyer, null)).size())
        .as("cart emptied")
        .isZero();

    JsonNode received = json(call("GET", sellerPath("/orders?side=SELLER"), seller, null));
    assertThat(received.get(0).get("buyerName").asString()).isEqualTo("Corner Kirana");
    assertThat(received.get(0).get("allowedActions").toString()).contains("ACCEPT", "DECLINE");

    act(seller, sellerBusiness, orderId, "accept", null);
    act(seller, sellerBusiness, orderId, "ready", null);
    assertThat(stock()).as("still reserved, not taken").isEqualTo(500);
    act(seller, sellerBusiness, orderId, "picked-up", null);
    assertThat(stock()).isEqualTo(400);
    JsonNode done = act(seller, sellerBusiness, orderId, "paid", null);
    assertThat(done.get("status").asString()).isEqualTo("COMPLETED");
    assertThat(done.get("paymentStatus").asString()).isEqualTo("PAID");
    assertThat(
            jdbc.queryForObject(
                "select status from inventory_reservations where order_id = ?::uuid",
                String.class,
                orderId))
        .isEqualTo("CONSUMED");
  }

  @Test
  void aDeliveredOrderNeedsTheSellersDeliveryAreaCodAndADeliveryPerson() {
    HttpResponse<String> set =
        call(
            "PUT",
            sellerPath("/listings/" + listingId + "/delivery"),
            seller,
            Map.of(
                "pickupAvailable", true,
                "ownDelivery", true,
                "deliveryPincodes", List.of("522002", "522003"),
                "deliveryChargePaise", 5_000,
                "codAccepted", true));
    assertThat(set.statusCode()).as(set.body()).isEqualTo(200);
    assertThat(
            json(get("/api/v1/offers/" + listingId)).get("delivery").get("codAccepted").asBoolean())
        .isTrue();

    addToCart(buyer, buyerBusiness, 10);
    Map<String, Object> outside =
        Map.of(
            "listingIds",
            List.of(listingId),
            "deliveryMethod",
            "SELLER_DELIVERY",
            "deliveryAddress",
            "12 Main Road",
            "deliveryPincode",
            "500001",
            "paymentMethod",
            "COD",
            "idempotencyKey",
            UUID.randomUUID().toString());
    assertThat(call("POST", buyerPath("/orders"), buyer, outside).statusCode()).isEqualTo(400);

    Map<String, Object> inside = new HashMap<>(outside);
    inside.put("deliveryPincode", "522003");
    JsonNode order = json(call("POST", buyerPath("/orders"), buyer, inside));
    assertThat(order.get("deliveryPaise").asLong()).isEqualTo(5_000);
    String orderId = order.get("id").asString();

    act(seller, sellerBusiness, orderId, "accept", null);
    assertThat(
            call("POST", sellerPath("/orders/" + orderId + "/out-for-delivery"), seller, Map.of())
                .statusCode())
        .as("delivery person required")
        .isEqualTo(400);
    act(
        seller,
        sellerBusiness,
        orderId,
        "out-for-delivery",
        Map.of("deliveryPersonName", "Ravi", "deliveryPersonPhone", "9876543210"));
    JsonNode seen = json(call("GET", buyerPath("/orders/" + orderId), buyer, null));
    assertThat(seen.get("deliveryPersonPhone").asString()).isEqualTo("9876543210");
    act(seller, sellerBusiness, orderId, "delivered", null);
    assertThat(act(seller, sellerBusiness, orderId, "paid", null).get("status").asString())
        .isEqualTo("COMPLETED");
  }

  @Test
  void twoBuyersCannotTakeTheSameStockEvenAtTheSameMoment() throws Exception {
    String other = newPerson("other");
    String otherBusiness = createBusiness(other, "Other Kirana");
    addToCart(buyer, buyerBusiness, 300);
    addToCart(other, otherBusiness, 300);

    List<Callable<Integer>> both =
        List.of(
            () -> checkout(buyer, buyerBusiness, UUID.randomUUID().toString()).statusCode(),
            () -> checkout(other, otherBusiness, UUID.randomUUID().toString()).statusCode());
    List<Integer> statuses = new ArrayList<>();
    try (var pool = Executors.newFixedThreadPool(2)) {
      for (Future<Integer> f : pool.invokeAll(both)) {
        statuses.add(f.get());
      }
    }
    assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    assertThat(
            jdbc.queryForObject(
                "select coalesce(sum(quantity), 0) from inventory_reservations where batch_id = ?::uuid and status = 'ACTIVE'",
                Long.class,
                batchOfListing(listingId)))
        .isEqualTo(300);
  }

  @Test
  void pressingPlaceOrderTwiceMakesOneOrder() {
    addToCart(buyer, buyerBusiness, 10);
    String key = UUID.randomUUID().toString();
    String first = json(checkout(buyer, buyerBusiness, key)).get("id").asString();
    addToCart(buyer, buyerBusiness, 10);
    String second = json(checkout(buyer, buyerBusiness, key)).get("id").asString();

    assertThat(second).isEqualTo(first);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from orders where buyer_business_id = ?::uuid",
                Integer.class,
                buyerBusiness))
        .isEqualTo(1);
  }

  @Test
  void eachPartyDoesOnlyItsOwnStepsAndNobodyElseSeesTheOrder() {
    addToCart(buyer, buyerBusiness, 10);
    String orderId =
        json(checkout(buyer, buyerBusiness, UUID.randomUUID().toString())).get("id").asString();

    assertThat(call("POST", buyerPath("/orders/" + orderId + "/accept"), buyer, null).statusCode())
        .as("the buyer cannot accept")
        .isEqualTo(409);
    String staff = newPerson("staff");
    addMember(sellerBusiness, staff, "STAFF");
    assertThat(call("POST", sellerPath("/orders/" + orderId + "/accept"), staff, null).statusCode())
        .as("seller staff cannot decide")
        .isEqualTo(403);
    assertThat(
            call("POST", sellerPath("/orders/" + orderId + "/decline"), seller, Map.of())
                .statusCode())
        .as("a decline needs a reason")
        .isEqualTo(400);

    String stranger = newPerson("stranger");
    String strangerBusiness = createBusiness(stranger, "Nosy Co");
    String strangerPath = "/api/v1/businesses/" + strangerBusiness + "/orders/" + orderId;
    assertThat(call("GET", strangerPath, stranger, null).statusCode()).isEqualTo(404);
    assertThat(call("POST", strangerPath + "/accept", stranger, null).statusCode()).isEqualTo(404);
    assertThat(call("GET", buyerPath("/orders/" + orderId), stranger, null).statusCode())
        .isEqualTo(404);
    assertThat(
            json(call(
                    "GET",
                    "/api/v1/businesses/" + strangerBusiness + "/orders?side=SELLER",
                    stranger,
                    null))
                .size())
        .isZero();

    act(seller, sellerBusiness, orderId, "decline", Map.of("reason", "Out of stock at the godown"));
    JsonNode seen = json(call("GET", buyerPath("/orders/" + orderId), buyer, null));
    assertThat(seen.get("status").asString()).isEqualTo("DECLINED");
    assertThat(seen.get("statusReason").asString()).contains("godown");
  }

  @Test
  void aConfidentialSellerIsNamedOnlyOnceTheyAccept() {
    jdbc.update("update listings set visibility = 'CONFIDENTIAL' where id = ?::uuid", listingId);
    addToCart(buyer, buyerBusiness, 10);
    JsonNode cart = json(call("GET", buyerPath("/cart"), buyer, null));
    assertThat(cart.get(0).get("sellerName").isNull()).isTrue();
    String orderId =
        json(checkout(buyer, buyerBusiness, UUID.randomUUID().toString())).get("id").asString();
    assertThat(
            json(call("GET", buyerPath("/orders/" + orderId), buyer, null))
                .get("sellerName")
                .isNull())
        .isTrue();

    act(seller, sellerBusiness, orderId, "accept", null);
    assertThat(
            json(call("GET", buyerPath("/orders/" + orderId), buyer, null))
                .get("sellerName")
                .asString())
        .isEqualTo("Stock Traders");
  }

  @Test
  void unacceptedOrdersExpireAndGiveTheStockBack() {
    addToCart(buyer, buyerBusiness, 400);
    String orderId =
        json(checkout(buyer, buyerBusiness, UUID.randomUUID().toString())).get("id").asString();
    jdbc.update(
        "update orders set placed_at = ? where id = ?::uuid",
        Timestamp.from(Instant.now().minusSeconds(25 * 3600)),
        orderId);

    jobs.expireUnacceptedOrders();

    assertThat(
            json(call("GET", buyerPath("/orders/" + orderId), buyer, null))
                .get("status")
                .asString())
        .isEqualTo("EXPIRED");
    assertThat(
            jdbc.queryForObject(
                "select status from inventory_reservations where order_id = ?::uuid",
                String.class,
                orderId))
        .isEqualTo("RELEASED");
    addToCart(buyer, buyerBusiness, 400);
    assertThat(checkout(buyer, buyerBusiness, UUID.randomUUID().toString()).statusCode())
        .as("stock free again")
        .isEqualTo(201);
  }

  @Test
  void aQuoteBecomesAnOrderAtTheQuotedPriceWhileValid() {
    JsonNode quote =
        json(
            call(
                "POST",
                buyerPath("/quotes"),
                buyer,
                Map.of("listingId", listingId, "quantity", 5, "note", "Small trial order")));
    String quoteId = quote.get("id").asString();
    assertThat(quote.get("status").asString()).isEqualTo("REQUESTED");
    assertThat(
            call(
                    "POST",
                    buyerPath("/quotes/" + quoteId + "/offer"),
                    buyer,
                    Map.of("unitPricePaise", 1))
                .statusCode())
        .as("only the seller prices")
        .isEqualTo(403);

    JsonNode offered =
        json(
            call(
                "POST",
                sellerPath("/quotes/" + quoteId + "/offer"),
                seller,
                Map.of("unitPricePaise", 2_900)));
    assertThat(offered.get("status").asString()).isEqualTo("OFFERED");

    Map<String, Object> how =
        Map.of(
            "deliveryMethod",
            "PICKUP",
            "paymentMethod",
            "PAY_ON_PICKUP",
            "idempotencyKey",
            UUID.randomUUID().toString());
    HttpResponse<String> ordered =
        call("POST", buyerPath("/quotes/" + quoteId + "/order"), buyer, how);
    assertThat(ordered.statusCode()).as(ordered.body()).isEqualTo(201);
    assertThat(json(ordered).get("items").get(0).get("unitPricePaise").asLong()).isEqualTo(2_900);
    assertThat(json(ordered).get("items").get(0).get("quantity").asLong())
        .as("below the MOQ, as quoted")
        .isEqualTo(5);
    assertThat(
            json(call("GET", buyerPath("/quotes/" + quoteId), buyer, null))
                .get("status")
                .asString())
        .isEqualTo("ORDERED");
    assertThat(
            call("POST", sellerPath("/quotes/" + quoteId + "/decline"), seller, null).statusCode())
        .as("an ordered quote cannot be declined")
        .isEqualTo(409);

    String second =
        json(call(
                "POST", buyerPath("/quotes"), buyer, Map.of("listingId", listingId, "quantity", 5)))
            .get("id")
            .asString();
    call(
        "POST",
        sellerPath("/quotes/" + second + "/offer"),
        seller,
        Map.of("unitPricePaise", 2_900));
    jdbc.update(
        "update quotes set valid_until = now() - interval '1 hour' where id = ?::uuid", second);
    assertThat(
            json(call("GET", buyerPath("/quotes/" + second), buyer, null)).get("status").asString())
        .isEqualTo("EXPIRED");
    Map<String, Object> again = new HashMap<>(how);
    again.put("idempotencyKey", UUID.randomUUID().toString());
    assertThat(call("POST", buyerPath("/quotes/" + second + "/order"), buyer, again).statusCode())
        .isEqualTo(409);
  }

  @Test
  void stockThatExpiredAfterOrderingCannotBeSent() {
    addToCart(buyer, buyerBusiness, 10);
    String orderId =
        json(checkout(buyer, buyerBusiness, UUID.randomUUID().toString())).get("id").asString();
    act(seller, sellerBusiness, orderId, "accept", null);
    String batch = batchOfListing(listingId);
    jdbc.update(
        "update inventory_batches set expires_on = current_date - 1, status = 'PENDING_REVIEW' where id = ?::uuid",
        batch);
    jdbc.update("update inventory_batches set status = 'APPROVED' where id = ?::uuid", batch);

    HttpResponse<String> refused =
        call("POST", sellerPath("/orders/" + orderId + "/ready"), seller, null);

    assertThat(refused.statusCode()).isEqualTo(422);
    assertThat(refused.body()).contains("EXPIRED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from compliance_checks where listing_id = ?::uuid and stage = 'DISPATCH'",
                Integer.class,
                listingId))
        .as("the refused dispatch check is not stored (its transaction rolled back)")
        .isZero();
  }

  @Test
  void ownStockCannotBeOrdered() {
    HttpResponse<String> own =
        call("POST", sellerPath("/cart"), seller, Map.of("listingId", listingId, "quantity", 10));
    assertThat(own.statusCode()).isEqualTo(400);
  }
}
