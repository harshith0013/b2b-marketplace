import { Suspense } from "react";
import Link from "next/link";
import { notFound, unstable_rethrow } from "next/navigation";
import { ArrowLeft, EyeOff, MapPin } from "lucide-react";
import {
  OfferCard,
  offerPhotoSrc,
  offerTitle,
} from "@/components/offers/offer-card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import { formatDate } from "@/components/listings/listing-facts";
import { ApiError, callPublic } from "@/lib/api/backend";
import type { OfferDetails, OfferSummary } from "@/lib/api/types";
import { formatGstRate, formatPaise } from "@/lib/money";
import { requireUuids } from "@/lib/pages";

type Props = PageProps<"/[lang]/offers/[listingId]">;

async function OfferContent({ params }: { params: Props["params"] }) {
  const { lang, listingId } = await params;
  if (!hasLocale(lang)) notFound();
  requireUuids(listingId);
  const dict = await getDictionary(lang);
  const { offers: labels, labels: enums } = dict;

  let details: OfferDetails;
  try {
    details = await callPublic<OfferDetails>(`/api/v1/offers/${listingId}`);
  } catch (error) {
    unstable_rethrow(error);
    if (error instanceof ApiError && error.status === 404) notFound();
    console.error("Could not load the offer", error);
    return <ErrorState title={labels.unavailable} />;
  }
  const { offer } = details;
  const title = offerTitle(offer, enums);
  const unit = enums.stockUnitsShort[offer.quantityUnit];
  const statuses = dict.business.statuses as Record<string, string>;
  const facts: [string, string][] = [
    [enums.dateTypes[offer.dateType], formatDate(offer.expiresOn, lang)],
    [
      dict.listings.quantity,
      format(labels.available, {
        quantity: String(offer.availableQuantity),
        unit,
      }) +
        (offer.packsPerCase
          ? ` · ${format(labels.packsPerCase, { packs: String(offer.packsPerCase) })}`
          : ""),
    ],
    [
      labels.mrp,
      offer.mrpPaise === null
        ? dict.listings.notGiven
        : formatPaise(offer.mrpPaise, lang),
    ],
    [labels.gst, formatGstRate(offer.gstRateBp)],
    [labels.batchNumber, details.batchNumber],
    [labels.clearanceReason, enums.clearanceReasons[offer.clearanceReason]],
  ];

  return (
    <>
      <Link
        href={`/${lang}/offers`}
        className="inline-flex items-center gap-2 self-start font-semibold underline underline-offset-4"
      >
        <ArrowLeft aria-hidden="true" className="size-5" />
        {labels.back}
      </Link>

      <div className="flex flex-col gap-3">
        <h1 className="text-display font-bold">{title}</h1>
        <p className="text-2xl font-bold">
          {format(labels.perPack, {
            price: formatPaise(offer.pricePerPackPaise, lang),
          })}
        </p>
        <div className="flex flex-wrap gap-2">
          <Badge variant={offer.daysLeft < 60 ? "warning" : "neutral"}>
            {format(labels.daysLeft, { days: String(offer.daysLeft) })}
          </Badge>
          <Badge>
            <MapPin aria-hidden="true" className="size-4" />
            {offer.location.city}, {offer.location.state}{" "}
            {offer.location.pincode}
          </Badge>
        </div>
      </div>

      {details.photos.length > 0 && (
        <section
          aria-labelledby="photos-heading"
          className="flex flex-col gap-3"
        >
          <h2 id="photos-heading" className="text-heading font-bold">
            {dict.listings.photosHeading}
          </h2>
          <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {details.photos.map((photo) => (
              <li key={photo.id}>
                <a
                  href={offerPhotoSrc(offer.id, photo.id)}
                  target="_blank"
                  rel="noopener"
                >
                  {/* eslint-disable-next-line @next/next/no-img-element -- served by our own route */}
                  <img
                    src={offerPhotoSrc(offer.id, photo.id)}
                    alt={format(labels.photoAlt, {
                      kind: enums.photoKinds[photo.kind],
                      product: title,
                    })}
                    width={photo.width}
                    height={photo.height}
                    loading="lazy"
                    className="aspect-square w-full rounded-card border-2 border-border object-cover"
                  />
                </a>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section aria-labelledby="prices-heading" className="flex flex-col gap-3">
        <h2 id="prices-heading" className="text-heading font-bold">
          {labels.tiersHeading}
        </h2>
        <Card className="flex flex-col gap-3">
          <ul className="flex flex-col gap-2">
            {details.priceTiers.map((tier) => (
              <li
                key={tier.minQuantity}
                className="flex flex-wrap justify-between gap-2"
              >
                <span>
                  {format(labels.tierRow, {
                    quantity: String(tier.minQuantity),
                    unit,
                  })}
                </span>
                <span className="font-semibold">
                  {format(labels.perUnit, {
                    price: formatPaise(tier.unitPricePaise, lang),
                    unit,
                  })}
                </span>
              </li>
            ))}
          </ul>
          <p className="text-sm text-muted">
            {format(labels.minOrder, {
              quantity: String(offer.moq),
              unit,
              value: formatPaise(offer.minimumOrderValuePaise, lang),
            })}
          </p>
        </Card>
      </section>

      <section
        aria-labelledby="details-heading"
        className="flex flex-col gap-3"
      >
        <h2 id="details-heading" className="text-heading font-bold">
          {dict.listings.batchHeading}
        </h2>
        <Card>
          <dl className="divide-y-2 divide-border">
            {facts.map(([label, value]) => (
              <div
                key={label}
                className="flex flex-col gap-0.5 py-2 sm:flex-row sm:justify-between"
              >
                <dt className="text-muted">{label}</dt>
                <dd className="font-semibold">{value}</dd>
              </div>
            ))}
          </dl>
          {details.notes && (
            <div className="mt-3 flex flex-col gap-1">
              <h3 className="font-semibold">{labels.notes}</h3>
              <p>{details.notes}</p>
            </div>
          )}
        </Card>
      </section>

      <section aria-labelledby="seller-heading" className="flex flex-col gap-3">
        <h2 id="seller-heading" className="text-heading font-bold">
          {labels.seller}
        </h2>
        <Card className="flex flex-col gap-2">
          <p className="flex items-center gap-2 text-title font-bold">
            {offer.seller.confidential && (
              <EyeOff aria-hidden="true" className="size-5" />
            )}
            {offer.seller.name ?? labels.confidentialSeller}
          </p>
          <Badge
            variant={
              offer.seller.verificationStatus === "VERIFIED"
                ? "success"
                : "warning"
            }
            className="self-start"
          >
            {labels.verification}:{" "}
            {statuses[offer.seller.verificationStatus] ??
              offer.seller.verificationStatus}
          </Badge>
        </Card>
      </section>

      <Suspense fallback={null}>
        <SimilarOffers listingId={offer.id} lang={lang} dict={dict} />
      </Suspense>

      <div className="flex flex-col gap-2">
        <Button
          variant="accent"
          size="lg"
          disabled
          aria-describedby="quote-note"
          className="self-start"
        >
          {labels.requestQuote}
        </Button>
        <p id="quote-note" className="text-sm text-muted">
          {labels.quoteComingSoon}
        </p>
      </div>
    </>
  );
}

export default function OfferPage({ params }: Props) {
  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-8">
      <Suspense fallback={<Skeleton className="h-96" />}>
        <OfferContent params={params} />
      </Suspense>
    </div>
  );
}

/** Similar offers (plain rules: category, place, price). Left out if they cannot be loaded. */
async function SimilarOffers({
  listingId,
  lang,
  dict,
}: {
  listingId: string;
  lang: string;
  dict: Awaited<ReturnType<typeof getDictionary>>;
}) {
  let similar: OfferSummary[];
  try {
    similar = await callPublic<OfferSummary[]>(
      `/api/v1/offers/${listingId}/similar`,
    );
  } catch (error) {
    unstable_rethrow(error);
    return null;
  }
  if (similar.length === 0) return null;
  return (
    <section aria-labelledby="similar-heading" className="flex flex-col gap-3">
      <h2 id="similar-heading" className="text-heading font-bold">
        {dict.offers.similarHeading}
      </h2>
      <ul className="grid gap-3 sm:grid-cols-2">
        {similar.map((item) => (
          <li key={item.id}>
            <OfferCard
              offer={item}
              lang={lang}
              labels={dict.offers}
              enums={dict.labels}
            />
          </li>
        ))}
      </ul>
    </section>
  );
}
