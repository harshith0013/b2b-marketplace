package com.b2bmarket.orders;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.businesses.BusinessDtos.BusinessSummary;
import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.businesses.BusinessService;
import com.b2bmarket.businesses.TenantAccess;
import com.b2bmarket.orders.OrderDtos.Order;
import com.b2bmarket.orders.OrderDtos.Quote;
import com.b2bmarket.orders.OrderDtos.QuoteOrderRequest;
import com.b2bmarket.orders.OrderDtos.QuoteRequest;
import com.b2bmarket.orders.internal.OrderRecords;
import com.b2bmarket.orders.internal.OrderRecords.QuoteRow;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.TenantScope;
import com.b2bmarket.platform.Text;
import com.b2bmarket.search.OfferDtos.Orderable;
import com.b2bmarket.search.OfferSearchService;
import com.b2bmarket.users.CurrentUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Quotes: a buyer asks, the seller offers a price valid for 48 hours, the buyer orders it. */
@Service
public class QuoteService {

  static final Duration VALID_FOR = Duration.ofHours(48);
  static final int LIST_SIZE = 50;

  private final OrderRecords records;
  private final TenantAccess tenants;
  private final TenantScope scope;
  private final OfferSearchService offers;
  private final OrderService orders;
  private final BusinessService businesses;
  private final AuditService audit;
  private final TransactionTemplate transaction;
  private final Clock clock;

  QuoteService(
      OrderRecords records,
      TenantAccess tenants,
      TenantScope scope,
      OfferSearchService offers,
      OrderService orders,
      BusinessService businesses,
      AuditService audit,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.records = records;
    this.tenants = tenants;
    this.scope = scope;
    this.offers = offers;
    this.orders = orders;
    this.businesses = businesses;
    this.audit = audit;
    this.transaction = new TransactionTemplate(transactions);
    this.clock = clock;
  }

  public Quote request(CurrentUser caller, UUID buyer, QuoteRequest request) {
    Orderable offer =
        offers
            .orderable(request.listingId())
            .orElseThrow(() -> ApiException.notFound("This offer is not on sale."));
    if (request.quantity() > offer.stock()) {
      throw ApiException.conflict("Not enough stock.");
    }
    UUID id = UUID.randomUUID();
    transaction.executeWithoutResult(
        s -> {
          tenants.check(caller, buyer, BusinessRole.OWNER_OR_MANAGER);
          if (offer.sellerBusinessId().equals(buyer)) {
            throw ApiException.badRequest("You cannot ask yourself for a quote.");
          }
          scope.enterOrdering(buyer);
          Instant now = clock.instant();
          records.insertQuote(
              new QuoteRow(
                  id,
                  buyer,
                  offer.sellerBusinessId(),
                  offer.listingId(),
                  offer.confidential(),
                  offer.productLine(),
                  offer.unit(),
                  request.quantity(),
                  Text.optional(request.note()),
                  "REQUESTED",
                  null,
                  null,
                  now),
              now);
          audit.record(
              new AuditEntry(
                  "quote.requested",
                  caller.userId(),
                  buyer,
                  "quote",
                  id,
                  Map.of("quantity", request.quantity())));
        });
    return get(caller, buyer, id);
  }

  public Quote offer(CurrentUser caller, UUID seller, UUID quoteId, long unitPricePaise) {
    return transaction.execute(
        s -> {
          tenants.enter(caller, seller, BusinessRole.OWNER_OR_MANAGER);
          QuoteRow q = load(quoteId, seller);
          if (!q.seller().equals(seller)) {
            throw ApiException.forbidden("Only the seller can offer a price.");
          }
          Instant now = clock.instant();
          if (records.offerQuote(quoteId, unitPricePaise, now.plus(VALID_FOR), now) == 0) {
            throw ApiException.conflict("This quote has already been answered.");
          }
          audit.record(
              new AuditEntry(
                  "quote.offered",
                  caller.userId(),
                  seller,
                  "quote",
                  quoteId,
                  Map.of("unitPricePaise", unitPricePaise)));
          return view(load(quoteId, seller), seller);
        });
  }

