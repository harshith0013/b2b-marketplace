import { Suspense } from "react";
import Link from "next/link";
import { notFound, unstable_rethrow } from "next/navigation";
import {
  ArrowLeft,
  EyeOff,
  History,
  MapPin,
  ScanText,
  ShieldCheck,
} from "lucide-react";
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
import { OrderPanel } from "@/components/orders/order-panel";
import { addToCart, requestQuote } from "@/lib/actions/orders";
import { api, getSession } from "@/lib/auth/current-user";
import type { Locale } from "@/i18n/config";
import type { Me } from "@/lib/api/types";

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

      <section
        aria-labelledby="passport-heading"
        className="flex flex-col gap-3"
      >
        <h2 id="passport-heading" className="text-heading font-bold">
          {labels.passport.heading}
        </h2>
        <Card className="flex flex-col gap-2">
          <ul className="flex flex-col gap-2">
            {details.passport.approvedAt && (
              <li className="flex items-start gap-2">
                <ShieldCheck
                  aria-hidden="true"
                  className="mt-0.5 size-5 shrink-0 text-success"
                />
                {format(labels.passport.approved, {
                  date: new Intl.DateTimeFormat(`${lang}-IN`, {
                    dateStyle: "medium",
                  }).format(new Date(details.passport.approvedAt)),
                })}
              </li>
            )}
            <li className="flex items-start gap-2">
              <ScanText aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
              {details.passport.labelCheck === "MATCHES_LABEL"
                ? labels.passport.matchesLabel
                : labels.passport.reviewerChecked}
            </li>
            <li className="flex items-start gap-2">
              <History aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
              {details.passport.detailsChangedAfterApproval &&
              details.passport.changedAt
                ? format(labels.passport.changed, {
                    date: new Intl.DateTimeFormat(`${lang}-IN`, {
                      dateStyle: "medium",
                    }).format(new Date(details.passport.changedAt)),
                  })
                : labels.passport.notChanged}
            </li>
          </ul>
          <p className="text-sm text-muted">{labels.passport.disclaimer}</p>
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

      <section aria-labelledby="order-heading" className="flex flex-col gap-3">
        <h2 id="order-heading" className="text-heading font-bold">
          {dict.orders.checkoutHeading}
        </h2>
        <Card>
          <Suspense fallback={<Skeleton className="h-48" />}>
            <OfferOrdering
              lang={lang}
              listingId={offer.id}
              moq={offer.moq}
              unit={unit}
              dict={dict}
            />
          </Suspense>
        </Card>
      </section>
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

/** Ordering needs a signed-in owner or manager of a business; the seller's own offers are refused by the API. */
async function OfferOrdering({
  lang,
  listingId,
  moq,
  unit,
  dict,
}: {
  lang: Locale;
  listingId: string;
  moq: number;
  unit: string;
  dict: Awaited<ReturnType<typeof getDictionary>>;
}) {
  const labels = dict.orders;
  const returnTo = `/${lang}/offers/${listingId}`;
  const session = await getSession();
  if (!session)
    return (
      <div className="flex flex-col gap-3">
        <p>{labels.signInToOrder}</p>
        <Button asChild variant="accent" className="self-start">
          <Link
            href={`/${lang}/login?returnTo=${encodeURIComponent(returnTo)}`}
          >
            {dict.auth.signIn}
          </Link>
        </Button>
      </div>
    );
  const me = await api<Me>(session, lang, returnTo, "/api/v1/me");
  const buyers = me.businesses
    .filter((b) => b.role === "OWNER" || b.role === "MANAGER")
    .map((b) => ({ id: b.businessId, name: b.name }));
  if (buyers.length === 0)
    return (
      <div className="flex flex-col gap-3">
        <p>{labels.needBusiness}</p>
        <Button asChild variant="accent" className="self-start">
          <Link href={`/${lang}/onboarding`}>{dict.sell.createBusiness}</Link>
        </Button>
      </div>
    );
  return (
    <OrderPanel
      lang={lang}
      businesses={buyers}
      moq={moq}
      unit={unit}
      labels={labels}
      errors={dict.business.errors}
      addAction={addToCart.bind(null, lang, listingId)}
      quoteAction={requestQuote.bind(null, lang, listingId)}
    />
  );
}
