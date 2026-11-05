package com.b2bmarket.platform.web;

import com.b2bmarket.platform.ApiException;
import com.b2bmarket.platform.RequestIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts errors into RFC 9457 problem responses. Standard Spring MVC errors (validation, bad
 * requests, unsupported methods) are handled by the superclass. Expected failures ({@link
 * ApiException}) keep their status and message. Anything unexpected is logged in full on the server
 * and returned to the client with no internal detail, only the request id.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  static final String GENERIC_DETAIL =
      "Something went wrong on our side. Quote the request id if you contact support.";

  static final String ACCESS_DENIED_DETAIL = "You do not have permission to do this.";

  static final String CONFLICT_DETAIL =
      "This clashes with something that was saved at the same time. Refresh and try again.";

  @ExceptionHandler(ApiException.class)
  public ProblemDetail handleApiException(ApiException ex) {
    return withRequestId(ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage()));
  }

  /** Without this, a denied method-security check would fall through to the 500 handler below. */
  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    return withRequestId(
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ACCESS_DENIED_DETAIL));
  }

  /**
   * A database constraint caught what the service checks missed, usually two requests racing (the
   * same barcode saved twice at once). The constraint name is logged, never returned.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
    log.warn("Constraint violation: {}", ex.getMostSpecificCause().getMessage());
    return withRequestId(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, CONFLICT_DETAIL));
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return withRequestId(
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_DETAIL));
  }

  private static ProblemDetail withRequestId(ProblemDetail problem) {
    RequestIds.current().ifPresent(id -> problem.setProperty("requestId", id));
    return problem;
  }
}
