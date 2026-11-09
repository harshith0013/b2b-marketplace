package com.b2bmarket.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b2bmarket.orders.OrderStateMachine.Action;
import com.b2bmarket.orders.OrderStateMachine.Side;
import com.b2bmarket.orders.OrderStateMachine.Status;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.search.OfferDtos.Delivery;
import com.b2bmarket.search.OfferDtos.Orderable;
import com.b2bmarket.search.OfferDtos.PriceTier;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderRulesTest {

  static final List<PriceTier> TIERS = List.of(new PriceTier(10, 3_000), new PriceTier(100, 2_700));

  @Test
  void priceIsTheHighestTierTheQuantityReaches() {
    assertThat(OrderMath.unitPrice(TIERS, 10)).isEqualTo(3_000);
    assertThat(OrderMath.unitPrice(TIERS, 99)).isEqualTo(3_000);
    assertThat(OrderMath.unitPrice(TIERS, 100)).isEqualTo(2_700);
    assertThatThrownBy(() -> OrderMath.unitPrice(TIERS, 9)).isInstanceOf(ApiException.class);
  }

  @Test
  void gstIsRoundedHalfUpPerLineAndTotalsAddUp() {
    OrderMath.Line a = OrderMath.line(3, 333, 1800); // 999 paise, GST 179.82 -> 180
    OrderMath.Line b = OrderMath.line(1, 25, 500); // 25 paise, GST 1.25 -> 1
    assertThat(a.gstPaise()).isEqualTo(180);
    assertThat(b.gstPaise()).isEqualTo(1);
    OrderMath.Totals t = OrderMath.totals(List.of(a, b), 5_000);
    assertThat(t).isEqualTo(new OrderMath.Totals(1_024, 181, 5_000, 6_205));
  }

  @Test
  void onlyTheRightSideMayTakeEachStep() {
    assertThat(OrderStateMachine.next(Status.PLACED, Action.ACCEPT, Side.SELLER, "PICKUP"))
        .isEqualTo(Status.ACCEPTED);
    assertThatThrownBy(
            () -> OrderStateMachine.next(Status.PLACED, Action.ACCEPT, Side.BUYER, "PICKUP"))
        .isInstanceOf(ApiException.class);
    assertThat(OrderStateMachine.next(Status.PLACED, Action.CANCEL, Side.BUYER, "PICKUP"))
        .isEqualTo(Status.CANCELLED);
    assertThatThrownBy(
            () -> OrderStateMachine.next(Status.ACCEPTED, Action.CANCEL, Side.BUYER, "PICKUP"))
        .as("buyers cancel only before acceptance")
        .isInstanceOf(ApiException.class);
    assertThat(OrderStateMachine.next(Status.ACCEPTED, Action.CANCEL, Side.SELLER, "PICKUP"))
        .isEqualTo(Status.CANCELLED);
  }

  @Test
  void pickupAndDeliveryFollowTheirOwnPaths() {
    assertThat(OrderStateMachine.allowed(Status.ACCEPTED, Side.SELLER, "PICKUP"))
        .containsExactlyInAnyOrder(Action.READY, Action.CANCEL);
    assertThat(OrderStateMachine.allowed(Status.ACCEPTED, Side.SELLER, "SELLER_DELIVERY"))
        .containsExactlyInAnyOrder(Action.OUT_FOR_DELIVERY, Action.CANCEL);
    assertThat(
            OrderStateMachine.next(
                Status.OUT_FOR_DELIVERY, Action.DELIVERED, Side.SELLER, "SELLER_DELIVERY"))
        .isEqualTo(Status.DELIVERED);
    assertThat(
            OrderStateMachine.next(Status.DELIVERED, Action.PAID, Side.SELLER, "SELLER_DELIVERY"))
        .isEqualTo(Status.COMPLETED);
    assertThat(OrderStateMachine.allowed(Status.COMPLETED, Side.SELLER, "PICKUP")).isEmpty();
    assertThat(OrderStateMachine.allowed(Status.PLACED, Side.BUYER, "PICKUP"))
        .containsExactly(Action.CANCEL);
  }

  static Orderable offer(UUID seller, String city, boolean pickup, boolean delivers, boolean cod) {
    return offer(seller, city, pickup, delivers, cod, false);
  }

  static Orderable offer(
      UUID seller, String city, boolean pickup, boolean delivers, boolean cod, boolean online) {
    return new Orderable(
        UUID.randomUUID(),
        seller,
        UUID.randomUUID(),
        false,
        "Seller",
        "Soap, 100 G",
        10,
        "PIECE",
        1800,
        500,
        city,
        "Andhra Pradesh",
        new Delivery(pickup, delivers, List.of("522002"), 4_000, cod, online),
        TIERS);
  }

  static OrderService.Choice pickup() {
    return new OrderService.Choice("PICKUP", null, null, "PAY_ON_PICKUP");
  }

  static OrderService.Choice delivery(String pincode) {
    return new OrderService.Choice("SELLER_DELIVERY", "12 Main Road", pincode, "COD");
  }

  @Test
  void planChecksOneSellerQuantitiesDeliveryAndPayment() {
    UUID buyer = UUID.randomUUID();
    UUID seller = UUID.randomUUID();
    Orderable soap = offer(seller, "Guntur", true, true, true);

    OrderService.Plan plan =
        OrderService.plan(
            buyer, List.of(new OrderService.Wanted(soap, 100, null)), delivery("522002"));
    assertThat(plan.totals().itemsPaise()).isEqualTo(270_000);
    assertThat(plan.totals().deliveryPaise()).isEqualTo(4_000);

    assertThatThrownBy(
            () ->
                OrderService.plan(
                    seller, List.of(new OrderService.Wanted(soap, 10, null)), pickup()))
        .hasMessageContaining("own stock");
    Orderable other = offer(UUID.randomUUID(), "Guntur", true, false, false);
    assertThatThrownBy(
            () ->
                OrderService.plan(
                    buyer,
                    List.of(
                        new OrderService.Wanted(soap, 10, null),
                        new OrderService.Wanted(other, 10, null)),
                    pickup()))
        .hasMessageContaining("one seller");
    assertThatThrownBy(
            () ->
                OrderService.plan(
                    buyer, List.of(new OrderService.Wanted(soap, 10, null)), delivery("500001")))
        .hasMessageContaining("PIN code");
    assertThatThrownBy(
            () ->
                OrderService.plan(buyer, List.of(new OrderService.Wanted(soap, 5, null)), pickup()))
        .hasMessageContaining("minimum order");
    assertThatThrownBy(
            () ->
                OrderService.plan(
                    buyer, List.of(new OrderService.Wanted(soap, 501, null)), pickup()))
        .hasMessageContaining("not enough stock");
    Orderable noCod = offer(seller, "Guntur", true, true, false);
    assertThatThrownBy(
            () ->
                OrderService.plan(
                    buyer, List.of(new OrderService.Wanted(noCod, 10, null)), delivery("522002")))
        .hasMessageContaining("cash on delivery");
  }

  @Test
  void aQuotedPriceReplacesTheTierAndTheMinimumOrder() {
    Orderable soap = offer(UUID.randomUUID(), "Guntur", true, false, false);
    OrderService.Plan plan =
        OrderService.plan(
            UUID.randomUUID(), List.of(new OrderService.Wanted(soap, 5, 2_500L)), pickup());
    assertThat(plan.items().getFirst().unitPricePaise()).isEqualTo(2_500);
    assertThat(plan.totals().itemsPaise()).isEqualTo(12_500);
  }

  @Test
  void onlinePaymentNeedsTheSellersSayOnEveryItem() {
    UUID buyer = UUID.randomUUID();
    UUID seller = UUID.randomUUID();
    Orderable online = offer(seller, "Guntur", true, true, false, true);
    Orderable offline = offer(seller, "Guntur", true, true, false, false);
    OrderService.Choice payOnline = new OrderService.Choice("PICKUP", null, null, "ONLINE");
    OrderService.Choice deliverOnline =
        new OrderService.Choice("SELLER_DELIVERY", "12 Main Road", "522002", "ONLINE");

    assertThat(
            OrderService.plan(buyer, List.of(new OrderService.Wanted(online, 10, null)), payOnline)
                .totals()
                .itemsPaise())
        .isEqualTo(30_000);
    // Delivered and paid online: no cash on delivery needed.
    assertThat(
            OrderService.plan(
                    buyer, List.of(new OrderService.Wanted(online, 10, null)), deliverOnline)
                .totals()
                .deliveryPaise())
        .isEqualTo(4_000);
    assertThatThrownBy(
            () ->
                OrderService.plan(
                    buyer,
                    List.of(
                        new OrderService.Wanted(online, 10, null),
                        new OrderService.Wanted(offline, 10, null)),
                    payOnline))
        .hasMessageContaining("online payment");
    assertThatThrownBy(
            () ->
                OrderService.plan(
                    buyer,
                    List.of(new OrderService.Wanted(online, 10, null)),
                    new OrderService.Choice("PICKUP", null, null, "COD")))
        .hasMessageContaining("paid on pickup or online");
  }
}
