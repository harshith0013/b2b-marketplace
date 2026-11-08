package com.b2bmarket.ai.internal;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * One call to an OpenAI-compatible chat completions API with a JSON-schema answer format. Nothing
 * else: no tools, no conversation history.
 */
@Component
public class ChatCompletionsClient {

  /** The answer, and what it used, as reported by the provider. */
  public record Answer(String content, Integer promptTokens, Integer completionTokens) {}

  /** The provider did not answer in time. */
  public static class TimeoutException extends RuntimeException {
    TimeoutException(Throwable cause) {
      super("AI provider timed out", cause);
    }
  }

  /** The provider's quota is used up (HTTP 429). */
  public static class RateLimitedException extends RuntimeException {
    RateLimitedException(Throwable cause) {
      super("AI provider quota exceeded", cause);
    }
  }

  /** The provider answered with an error, or could not be reached. */
  public static class ProviderException extends RuntimeException {
    ProviderException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private final AiProperties properties;
  private final RestClient http;

  ChatCompletionsClient(AiProperties properties) {
    this.properties = properties;
    HttpClient client = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(properties.timeout());
    this.http =
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(factory)
            .defaultHeader("Authorization", "Bearer " + properties.apiKey())
            .build();
  }

  public Answer complete(String system, String user, Map<String, Object> schema) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", properties.model());
    if (properties.reasoningEffort() != null && !properties.reasoningEffort().isBlank()) {
      body.put("reasoning_effort", properties.reasoningEffort());
    }
    body.put(
        "messages",
        List.of(
            Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
    body.put(
        "response_format",
        Map.of(
            "type",
            "json_schema",
            "json_schema",
            Map.of("name", "search_filters", "strict", true, "schema", schema)));
    JsonNode response;
    try {
      response =
          http.post()
              .uri("/chat/completions")
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(JsonNode.class);
    } catch (ResourceAccessException e) {
      if (isTimeout(e)) {
        throw new TimeoutException(e);
      }
      throw new ProviderException("AI provider unreachable", e);
    } catch (HttpClientErrorException.TooManyRequests e) {
      throw new RateLimitedException(e);
    } catch (RestClientException e) {
      throw new ProviderException("AI provider error", e);
    }
    if (response == null || !response.path("choices").path(0).path("message").has("content")) {
      throw new ProviderException("AI provider answer had no content", null);
    }
    JsonNode usage = response.path("usage");
    return new Answer(
        response.path("choices").path(0).path("message").path("content").asString(),
        usage.has("prompt_tokens") ? usage.path("prompt_tokens").asInt() : null,
        usage.has("completion_tokens") ? usage.path("completion_tokens").asInt() : null);
  }

  private static boolean isTimeout(Throwable e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof HttpTimeoutException
          || cause instanceof java.net.SocketTimeoutException) {
        return true;
      }
    }
    return false;
  }
}
