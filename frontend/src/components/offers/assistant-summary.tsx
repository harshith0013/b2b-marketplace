import Link from "next/link";
import { Info, Sparkles, X } from "lucide-react";
import type { Dictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import type { Category } from "@/lib/api/types";
import { rupeesToPaise, formatPaise } from "@/lib/money";
import {
  withoutHref,
  type OfferSearch,
  type SearchKey,
} from "@/lib/offers/search-params";

/**
 * What the assistant made of the shopper's question, as removable filters. Built only from the
 * filters in the address (never from text the model wrote), so nothing here can be invented.
 */
export function AssistantSummary({
  lang,
  search,
  labels,
  enums,
  categories,
}: {
  lang: string;
  search: OfferSearch;
  labels: Dictionary["offers"];
  enums: Dictionary["labels"];
  categories: Category[];
}) {
  if (!search.asked) return null;
  const text = labels.assistant;

  if (search.assistant) {
    return (
      <p className="flex items-start gap-2 rounded-control bg-info-soft px-4 py-3 font-semibold text-info">
        <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
        {search.assistant === "busy" ? text.fallbackBusy : text.fallbackOff}
      </p>
    );
  }

  const money = (rupees: string) =>
    formatPaise(rupeesToPaise(rupees) ?? 0, lang);
  const category = categories.find((c) => c.id === search.categoryId);
  const chips: { label: string; remove: SearchKey[] }[] = [];
  if (search.q)
    chips.push({
      label: format(text.words, { words: search.q }),
      remove: ["q"],
    });
  if (category) {
    chips.push({
      label: category.name[lang as "en" | "te" | "hi"],
      remove: ["categoryId"],
    });
  }
  if (search.city || search.state) {
    chips.push({
      label: [search.city, search.state].filter(Boolean).join(", "),
      remove: ["city", "state"],
    });
  }
  if (search.quantity && search.unit) {
    chips.push({
      label: `${search.quantity} ${enums.stockUnitsShort[search.unit]}`,
      remove: ["quantity", "unit"],
    });
  }
  if (search.maxPrice) {
    chips.push({
      label: format(text.maxPrice, { price: money(search.maxPrice) }),
      remove: ["maxPrice"],
    });
  }
  if (search.maxOrder) {
    chips.push({
      label: format(text.maxOrder, { price: money(search.maxOrder) }),
      remove: ["maxOrder"],
    });
  }
  if (search.minDays) {
    chips.push({
      label: format(text.minDays, { days: search.minDays }),
      remove: ["minDays"],
    });
  }
  if (search.sort)
    chips.push({ label: labels.sorts[search.sort], remove: ["sort"] });

  return (
    <section
      aria-labelledby="assistant-summary"
      className="flex flex-col gap-3 rounded-card border-2 border-accent p-4"
    >
      <h2
        id="assistant-summary"
        className="flex items-start gap-2 font-semibold"
      >
        <Sparkles aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
        {format(text.asked, { question: search.asked })}
      </h2>
      {chips.length > 0 && (
        <>
          <p className="text-sm text-muted">{text.searchedFor}</p>
          <ul className="flex flex-wrap gap-2">
            {chips.map((chip) => (
              <li key={chip.label}>
                <Link
                  href={withoutHref(lang, search, ...chip.remove, "assistant")}
                  aria-label={format(text.remove, { filter: chip.label })}
                  className="inline-flex min-h-10 items-center gap-1 rounded-full border-2 border-border px-3 text-sm font-semibold hover:bg-surface"
                >
                  {chip.label}
                  <X aria-hidden="true" className="size-4" />
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}
      {search.place && (
        <p className="text-sm">
          {format(text.noPlace, { place: search.place })}
        </p>
      )}
      <p className="text-sm text-muted">{text.checkNote}</p>
    </section>
  );
}
