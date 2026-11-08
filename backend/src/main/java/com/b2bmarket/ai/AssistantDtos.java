package com.b2bmarket.ai;

import java.math.BigDecimal;
import java.util.List;

/** What goes into and comes out of the search assistant. */
public final class AssistantDtos {

  private AssistantDtos() {}

  /** A category the model may pick, by slug, with a name it can match words against. */
  public record CategoryChoice(String slug, String name) {}

  /**
   * What the model proposed. Every field may be null, and none of it is trusted: the caller
   * validates each value before using it.
   */
  public record ProposedFilters(
      String keywords,
      String category,
      String city,
      String state,
      Integer quantity,
      String unit,
      BigDecimal maxPricePerPackRupees,
      BigDecimal maxOrderValueRupees,
      Integer minDaysLeft,
      String sort) {}

  /** UNDERSTOOD carries filters; every other outcome means "use normal search". */
  public enum Outcome {
    UNDERSTOOD,
    NOT_CONFIGURED,
    VISITOR_LIMIT,
    DAILY_LIMIT,
    /** The provider refused: its own quota (for example a free tier's daily limit) is used up. */
    PROVIDER_LIMIT,
    PROVIDER_ERROR,
    TIMEOUT,
    INVALID_ANSWER
  }

  public record Interpretation(Outcome outcome, ProposedFilters filters) {

    public static Interpretation fallback(Outcome outcome) {
      return new Interpretation(outcome, null);
    }

    public boolean understood() {
      return outcome == Outcome.UNDERSTOOD && filters != null;
    }
  }

  /** The categories offered to the model; fixed per request. */
  public record Context(List<CategoryChoice> categories) {

    public Context {
      categories = List.copyOf(categories);
    }
  }
}
