package com.b2bmarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots the whole application against a real PostgreSQL (with pgvector) in Docker and checks the
 * Phase 1 foundations end to end: migrations, health probes and deny-by-default security.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationIT {

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  System.getProperty("pgvector.image", "pgvector/pgvector:0.8.0-pg17"))
              .asCompatibleSubstituteFor("postgres"));

  static {
    POSTGRES.start(); // Shared by all tests in this JVM; Testcontainers' Ryuk cleans up.
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired Environment environment;

  private final HttpClient http = HttpClient.newHttpClient();

  private HttpResponse<String> get(String path) throws Exception {
    String port = environment.getRequiredProperty("local.server.port");
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void flywayAppliedTheBaselineMigration() {
    Integer applied =
        jdbc.queryForObject(
            "select count(*) from flyway_schema_history where version = '1' and success",
            Integer.class);
    assertThat(applied).isEqualTo(1);
  }

  @Test
  void requiredPostgresExtensionsAreInstalled() {
    List<String> extensions = jdbc.queryForList("select extname from pg_extension", String.class);
    assertThat(extensions).contains("vector", "pg_trgm");
  }

  @Test
  void healthAndProbesArePublicAndUp() throws Exception {
    for (String path :
        List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
      HttpResponse<String> response = get(path);
      assertThat(response.statusCode()).as(path).isEqualTo(200);
      assertThat(response.body()).as(path).contains("\"UP\"");
    }
  }

  @Test
  void healthDoesNotExposeComponentDetails() throws Exception {
    assertThat(get("/actuator/health").body()).doesNotContain("db", "diskSpace", "PostgreSQL");
  }

  @Test
  void systemInfoIsPublic() throws Exception {
    HttpResponse<String> response = get("/api/v1/system/info");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("b2b-marketplace");
    assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
  }

  @Test
  void everythingElseIsDeniedByDefault() throws Exception {
    for (String path : List.of("/api/v1/orders", "/actuator/env", "/actuator/beans", "/")) {
      assertThat(get(path).statusCode()).as(path).isIn(401, 403);
    }
  }
}
