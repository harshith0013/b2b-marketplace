package com.b2bmarket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boots the whole application against a real PostgreSQL (with pgvector) and a real mock OAuth2
 * server in Docker. Tokens are genuinely signed and verified; nothing in the security chain is
 * mocked. Containers are shared by every integration test in the JVM.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestSupport {

  public static final String AUDIENCE = "b2b-marketplace-web";

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  System.getProperty("pgvector.image", "pgvector/pgvector:0.8.0-pg17"))
              .asCompatibleSubstituteFor("postgres"));

  static final GenericContainer<?> OAUTH =
      new GenericContainer<>(
              DockerImageName.parse(
                  System.getProperty(
                      "mock-oauth2.image", "ghcr.io/navikt/mock-oauth2-server:6.1.2")))
          .withEnv("JSON_CONFIG", readConfig())
          .withExposedPorts(8080)
          .waitingFor(Wait.forHttp("/default/.well-known/openid-configuration"));

  static {
    POSTGRES.start(); // Testcontainers' Ryuk removes both containers when the JVM exits.
    OAUTH.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("app.auth.issuer-uri", () -> issuer("default"));
    registry.add("app.auth.audience", () -> AUDIENCE);
  }

  protected static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @Autowired protected Environment environment;

  protected static String issuer(String issuerId) {
    return "http://" + OAUTH.getHost() + ":" + OAUTH.getMappedPort(8080) + "/" + issuerId;
  }

  /** A signed access token for a person with a verified email, from the given issuer. */
  protected static String tokenFor(
      String issuerId, String subject, String email, boolean verified) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("grant_type", "client_credentials");
    form.put("client_id", "integration-tests");
    form.put("client_secret", "not-checked");
    form.put("user_sub", subject);
    form.put("email", email);
    if (!verified) {
      form.put("email_verified", "false");
    }
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(issuer(issuerId) + "/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
            .build();
    return JSON.readTree(send(request).body()).get("access_token").asString();
  }

  protected static String tokenFor(String subject, String email) {
    return tokenFor("default", subject, email, true);
  }

  protected HttpResponse<String> call(String method, String path, String token, Object body) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(baseUrl() + path))
            .header("Accept", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
    if (body != null) {
      request.header("Content-Type", "application/json");
    }
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return send(request.build());
  }

  protected HttpResponse<String> get(String path) {
    return call("GET", path, null, null);
  }

  protected static JsonNode json(HttpResponse<String> response) {
    return JSON.readTree(response.body());
  }

  private String baseUrl() {
    return "http://localhost:" + environment.getRequiredProperty("local.server.port");
  }

  private static HttpResponse<String> send(HttpRequest request) {
    try {
      return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static String formEncode(Map<String, String> form) {
    return form.entrySet().stream()
        .map(
            e ->
                URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  private static String readConfig() {
    try {
      return new ClassPathResource("mock-oauth2/config.json")
          .getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
