import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { OrderStepForm } from "@/components/orders/order-step-form";
import {
  OrderStatusBadge,
  OrderTotals,
  counterparty,
  placedLine,
} from "@/components/orders/order-parts";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import { actOnOrder } from "@/lib/actions/orders";
import { ORDER_ACTIONS, type Order } from "@/lib/api/types";
import { requireSession } from "@/lib/auth/current-user";
import { NEEDS_DELIVERY_PERSON, needsReason } from "@/lib/orders";
import { apiOrNotFound, requireUuids } from "@/lib/pages";

type Props = PageProps<"/[lang]/business/[id]/orders/[orderId]">;

async function OrderContent({ params }: { params: Props["params"] }) {
  const { lang, id, orderId } = await params;
  if (!hasLocale(lang)) notFound();
  requireUuids(id, orderId);
  const dict = await getDictionary(lang);
  const labels = dict.orders;
  const base = `/${lang}/business/${id}`;
  const returnTo = `${base}/orders/${orderId}`;
  const session = await requireSession(lang, returnTo);
  const order = await apiOrNotFound<Order>(
    session,
    lang,
    returnTo,
    `/api/v1/businesses/${id}/orders/${orderId}`,
  );
  // Show steps in the order they happen, whatever order the API lists them in.
  const steps = ORDER_ACTIONS.filter((a) => order.allowedActions.includes(a));
  const handover: [string, string][] =
    order.deliveryMethod === "PICKUP"
      ? [[labels.pickupFrom, `${order.pickupCity}, ${order.pickupState}`]]
      : [
          [
            labels.deliverTo,
            `${order.deliveryAddress ?? ""} ${order.deliveryPincode ?? ""}`,
          ],
        ];
  const facts: [string, string][] = [
    [
      order.viewerSide === "SELLER" ? labels.buyer : labels.seller,
      counterparty(order, labels),
    ],
    ...handover,
    [
      labels.payment,
      `${labels.paymentMethods[order.paymentMethod]} · ${
        order.paymentStatus === "PAID" ? labels.paid : labels.unpaid
      }`,
    ],
  ];
  if (order.statusReason) facts.push([labels.reason, order.statusReason]);
  if (order.deliveryPersonName)
    facts.push([
      labels.deliveryPerson,
      `${order.deliveryPersonName} · ${order.deliveryPersonPhone ?? ""}`,
    ]);

  return (
    <>
      <div className="flex flex-col gap-3">
        <Link
          href={`${base}/orders?side=${order.viewerSide}`}
          className="inline-flex items-center gap-2 self-start font-semibold underline underline-offset-4"
        >
          <ArrowLeft aria-hidden="true" className="size-5" />
          {labels.ordersTitle}
        </Link>
        <h1 className="text-display font-bold">{labels.orderTitle}</h1>
        <p className="text-muted">{placedLine(order, lang, labels)}</p>
        <div className="flex flex-wrap gap-2">
          <OrderStatusBadge status={order.status} labels={labels} />
          {order.paymentStatus === "PAID" && (
            <Badge variant="success">{labels.paid}</Badge>
          )}
        </div>
      </div>

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
      </Card>

      <Card>
        <OrderTotals
          order={order}
          lang={lang}
          labels={labels}
          units={dict.labels.stockUnitsShort as Record<string, string>}
        />
      </Card>

      {steps.length > 0 && (
        <section className="flex flex-col gap-6" aria-label={labels.orderTitle}>
          {steps.map((step) => (
            <OrderStepForm
              key={step}
              step={step}
              askReason={needsReason(step, order.viewerSide)}
              askDeliveryPerson={NEEDS_DELIVERY_PERSON.includes(step)}
              labels={labels}
              errors={dict.business.errors}
              action={actOnOrder.bind(null, lang, id, orderId, step)}
            />
          ))}
        </section>
      )}
    </>
  );
}

export default function OrderPage({ params }: Props) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-8">
      <Suspense fallback={<Skeleton className="h-96" />}>
        <OrderContent params={params} />
      </Suspense>
    </div>
  );
}
