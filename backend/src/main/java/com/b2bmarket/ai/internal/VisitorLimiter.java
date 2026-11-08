package com.b2bmarket.ai.internal;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Per-visitor limits on assistant questions (per minute and per day), kept in memory. Visitors are
 * known only by a keyed hash of their address, never the address itself. Enough for one server;
 * with several, this moves to the load balancer's firewall (Phase 12).
 */
@Component
public class VisitorLimiter {

  /** Stops the table growing without bound if many distinct visitors appear. */
  static final int MAX_TRACKED_VISITORS = 100_000;

  private record Counts(Instant minute, int inMinute, Instant day, int inDay) {}

  private final ConcurrentHashMap<String, Counts> counts = new ConcurrentHashMap<>();
  private final AiProperties properties;
  private final Clock clock;
  private final byte[] hashKey = new byte[32];

  VisitorLimiter(AiProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    new SecureRandom().nextBytes(hashKey);
  }

  /** Counts this question; false when the visitor is over a limit. */
  public boolean tryAcquire(String visitor) {
    if (counts.size() > MAX_TRACKED_VISITORS) {
      counts.clear(); // Crude but safe: limits restart rather than memory running out.
    }
    Instant now = clock.instant();
    Instant minute = now.truncatedTo(ChronoUnit.MINUTES);
    Instant day = now.truncatedTo(ChronoUnit.DAYS);
    boolean[] allowed = {false};
    counts.compute(
        hash(visitor),
        (key, current) -> {
          int inMinute =
              current != null && current.minute().equals(minute) ? current.inMinute() : 0;
          int inDay = current != null && current.day().equals(day) ? current.inDay() : 0;
          if (inMinute >= properties.visitorPerMinute() || inDay >= properties.visitorPerDay()) {
            return new Counts(minute, inMinute, day, inDay);
          }
          allowed[0] = true;
          return new Counts(minute, inMinute + 1, day, inDay + 1);
        });
    return allowed[0];
  }

  private String hash(String visitor) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
      return HexFormat.of()
          .formatHex(
              mac.doFinal((visitor == null ? "" : visitor).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }
}
