import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, EyeOff, ShoppingCart } from "lucide-react";
import { DeliveryChoice } from "@/components/orders/delivery-choice";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import { checkout, removeFromCart } from "@/lib/actions/orders";
import type { BusinessDetails, CartLine } from "@/lib/api/types";
import { requireSession } from "@/lib/auth/current-user";
import { formatPaise } from "@/lib/money";
import { groupBySeller } from "@/lib/orders";
import { apiOrNotFound, requireUuids } from "@/lib/pages";

type Props = PageProps<"/[lang]/business/[id]/cart">;

async function CartContent({ params }: { params: Props["params"] }) {
  const { lang, id } = await params;
  if (!hasLocale(lang)) notFound();
  requireUuids(id);
  const dict = await getDictionary(lang);
  const labels = dict.orders;
  const units = dict.labels.stockUnitsShort as Record<string, string>;
  const returnTo = `/${lang}/business/${id}/cart`;
  const session = await requireSession(lang, returnTo);
  const [business, lines] = await Promise.all([
    apiOrNotFound<BusinessDetails>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${id}`,
    ),
    apiOrNotFound<CartLine[]>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${id}/cart`,
    ),
  ]);

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
        <h1 className="text-display font-bold">{labels.cartTitle}</h1>
      </div>

      {lines.length === 0 ? (
        <EmptyState
          icon={<ShoppingCart />}
          title={labels.cartEmpty}
          action={
            <Button asChild variant="accent">
              <Link href={`/${lang}/offers`}>{labels.browse}</Link>
            </Button>
          }
        />
      ) : (
        groupBySeller(lines).map((group, index) => {
          const headingId = `seller-${index}`;
          const itemsPaise = group.orderable.reduce(
            (sum, line) => sum + line.quantity * line.unitPricePaise,
            0,
          );
          return (
            <section
              key={group.sellerKey}
              aria-labelledby={headingId}
              className="flex flex-col gap-3"
            >
              <h2
                id={headingId}
                className="flex items-center gap-2 text-heading font-bold"
              >
                {!group.sellerName && (
                  <EyeOff aria-hidden="true" className="size-5" />
                )}
                {format(labels.sellerGroup, {
                  seller: group.sellerName ?? labels.confidentialSeller,
                  city: group.city,
                })}
              </h2>
              <Card className="flex flex-col gap-4">
                <ul className="flex flex-col gap-3">
                  {group.lines.map((line) => (
                    <li
                      key={line.listingId}
                      className="flex flex-wrap items-center justify-between gap-3"
                    >
                      <span className="flex flex-col gap-1">
                        <Link
                          href={`/${lang}/offers/${line.listingId}`}
                          className="font-semibold underline underline-offset-4"
                        >
                          {line.productLine}
                        </Link>
                        <span className="text-sm text-muted">
                          {line.available
                            ? `${line.quantity} ${units[line.unit]} × ${formatPaise(line.unitPricePaise, lang)}`
                            : labels.unavailable}
                        </span>
                      </span>
                      <form
                        action={removeFromCart.bind(
                          null,
                          lang,
                          id,
                          line.listingId,
                        )}
                      >
                        <Button type="submit" variant="outline">
                          {labels.remove}
                          <span className="sr-only">, {line.productLine}</span>
                        </Button>
                      </form>
                    </li>
                  ))}
                </ul>
                {group.orderable.length > 0 && (
                  <>
                    <p className="flex justify-between border-t-2 border-border pt-3 font-semibold">
                      <span>{labels.subtotal}</span>
                      <span>{formatPaise(itemsPaise, lang)}</span>
                    </p>
                    <DeliveryChoice
                      formId={headingId}
                      city={group.city}
                      pickupAvailable={group.pickupAvailable}
                      deliveryAvailable={
                        group.ownDelivery &&
                        (group.codAccepted || group.onlineAccepted)
                      }
                      codAvailable={group.codAccepted}
                      onlineAvailable={group.onlineAccepted}
                      pincodes={group.deliveryPincodes}
                      idempotencyKey={crypto.randomUUID()}
                      submitLabel={labels.placeOrder}
                      labels={labels}
                      errors={dict.business.errors}
                      action={checkout.bind(
                        null,
                        lang,
                        id,
                        group.orderable.map((line) => line.listingId),
                      )}
                    />
                  </>
                )}
              </Card>
            </section>
          );
        })
      )}
    </>
  );
}

export default function CartPage({ params }: Props) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-8">
      <Suspense fallback={<Skeleton className="h-96" />}>
        <CartContent params={params} />
      </Suspense>
    </div>
  );
}
