package com.b2bmarket.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2bmarket.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Logged questions are deleted after the retention period (90 days by default). */
class AssistantRetentionIT extends IntegrationTestSupport {

  @Autowired AiConfig config;
  @Autowired JdbcTemplate jdbc;

  @Test
  void deletesQuestionsOlderThanTheRetentionPeriod() {
    UUID old = UUID.randomUUID();
    UUID recent = UUID.randomUUID();
    insert(old, Instant.now().minus(Duration.ofDays(91)));
    insert(recent, Instant.now().minus(Duration.ofDays(89)));

    config.deleteOldQuestions();

    assertThat(
            jdbc.queryForObject(
                "select count(*) from ai_search_requests where id = ?", Integer.class, old))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ai_search_requests where id = ?", Integer.class, recent))
        .isEqualTo(1);
  }

  void insert(UUID id, Instant at) {
    jdbc.update(
        "insert into ai_search_requests (id, created_at, question, outcome) values (?, ?, 'q', 'UNDERSTOOD')",
        id,
        Timestamp.from(at));
  }
}
