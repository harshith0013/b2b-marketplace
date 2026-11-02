package com.b2bmarket.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void hidesInternalDetailsAndIncludesTheRequestId() {
    MDC.put(RequestIdFilter.MDC_KEY, "req-123");

    ProblemDetail problem =
        handler.handleUnexpected(
            new IllegalStateException("jdbc:postgresql://db-host password=secret"));

    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.GENERIC_DETAIL);
    assertThat(problem.getDetail()).doesNotContain("jdbc", "secret");
    assertThat(problem.getProperties()).containsEntry("requestId", "req-123");
  }
}
