package com.b2bmarket;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A stand-in for the AI provider's OpenAI-compatible chat API, on a local port. Tests set what it
 * answers next; it records every request so tests can check what was (and was not) sent.
 */
public final class FakeAiProvider {

  /** What the next request gets: a JSON content string, an HTTP error, or a slow answer. */
  public sealed interface Reply {
    record Content(String json) implements Reply {}

    record Error(int status) implements Reply {}

    record Slow(long millis) implements Reply {}
  }

  public record Seen(Map<String, List<String>> headers, String body) {}

  private final HttpServer server;
  private final AtomicReference<Reply> next = new AtomicReference<>(new Reply.Content("{}"));
  private final List<Seen> seen = new CopyOnWriteArrayList<>();

  public FakeAiProvider() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/chat/completions", this::handle);
    // Several at once: a deliberately slow answer must not hold up the next request.
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.start();
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public void answer(Reply reply) {
    next.set(reply);
  }

  public List<Seen> seen() {
    return seen;
  }

  private void handle(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    seen.add(new Seen(Map.copyOf(exchange.getRequestHeaders()), body));
    Reply reply = next.get();
    if (reply instanceof Reply.Slow slow) {
      try {
        Thread.sleep(slow.millis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      reply = new Reply.Content("{}");
    }
    if (reply instanceof Reply.Error error) {
      exchange.sendResponseHeaders(error.status(), -1);
      exchange.close();
      return;
    }
    String content = ((Reply.Content) reply).json();
    String response =
        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
            + quote(content)
            + "}}],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":30}}";
    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private static String quote(String text) {
    return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }
}
