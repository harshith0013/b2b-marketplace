package com.b2bmarket.ai;

import com.b2bmarket.ai.AssistantDtos.Context;
import com.b2bmarket.ai.AssistantDtos.Interpretation;
import com.b2bmarket.ai.AssistantDtos.Outcome;
import com.b2bmarket.ai.AssistantDtos.ProposedFilters;
import com.b2bmarket.ai.internal.AiProperties;
import com.b2bmarket.ai.internal.AiRecords;
import com.b2bmarket.ai.internal.AssistantCalls;
import com.b2bmarket.ai.internal.ChatCompletionsClient;
import com.b2bmarket.ai.internal.ChatCompletionsClient.Answer;
import com.b2bmarket.ai.internal.VisitorLimiter;
import com.b2bmarket.platform.IndiaTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a question into proposed search filters. Never throws for provider trouble: every failure
 * becomes an outcome that means "use normal search". Not transactional: the usage counter and the
 * log are written in their own short transactions, never inside a search.
 */
@Service
public class SearchAssistant {

  private static final Logger log = LoggerFactory.getLogger(SearchAssistant.class);

  /** Longest question accepted and stored. */
  public static final int MAX_QUESTION_LENGTH = 300;

  /** After the provider says its quota is used up, stop asking it for this long. */
  static final Duration QUOTA_PAUSE = Duration.ofMinutes(15);

  /** Until when the provider is not asked (after a quota refusal); one server, so in memory. */
  private volatile Instant pausedUntil = Instant.MIN;

  private final AiProperties properties;
  private final ChatCompletionsClient client;
  private final VisitorLimiter visitors;
  private final AiRecords records;
  private final JsonMapper json;
  private final Clock clock;

  SearchAssistant(
      AiProperties properties,
      ChatCompletionsClient client,
      VisitorLimiter visitors,
      AiRecords records,
      JsonMapper json,
      Clock clock) {
    this.properties = properties;
    this.client = client;
    this.visitors = visitors;
    this.records = records;
    this.json = json;
    this.clock = clock;
  }

  /**
   * @param visitor who is asking, for the per-visitor limit only (an address; hashed, not stored)
   */
  public Interpretation interpret(String question, String visitor, Context context) {
    String text = question.strip();
    if (text.length() > MAX_QUESTION_LENGTH) {
      text = text.substring(0, MAX_QUESTION_LENGTH);
    }
    Instant started = clock.instant();
    if (!properties.enabled()) {
      return logged(text, Interpretation.fallback(Outcome.NOT_CONFIGURED), started, null);
    }
    if (!visitors.tryAcquire(visitor)) {
      return logged(text, Interpretation.fallback(Outcome.VISITOR_LIMIT), started, null);
    }
    if (clock.instant().isBefore(pausedUntil)) {
      return logged(text, Interpretation.fallback(Outcome.PROVIDER_LIMIT), started, null);
    }
    LocalDate today = IndiaTime.today(clock);
    if (records.countRequest(today) > properties.dailyRequestLimit()) {
      return logged(text, Interpretation.fallback(Outcome.DAILY_LIMIT), started, null);
    }

    Answer answer;
    try {
      answer = AssistantCalls.ask(client, context.categories(), text);
    } catch (ChatCompletionsClient.TimeoutException e) {
      log.warn("Assistant timed out after {}", properties.timeout());
      return logged(text, Interpretation.fallback(Outcome.TIMEOUT), started, null);
    } catch (ChatCompletionsClient.RateLimitedException e) {
      pausedUntil = clock.instant().plus(QUOTA_PAUSE);
      log.warn("Assistant provider quota used up; pausing for {}", QUOTA_PAUSE);
      return logged(text, Interpretation.fallback(Outcome.PROVIDER_LIMIT), started, null);
    } catch (ChatCompletionsClient.ProviderException e) {
      log.warn("Assistant provider error: {}", e.getMessage(), e.getCause());
      return logged(text, Interpretation.fallback(Outcome.PROVIDER_ERROR), started, null);
    }
    records.addTokens(today, answer.promptTokens(), answer.completionTokens());
    try {
      ProposedFilters filters = AssistantCalls.read(answer.content(), json);
      return logged(text, new Interpretation(Outcome.UNDERSTOOD, filters), started, answer);
    } catch (IllegalArgumentException e) {
      log.warn("Assistant answer was not in the agreed format");
      return logged(text, Interpretation.fallback(Outcome.INVALID_ANSWER), started, answer);
    }
  }

  private Interpretation logged(
      String question, Interpretation interpretation, Instant started, Answer answer) {
    try {
      records.log(
          new AiRecords.Logged(
              started,
              question,
              interpretation.filters() == null
                  ? null
                  : json.writeValueAsString(interpretation.filters()),
              interpretation.outcome().name(),
              answer == null ? null : properties.model(),
              (int) Duration.between(started, clock.instant()).toMillis(),
              answer == null ? null : answer.promptTokens(),
              answer == null ? null : answer.completionTokens()));
    } catch (RuntimeException e) {
      // The log is for measuring quality; never let it break a search.
      log.warn("Could not log an assistant question", e);
    }
    return interpretation;
  }
}
