package com.b2bmarket.ai.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The assistant's usage counter and question log. Each call is its own short transaction. */
@Repository
public class AiRecords {

  private final JdbcClient jdbc;

  AiRecords(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Counts one more request for the day and returns the new total. */
  public int countRequest(LocalDate day) {
    return jdbc.sql(
            """
            insert into ai_usage_daily (day, requests) values (:day, 1)
            on conflict (day) do update set requests = ai_usage_daily.requests + 1
            returning requests
            """)
        .param("day", day)
        .query(Integer.class)
        .single();
  }

  /** Counts one more label reading for the day and returns the new total. */
  public int countVisionRequest(LocalDate day) {
    return jdbc.sql(
            """
            insert into ai_usage_daily (day, vision_requests) values (:day, 1)
            on conflict (day) do update set vision_requests = ai_usage_daily.vision_requests + 1
            returning vision_requests
            """)
        .param("day", day)
        .query(Integer.class)
        .single();
  }

  public void addTokens(LocalDate day, Integer prompt, Integer completion) {
    jdbc.sql(
            "update ai_usage_daily set prompt_tokens = prompt_tokens + :p,"
                + " completion_tokens = completion_tokens + :c where day = :day")
        .param("p", prompt == null ? 0 : prompt)
        .param("c", completion == null ? 0 : completion)
        .param("day", day)
        .update();
  }

  /** One logged question. The interpretation is JSON (null when nothing was understood). */
  public record Logged(
      Instant at,
      String question,
      String interpretationJson,
      String outcome,
      String model,
      Integer latencyMs,
      Integer promptTokens,
      Integer completionTokens) {}

  public void log(Logged entry) {
    jdbc.sql(
            """
            insert into ai_search_requests (id, created_at, question, interpretation, outcome, model,
                latency_ms, prompt_tokens, completion_tokens)
            values (:id, :at, :question, cast(:interpretation as jsonb), :outcome, :model,
                :latency, :prompt, :completion)
            """)
        .param("id", UUID.randomUUID())
        .param("at", Timestamp.from(entry.at()))
        .param("question", entry.question())
        .param("interpretation", entry.interpretationJson())
        .param("outcome", entry.outcome())
        .param("model", entry.model())
        .param("latency", entry.latencyMs())
        .param("prompt", entry.promptTokens())
        .param("completion", entry.completionTokens())
        .update();
  }

  public int deleteOlderThan(Instant cutoff) {
    return jdbc.sql("delete from ai_search_requests where created_at < :cutoff")
        .param("cutoff", Timestamp.from(cutoff))
        .update();
  }
}
