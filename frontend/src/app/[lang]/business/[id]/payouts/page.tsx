import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { PayoutForm } from "@/components/orders/payout-form";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import { setUpPayouts } from "@/lib/actions/orders";
import type { BusinessDetails, PayoutAccount } from "@/lib/api/types";
import { requireSession } from "@/lib/auth/current-user";
import { apiOrNotFound, requireUuids } from "@/lib/pages";

type Props = PageProps<"/[lang]/business/[id]/payouts">;

/** Set-up can be (re)done until Razorpay has the account active or under review. */
const SETTLED = new Set(["ACTIVATED", "UNDER_REVIEW"]);

async function PayoutsContent({ params }: { params: Props["params"] }) {
  const { lang, id } = await params;
  if (!hasLocale(lang)) notFound();
  requireUuids(id);
  const dict = await getDictionary(lang);
  const labels = dict.payouts;
  const base = `/${lang}/business/${id}`;
  const returnTo = `${base}/payouts`;
  const session = await requireSession(lang, returnTo);
  const [business, account] = await Promise.all([
    apiOrNotFound<BusinessDetails>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${id}`,
    ),
    apiOrNotFound<PayoutAccount>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${id}/payout-account`,
    ),
  ]);
  const isOwner = business.yourRole === "OWNER";

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
        <h1 className="text-display font-bold">{labels.title}</h1>
        <p className="text-muted">{labels.intro}</p>
      </div>

      <Card className="flex flex-col gap-2">
        <p className="flex flex-wrap items-center gap-2">
          <span className="text-muted">{labels.statusLabel}:</span>
          <Badge variant={account.canTakeOnline ? "success" : "warning"}>
            {labels.statuses[account.status]}
          </Badge>
        </p>
        {account.bankLast4 && account.ifscCode && (
          <p>
            {format(labels.bank, {
              last4: account.bankLast4,
              ifsc: account.ifscCode,
            })}
          </p>
        )}
      </Card>

      {!account.available ? (
        <p className="text-muted">{labels.off}</p>
      ) : !isOwner ? (
        <p className="text-muted">{labels.ownerOnly}</p>
      ) : (
        !SETTLED.has(account.status) && (
          <Card>
            <PayoutForm
              labels={labels}
              errors={dict.business.errors}
              action={setUpPayouts.bind(null, lang, id)}
            />
          </Card>
        )
      )}
    </>
  );
}

export default function PayoutsPage({ params }: Props) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-8">
      <Suspense fallback={<Skeleton className="h-96" />}>
        <PayoutsContent params={params} />
      </Suspense>
    </div>
  );
}
