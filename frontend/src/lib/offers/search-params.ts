import { isUuid } from "@/lib/actions/ids";
import { OFFER_SORTS, type OfferSort } from "@/lib/api/types";
import { rupeesToPaise } from "@/lib/money";

/**
 * The offer search as it appears in the page URL. Prices are in rupees there (what people type);
 * the API takes paise. Anything malformed is dropped and reported, never passed on.
 */
export interface OfferSearch {
  q: string;
  categoryId: string;
  state: string;
  city: string;
  brand: string;
  maxPrice: string;
  maxOrder: string;
  minDays: string;
  sort: OfferSort | "";
  page: number;
}

export const EMPTY_SEARCH: OfferSearch = {
  q: "",
  categoryId: "",
  state: "",
  city: "",
  brand: "",
  maxPrice: "",
  maxOrder: "",
  minDays: "",
  sort: "",
  page: 0,
};

const MAX_TEXT = 100;
const MAX_PAGE = 400;

type RawParams = Record<string, string | string[] | undefined>;

function one(params: RawParams, key: string): string {
  const value = params[key];
  const text = Array.isArray(value) ? value[0] : value;
  return (text ?? "").trim().slice(0, MAX_TEXT);
}

/** Reads the URL's search parameters; invalid says whether anything had to be dropped. */
export function parseOfferSearch(params: RawParams): {
  search: OfferSearch;
  invalid: boolean;
} {
  let invalid = false;
  const keep = (value: string, ok: boolean) => {
    if (value && !ok) invalid = true;
    return ok ? value : "";
  };
  const money = (value: string) => {
    const paise = value ? rupeesToPaise(value) : null;
    return keep(value, paise !== null && paise > 0);
  };
  const sort = one(params, "sort");
  const minDays = one(params, "minDays");
  const pageText = one(params, "page");
  const page = /^\d{1,3}$/.test(pageText)
    ? Math.min(Number(pageText), MAX_PAGE)
    : 0;
  return {
    search: {
      q: one(params, "q"),
      categoryId: keep(
        one(params, "categoryId"),
        isUuid(one(params, "categoryId")),
      ),
      state: one(params, "state"),
      city: one(params, "city"),
      brand: one(params, "brand"),
      maxPrice: money(one(params, "maxPrice")),
      maxOrder: money(one(params, "maxOrder")),
      minDays: keep(minDays, /^\d{1,4}$/.test(minDays)),
      sort: keep(sort, (OFFER_SORTS as readonly string[]).includes(sort)) as
        OfferSort | "",
      page,
    },
    invalid,
  };
}

/** The API query for a search: rupees become paise, empty values are left out. */
export function toApiQuery(search: OfferSearch, size: number): string {
  const query = new URLSearchParams();
  const add = (key: string, value: string) => {
    if (value) query.set(key, value);
  };
  add("q", search.q);
  add("categoryId", search.categoryId);
  add("state", search.state);
  add("city", search.city);
  add("brand", search.brand);
  if (search.maxPrice)
    add("maxPricePerPackPaise", String(rupeesToPaise(search.maxPrice)));
  if (search.maxOrder)
    add("maxOrderValuePaise", String(rupeesToPaise(search.maxOrder)));
  add("minDaysLeft", search.minDays);
  add("sort", search.sort);
  if (search.page > 0) query.set("page", String(search.page));
  query.set("size", String(size));
  return query.toString();
}

/** A link to the same search on another page. */
export function pageHref(
  lang: string,
  search: OfferSearch,
  page: number,
): string {
  const query = new URLSearchParams();
  for (const key of [
    "q",
    "categoryId",
    "state",
    "city",
    "brand",
    "maxPrice",
    "maxOrder",
    "minDays",
    "sort",
  ] as const) {
    if (search[key]) query.set(key, search[key]);
  }
  if (page > 0) query.set("page", String(page));
  const text = query.toString();
  return `/${lang}/offers${text ? `?${text}` : ""}`;
}
