package com.b2bmarket.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.b2bmarket.platform.RequestIds;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void reusesAWellFormedIncomingId() {
    String incoming = "3F2504E0-4F89-41D3-9A0C-0305E82C3301";
    assertThat(RequestIdFilter.resolveRequestId(incoming)).isEqualTo(incoming.toLowerCase());
  }

  @Test
  void replacesMissingOrMalformedIds() {
    for (String bad : new String[] {null, "", "abc", "x\r\nforged-log-line", "1".repeat(36)}) {
      String resolved = RequestIdFilter.resolveRequestId(bad);
      assertThat(UUID.fromString(resolved).toString()).isEqualTo(resolved);
      assertThat(resolved).isNotEqualTo(bad);
    }
  }

  @Test
  void setsResponseHeaderAndMdcDuringTheRequestThenClearsMdc() throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/v1/system/info");
    var response = new MockHttpServletResponse();
    var seenInMdc = new AtomicReference<String>();
    var chain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            seenInMdc.set(MDC.get(RequestIds.MDC_KEY));
          }
        };

    filter.doFilter(request, response, chain);

    String header = response.getHeader(RequestIdFilter.HEADER);
    assertThat(header).isNotBlank();
    assertThat(seenInMdc.get()).isEqualTo(header);
    assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
  }
}
