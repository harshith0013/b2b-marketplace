package com.b2bmarket.payments;

import com.b2bmarket.auditing.AuditEntry;
import com.b2bmarket.auditing.AuditService;
import com.b2bmarket.businesses.BusinessDtos.BusinessDetails;
import com.b2bmarket.businesses.BusinessRole;
import com.b2bmarket.businesses.BusinessService;
import com.b2bmarket.businesses.TenantAccess;
import com.b2bmarket.orders.OnlinePayments;
import com.b2bmarket.payments.PaymentDtos.PayoutAccount;
import com.b2bmarket.payments.PaymentDtos.PayoutRequest;
import com.b2bmarket.payments.internal.PaymentRecords;
import com.b2bmarket.payments.internal.PaymentRecords.PayoutRow;
import com.b2bmarket.payments.internal.RazorpayClient;
import com.b2bmarket.payments.internal.RazorpayClient.RazorpayException;
import com.b2bmarket.payments.internal.RazorpayClient.RzProduct;
import com.b2bmarket.payments.internal.RazorpayProperties;
import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.TenantScope;
import com.b2bmarket.users.CurrentUser;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A seller's payout account: a Razorpay Route linked account with their bank account. Set up in
 * four Razorpay steps; each step's id is saved as soon as it exists, so a failed set-up resumes.
 */
@Service
public class PayoutService implements OnlinePayments {

  static final String ACTIVATED = "ACTIVATED";

  /** Problem code: Razorpay has not enabled Route on the platform's account. */
  public static final String ROUTE_NOT_ENABLED = "ROUTE_NOT_ENABLED";

  /** Razorpay is still deciding: look again when the seller opens the page. */
  static final Set<String> IN_PROGRESS =
      Set.of("CREATED", "REQUESTED", "NEEDS_CLARIFICATION", "UNDER_REVIEW");

  private final PaymentRecords records;
  private final RazorpayClient razorpay;
  private final RazorpayProperties properties;
  private final TenantAccess tenants;
  private final TenantScope scope;
  private final BusinessService businesses;
  private final AuditService audit;
  private final TransactionTemplate transaction;
  private final Clock clock;

  PayoutService(
      PaymentRecords records,
      RazorpayClient razorpay,
      RazorpayProperties properties,
      TenantAccess tenants,
      TenantScope scope,
      BusinessService businesses,
      AuditService audit,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.records = records;
    this.razorpay = razorpay;
    this.properties = properties;
    this.tenants = tenants;
    this.scope = scope;
    this.businesses = businesses;
    this.audit = audit;
    this.transaction = new TransactionTemplate(transactions);
    this.clock = clock;
  }

  @Override
  public boolean canTakeOnline(UUID sellerBusinessId) {
    if (!properties.enabled()) {
      return false;
    }
    return Boolean.TRUE.equals(
        transaction.execute(
            s -> {
              scope.enterPayments();
              return records.payout(sellerBusinessId).map(PayoutService::active).orElse(false);
            }));
  }

  public PayoutAccount get(CurrentUser caller, UUID business) {
    Optional<PayoutRow> row =
        transaction.execute(
            s -> {
              tenants.enter(caller, business, BusinessRole.OWNER_OR_MANAGER);
              return records.payout(business);
            });
    if (row.isPresent() && properties.enabled() && needsRefresh(row.get())) {
      refresh(row.get());
      row =
          transaction.execute(
              s -> {
                tenants.enter(caller, business, BusinessRole.OWNER_OR_MANAGER);
                return records.payout(business);
              });
    }
    return view(row.orElse(null));
  }

