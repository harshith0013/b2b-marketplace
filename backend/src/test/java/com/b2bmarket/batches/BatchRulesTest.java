package com.b2bmarket.batches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b2bmarket.platform.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BatchRulesTest {

  /** 20:00 UTC on 5 November is already 01:30 on 6 November in India. */
  static final Clock LATE_EVENING_UTC =
      Clock.fixed(Instant.parse("2026-11-05T20:00:00Z"), ZoneOffset.UTC);

  static final LocalDate INDIA_TODAY = LocalDate.parse("2026-11-06");

  @Test
  void todayIsTheDateInIndiaNotUtc() {
    assertThat(BatchRules.today(LATE_EVENING_UTC)).isEqualTo(INDIA_TODAY);
    assertThat(BatchRules.daysUntil(INDIA_TODAY.plusDays(30), LATE_EVENING_UTC)).isEqualTo(30);
  }

  @Test
  void stockExpiringTodayCanStillBeAddedButExpiredStockCannot() {
    assertThatCode(() -> BatchRules.requireDates(null, INDIA_TODAY, LATE_EVENING_UTC))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () -> BatchRules.requireDates(null, INDIA_TODAY.minusDays(1), LATE_EVENING_UTC))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("already expired");
  }

  @Test
  void manufacturingDateCannotBeInTheFuture() {
    assertThatThrownBy(
            () ->
                BatchRules.requireDates(
                    INDIA_TODAY.plusDays(1), INDIA_TODAY.plusDays(90), LATE_EVENING_UTC))
        .hasMessageContaining("future");
    assertThatCode(() -> BatchRules.requireDates(INDIA_TODAY, INDIA_TODAY, LATE_EVENING_UTC))
        .doesNotThrowAnyException();
  }

  @Test
  void expiryMoreThanTenYearsAwayIsTreatedAsATypo() {
    assertThatThrownBy(
            () -> BatchRules.requireDates(null, INDIA_TODAY.plusYears(11), LATE_EVENING_UTC))
        .hasMessageContaining("10 years");
  }

  @Test
  void batchNumbersAreTrimmedAndLimitedToPrintableCharacters() {
    assertThat(BatchRules.batchNumber("  B23-0456/A ")).isEqualTo("B23-0456/A");
    assertThatThrownBy(() -> BatchRules.batchNumber("<script>")).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> BatchRules.batchNumber("   ")).isInstanceOf(ApiException.class);
  }

  @Test
  void casesNeedTheirSizeAndPiecesMustNotHaveOne() {
    assertThat(BatchRules.packsPerCase(StockUnit.CASE, 24)).isEqualTo(24);
    assertThat(BatchRules.packsPerCase(StockUnit.PIECE, null)).isNull();
    assertThatThrownBy(() -> BatchRules.packsPerCase(StockUnit.CASE, null))
        .hasMessageContaining("how many packs");
    assertThatThrownBy(() -> BatchRules.packsPerCase(StockUnit.PIECE, 12))
        .hasMessageContaining("only when counting in cases");
  }
}
