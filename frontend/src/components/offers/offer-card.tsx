import Link from "next/link";
import { ImageOff, MapPin } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { Dictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import type { OfferSummary } from "@/lib/api/types";
import { formatPaise } from "@/lib/money";

export type OfferLabels = Dictionary["offers"];

export const offerPhotoSrc = (offerId: string, photoId: string) =>
  `/api/offer-photos/${offerId}/${photoId}`;

/** "Brand Product, 100 g". */
export function offerTitle(
  offer: OfferSummary,
  enums: Dictionary["labels"],
): string {
  return `${offer.brand} ${offer.productName}, ${offer.packSize} ${enums.packUnits[offer.packUnit]}`;
}

/** One offer in a list: photo, product, price per pack, days left and place. */
export function OfferCard({
  offer,
  lang,
  labels,
  enums,
}: {
  offer: OfferSummary;
  lang: string;
  labels: OfferLabels;
  enums: Dictionary["labels"];
}) {
  const title = offerTitle(offer, enums);
  return (
    <Link
      href={`/${lang}/offers/${offer.id}`}
      className="flex h-full flex-col overflow-hidden rounded-card border-2 border-border hover:bg-surface"
    >
      {offer.coverPhotoId ? (
        // eslint-disable-next-line @next/next/no-img-element -- served by our own route
        <img
          src={offerPhotoSrc(offer.id, offer.coverPhotoId)}
          alt=""
          loading="lazy"
          width={400}
          height={300}
          className="aspect-[4/3] w-full bg-surface object-cover"
        />
      ) : (
        <span className="flex aspect-[4/3] w-full items-center justify-center bg-surface text-muted">
          <ImageOff aria-hidden="true" className="size-8" />
          <span className="sr-only">{labels.noPhoto}</span>
        </span>
      )}
      <span className="flex flex-1 flex-col gap-2 p-4">
        <span className="text-title font-bold">{title}</span>
        <span className="text-lg font-semibold">
          {format(labels.perPack, {
            price: formatPaise(offer.pricePerPackPaise, lang),
          })}
        </span>
        <span className="flex flex-wrap gap-2">
          <Badge variant={offer.daysLeft < 60 ? "warning" : "neutral"}>
            {format(labels.daysLeft, { days: String(offer.daysLeft) })}
          </Badge>
          <Badge variant="accent">
            {enums.clearanceReasons[offer.clearanceReason]}
          </Badge>
        </span>
        <span className="mt-auto flex items-center gap-1 text-sm text-muted">
          <MapPin aria-hidden="true" className="size-4 shrink-0" />
          {offer.location.city}, {offer.location.state}
        </span>
      </span>
    </Link>
  );
}