  /** Owner only. Creates the linked account, or finishes one whose set-up stopped part-way. */
  public PayoutAccount setUp(CurrentUser caller, UUID business, PayoutRequest request) {
    if (!properties.enabled()) {
      throw ApiException.unavailable("Online payment is not switched on yet.");
    }
    transaction.executeWithoutResult(s -> tenants.check(caller, business, BusinessRole.OWNER_ONLY));
    BusinessDetails details = businesses.get(caller, business);
    String last4 = request.accountNumber().substring(request.accountNumber().length() - 4);
    PayoutRow row =
        inPayments(
            () -> {
              Optional<PayoutRow> existing = records.lockPayout(business);
              if (existing.isPresent()
                  && Set.of(ACTIVATED, "UNDER_REVIEW").contains(existing.get().status())) {
                throw ApiException.conflict("The payout account is already set up.");
              }
              records.savePayoutBank(
                  business, last4, request.ifsc(), request.beneficiaryName(), clock.instant());
              return records.lockPayout(business).orElseThrow();
            });
    try {
      String account = row.razorpayAccountId();
      if (account == null) {
        String created =
            razorpay.createLinkedAccount(
                new RazorpayClient.NewAccount(
                    request.email(),
                    request.phone(),
                    details.legalName(),
                    request.businessType(),
                    request.contactName(),
                    request.street(),
                    details.city(),
                    details.state().toUpperCase(Locale.ROOT),
                    details.pincode()));
        step(business, created, null, null, "CREATED");
        account = created;
      }
      if (row.stakeholderId() == null) {
        String stakeholder =
            razorpay.createStakeholder(account, request.contactName(), request.email());
        step(business, null, stakeholder, null, "CREATED");
      }
      String product = row.productId();
      if (product == null) {
        RzProduct requested = razorpay.requestRoute(account);
        product = requested.id();
        step(business, null, null, product, status(requested.activationStatus()));
      }
      RzProduct settled =
          razorpay.setSettlementAccount(
              account, product, request.accountNumber(), request.ifsc(), request.beneficiaryName());
      step(business, null, null, null, status(settled.activationStatus()));
    } catch (RazorpayException e) {
      if (RazorpayClient.isRouteNotEnabled(e)) {
        // The platform's own Razorpay account lacks Route: nothing the seller can fix.
        throw ApiException.unprocessable(
            "Payouts are not switched on for this platform's Razorpay account yet.",
            Map.of("problems", java.util.List.of(ROUTE_NOT_ENABLED)));
      }
      if (e.status() == 400) {
        throw ApiException.badRequest("Razorpay did not accept these payout details.");
      }
      throw ApiException.unavailable("Razorpay could not be reached. Try again in a minute.");
    }
    inPayments(
        () -> {
          audit.record(
              new AuditEntry(
                  "payout.set_up",
                  caller.userId(),
                  business,
                  "payout_account",
                  business,
                  Map.of("ifsc", request.ifsc(), "bankLast4", last4)));
          return null;
        });
    return get(caller, business);
  }

  private void refresh(PayoutRow row) {
    try {
      RzProduct product = razorpay.fetchProduct(row.razorpayAccountId(), row.productId());
      step(row.businessId(), null, null, null, status(product.activationStatus()));
    } catch (RazorpayException e) {
      // Shown as it was; looked at again next time.
    }
  }

  private void step(
      UUID business, String account, String stakeholder, String product, String status) {
    inPayments(
        () -> {
          records.setPayoutStep(business, account, stakeholder, product, status, clock.instant());
          return null;
        });
  }

  private <T> T inPayments(java.util.function.Supplier<T> work) {
    return transaction.execute(
        s -> {
          scope.enterPayments();
          return work.get();
        });
  }

  private static boolean needsRefresh(PayoutRow row) {
    return IN_PROGRESS.contains(row.status())
        && row.razorpayAccountId() != null
        && row.productId() != null;
  }

  private static boolean active(PayoutRow row) {
    return ACTIVATED.equals(row.status()) && row.razorpayAccountId() != null;
  }

  /** Razorpay's activation status, in our words. */
  static String status(String activationStatus) {
    return switch (activationStatus == null ? "" : activationStatus) {
      case "activated" -> ACTIVATED;
      case "needs_clarification" -> "NEEDS_CLARIFICATION";
      case "under_review" -> "UNDER_REVIEW";
      case "suspended" -> "SUSPENDED";
      default -> "REQUESTED";
    };
  }

  private PayoutAccount view(PayoutRow row) {
    if (row == null) {
      return new PayoutAccount("NOT_SET_UP", null, null, null, false, properties.enabled());
    }
    return new PayoutAccount(
        row.status(),
        row.bankLast4(),
        row.ifscCode(),
        row.beneficiaryName(),
        properties.enabled() && active(row),
        properties.enabled());
  }

  /** The seller's linked account id, if payouts can be made to it. Payments role. */
  Optional<String> linkedAccount(UUID seller) {
    return records.payout(seller).filter(PayoutService::active).map(PayoutRow::razorpayAccountId);
  }
}
