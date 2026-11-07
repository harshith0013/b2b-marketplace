package com.b2bmarket.batches;

import java.time.Instant;
import java.util.UUID;

/** Response bodies of the batch photos API. Storage locations are never exposed. */
public final class PhotoDtos {

  private PhotoDtos() {}

  public record Photo(
      UUID id,
      PhotoKind kind,
      String contentType,
      int width,
      int height,
      int sizeBytes,
      Instant uploadedAt) {}

  /** A photo's bytes, served only after an authorization check. */
  public record PhotoContent(byte[] bytes, String contentType) {}
}
