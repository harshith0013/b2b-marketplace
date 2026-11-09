package com.b2bmarket.payments.internal;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Razorpay's REST API (Orders, Payments, Route, Refunds), with basic auth from the key id and
 * secret. Request bodies are never logged: they can hold bank details.
 */
@Component
public class RazorpayClient {

  private static final Logger log = LoggerFactory.getLogger(RazorpayClient.class);
  public static final String CURRENCY = "INR";

  /** Razorpay answered with an error, or could not be reached. Safe to log: no secrets. */
  public static class RazorpayException extends RuntimeException {
    private final int status;
    private final String code;

    RazorpayException(int status, String code, String description, Throwable cause) {
      super("Razorpay " + status + " " + code + ": " + description, cause);
      this.status = status;
      this.code = code;
    }

    public int status() {
      return status;
    }

    public String code() {
      return code;
    }

    boolean describes(String text) {
      return getMessage() != null && getMessage().contains(text);
    }
  }

  /** Razorpay refuses linked accounts until it enables Route on the platform's own account. */
  public static boolean isRouteNotEnabled(RazorpayException e) {
    return e.status() == 400 && e.describes("Route feature not enabled");
  }

  public record RzPayment(String id, String orderId, long amount, String status) {}

  public record RzTransfer(String id, String recipient, long amount, long amountReversed) {}

  public record RzProduct(String id, String activationStatus) {}

  /** What a linked account needs. */
  public record NewAccount(
      String email,
      String phone,
      String legalBusinessName,
      String businessType,
      String contactName,
      String street,
      String city,
      String state,
      String pincode) {}

  private final RestClient http;

