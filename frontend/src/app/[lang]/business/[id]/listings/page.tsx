import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, ChevronRight, PackageOpen, Plus } from "lucide-react";
import {
  ListingStatusBadge,
  expiryLine,
  productLine,
} from "@/components/listings/listing-facts";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { daysUntilInIndia } from "@/lib/dates";
import type { Dictionary } from "@/i18n/dictionaries";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import type { BusinessDetails, Listing, PageResult } from "@/lib/api/types";
import { api, requireSession } from "@/lib/auth/current-user";
import { formatPaise } from "@/lib/money";
import { apiOrNotFound, canEdit, requireUuids } from "@/lib/pages";

type Params = PageProps<"/[lang]/business/[id]/listings">["params"];

async function ListingsContent({ params }: { params: Params }) {
  const { lang, id } = await params;
  if (!hasLocale(lang)) notFound();
  requireUuids(id);
  const dict = await getDictionary(lang);
  const { listings: labels, labels: enums } = dict;
  const returnTo = `/${lang}/business/${id}/listings`;
  const session = await requireSession(lang, returnTo);
  const business = await apiOrNotFound<BusinessDetails>(
    session,
    lang,
    returnTo,
    `/api/v1/businesses/${id}`,
  );
  const page = await api<PageResult<Listing>>(
    session,
    lang,
    returnTo,
    `/api/v1/businesses/${id}/listings?size=50`,
  );
  const editable = canEdit(business.yourRole);

  return (
    <>
      <div className="flex flex-col gap-3">
        <Link
          href={`/${lang}/business/${id}`}
          className="inline-flex items-center gap-2 self-start font-semibold underline underline-offset-4"
        >
          <ArrowLeft aria-hidden="true" className="size-5" />
          {business.tradeName ?? business.legalName}
        </Link>
        <h1 className="text-display font-bold">{labels.title}</h1>
        {editable ? (
          <Button asChild variant="accent" size="lg" className="self-start">
            <Link href={`${returnTo}/new`}>
              <Plus aria-hidden="true" className="size-5" />
              {labels.newListing}
            </Link>
          </Button>
        ) : (
          <p className="text-muted">{labels.readOnly}</p>
        )}
      </div>

      {page.items.length === 0 ? (
        <EmptyState icon={<PackageOpen />} title={labels.none} />
      ) : (
        <ul className="flex flex-col gap-3">
          {page.items.map((listing) => {
            const first = listing.priceTiers[0];
            const unit = enums.stockUnitsShort[listing.quantityUnit];
            return (
              <li key={listing.id}>
                <Link
                  href={`${returnTo}/${listing.id}`}
                  className="flex items-center justify-between gap-3 rounded-card border-2 border-border p-4 hover:bg-surface"
                >
                  <span className="flex flex-col gap-1">
                    <span className="font-semibold">
                      {productLine(listing, labels, enums)}
                    </span>
                    <span className="text-sm text-muted">
                      {expiryLine(listing, lang, enums, labels)}
                    </span>
                    {first && (
                      <span className="text-sm">
                        {format(labels.priceFrom, {
                          price: formatPaise(first.unitPricePaise, lang),
                          unit,
                        })}
                      </span>
                    )}
                    <span className="flex flex-wrap gap-2 self-start">
                      <ListingStatusBadge
                        status={listing.status}
                        enums={enums}
                      />
                      <ExpiryBadge listing={listing} labels={labels} />
                    </span>
                  </span>
                  <span className="inline-flex items-center gap-1 font-semibold">
                    {labels.view}
                    <ChevronRight aria-hidden="true" className="size-5" />
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </>
  );
}

/** For live listings: under 30 days left (buyers no longer see it) or under 45 (soon). */
function ExpiryBadge({
  listing,
  labels,
}: {
  listing: Listing;
  labels: Dictionary["listings"];
}) {
  if (listing.status !== "PUBLISHED" && listing.status !== "PAUSED")
    return null;
  const days = daysUntilInIndia(listing.batch.expiresOn);
  if (days < 30) return <Badge variant="danger">{labels.offSale}</Badge>;
  if (days < 45) return <Badge variant="warning">{labels.expiringSoon}</Badge>;
  return null;
}

export default function ListingsPage({
  params,
}: PageProps<"/[lang]/business/[id]/listings">) {
  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-8">
      <Suspense fallback={<Skeleton className="h-96" />}>
        <ListingsContent params={params} />
      </Suspense>
    </div>
  );
}
