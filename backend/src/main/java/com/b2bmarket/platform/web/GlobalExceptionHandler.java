package com.b2bmarket.platform.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts errors into RFC 9457 problem responses. Standard Spring MVC errors (validation, bad
 * requests, unsupported methods) are handled by the superclass. Anything unexpected is logged in
 * full on the server and returned to the client with no internal detail, only the request id.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  static final String GENERIC_DETAIL =
      "Something went wrong on our side. Quote the request id if you contact support.";

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_DETAIL);
    String requestId = MDC.get(RequestIdFilter.MDC_KEY);
    if (requestId != null) {
      problem.setProperty("requestId", requestId);
    }
    return problem;
  }
}
