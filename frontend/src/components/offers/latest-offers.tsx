import Link from "next/link";
import { unstable_rethrow } from "next/navigation";
import { connection } from "next/server";
import { ArrowRight, PackageOpen } from "lucide-react";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import type { Locale } from "@/i18n/config";
import type { Dictionary } from "@/i18n/dictionaries";
import { callPublic } from "@/lib/api/backend";
import type { OfferPage } from "@/lib/api/types";
import { OfferCard } from "./offer-card";

const LATEST_COUNT = 6;

/** The newest offers, read per request. Render inside <Suspense>. */
export async function LatestOffers({
  lang,
  dict,
}: {
  lang: Locale;
  dict: Dictionary;
}) {
  // Offers change all the time: never part of the prerendered page.
  await connection();
  let page: OfferPage;
  try {
    page = await callPublic<OfferPage>(
      `/api/v1/offers?sort=NEWEST&size=${LATEST_COUNT}`,
    );
  } catch (error) {
    unstable_rethrow(error); // Let Next.js handle its own signals (prerendering, redirects).
    console.error("Could not load the latest offers", error);
    return <ErrorState title={dict.home.offersUnavailable} />;
  }
  if (page.items.length === 0) {
    return <EmptyState icon={<PackageOpen />} title={dict.home.noOffers} />;
  }
  return (
    <div className="flex flex-col gap-4">
      <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {page.items.map((offer) => (
          <li key={offer.id}>
            <OfferCard
              offer={offer}
              lang={lang}
              labels={dict.offers}
              enums={dict.labels}
            />
          </li>
        ))}
      </ul>
      <Link
        href={`/${lang}/offers`}
        className="inline-flex items-center gap-2 self-start font-semibold underline underline-offset-4"
      >
        {dict.home.seeAll}
        <ArrowRight aria-hidden="true" className="size-5" />
      </Link>
    </div>
  );
}
