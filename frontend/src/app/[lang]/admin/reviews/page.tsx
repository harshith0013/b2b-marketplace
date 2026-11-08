import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronRight, Inbox, ShieldAlert } from "lucide-react";
import { expiryLine, productLine } from "@/components/listings/listing-facts";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import type { PageResult, ReviewItem } from "@/lib/api/types";
import { requireSession } from "@/lib/auth/current-user";
import { reviewerApi } from "@/lib/review-page";

type Params = PageProps<"/[lang]/admin/reviews">["params"];

async function QueueContent({ params }: { params: Params }) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const dict = await getDictionary(lang);
  const { review: labels } = dict;
  const returnTo = `/${lang}/admin/reviews`;
  const session = await requireSession(lang, returnTo);
  const queue = await reviewerApi<PageResult<ReviewItem>>(
    session,
    lang,
    returnTo,
    "/api/v1/admin/reviews?size=50",
  );
  if (queue === "forbidden" || queue === "not-found") {
    return <EmptyState icon={<ShieldAlert />} title={labels.notAllowed} />;
  }
  if (queue.items.length === 0)
    return <EmptyState icon={<Inbox />} title={labels.empty} />;
  const date = new Intl.DateTimeFormat(`${lang}-IN`, {
    dateStyle: "medium",
    timeStyle: "short",
  });

  return (
    <ul className="flex flex-col gap-3">
      {queue.items.map(({ listing, seller, problems, flags }) => (
        <li key={listing.id}>
          <Link
            href={`${returnTo}/${listing.id}`}
            className="flex items-center justify-between gap-3 rounded-card border-2 border-border p-4 hover:bg-surface"
          >
            <span className="flex flex-col gap-1">
              <span className="font-semibold">
                {productLine(listing, dict.listings, dict.labels)}
              </span>
              <span className="text-sm text-muted">
                {labels.seller}: {seller.name}
              </span>
              <span className="text-sm text-muted">
                {expiryLine(listing, lang, dict.labels, dict.listings)}
              </span>
              {listing.submittedAt && (
                <span className="text-sm text-muted">
                  {format(labels.submitted, {
                    date: date.format(new Date(listing.submittedAt)),
                  })}
                </span>
              )}
              {flags.length > 0 && (
                <Badge variant="info" className="self-start">
                  {flags.length} × {labels.flagsHeading}
                </Badge>
              )}
              {problems.length > 0 && (
                <Badge variant="warning" className="self-start">
                  {problems.length} × {labels.problemsHeading}
                </Badge>
              )}
            </span>
            <span className="inline-flex items-center gap-1 font-semibold">
              {labels.open}
              <ChevronRight aria-hidden="true" className="size-5" />
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

export default async function ReviewQueuePage({
  params,
}: PageProps<"/[lang]/admin/reviews">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const { review } = await getDictionary(lang);
  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-8">
      <div className="flex flex-col gap-2">
        <h1 className="text-display font-bold">{review.title}</h1>
        <p className="text-muted">{review.intro}</p>
      </div>
      <Suspense fallback={<Skeleton className="h-96" />}>
        <QueueContent params={params} />
      </Suspense>
    </div>
  );
}