  RazorpayClient(RazorpayProperties properties) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(properties.timeout());
    String credentials =
        Base64.getEncoder()
            .encodeToString(
                (nullToEmpty(properties.keyId()) + ":" + nullToEmpty(properties.keySecret()))
                    .getBytes(StandardCharsets.UTF_8));
    this.http =
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(factory)
            .defaultHeader("Authorization", "Basic " + credentials)
            .build();
  }

  // --- Orders and payments --------------------------------------------------------------------

  /** A Razorpay order for the amount; receipt is our payment id (max 40 characters). */
  public String createOrder(long amountPaise, String receipt, Map<String, String> notes) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("amount", amountPaise);
    body.put("currency", CURRENCY);
    body.put("receipt", receipt);
    body.put("notes", notes);
    return post("/v1/orders", body).path("id").asString();
  }

  public List<RzPayment> paymentsOfOrder(String razorpayOrderId) {
    List<RzPayment> payments = new ArrayList<>();
    for (JsonNode item : get("/v1/orders/" + razorpayOrderId + "/payments").path("items")) {
      payments.add(payment(item));
    }
    return payments;
  }

  public RzPayment fetchPayment(String paymentId) {
    return payment(get("/v1/payments/" + paymentId));
  }

  public RzPayment capture(String paymentId, long amountPaise) {
    return payment(
        post(
            "/v1/payments/" + paymentId + "/capture",
            Map.of("amount", amountPaise, "currency", CURRENCY)));
  }

  /** Refund in full. The receipt makes a repeat fail instead of refunding twice. */
  public String refund(String paymentId, String receipt) {
    return post("/v1/payments/" + paymentId + "/refund", Map.of("receipt", receipt))
        .path("id")
        .asString();
  }

  /** Refunds already made for the payment (to recover after a lost answer). */
  public List<String> refundsOf(String paymentId) {
    List<String> ids = new ArrayList<>();
    for (JsonNode item : get("/v1/payments/" + paymentId + "/refunds").path("items")) {
      ids.add(item.path("id").asString());
    }
    return ids;
  }

  // --- Route transfers ------------------------------------------------------------------------

  /** The seller's share, kept on hold until the goods are handed over. */
  public RzTransfer transferOnHold(
      String paymentId, String linkedAccountId, long amountPaise, String orderNote) {
    Map<String, Object> transfer = new LinkedHashMap<>();
    transfer.put("account", linkedAccountId);
    transfer.put("amount", amountPaise);
    transfer.put("currency", CURRENCY);
    transfer.put("notes", Map.of("order", orderNote));
    transfer.put("on_hold", true);
    JsonNode items =
        post("/v1/payments/" + paymentId + "/transfers", Map.of("transfers", List.of(transfer)))
            .path("items");
    return transfer(items.path(0));
  }

  /** Transfers already made from the payment (to recover after a lost answer). */
  public List<RzTransfer> transfersOf(String paymentId) {
    List<RzTransfer> transfers = new ArrayList<>();
    for (JsonNode item : get("/v1/payments/" + paymentId + "/transfers").path("items")) {
      transfers.add(transfer(item));
    }
    return transfers;
  }

  public RzTransfer fetchTransfer(String transferId) {
    return transfer(get("/v1/transfers/" + transferId));
  }

  public void releaseTransfer(String transferId) {
    send("PATCH", "/v1/transfers/" + transferId, Map.of("on_hold", false));
  }

  public void reverseTransfer(String transferId, long amountPaise) {
    post("/v1/transfers/" + transferId + "/reversals", Map.of("amount", amountPaise));
  }

  // --- Linked accounts (seller payouts) ------------------------------------------------------

  public String createLinkedAccount(NewAccount a) {
    Map<String, Object> registered = new LinkedHashMap<>();
    registered.put("street1", a.street());
    registered.put("street2", a.city());
    registered.put("city", a.city());
    registered.put("state", a.state());
    registered.put("postal_code", a.pincode());
    registered.put("country", "IN");
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("email", a.email());
    body.put("phone", a.phone());
    body.put("type", "route");
    body.put("legal_business_name", a.legalBusinessName());
    body.put("business_type", a.businessType());
    body.put("contact_name", a.contactName());
    body.put(
        "profile",
        Map.of(
            "category",
            "ecommerce",
            "subcategory",
            "wholesale",
            "addresses",
            Map.of("registered", registered)));
    return post("/v2/accounts", body).path("id").asString();
  }

  public String createStakeholder(String accountId, String name, String email) {
    return post("/v2/accounts/" + accountId + "/stakeholders", Map.of("name", name, "email", email))
        .path("id")
        .asString();
  }

  public RzProduct requestRoute(String accountId) {
    return product(
        post(
            "/v2/accounts/" + accountId + "/products",
            Map.of("product_name", "route", "tnc_accepted", true)));
  }

  public RzProduct setSettlementAccount(
      String accountId,
      String productId,
      String accountNumber,
      String ifsc,
      String beneficiaryName) {
    return product(
        send(
            "PATCH",
            "/v2/accounts/" + accountId + "/products/" + productId,
            Map.of(
                "settlements",
                Map.of(
                    "account_number",
                    accountNumber,
                    "ifsc_code",
                    ifsc,
                    "beneficiary_name",
                    beneficiaryName),
                "tnc_accepted",
                true)));
  }

  public RzProduct fetchProduct(String accountId, String productId) {
    return product(get("/v2/accounts/" + accountId + "/products/" + productId));
  }

  // --- HTTP -----------------------------------------------------------------------------------

  private JsonNode get(String path) {
    return call("GET", path, null);
  }

  private JsonNode post(String path, Object body) {
    return call("POST", path, body);
  }

  private JsonNode send(String method, String path, Object body) {
    return call(method, path, body);
  }

  private JsonNode call(String method, String path, Object body) {
    try {
      RestClient.RequestBodySpec request =
          http.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path);
      if (body != null) {
        request.contentType(MediaType.APPLICATION_JSON).body(body);
      }
      JsonNode response = request.retrieve().body(JsonNode.class);
      if (response == null) {
        throw new RazorpayException(0, "EMPTY", "no answer body", null);
      }
      return response;
    } catch (RestClientResponseException e) {
      JsonNode error = errorBody(e);
      RazorpayException failure =
          new RazorpayException(
              e.getStatusCode().value(),
              error.path("code").asString("UNKNOWN"),
              error.path("description").asString(""),
              null);
      log.warn("{} {} failed: {}", method, pathForLog(path), failure.getMessage());
      throw failure;
    } catch (RestClientException e) {
      log.warn("{} {} could not reach Razorpay: {}", method, pathForLog(path), e.toString());
      throw new RazorpayException(0, "UNREACHABLE", "could not reach Razorpay", e);
    }
  }

  private static JsonNode errorBody(RestClientResponseException e) {
    try {
      return tools.jackson.databind.json.JsonMapper.builder()
          .build()
          .readTree(e.getResponseBodyAsString())
          .path("error");
    } catch (RuntimeException unreadable) {
      return tools.jackson.databind.node.MissingNode.getInstance();
    }
  }

  /** Paths carry only provider ids; keep the shape, not the ids, in logs. */
  private static String pathForLog(String path) {
    return path.replaceAll("(acc|pay|order|trf|rfnd|acc_prd)_[A-Za-z0-9]+", "$1_…");
  }

  private static RzPayment payment(JsonNode p) {
    return new RzPayment(
        p.path("id").asString(),
        p.path("order_id").asString(null),
        p.path("amount").asLong(),
        p.path("status").asString());
  }

  private static RzTransfer transfer(JsonNode t) {
    return new RzTransfer(
        t.path("id").asString(),
        t.path("recipient").asString(null),
        t.path("amount").asLong(),
        t.path("amount_reversed").asLong(0));
  }

  private static RzProduct product(JsonNode p) {
    return new RzProduct(p.path("id").asString(), p.path("activation_status").asString(""));
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
