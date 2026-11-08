package com.b2bmarket.orders.internal;

import com.b2bmarket.orders.CartService;
import com.b2bmarket.orders.OrderDtos.ActionRequest;
import com.b2bmarket.orders.OrderDtos.CartLine;
import com.b2bmarket.orders.OrderDtos.CartRequest;
import com.b2bmarket.orders.OrderDtos.CheckoutRequest;
import com.b2bmarket.orders.OrderDtos.Order;
import com.b2bmarket.orders.OrderDtos.Quote;
import com.b2bmarket.orders.OrderDtos.QuoteOffer;
import com.b2bmarket.orders.OrderDtos.QuoteOrderRequest;
import com.b2bmarket.orders.OrderDtos.QuoteRequest;
import com.b2bmarket.orders.OrderService;
import com.b2bmarket.orders.OrderStateMachine.Action;
import com.b2bmarket.orders.QuoteService;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.users.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cart, orders and quotes of the business in the path, acting as buyer or as seller. Membership,
 * roles and which party may do what are checked in the services.
 */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}")
class OrderController {

  private final CartService carts;
  private final OrderService orders;
  private final QuoteService quotes;

  OrderController(CartService carts, OrderService orders, QuoteService quotes) {
    this.carts = carts;
    this.orders = orders;
    this.quotes = quotes;
  }

  @GetMapping("/cart")
  List<CartLine> cart(CurrentUser caller, @PathVariable UUID businessId) {
    return carts.list(caller, businessId);
  }

  @PostMapping("/cart")
  List<CartLine> addToCart(
      CurrentUser caller, @PathVariable UUID businessId, @Valid @RequestBody CartRequest request) {
    return carts.add(caller, businessId, request);
  }

  @DeleteMapping("/cart/{listingId}")
  List<CartLine> removeFromCart(
      CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID listingId) {
    return carts.remove(caller, businessId, listingId);
  }

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  Order checkout(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @Valid @RequestBody CheckoutRequest request) {
    return carts.checkout(caller, businessId, request);
  }

  /** side=BUYER (orders this business placed) or SELLER (orders it received). */
  @GetMapping("/orders")
  List<Order> orders(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @RequestParam(defaultValue = "BUYER") String side,
      @RequestParam(defaultValue = "0") int page) {
    return orders.list(caller, businessId, seller(side), page);
  }

  @GetMapping("/orders/{orderId}")
  Order order(CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID orderId) {
    return orders.get(caller, businessId, orderId);
  }

  /** action: accept, decline, cancel, ready, out-for-delivery, delivered, picked-up, paid. */
  @PostMapping("/orders/{orderId}/{action}")
  Order act(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @PathVariable UUID orderId,
      @PathVariable String action,
      @Valid @RequestBody(required = false) ActionRequest request) {
    return orders.act(caller, businessId, orderId, action(action), request);
  }

  @PostMapping("/quotes")
  @ResponseStatus(HttpStatus.CREATED)
  Quote requestQuote(
      CurrentUser caller, @PathVariable UUID businessId, @Valid @RequestBody QuoteRequest request) {
    return quotes.request(caller, businessId, request);
  }

  @GetMapping("/quotes")
  List<Quote> quotes(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @RequestParam(defaultValue = "BUYER") String side) {
    return quotes.list(caller, businessId, seller(side));
  }

  @GetMapping("/quotes/{quoteId}")
  Quote quote(CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID quoteId) {
    return quotes.get(caller, businessId, quoteId);
  }

  @PostMapping("/quotes/{quoteId}/offer")
  Quote offer(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @PathVariable UUID quoteId,
      @Valid @RequestBody QuoteOffer request) {
    return quotes.offer(caller, businessId, quoteId, request.unitPricePaise());
  }

  @PostMapping("/quotes/{quoteId}/decline")
  Quote declineQuote(
      CurrentUser caller, @PathVariable UUID businessId, @PathVariable UUID quoteId) {
    return quotes.decline(caller, businessId, quoteId);
  }

  @PostMapping("/quotes/{quoteId}/order")
  @ResponseStatus(HttpStatus.CREATED)
  Order orderQuote(
      CurrentUser caller,
      @PathVariable UUID businessId,
      @PathVariable UUID quoteId,
      @Valid @RequestBody QuoteOrderRequest request) {
    return quotes.order(caller, businessId, quoteId, request);
  }

  private static boolean seller(String side) {
    return switch (side) {
      case "SELLER" -> true;
      case "BUYER" -> false;
      default -> throw ApiException.badRequest("side must be BUYER or SELLER.");
    };
  }

  static Action action(String path) {
    try {
      return Action.valueOf(path.toUpperCase(Locale.ROOT).replace('-', '_'));
    } catch (IllegalArgumentException e) {
      throw ApiException.notFound("No such step.");
    }
  }
}
