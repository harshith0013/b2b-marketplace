package com.b2bmarket.platform;

import java.util.Optional;
import org.slf4j.MDC;

/** The correlation id of the request being handled, set by the request id filter. */
public final class RequestIds {

  public static final String MDC_KEY = "requestId";

  private RequestIds() {}

  public static Optional<String> current() {
    return Optional.ofNullable(MDC.get(MDC_KEY));
  }
}
