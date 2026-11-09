package com.b2bmarket.orders;

import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.businesses.TenantAccess;
import com.b2bmarket.orders.OrderDtos.CartLine;
import com.b2bmarket.orders.OrderDtos.CartRequest;
import com.b2bmarket.orders.OrderDtos.CheckoutRequest;
import com.b2bmarket.orders.OrderDtos.Order;
import com.b2bmarket.orders.internal.OrderRecords;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.search.OfferDtos.Orderable;
import com.b2bmarket.search.OfferSearchService;
import com.b2bmarket.users.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A buyer business's cart. Offers are looked up as they are on sale now (outside the cart's
 * transaction); checkout places one order per seller.
 */
@Service
public class CartService {

  private final OrderRecords records;
  private final TenantAccess tenants;
  private final OfferSearchService offers;
  private final OrderService orders;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final OnlinePayments onlinePayments;

  /** Groups lines by seller without showing the seller's id: a keyed hash, per server run. */
  private final byte[] sellerKeySalt = new byte[16];

  CartService(
      OrderRecords records,
      TenantAccess tenants,
      OfferSearchService offers,
      OrderService orders,
      PlatformTransactionManager transactions,
      Clock clock,
      OnlinePayments onlinePayments) {
    this.onlinePayments = onlinePayments;
    this.records = records;
    this.tenants = tenants;
    this.offers = offers;
    this.orders = orders;
    this.transaction = new TransactionTemplate(transactions);
    this.clock = clock;
    new SecureRandom().nextBytes(sellerKeySalt);
  }

  public List<CartLine> add(CurrentUser caller, UUID buyer, CartRequest request) {
    Orderable offer = onSale(request.listingId());
    if (request.quantity() < offer.moq()) {
      throw ApiException.badRequest("Order at least the minimum order.");
    }
    if (request.quantity() > offer.stock()) {
      throw ApiException.conflict("Not enough stock.");
    }
    transaction.executeWithoutResult(
        s -> {
          tenants.enter(caller, buyer, BusinessRole.OWNER_OR_MANAGER);
          if (offer.sellerBusinessId().equals(buyer)) {
            throw ApiException.badRequest("You cannot order your own stock.");
          }
          records.upsertCart(buyer, offer.listingId(), request.quantity(), clock.instant());
        });
    return list(caller, buyer);
  }

  public List<CartLine> remove(CurrentUser caller, UUID buyer, UUID listingId) {
    transaction.executeWithoutResult(
        s -> {
          tenants.enter(caller, buyer, BusinessRole.OWNER_OR_MANAGER);
          records.removeFromCart(buyer, List.of(listingId));
        });
    return list(caller, buyer);
  }

  public List<CartLine> list(CurrentUser caller, UUID buyer) {
    List<OrderRecords.CartRow> rows =
        transaction.execute(
            s -> {
              tenants.enter(caller, buyer, BusinessRole.ANY);
              return records.cart(buyer);
            });
    // Online payment is offered only where the seller can be paid out (asked once per seller).
    Map<UUID, Boolean> sellerOnline = new java.util.HashMap<>();
    return rows.stream()
        .map(
            row -> {
              Optional<Orderable> found = offers.orderable(row.listingId());
              boolean online =
                  found
                      .filter(o -> o.delivery().onlineAccepted())
                      .map(
                          o ->
                              sellerOnline.computeIfAbsent(
                                  o.sellerBusinessId(), onlinePayments::canTakeOnline))
                      .orElse(false);
              return line(row, found, online);
            })
        .toList();
  }

  /** Places one order for the chosen cart lines (all from one seller). */
  public Order checkout(CurrentUser caller, UUID buyer, CheckoutRequest request) {
    Map<UUID, Long> inCart =
        transaction
            .execute(
                s -> {
                  tenants.enter(caller, buyer, BusinessRole.OWNER_OR_MANAGER);
                  return records.cart(buyer);
                })
            .stream()
            .collect(
                Collectors.toMap(OrderRecords.CartRow::listingId, OrderRecords.CartRow::quantity));
    List<OrderService.Wanted> wanted =
        request.listingIds().stream()
            .distinct()
            .map(
                id -> {
                  Long quantity = inCart.get(id);
                  if (quantity == null) {
                    throw ApiException.badRequest("That item is not in the cart any more.");
                  }
                  return new OrderService.Wanted(onSale(id), quantity, null);
                })
            .toList();
    return orders.place(
        caller,
        buyer,
        wanted,
        new OrderService.Choice(
            request.deliveryMethod(),
            request.deliveryAddress(),
            request.deliveryPincode(),
            request.paymentMethod()),
        request.idempotencyKey(),
        null);
  }

  private Orderable onSale(UUID listingId) {
    return offers
        .orderable(listingId)
        .orElseThrow(() -> ApiException.notFound("This offer is not on sale."));
  }

  private CartLine line(OrderRecords.CartRow row, Optional<Orderable> found, boolean online) {
    if (found.isEmpty()) {
      return new CartLine(
          row.listingId(),
          row.quantity(),
          false,
          null,
          null,
          0,
          0,
          null,
          null,
          null,
          false,
          false,
          List.of(),
          0,
          false,
          false);
    }
    Orderable o = found.get();
    long price;
    try {
      price = OrderMath.unitPrice(o.priceTiers(), row.quantity());
    } catch (ApiException belowMinimum) {
      price = o.priceTiers().isEmpty() ? 0 : o.priceTiers().getFirst().unitPricePaise();
    }
    return new CartLine(
        row.listingId(),
        row.quantity(),
        true,
        o.productLine(),
        o.unit(),
        o.moq(),
        price,
        sellerKey(o.sellerBusinessId()),
        o.confidential() ? null : o.sellerName(),
        o.city(),
        o.delivery().pickupAvailable(),
        o.delivery().ownDelivery(),
        o.delivery().deliveryPincodes(),
        o.delivery().deliveryChargePaise(),
        o.delivery().codAccepted(),
        online);
  }

  String sellerKey(UUID seller) {
    try {
      var mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(sellerKeySalt, "HmacSHA256"));
      return HexFormat.of()
          .formatHex(mac.doFinal(seller.toString().getBytes(StandardCharsets.UTF_8)))
          .substring(0, 16);
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
