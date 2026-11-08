package com.b2bmarket.ai.internal;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Assistant settings and the daily clean-up of old question logs. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
@EnableScheduling
class AiConfig {

  private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

  private final AiRecords records;
  private final AiProperties properties;
  private final Clock clock;

  AiConfig(AiRecords records, AiProperties properties, Clock clock) {
    this.records = records;
    this.properties = properties;
    this.clock = clock;
  }

  /** Every night at 03:15 India time: delete logged questions past the retention period. */
  @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Kolkata")
  void deleteOldQuestions() {
    int deleted =
        records.deleteOlderThan(
            clock.instant().minus(Duration.ofDays(properties.logRetentionDays())));
    if (deleted > 0) {
      log.info(
          "Deleted {} assistant questions older than {} days",
          deleted,
          properties.logRetentionDays());
    }
  }
}
