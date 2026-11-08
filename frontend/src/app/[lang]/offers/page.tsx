import { Suspense } from "react";
import Link from "next/link";
import { notFound, unstable_rethrow } from "next/navigation";
import { ArrowLeft, ArrowRight, SearchX } from "lucide-react";
import { AssistantSummary } from "@/components/offers/assistant-summary";
import { OfferCard } from "@/components/offers/offer-card";
import { OfferFilters } from "@/components/offers/offer-filters";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import { ApiError, callPublic } from "@/lib/api/backend";
import type { Category, OfferPage, Place } from "@/lib/api/types";
import {
  pageHref,
  parseOfferSearch,
  toApiQuery,
} from "@/lib/offers/search-params";

const PAGE_SIZE = 24;

type Props = PageProps<"/[lang]/offers">;

/** Everything here depends on the address's search parameters, so it renders per request. */
async function OffersContent({ params, searchParams }: Props) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const dict = await getDictionary(lang);
  const labels = dict.offers;
  const { search, invalid } = parseOfferSearch(await searchParams);

  const [result, categories, places, brands] = await Promise.all([
    callPublic<OfferPage>(
      `/api/v1/offers?${toApiQuery(search, PAGE_SIZE)}`,
    ).then(
      (page) => ({ ok: true as const, page }),
      (error: unknown) => {
        unstable_rethrow(error);
        return { ok: false as const, error };
      },
    ),
    callPublic<Category[]>("/api/v1/categories").catch(() => [] as Category[]),
    callPublic<Place[]>("/api/v1/offers/locations").catch(() => [] as Place[]),
    callPublic<string[]>("/api/v1/offers/brands").catch(() => [] as string[]),
  ]);

  let results: React.ReactNode;
  if (!result.ok) {
    const badFilters =
      result.error instanceof ApiError && result.error.status === 400;
    if (!badFilters) console.error("Could not load offers", result.error);
    results = (
      <ErrorState
        title={badFilters ? labels.invalidFilters : labels.unavailable}
      />
    );
  } else if (result.page.items.length === 0) {
    results = <EmptyState icon={<SearchX />} title={labels.none} />;
  } else {
    const { page } = result;
    results = (
      <div className="flex flex-col gap-4">
        <ul className="grid gap-3 sm:grid-cols-2">
          {page.items.map((offer) => (
            <li key={offer.id}>
              <OfferCard
                offer={offer}
                lang={lang}
                labels={labels}
                enums={dict.labels}
              />
            </li>
          ))}
        </ul>
        <nav
          aria-label={format(labels.page, { page: String(page.page + 1) })}
          className="flex flex-wrap items-center justify-between gap-3"
        >
          {page.page > 0 ? (
            <Link
              href={pageHref(lang, search, page.page - 1)}
              className="inline-flex min-h-12 items-center gap-2 font-semibold underline underline-offset-4"
            >
              <ArrowLeft aria-hidden="true" className="size-5" />
              {labels.previous}
            </Link>
          ) : (
            <span />
          )}
          <span className="text-muted">
            {format(labels.page, { page: String(page.page + 1) })}
          </span>
          {page.hasNext ? (
            <Link
              href={pageHref(lang, search, page.page + 1)}
              className="inline-flex min-h-12 items-center gap-2 font-semibold underline underline-offset-4"
            >
              {labels.next}
              <ArrowRight aria-hidden="true" className="size-5" />
            </Link>
          ) : (
            <span />
          )}
        </nav>
      </div>
    );
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[18rem_1fr]">
      <aside aria-labelledby="filters-heading" className="flex flex-col gap-3">
        <h2 id="filters-heading" className="text-heading font-bold">
          {labels.filtersHeading}
        </h2>
        {invalid && (
          <p className="rounded-control bg-warning-soft px-4 py-3 font-semibold text-warning">
            {labels.invalidFilters}
          </p>
        )}
        <OfferFilters
          unitLabels={dict.labels.stockUnits}
          lang={lang}
          labels={labels}
          search={search}
          categories={categories}
          places={places}
          brands={brands}
        />
      </aside>
      <section
        aria-labelledby="results-heading"
        className="flex flex-col gap-4"
      >
        <h2 id="results-heading" className="text-heading font-bold">
          {labels.resultsHeading}
        </h2>
        <AssistantSummary
          lang={lang}
          search={search}
          labels={labels}
          enums={dict.labels}
          categories={categories}
        />
        {results}
      </section>
    </div>
  );
}

export default function OffersPage(props: Props) {
  return (
    <div className="flex flex-col gap-6">
      <Suspense fallback={<Skeleton className="h-96" />}>
        <OffersHeader params={props.params} />
      </Suspense>
      <Suspense fallback={<Skeleton className="h-96" />}>
        <OffersContent {...props} />
      </Suspense>
    </div>
  );
}

async function OffersHeader({ params }: { params: Props["params"] }) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const { offers } = await getDictionary(lang);
  return (
    <div className="flex flex-col gap-2">
      <h1 className="text-display font-bold">{offers.title}</h1>
      <p className="max-w-prose text-muted">{offers.intro}</p>
    </div>
  );
}
