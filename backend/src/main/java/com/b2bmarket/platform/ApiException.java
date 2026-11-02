package com.b2bmarket.platform;

import org.springframework.http.HttpStatus;

/**
 * An expected failure with a status and a message that is safe to show the caller. Thrown by
 * services; turned into an RFC 9457 problem response by the global exception handler.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;

  private ApiException(HttpStatus status, String detail) {
    super(detail);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }

  public static ApiException badRequest(String detail) {
    return new ApiException(HttpStatus.BAD_REQUEST, detail);
  }

  /** Also used when the caller may not know the resource exists, so its existence is not leaked. */
  public static ApiException notFound(String detail) {
    return new ApiException(HttpStatus.NOT_FOUND, detail);
  }

  public static ApiException forbidden(String detail) {
    return new ApiException(HttpStatus.FORBIDDEN, detail);
  }

  public static ApiException conflict(String detail) {
    return new ApiException(HttpStatus.CONFLICT, detail);
  }
}
