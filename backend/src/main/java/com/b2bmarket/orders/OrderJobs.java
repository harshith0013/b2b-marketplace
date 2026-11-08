package com.b2bmarket.orders;

import com.b2bmarket.orders.internal.OrderRecords;
import com.b2bmarket.platform.TenantScope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orders not accepted within 24 hours expire and give their stock back; delivery people's names and
 * phone numbers are removed 30 days after an order completes. Runs as the ordering role.
 */
@Component
public class OrderJobs {

  private static final Logger log = LoggerFactory.getLogger(OrderJobs.class);
  static final Duration FORGET_DELIVERY_PEOPLE_AFTER = Duration.ofDays(30);

  private final OrderRecords records;
  private final TenantScope scope;
  private final TransactionTemplate transaction;
  private final Clock clock;

  OrderJobs(
      OrderRecords records,
      TenantScope scope,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.records = records;
    this.scope = scope;
    this.transaction = new TransactionTemplate(transactions);
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
  public void expireUnacceptedOrders() {
    Instant now = clock.instant();
    int released =
        transaction.execute(
            s -> {
              scope.enterOrderingSweep();
              List<UUID> expired =
                  records.expirePlaced(now.minus(OrderService.RESERVATION_HOLD), now);
              return records.releaseFor(expired);
            });
    if (released > 0) {
      log.info("Expired unaccepted orders and released {} stock reservations", released);
    }
  }

  @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Kolkata")
  public void forgetDeliveryPeople() {
    Instant cutoff = clock.instant().minus(FORGET_DELIVERY_PEOPLE_AFTER);
    transaction.executeWithoutResult(
        s -> {
          scope.enterOrderingSweep();
          records.forgetDeliveryPeople(cutoff);
        });
  }
}
