import { Badge } from "@/components/ui/badge";
import type { Dictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import type { Listing, ListingStatus } from "@/lib/api/types";
import { formatGstRate, formatPaise } from "@/lib/money";

type Labels = Dictionary["listings"];
type Enums = Dictionary["labels"];

const STATUS_VARIANT: Record<
  ListingStatus,
  "neutral" | "info" | "success" | "warning" | "danger"
> = {
  DRAFT: "neutral",
  PENDING_REVIEW: "info",
  PUBLISHED: "success",
  PAUSED: "warning",
  CLOSED: "neutral",
};

export function ListingStatusBadge({
  status,
  enums,
}: {
  status: ListingStatus;
  enums: Enums;
}) {
  return (
    <Badge variant={STATUS_VARIANT[status]}>
      {enums.listingStatuses[status]}
    </Badge>
  );
}

/** "Brand Product, 100 g". */
export function productLine(
  listing: Listing,
  labels: Labels,
  enums: Enums,
): string {
  const { variant } = listing.batch;
  const pack = format(labels.pack, {
    size: String(variant.packSize),
    unit: enums.packUnits[variant.packUnit],
  });
  return `${variant.brand} ${variant.productName}, ${pack}`;
}

/** A calendar date from the API ("2026-12-31"), shown as printed on the pack. */
export function formatDate(isoDate: string, lang: string): string {
  return new Intl.DateTimeFormat(`${lang}-IN`, {
    dateStyle: "medium",
    timeZone: "UTC",
  }).format(new Date(`${isoDate}T00:00:00Z`));
}

export function expiryLine(
  listing: Listing,
  lang: string,
  enums: Enums,
  labels: Labels,
): string {
  return format(labels.expires, {
    type: enums.dateTypes[listing.batch.dateType],
    date: formatDate(listing.batch.expiresOn, lang),
  });
}

/** Stock, prices and dates of a listing, as a seller or reviewer reads them. */
export function ListingFacts({
  listing,
  lang,
  labels,
  enums,
}: {
  listing: Listing;
  lang: string;
  labels: Labels;
  enums: Enums;
}) {
  const unit = enums.stockUnitsShort[listing.quantityUnit];
  const { batch } = listing;
  const rows: [string, string][] = [
    [labels.batchNumber, batch.batchNumber],
    [enums.dateTypes[batch.dateType], formatDate(batch.expiresOn, lang)],
    [
      labels.quantity,
      `${batch.quantity} ${unit}${batch.packsPerCase ? ` × ${batch.packsPerCase}` : ""}`,
    ],
    [
      labels.mrp,
      batch.mrpPaise === null
        ? labels.notGiven
        : formatPaise(batch.mrpPaise, lang),
    ],
    [labels.gst, formatGstRate(listing.gstRateBp)],
  ];

  return (
    <div className="flex flex-col gap-4">
      <dl className="divide-y-2 divide-border">
        {rows.map(([label, value]) => (
          <div
            key={label}
            className="flex flex-col gap-0.5 py-2 sm:flex-row sm:justify-between"
          >
            <dt className="text-muted">{label}</dt>
            <dd className="font-semibold">{value}</dd>
          </div>
        ))}
      </dl>
      <div className="flex flex-col gap-2">
        <h3 className="text-title font-bold">{labels.pricesHeading}</h3>
        <ul className="flex flex-col gap-1">
          {listing.priceTiers.map((tier) => (
            <li key={tier.minQuantity} className="flex justify-between gap-3">
              <span>
                {format(labels.tier, {
                  quantity: String(tier.minQuantity),
                  unit,
                })}
              </span>
              <span className="font-semibold">
                {format(labels.priceFrom, {
                  price: formatPaise(tier.unitPricePaise, lang),
                  unit,
                })}
              </span>
            </li>
          ))}
        </ul>
        <p className="text-sm text-muted">
          {enums.clearanceReasons[listing.clearanceReason]} ·{" "}
          {enums.visibility[listing.visibility]}
        </p>
        {listing.notes && <p className="text-sm">{listing.notes}</p>}
      </div>
    </div>
  );
}
