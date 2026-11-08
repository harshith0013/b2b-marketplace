import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, ClipboardList } from "lucide-react";
import {
  OrderStatusBadge,
  QuoteStatusBadge,
  counterparty,
  formatDateTime,
} from "@/components/orders/order-parts";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import type { BusinessDetails, Order, Quote } from "@/lib/api/types";
import { requireSession } from "@/lib/auth/current-user";
import { formatPaise } from "@/lib/money";
import { isSide } from "@/lib/orders";
import { apiOrNotFound, requireUuids } from "@/lib/pages";
import { cn } from "@/lib/utils";

type Props = PageProps<"/[lang]/business/[id]/orders">;

async function OrdersContent({ params, searchParams }: Props) {
  const { lang, id } = await params;
  if (!hasLocale(lang)) notFound();
  requireUuids(id);
  const requested = (await searchParams).side;
  const side = isSide(requested) ? requested : "BUYER";
  const dict = await getDictionary(lang);
  const labels = dict.orders;
  const base = `/${lang}/business/${id}`;
  const returnTo = `${base}/orders?side=${side}`;
  const session = await requireSession(lang, returnTo);
  const [business, orders, quotes] = await Promise.all([
    apiOrNotFound<BusinessDetails>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${id}`,
    ),
    apiOrNotFound<Order[]>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${id}/orders?side=${side}`,
    ),
    apiOrNotFound<Quote[]>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${id}/quotes?side=${side}`,
    ),
  ]);
  const tabs = [
    ["BUYER", labels.bought],
    ["SELLER", labels.sold],
  ] as const;

  return (
    <>
      <div className="flex flex-col gap-3">
        <Link
          href={base}
          className="inline-flex items-center gap-2 self-start font-semibold underline underline-offset-4"
        >
          <ArrowLeft aria-hidden="true" className="size-5" />
          {business.tradeName ?? business.legalName}
        </Link>
        <h1 className="text-display font-bold">{labels.ordersTitle}</h1>
        <nav aria-label={labels.ordersTitle} className="flex gap-2">
          {tabs.map(([value, label]) => (
            <Link
              key={value}
              href={`${base}/orders?side=${value}`}
              aria-current={side === value ? "page" : undefined}
              className={cn(
                "rounded-lg border-2 border-border px-4 py-2 font-semibold",
                side === value && "bg-foreground text-background",
              )}
            >
              {label}
            </Link>
          ))}
        </nav>
      </div>

      <section aria-labelledby="orders-heading" className="flex flex-col gap-3">
        <h2 id="orders-heading" className="text-heading font-bold">
          {labels.ordersTitle}
        </h2>
        {orders.length === 0 ? (
          <EmptyState icon={<ClipboardList />} title={labels.noOrders} />
        ) : (
          <ul className="flex flex-col gap-2">
            {orders.map((order) => (
              <li key={order.id}>
                <Link
                  href={`${base}/orders/${order.id}`}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-card border-2 border-border p-4 hover:bg-surface"
                >
                  <span className="flex flex-col gap-1">
                    <span className="font-semibold">
                      {order.items.map((item) => item.productLine).join(", ")}
                    </span>
                    <span className="text-sm text-muted">
                      {counterparty(order, labels)} ·{" "}
                      {formatDateTime(order.placedAt, lang)} ·{" "}
                      {formatPaise(order.totalPaise, lang)}
                    </span>
                  </span>
                  <OrderStatusBadge status={order.status} labels={labels} />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {quotes.length > 0 && (
        <section
          aria-labelledby="quotes-heading"
          className="flex flex-col gap-3"
        >
          <h2 id="quotes-heading" className="text-heading font-bold">
            {labels.quotesHeading}
          </h2>
          <ul className="flex flex-col gap-2">
            {quotes.map((quote) => (
              <li key={quote.id}>
                <Link
                  href={`${base}/quotes/${quote.id}`}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-card border-2 border-border p-4 hover:bg-surface"
                >
                  <span className="flex flex-col gap-1">
                    <span className="font-semibold">{quote.productLine}</span>
                    <span className="text-sm text-muted">
                      {quote.quantity}{" "}
                      {
                        (dict.labels.stockUnitsShort as Record<string, string>)[
                          quote.unit
                        ]
                      }{" "}
                      · {formatDateTime(quote.createdAt, lang)}
                    </span>
                  </span>
                  <QuoteStatusBadge status={quote.status} labels={labels} />
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}

export default function OrdersPage(props: Props) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-8">
      <Suspense fallback={<Skeleton className="h-96" />}>
        <OrdersContent {...props} />
      </Suspense>
    </div>
  );
}
