package com.b2bmarket.platform;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * An expected failure with a status and a message that is safe to show the caller. Thrown by
 * services; turned into an RFC 9457 problem response by the global exception handler.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final Map<String, Object> properties;

  private ApiException(HttpStatus status, String detail, Map<String, Object> properties) {
    super(detail);
    this.status = status;
    this.properties = Map.copyOf(properties);
  }

  private ApiException(HttpStatus status, String detail) {
    this(status, detail, Map.of());
  }

  public HttpStatus status() {
    return status;
  }

  /** Extra machine-readable fields for the problem response, such as a list of failed checks. */
  public Map<String, Object> properties() {
    return properties;
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

  /** A provider we depend on (the payment gateway) failed or did not answer; try again later. */
  public static ApiException unavailable(String detail) {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, detail);
  }

  /** The request is well-formed but the thing cannot be done yet; properties say why. */
  public static ApiException unprocessable(String detail, Map<String, Object> properties) {
    return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, detail, properties);
  }
}
