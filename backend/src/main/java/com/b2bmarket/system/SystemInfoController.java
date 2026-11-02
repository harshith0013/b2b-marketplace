package com.b2bmarket.system;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

  private final Clock clock;
  private final String name;
  private final String version;

  public SystemInfoController(
      Clock clock,
      @Value("${spring.application.name}") String name,
      @Value("${app.version:unknown}") String version) {
    this.clock = clock;
    this.name = name;
    this.version = version;
  }

  @GetMapping("/info")
  public SystemInfoResponse info() {
    return new SystemInfoResponse(name, version, Instant.now(clock));
  }
}
