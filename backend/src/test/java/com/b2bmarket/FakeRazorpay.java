package com.b2bmarket;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A stand-in for Razorpay's REST API on a local port, with just enough state for orders, payments,
 * Route transfers, refunds and linked accounts. Tests "pay" an order with {@link #pay}, make the
 * next call to a path fail with {@link #failNext}, and read every call from {@link #calls}.
 */
public final class FakeRazorpay {

  public static final String KEY_ID = "rzp_test_fake";
  public static final String KEY_SECRET = "fake_key_secret";

  public record Call(String method, String path, JsonNode body) {}

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final HttpServer server;
  private final AtomicInteger ids = new AtomicInteger();
  private final Map<String, ObjectNode> orders = new ConcurrentHashMap<>();
  private final Map<String, ObjectNode> payments = new ConcurrentHashMap<>();
  private final Map<String, ObjectNode> transfers = new ConcurrentHashMap<>();
  private final Map<String, List<String>> refunds = new ConcurrentHashMap<>();
  private final Map<String, Integer> failures = new ConcurrentHashMap<>();
  private final Map<String, String> failureTexts = new ConcurrentHashMap<>();
  private final List<Call> calls = new CopyOnWriteArrayList<>();
  private volatile String productStatus = "activated";

  public FakeRazorpay() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/", this::handle);
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.start();
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public List<Call> calls() {
    return calls;
  }

  public List<Call> calls(String method, String pathPattern) {
    return calls.stream()
        .filter(c -> c.method().equals(method) && c.path().matches(pathPattern))
        .toList();
  }

  /** The next call whose path matches answers with this HTTP status. */
  public void failNext(String pathPattern, int status) {
    failNext("ANY", pathPattern, status);
  }

  /** The next call with this method (or ANY) whose path matches answers with this HTTP status. */
  public void failNext(String method, String pathPattern, int status) {
    failures.put(method + " " + pathPattern, status);
  }

  /** Like {@link #failNext}, with Razorpay's own error description. */
  public void failNext(String method, String pathPattern, int status, String description) {
    failureTexts.put(method + " " + pathPattern, description);
    failNext(method, pathPattern, status);
  }

  /** What the linked account's Route product reports. */
  public void productStatus(String status) {
    productStatus = status;
  }

  /** The buyer pays the Razorpay order in full; returns the payment id. */
  public String pay(String razorpayOrderId, String status) {
    ObjectNode order = orders.get(razorpayOrderId);
    String id = next("pay");
    ObjectNode payment = JSON.createObjectNode();
    payment.put("id", id);
    payment.put("entity", "payment");
    payment.put("order_id", razorpayOrderId);
    payment.put("amount", order.path("amount").asLong());
    payment.put("status", status);
    payments.put(id, payment);
    return id;
  }

  public long amountOf(String razorpayOrderId) {
    return orders.get(razorpayOrderId).path("amount").asLong();
  }

  public JsonNode transfer(String id) {
    return transfers.get(id);
  }

  private static final Pattern ORDER_PAYMENTS = Pattern.compile("/v1/orders/([^/]+)/payments");
  private static final Pattern PAYMENT = Pattern.compile("/v1/payments/([^/]+)");
  private static final Pattern CAPTURE = Pattern.compile("/v1/payments/([^/]+)/capture");
  private static final Pattern PAYMENT_TRANSFERS =
      Pattern.compile("/v1/payments/([^/]+)/transfers");
  private static final Pattern REFUND = Pattern.compile("/v1/payments/([^/]+)/refund");
  private static final Pattern REFUNDS = Pattern.compile("/v1/payments/([^/]+)/refunds");
  private static final Pattern TRANSFER = Pattern.compile("/v1/transfers/([^/]+)");
  private static final Pattern REVERSAL = Pattern.compile("/v1/transfers/([^/]+)/reversals");
  private static final Pattern STAKEHOLDERS = Pattern.compile("/v2/accounts/([^/]+)/stakeholders");
  private static final Pattern PRODUCTS = Pattern.compile("/v2/accounts/([^/]+)/products");
  private static final Pattern PRODUCT = Pattern.compile("/v2/accounts/([^/]+)/products/([^/]+)");

  private void handle(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
    String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    JsonNode body = raw.isBlank() ? JSON.createObjectNode() : JSON.readTree(raw);
    calls.add(new Call(method, path, body));
    String expected =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((KEY_ID + ":" + KEY_SECRET).getBytes(StandardCharsets.UTF_8));
    if (!expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
      reply(exchange, 401, error("BAD_REQUEST_ERROR", "Authentication failed"));
      return;
    }
    for (var failure : failures.entrySet()) {
      String[] rule = failure.getKey().split(" ", 2);
      boolean methodMatches = rule[0].equals("ANY") || rule[0].equals(method);
      if (methodMatches && path.matches(rule[1]) && failures.remove(failure.getKey()) != null) {
        String text = failureTexts.getOrDefault(failure.getKey(), "Injected failure");
        failureTexts.remove(failure.getKey());
        reply(exchange, failure.getValue(), error("BAD_REQUEST_ERROR", text));
        return;
      }
    }
    Object answer;
    try {
      answer = route(method, path, body);
    } catch (IllegalArgumentException e) {
      reply(exchange, 400, error("BAD_REQUEST_ERROR", e.getMessage()));
      return;
    }
    if (answer == null) {
      reply(exchange, 404, error("BAD_REQUEST_ERROR", "The id provided does not exist"));
      return;
    }
    reply(exchange, 200, JSON.writeValueAsString(answer));
  }

  private Object route(String method, String path, JsonNode body) {
    Matcher m;
    if (method.equals("POST") && path.equals("/v1/orders")) {
      ObjectNode order = JSON.createObjectNode();
      order.put("id", next("order"));
      order.put("entity", "order");
      order.put("amount", body.path("amount").asLong());
      order.put("currency", body.path("currency").asString());
      order.put("receipt", body.path("receipt").asString());
      order.put("status", "created");
      orders.put(order.path("id").asString(), order);
      return order;
    }
    if ((m = ORDER_PAYMENTS.matcher(path)).matches()) {
      String orderId = m.group(1);
      return collection(
          payments.values().stream()
              .filter(p -> p.path("order_id").asString().equals(orderId))
              .toList());
    }
    if (method.equals("POST") && (m = CAPTURE.matcher(path)).matches()) {
      ObjectNode payment = payments.get(m.group(1));
      payment.put("status", "captured");
      return payment;
    }
    if ((m = PAYMENT_TRANSFERS.matcher(path)).matches()) {
      String paymentId = m.group(1);
      if (method.equals("POST")) {
        List<ObjectNode> made = new ArrayList<>();
        for (JsonNode t : body.path("transfers")) {
          ObjectNode transfer = JSON.createObjectNode();
          transfer.put("id", next("trf"));
          transfer.put("source", paymentId);
          transfer.put("recipient", t.path("account").asString());
          transfer.put("amount", t.path("amount").asLong());
          transfer.put("amount_reversed", 0);
          transfer.put("on_hold", t.path("on_hold").asBoolean());
          transfers.put(transfer.path("id").asString(), transfer);
          made.add(transfer);
        }
        return collection(made);
      }
      return collection(
          transfers.values().stream()
              .filter(t -> t.path("source").asString().equals(paymentId))
              .toList());
    }
    if (method.equals("POST") && (m = REFUND.matcher(path)).matches()) {
      String receipt = body.path("receipt").asString();
      List<String> made = refunds.computeIfAbsent(m.group(1), k -> new CopyOnWriteArrayList<>());
      if (made.contains(receipt)) {
        throw new IllegalArgumentException("duplicate receipt");
      }
      made.add(receipt);
      return Map.of("id", "rfnd_" + receipt.substring(0, 8), "status", "processed");
    }
    if ((m = REFUNDS.matcher(path)).matches()) {
      return Map.of(
          "items",
          refunds.getOrDefault(m.group(1), List.of()).stream()
              .map(r -> Map.of("id", "rfnd_" + r.substring(0, 8)))
              .toList());
    }
    if ((m = PAYMENT.matcher(path)).matches()) {
      return payments.get(m.group(1));
    }
    if (method.equals("POST") && (m = REVERSAL.matcher(path)).matches()) {
      ObjectNode transfer = transfers.get(m.group(1));
      transfer.put(
          "amount_reversed",
          transfer.path("amount_reversed").asLong() + body.path("amount").asLong());
      return Map.of("id", next("rvrsl"));
    }
    if ((m = TRANSFER.matcher(path)).matches()) {
      ObjectNode transfer = transfers.get(m.group(1));
      if (transfer != null && method.equals("PATCH")) {
        transfer.put("on_hold", body.path("on_hold").asBoolean());
      }
      return transfer;
    }
    if (method.equals("POST") && path.equals("/v2/accounts")) {
      if (!"route".equals(body.path("type").asString())) {
        throw new IllegalArgumentException("type must be route");
      }
      return Map.of("id", next("acc"), "status", "created");
    }
    if ((m = STAKEHOLDERS.matcher(path)).matches()) {
      return Map.of("id", next("sth"));
    }
    if (method.equals("POST") && (m = PRODUCTS.matcher(path)).matches()) {
      return Map.of("id", next("acc_prd"), "activation_status", "requested");
    }
    if ((m = PRODUCT.matcher(path)).matches()) {
      if (method.equals("PATCH")
          && !body.path("settlements").path("account_number").asString("").matches("[0-9]+")) {
        throw new IllegalArgumentException("account_number is invalid");
      }
      return Map.of("id", m.group(2), "activation_status", productStatus);
    }
    return null;
  }

  private String next(String prefix) {
    return prefix + "_Fake" + ids.incrementAndGet();
  }

  private static Map<String, Object> collection(List<? extends JsonNode> items) {
    return Map.of("entity", "collection", "count", items.size(), "items", items);
  }

  private static String error(String code, String description) {
    return JSON.writeValueAsString(
        Map.of("error", Map.of("code", code, "description", description)));
  }

  private static void reply(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
