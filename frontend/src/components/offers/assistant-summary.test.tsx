import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import en from "@/i18n/dictionaries/en.json";
import { parseOfferSearch } from "@/lib/offers/search-params";
import { AssistantSummary } from "./assistant-summary";

afterEach(cleanup);

const LAUNDRY = "0f8fad5b-d9cb-469f-a165-70867728950e";
const categories = [
  {
    id: LAUNDRY,
    parentId: null,
    slug: "laundry",
    name: { en: "Laundry detergents", te: "", hi: "" },
    regulated: false,
    leaf: true,
  },
];

function renderFor(params: Record<string, string>) {
  const { search } = parseOfferSearch(params);
  return render(
    <AssistantSummary
      lang="en"
      search={search}
      labels={en.offers}
      enums={en.labels}
      categories={categories}
    />,
  );
}

describe("AssistantSummary", () => {
  it("shows nothing for an ordinary search", () => {
    const { container } = renderFor({ q: "soap" });
    expect(container.textContent).toBe("");
  });

  it("lists what it searched for as removable filters, built from the address only", () => {
    renderFor({
      asked: "విజయవాడ దగ్గర 20 కేసుల డిటర్జెంట్",
      q: "detergent",
      categoryId: LAUNDRY,
      city: "Vijayawada",
      state: "Andhra Pradesh",
      quantity: "20",
      unit: "CASE",
      maxPrice: "30",
      sort: "PRICE",
    });

    expect(screen.getByRole("heading").textContent).toContain(
      "విజయవాడ దగ్గర 20 కేసుల డిటర్జెంట్",
    );
    const remove = screen.getByRole("link", {
      name: "Remove: Vijayawada, Andhra Pradesh",
    });
    expect(remove.getAttribute("href")).not.toContain("city=");
    expect(remove.getAttribute("href")).toContain("categoryId=");
    expect(
      screen.getByRole("link", { name: "Remove: Laundry detergents" }),
    ).toBeTruthy();
    expect(
      screen.getByRole("link", {
        name: `Remove: 20 ${en.labels.stockUnitsShort.CASE}`,
      }),
    ).toBeTruthy();
    expect(
      screen.getByRole("link", { name: "Remove: Up to ₹30.00 a pack" }),
    ).toBeTruthy();
    expect(
      screen.getByRole("link", { name: `Remove: ${en.offers.sorts.PRICE}` }),
    ).toBeTruthy();
    expect(screen.getByText(en.offers.assistant.checkNote)).toBeTruthy();
  });

  it("says when a named place has no offers, and when the assistant could not help", () => {
    renderFor({ asked: "soap in Chennai", place: "Chennai", q: "soap" });
    expect(screen.getByText(/There are no offers in Chennai yet/)).toBeTruthy();
    cleanup();

    renderFor({ asked: "soap", q: "soap", assistant: "busy" });
    expect(screen.getByText(en.offers.assistant.fallbackBusy)).toBeTruthy();
    expect(screen.queryByRole("link")).toBeNull();
  });
});
