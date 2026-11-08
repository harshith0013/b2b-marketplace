import { Suspense } from "react";
import Link from "next/link";
import { notFound, unstable_rethrow } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { ActionForm } from "@/components/forms/action-form";
import { DeliveryChoice } from "@/components/orders/delivery-choice";
import {
  QuoteStatusBadge,
  formatDateTime,
} from "@/components/orders/order-parts";
import { QuotePriceForm } from "@/components/orders/quote-price-form";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import { declineQuote, offerQuote, orderQuote } from "@/lib/actions/orders";
import { callPublic } from "@/lib/api/backend";
import type { OfferDetails, Quote } from "@/lib/api/types";
import { requireSession } from "@/lib/auth/current-user";
import { formatPaise } from "@/lib/money";
import { apiOrNotFound, requireUuids } from "@/lib/pages";

type Props = PageProps<"/[lang]/business/[id]/quotes/[quoteId]">;

/** The offer as it is now, for the buyer's delivery choice; null if no longer on sale. */
async function offerNow(listingId: string): Promise<OfferDetails | null> {
  try {
    return await callPublic<OfferDetails>(`/api/v1/offers/${listingId}`);
  } catch (error) {
    unstable_rethrow(error);
    return null;
  }
}

async function QuoteContent({ params }: { params: Props["params"] }) {
  const { lang, id, quoteId } = await params;
  if (!hasLocale(lang)) notFound();
  requireUuids(id, quoteId);
  const dict = await getDictionary(lang);
  const labels = dict.orders;
  const errors = dict.business.errors;
  const units = dict.labels.stockUnitsShort as Record<string, string>;
  const base = `/${lang}/business/${id}`;
  const returnTo = `${base}/quotes/${quoteId}`;
  const session = await requireSession(lang, returnTo);
  const quote = await apiOrNotFound<Quote>(
    session,
    lang,
    returnTo,
    `/api/v1/businesses/${id}/quotes/${quoteId}`,
  );
  const asSeller = quote.viewerSide === "SELLER";
  const open = quote.status === "REQUESTED" || quote.status === "OFFERED";
  const offer =
    !asSeller && quote.status === "OFFERED"
      ? await offerNow(quote.listingId)
      : null;

  return (
    <>
      <div className="flex flex-col gap-3">
        <Link
          href={`${base}/orders?side=${quote.viewerSide}`}
          className="inline-flex items-center gap-2 self-start font-semibold underline underline-offset-4"
        >
          <ArrowLeft aria-hidden="true" className="size-5" />
          {labels.ordersTitle}
        </Link>
        <h1 className="text-display font-bold">{quote.productLine}</h1>
        <p className="text-muted">
          {quote.quantity} {units[quote.unit]} ·{" "}
          {asSeller
            ? quote.buyerName
            : (quote.sellerName ?? labels.confidentialSeller)}
        </p>
        <QuoteStatusBadge status={quote.status} labels={labels} />
      </div>

      <Card className="flex flex-col gap-2">
        {quote.note && <p>{quote.note}</p>}
        {quote.offeredUnitPricePaise !== null && (
          <p className="text-xl font-bold">
            {formatPaise(quote.offeredUnitPricePaise, lang)} /{" "}
            {units[quote.unit]}
          </p>
        )}
        {quote.validUntil && (
          <p className="text-sm text-muted">
            {format(labels.validUntil, {
              date: formatDateTime(quote.validUntil, lang),
            })}
          </p>
        )}
      </Card>

      {asSeller && quote.status === "REQUESTED" && (
        <QuotePriceForm
          labels={labels}
          errors={errors}
          action={offerQuote.bind(null, lang, id, quoteId)}
        />
      )}
      {asSeller && open && (
        <ActionForm
          label={labels.declineQuote}
          variant="outline"
          errors={errors}
          action={declineQuote.bind(null, lang, id, quoteId)}
        />
      )}
      {offer && (
        <section aria-labelledby="order-quote" className="flex flex-col gap-3">
          <h2 id="order-quote" className="text-heading font-bold">
            {labels.orderQuote}
          </h2>
          <DeliveryChoice
            formId="quote"
            city={offer.offer.location.city}
            pickupAvailable={offer.delivery.pickupAvailable}
            deliveryAvailable={
              offer.delivery.ownDelivery && offer.delivery.codAccepted
            }
            pincodes={offer.delivery.deliveryPincodes}
            idempotencyKey={crypto.randomUUID()}
            submitLabel={labels.orderQuote}
            labels={labels}
            errors={errors}
            action={orderQuote.bind(null, lang, id, quoteId)}
          />
        </section>
      )}
    </>
  );
}

export default function QuotePage({ params }: Props) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-8">
      <Suspense fallback={<Skeleton className="h-96" />}>
        <QuoteContent params={params} />
      </Suspense>
    </div>
  );
}
