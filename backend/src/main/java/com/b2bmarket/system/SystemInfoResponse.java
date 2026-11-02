package com.b2bmarket.system;

import java.time.Instant;

/** Public, non-sensitive facts about the running service. */
public record SystemInfoResponse(String name, String version, Instant serverTime) {}
