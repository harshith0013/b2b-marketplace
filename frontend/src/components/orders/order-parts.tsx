import { Badge } from "@/components/ui/badge";
import type { Dictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import type { Order, OrderStatus, QuoteStatus } from "@/lib/api/types";
import { formatPaise } from "@/lib/money";

type BadgeVariant = "neutral" | "success" | "danger" | "warning" | "info";

const ORDER_VARIANTS: Record<OrderStatus, BadgeVariant> = {
  PLACED: "warning",
  ACCEPTED: "info",
  DECLINED: "danger",
  CANCELLED: "danger",
  EXPIRED: "neutral",
  READY_FOR_PICKUP: "info",
  OUT_FOR_DELIVERY: "info",
  DELIVERED: "success",
  PICKED_UP: "success",
  COMPLETED: "success",
};

const QUOTE_VARIANTS: Record<QuoteStatus, BadgeVariant> = {
  REQUESTED: "warning",
  OFFERED: "info",
  DECLINED: "danger",
  ORDERED: "success",
  EXPIRED: "neutral",
};

export function OrderStatusBadge({
  status,
  labels,
}: {
  status: OrderStatus;
  labels: Dictionary["orders"];
}) {
  return (
    <Badge variant={ORDER_VARIANTS[status]}>{labels.statuses[status]}</Badge>
  );
}

export function QuoteStatusBadge({
  status,
  labels,
}: {
  status: QuoteStatus;
  labels: Dictionary["orders"];
}) {
  return (
    <Badge variant={QUOTE_VARIANTS[status]}>
      {labels.quoteStatuses[status]}
    </Badge>
  );
}

export function formatDateTime(iso: string, lang: string): string {
  return new Intl.DateTimeFormat(`${lang}-IN`, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(iso));
}

/** The other business's name, or "a confidential seller" while it is hidden. */
export function counterparty(order: Order, labels: Dictionary["orders"]) {
  return order.viewerSide === "SELLER"
    ? (order.buyerName ?? "")
    : (order.sellerName ?? labels.confidentialSeller);
}

/** Items, GST, delivery and total, as the API computed them. */
export function OrderTotals({
  order,
  lang,
  labels,
  units,
}: {
  order: Order;
  lang: string;
  labels: Dictionary["orders"];
  units: Record<string, string>;
}) {
  const rows: [string, number][] = [
    [labels.subtotal, order.itemsPaise],
    [labels.gst, order.gstPaise],
    [labels.deliveryCharge, order.deliveryPaise],
  ];
  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col gap-2">
        {order.items.map((item) => (
          <li
            key={item.listingId}
            className="flex flex-wrap justify-between gap-2"
          >
            <span>
              {item.productLine} · {item.quantity} {units[item.unit]} ×{" "}
              {formatPaise(item.unitPricePaise, lang)}
            </span>
            <span className="font-semibold">
              {formatPaise(item.linePaise, lang)}
            </span>
          </li>
        ))}
      </ul>
      <dl className="flex flex-col gap-1 border-t-2 border-border pt-3">
        {rows.map(([label, paise]) => (
          <div key={label} className="flex justify-between gap-2">
            <dt className="text-muted">{label}</dt>
            <dd>{formatPaise(paise, lang)}</dd>
          </div>
        ))}
        <div className="flex justify-between gap-2 text-lg font-bold">
          <dt>{labels.total}</dt>
          <dd>{formatPaise(order.totalPaise, lang)}</dd>
        </div>
      </dl>
    </div>
  );
}

export function placedLine(
  order: Order,
  lang: string,
  labels: Dictionary["orders"],
) {
  return format(labels.placed, { date: formatDateTime(order.placedAt, lang) });
}
