package com.b2bmarket.ai;

import com.b2bmarket.ai.internal.AiProperties;
import com.b2bmarket.ai.internal.AiRecords;
import com.b2bmarket.ai.internal.ChatCompletionsClient;
import com.b2bmarket.ai.internal.ChatCompletionsClient.Answer;
import com.b2bmarket.ai.internal.LabelCalls;
import com.b2bmarket.ai.internal.ProviderPause;
import com.b2bmarket.platform.IndiaTime;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the date, batch number and MRP printed on a label photo. Only flags: the result is compared
 * with what the seller typed and a person decides. Never throws for provider trouble: that is a
 * NOT_CHECKED reading with a reason. Call outside any database transaction (it waits for the
 * provider).
 */
@Service
public class LabelReader {

  private static final Logger log = LoggerFactory.getLogger(LabelReader.class);

  private final AiProperties properties;
  private final ChatCompletionsClient client;
  private final AiRecords records;
  private final ProviderPause pause;
  private final JsonMapper json;
  private final Clock clock;

  LabelReader(
      AiProperties properties,
      ChatCompletionsClient client,
      AiRecords records,
      ProviderPause pause,
      JsonMapper json,
      Clock clock) {
    this.properties = properties;
    this.client = client;
    this.records = records;
    this.pause = pause;
    this.json = json;
    this.clock = clock;
  }

  public LabelReading read(byte[] image, String contentType) {
    if (!properties.enabled()) {
      return LabelReading.notChecked("NOT_CONFIGURED");
    }
    if (pause.paused()) {
      return LabelReading.notChecked("PROVIDER_LIMIT");
    }
    LocalDate today = IndiaTime.today(clock);
    if (records.countVisionRequest(today) > properties.visionDailyLimit()) {
      return LabelReading.notChecked("DAILY_LIMIT");
    }
    Answer answer;
    try {
      answer = LabelCalls.ask(client, image, contentType);
    } catch (ChatCompletionsClient.RateLimitedException e) {
      pause.pause();
      log.warn("AI provider quota used up; pausing for {}", ProviderPause.QUOTA_PAUSE);
      return LabelReading.notChecked("PROVIDER_LIMIT");
    } catch (ChatCompletionsClient.TimeoutException e) {
      log.warn("Label reading timed out after {}", properties.visionTimeout());
      return LabelReading.notChecked("TIMEOUT");
    } catch (ChatCompletionsClient.ProviderException e) {
      log.warn("Label reading provider error: {}", e.getMessage());
      return LabelReading.notChecked("PROVIDER_ERROR");
    }
    records.addTokens(today, answer.promptTokens(), answer.completionTokens());
    try {
      return LabelCalls.read(answer.content(), json, properties.model());
    } catch (IllegalArgumentException e) {
      log.warn("Label reading answer was not in the agreed format");
      return LabelReading.notChecked("INVALID_ANSWER");
    }
  }
}
