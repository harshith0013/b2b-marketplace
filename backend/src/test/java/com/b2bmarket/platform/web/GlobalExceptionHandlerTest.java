package com.b2bmarket.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2bmarket.platform.RequestIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void hidesInternalDetailsAndIncludesTheRequestId() {
    MDC.put(RequestIds.MDC_KEY, "req-123");

    ProblemDetail problem =
        handler.handleUnexpected(
            new IllegalStateException("jdbc:postgresql://db-host password=secret"));

    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.GENERIC_DETAIL);
    assertThat(problem.getDetail()).doesNotContain("jdbc", "secret");
    assertThat(problem.getProperties()).containsEntry("requestId", "req-123");
  }

  @Test
  void constraintViolationsBecomeConflictsWithoutTheConstraintName() {
    ProblemDetail problem =
        handler.handleDataIntegrity(
            new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"product_variants_gtin_key\""));

    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.CONFLICT_DETAIL);
    assertThat(problem.getDetail()).doesNotContain("product_variants");
  }
}
