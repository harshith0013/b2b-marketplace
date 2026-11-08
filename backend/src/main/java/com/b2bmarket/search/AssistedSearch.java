package com.b2bmarket.search;

import com.b2bmarket.ai.AssistantDtos.CategoryChoice;
import com.b2bmarket.ai.AssistantDtos.Context;
import com.b2bmarket.ai.AssistantDtos.Interpretation;
import com.b2bmarket.ai.AssistantDtos.ProposedFilters;
import com.b2bmarket.ai.SearchAssistant;
import com.b2bmarket.catalog.CategoryDtos.Category;
import com.b2bmarket.catalog.CategoryService;
import com.b2bmarket.search.OfferDtos.Place;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * A question in the shopper's own words, turned into search filters by the assistant and then
 * checked here, value by value, against what actually exists: categories, and places that have
 * offers. Anything that does not check out is dropped. The result is only filters; the offers
 * themselves always come from {@link OfferSearchService}.
 */
@Service
public class AssistedSearch {

  static final int MAX_KEYWORDS = 100;
  static final BigDecimal MAX_RUPEES = new BigDecimal("10000000");
  static final long MAX_QUANTITY = 10_000_000L;
  static final int MAX_DAYS = 3650;

  /**
   * The checked filters, as the website puts them in the search address. placeNotFound is a place
   * the shopper named where there are no offers (so the website can say so rather than hide it).
   */
  public record Understood(
      String query,
      UUID categoryId,
      String state,
      String city,
      Long maxPricePerPackPaise,
      Long maxOrderValuePaise,
      Integer minDaysLeft,
      Long quantity,
      String unit,
      OfferSort sort,
      String placeNotFound) {}

  /** understood is null unless the outcome is UNDERSTOOD. */
  public record Result(String outcome, Understood understood) {}

  private final SearchAssistant assistant;
  private final CategoryService categories;
  private final OfferSearchService offers;

  AssistedSearch(SearchAssistant assistant, CategoryService categories, OfferSearchService offers) {
    this.assistant = assistant;
    this.categories = categories;
    this.offers = offers;
  }

  public Result interpret(String question, String visitor) {
    List<Category> all = categories.all().stream().filter(c -> !c.regulated()).toList();
    Interpretation interpretation =
        assistant.interpret(
            question,
            visitor,
            new Context(
                all.stream().map(c -> new CategoryChoice(c.slug(), c.name().en())).toList()));
    if (!interpretation.understood()) {
      return new Result(interpretation.outcome().name(), null);
    }
    return new Result(
        interpretation.outcome().name(), check(interpretation.filters(), all, offers.places()));
  }

  /** Keeps only values that are valid and exist; never trusts the model. */
  static Understood check(ProposedFilters proposed, List<Category> all, List<Place> places) {
    UUID categoryId =
        proposed.category() == null
            ? null
            : all.stream()
                .filter(c -> c.slug().equals(proposed.category()))
                .map(Category::id)
                .findFirst()
                .orElse(null);

    String state = null;
    String city = null;
    String placeNotFound = null;
    if (proposed.city() != null) {
      Optional<Place> withCity =
          places.stream()
              .filter(p -> p.cities().stream().anyMatch(c -> same(c, proposed.city())))
              .findFirst();
      if (withCity.isPresent()) {
        state = withCity.get().state();
        city =
            withCity.get().cities().stream()
                .filter(c -> same(c, proposed.city()))
                .findFirst()
                .orElseThrow();
      } else {
        placeNotFound = proposed.city();
      }
    }
    if (state == null && proposed.state() != null) {
      Optional<Place> named =
          places.stream().filter(p -> same(p.state(), proposed.state())).findFirst();
      if (named.isPresent()) {
        state = named.get().state();
      } else if (placeNotFound == null) {
        placeNotFound = proposed.state();
      }
    }

    Long quantity = null;
    String unit = null;
    if (proposed.quantity() != null
        && proposed.unit() != null
        && proposed.quantity() >= 1
        && proposed.quantity() <= MAX_QUANTITY
        && OfferFilters.UNITS.contains(proposed.unit())) {
      quantity = proposed.quantity().longValue();
      unit = proposed.unit();
    }

    OfferSort sort =
        "PRICE".equals(proposed.sort())
            ? OfferSort.PRICE
            : "EXPIRY".equals(proposed.sort()) ? OfferSort.EXPIRY : null;

    return new Understood(
        keywords(proposed.keywords()),
        categoryId,
        state,
        city,
        paise(proposed.maxPricePerPackRupees()),
        paise(proposed.maxOrderValueRupees()),
        proposed.minDaysLeft() != null
                && proposed.minDaysLeft() > 0
                && proposed.minDaysLeft() <= MAX_DAYS
            ? proposed.minDaysLeft()
            : null,
        quantity,
        unit,
        sort,
        placeNotFound == null ? null : truncate(placeNotFound, MAX_KEYWORDS));
  }

  private static boolean same(String a, String b) {
    return a.strip().toLowerCase(Locale.ROOT).equals(b.strip().toLowerCase(Locale.ROOT));
  }

  private static String keywords(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return truncate(value.strip(), MAX_KEYWORDS);
  }

  private static String truncate(String value, int max) {
    return value.length() > max ? value.substring(0, max) : value;
  }

  private static Long paise(BigDecimal rupees) {
    if (rupees == null || rupees.signum() <= 0 || rupees.compareTo(MAX_RUPEES) > 0) {
      return null;
    }
    return rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
  }
}
