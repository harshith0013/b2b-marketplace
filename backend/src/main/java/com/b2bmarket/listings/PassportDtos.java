package com.b2bmarket.listings;

import java.time.Instant;
import java.util.List;

/** The Batch Passport: what happened to a batch, oldest first. */
public final class PassportDtos {

  private PassportDtos() {}

  /**
   * One event. kind is a code the website words in the reader's language, for example
   * "batch.created", "batch_photo.added", "label.read", "check.SUBMIT", "listing.approved". codes
   * are the rule codes or reading outcome that matter for that event.
   */
  public record Entry(Instant at, String kind, List<String> codes) {

    public Entry {
      codes = List.copyOf(codes);
    }
  }

  public record Passport(List<Entry> entries) {

    public Passport {
      entries = List.copyOf(entries);
    }
  }
}