  public Quote decline(CurrentUser caller, UUID seller, UUID quoteId) {
    return transaction.execute(
        s -> {
          tenants.enter(caller, seller, BusinessRole.OWNER_OR_MANAGER);
          QuoteRow q = load(quoteId, seller);
          boolean open = "REQUESTED".equals(q.status()) || "OFFERED".equals(q.status());
          // Check first, then change: never touch a quote that is no longer open.
          if (!q.seller().equals(seller)
              || !open
              || records.quoteStatus(quoteId, q.status(), "DECLINED", clock.instant()) == 0) {
            throw ApiException.conflict("This quote cannot be declined now.");
          }
          audit.record(
              new AuditEntry(
                  "quote.declined", caller.userId(), seller, "quote", quoteId, Map.of()));
          return view(load(quoteId, seller), seller);
        });
  }

  /** The buyer orders an offered quote: its quantity at its price, while still valid. */
  public Order order(CurrentUser caller, UUID buyer, UUID quoteId, QuoteOrderRequest request) {
    QuoteRow q =
        transaction.execute(
            s -> {
              tenants.enter(caller, buyer, BusinessRole.OWNER_OR_MANAGER);
              return load(quoteId, buyer);
            });
    if (!q.buyer().equals(buyer) || !"OFFERED".equals(q.status())) {
      throw ApiException.conflict("This quote has no price to order yet.");
    }
    if (!clock.instant().isBefore(q.validUntil())) {
      throw ApiException.conflict("This quote has expired. Ask for a new one.");
    }
    Orderable offer =
        offers
            .orderable(q.listingId())
            .orElseThrow(() -> ApiException.conflict("This offer is no longer on sale."));
    return orders.place(
        caller,
        buyer,
        List.of(new OrderService.Wanted(offer, q.quantity(), q.offeredUnitPricePaise())),
        new OrderService.Choice(
            request.deliveryMethod(),
            request.deliveryAddress(),
            request.deliveryPincode(),
            request.paymentMethod()),
        request.idempotencyKey(),
        quoteId);
  }

  public Quote get(CurrentUser caller, UUID business, UUID quoteId) {
    return transaction.execute(
        s -> {
          tenants.enter(caller, business, BusinessRole.ANY);
          return view(load(quoteId, business), business);
        });
  }

  public List<Quote> list(CurrentUser caller, UUID business, boolean asSeller) {
    return transaction.execute(
        s -> {
          tenants.enter(caller, business, BusinessRole.ANY);
          return records.quotes(business, asSeller, LIST_SIZE).stream()
              .map(q -> view(q, business))
              .toList();
        });
  }

  private QuoteRow load(UUID quoteId, UUID business) {
    return records
        .quote(quoteId, business)
        .orElseThrow(() -> ApiException.notFound("Quote not found."));
  }

  private Quote view(QuoteRow q, UUID viewer) {
    boolean seller = q.seller().equals(viewer);
    Map<UUID, BusinessSummary> names = businesses.summaries(List.of(q.buyer(), q.seller()));
    String status =
        "OFFERED".equals(q.status()) && !clock.instant().isBefore(q.validUntil())
            ? "EXPIRED"
            : q.status();
    boolean hideSeller = !seller && q.sellerConfidential();
    return new Quote(
        q.id(),
        seller ? "SELLER" : "BUYER",
        q.listingId(),
        q.productLine(),
        q.quantity(),
        q.unit(),
        q.note(),
        status,
        q.offeredUnitPricePaise(),
        q.validUntil(),
        names.get(q.buyer()) == null ? null : names.get(q.buyer()).name(),
        hideSeller || names.get(q.seller()) == null ? null : names.get(q.seller()).name(),
        q.createdAt());
  }
}
