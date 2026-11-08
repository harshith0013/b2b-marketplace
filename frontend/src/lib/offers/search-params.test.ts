import { describe, expect, it } from "vitest";
import {
  EMPTY_SEARCH,
  pageHref,
  parseOfferSearch,
  toApiQuery,
  withoutHref,
} from "./search-params";

const CATEGORY = "0f8fad5b-d9cb-469f-a165-70867728950e";

describe("parseOfferSearch", () => {
  it("reads every filter from the page URL", () => {
    const { search, invalid } = parseOfferSearch({
      q: "  soap ",
      categoryId: CATEGORY,
      state: "Andhra Pradesh",
      city: "Guntur",
      brand: "Lifebuoy",
      maxPrice: "30",
      maxOrder: "1,500",
      minDays: "60",
      sort: "PRICE",
      page: "2",
    });
    expect(invalid).toBe(false);
    expect(search).toMatchObject({
      q: "soap",
      maxOrder: "1,500",
      sort: "PRICE",
      page: 2,
    });
  });

  it("drops malformed values and says so, instead of passing them on", () => {
    const { search, invalid } = parseOfferSearch({
      categoryId: "not-an-id",
      maxPrice: "-5",
      minDays: "lots",
      sort: "DROP TABLE",
      page: "x",
    });
    expect(invalid).toBe(true);
    expect(search).toEqual(EMPTY_SEARCH);
  });

  it("takes the first value when a parameter repeats, and caps long text", () => {
    const { search } = parseOfferSearch({
      q: ["soap", "rice"],
      brand: "b".repeat(300),
    });
    expect(search.q).toBe("soap");
    expect(search.brand).toHaveLength(100);
  });
});

describe("toApiQuery", () => {
  it("sends paise to the API and leaves out empty filters", () => {
    const { search } = parseOfferSearch({
      q: "soap",
      maxPrice: "29.50",
      maxOrder: "1,500",
      page: "1",
    });
    expect(toApiQuery(search, 24)).toBe(
      "q=soap&maxPricePerPackPaise=2950&maxOrderValuePaise=150000&page=1&size=24",
    );
    expect(toApiQuery(EMPTY_SEARCH, 6)).toBe("size=6");
  });
});

describe("pageHref", () => {
  it("keeps the filters and changes only the page", () => {
    const { search } = parseOfferSearch({
      q: "soap",
      city: "Guntur",
      page: "3",
    });
    expect(pageHref("te", search, 4)).toBe(
      "/te/offers?q=soap&city=Guntur&page=4",
    );
    expect(pageHref("en", search, 0)).toBe("/en/offers?q=soap&city=Guntur");
  });
});

describe("assistant and quantity parameters", () => {
  it("keeps a wanted quantity only with its unit, and sends both to the API", () => {
    const both = parseOfferSearch({ quantity: "20", unit: "CASE" });
    expect(both.invalid).toBe(false);
    expect(toApiQuery(both.search, 24)).toBe("quantity=20&unit=CASE&size=24");

    const alone = parseOfferSearch({ quantity: "20" });
    expect(alone.invalid).toBe(true);
    expect(alone.search.quantity).toBe("");
    expect(parseOfferSearch({ quantity: "20", unit: "BOX" }).search.unit).toBe(
      "",
    );
  });

  it("reads the question asked (up to 300 characters) and a known fallback reason only", () => {
    const { search } = parseOfferSearch({
      asked: "q".repeat(400),
      assistant: "busy",
      place: "Chennai",
    });
    expect(search.asked).toHaveLength(300);
    expect(search.assistant).toBe("busy");
    expect(search.place).toBe("Chennai");
    expect(parseOfferSearch({ assistant: "<script>" }).search.assistant).toBe(
      "",
    );
  });

  it("removes one filter and keeps the rest, including the question", () => {
    const { search } = parseOfferSearch({
      q: "detergent",
      city: "Guntur",
      state: "Andhra Pradesh",
      quantity: "20",
      unit: "CASE",
      asked: "20 cases detergent Guntur",
    });
    expect(withoutHref("en", search, "city", "state")).toBe(
      "/en/offers?q=detergent&quantity=20&unit=CASE&asked=20+cases+detergent+Guntur",
    );
  });
});
