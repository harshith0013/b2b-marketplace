import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import type { Dictionary } from "@/i18n/dictionaries";
import { OFFER_SORTS, type Category, type Place } from "@/lib/api/types";
import type { OfferSearch } from "@/lib/offers/search-params";

/**
 * Filters as a plain GET form: every choice becomes part of the page address, so results can be
 * shared and the form works without JavaScript.
 */
export function OfferFilters({
  lang,
  labels,
  search,
  categories,
  places,
  brands,
  unitLabels,
}: {
  lang: string;
  labels: Dictionary["offers"];
  search: OfferSearch;
  categories: Category[];
  places: Place[];
  brands: string[];
  /** "Pieces (single packs)", "Cases" in the reader's language. */
  unitLabels: Record<"PIECE" | "CASE", string>;
}) {
  const parents = categories.filter((c) => c.parentId === null);
  const categoryOptions = parents.flatMap((parent) => [
    { value: parent.id, label: parent.name[lang as "en" | "te" | "hi"] },
    ...categories
      .filter((c) => c.parentId === parent.id && !c.regulated)
      .map((c) => ({
        value: c.id,
        label: `— ${c.name[lang as "en" | "te" | "hi"]}`,
      })),
  ]);
  const cityOptions = places.flatMap((place) =>
    place.cities.map((city) => ({
      value: city,
      label: `${city} (${place.state})`,
    })),
  );

  return (
    <form
      action={`/${lang}/offers`}
      method="get"
      className="flex flex-col gap-4"
      aria-label={labels.filtersHeading}
    >
      <Input
        id="offers-q"
        name="q"
        type="search"
        label={labels.query}
        placeholder={labels.queryPlaceholder}
        maxLength={100}
        defaultValue={search.q}
      />
      <Select
        id="offers-category"
        name="categoryId"
        label={labels.category}
        placeholder={labels.anyCategory}
        options={categoryOptions}
        defaultValue={search.categoryId}
      />
      <Select
        id="offers-state"
        name="state"
        label={labels.state}
        placeholder={labels.anyState}
        options={places.map((p) => ({ value: p.state, label: p.state }))}
        defaultValue={search.state}
      />
      <Select
        id="offers-city"
        name="city"
        label={labels.city}
        placeholder={labels.anyCity}
        options={cityOptions}
        defaultValue={search.city}
      />
      <Select
        id="offers-brand"
        name="brand"
        label={labels.brand}
        placeholder={labels.anyBrand}
        options={brands.map((b) => ({ value: b, label: b }))}
        defaultValue={search.brand}
      />
      <Input
        id="offers-maxPrice"
        name="maxPrice"
        inputMode="decimal"
        label={labels.maxPrice}
        defaultValue={search.maxPrice}
        autoComplete="off"
      />
      <Input
        id="offers-maxOrder"
        name="maxOrder"
        inputMode="decimal"
        label={labels.maxOrder}
        hint={labels.maxOrderHint}
        defaultValue={search.maxOrder}
        autoComplete="off"
      />
      <Input
        id="offers-minDays"
        name="minDays"
        inputMode="numeric"
        label={labels.minDays}
        hint={labels.minDaysHint}
        defaultValue={search.minDays}
        autoComplete="off"
      />
      <Input
        id="offers-quantity"
        name="quantity"
        inputMode="numeric"
        label={labels.quantity}
        defaultValue={search.quantity}
        autoComplete="off"
      />
      <Select
        id="offers-unit"
        name="unit"
        label={labels.unit}
        placeholder={labels.anyUnit}
        options={(["PIECE", "CASE"] as const).map((value) => ({
          value,
          label: unitLabels[value],
        }))}
        defaultValue={search.unit}
      />
      <Select
        id="offers-sort"
        name="sort"
        label={labels.sort}
        options={OFFER_SORTS.map((value) => ({
          value,
          label: labels.sorts[value],
        }))}
        defaultValue={search.sort || (search.q ? "RELEVANCE" : "NEWEST")}
      />
      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" variant="accent">
          {labels.apply}
        </Button>
        <Link
          href={`/${lang}/offers`}
          className="font-semibold underline underline-offset-4"
        >
          {labels.clear}
        </Link>
      </div>
    </form>
  );
}
