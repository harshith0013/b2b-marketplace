package com.b2bmarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Phase 1 foundations end to end: migrations, health probes and deny-by-default security. */
class ApplicationIT extends IntegrationTestSupport {

  @Autowired JdbcTemplate jdbc;

  @Test
  void flywayAppliedAllMigrations() {
    List<String> applied =
        jdbc.queryForList(
            "select version from flyway_schema_history where success order by installed_rank",
            String.class);
    assertThat(applied).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
  }

  @Test
  void requiredPostgresExtensionsAreInstalled() {
    List<String> extensions = jdbc.queryForList("select extname from pg_extension", String.class);
    assertThat(extensions).contains("vector", "pg_trgm");
  }

  @Test
  void healthAndProbesArePublicAndUp() {
    for (String path :
        List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
      HttpResponse<String> response = get(path);
      assertThat(response.statusCode()).as(path).isEqualTo(200);
      assertThat(response.body()).as(path).contains("\"UP\"");
    }
  }

  @Test
  void healthDoesNotExposeComponentDetails() {
    assertThat(get("/actuator/health").body()).doesNotContain("db", "diskSpace", "PostgreSQL");
  }

  @Test
  void systemInfoIsPublic() {
    HttpResponse<String> response = get("/api/v1/system/info");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("b2b-marketplace");
    assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
  }

  @Test
  void everythingElseIsDeniedByDefault() {
    for (String path : List.of("/api/v1/orders", "/actuator/env", "/actuator/beans", "/")) {
      assertThat(get(path).statusCode()).as(path).isIn(401, 403);
    }
  }
}
