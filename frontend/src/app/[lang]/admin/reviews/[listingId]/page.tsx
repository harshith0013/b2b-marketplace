import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, ShieldAlert } from "lucide-react";
import { ActionForm } from "@/components/forms/action-form";
import {
  ListingFacts,
  ListingStatusBadge,
  productLine,
} from "@/components/listings/listing-facts";
import { PhotoGrid } from "@/components/listings/photo-grid";
import { ProblemList } from "@/components/listings/problem-list";
import { RejectForm } from "@/components/review/reject-form";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import { approveListing, rejectListing } from "@/lib/actions/review";
import type { ReviewDetails } from "@/lib/api/types";
import { requireSession } from "@/lib/auth/current-user";
import { requireUuids } from "@/lib/pages";
import { reviewerApi } from "@/lib/review-page";

type Params = PageProps<"/[lang]/admin/reviews/[listingId]">["params"];

async function ReviewContent({ params }: { params: Params }) {
  const { lang, listingId } = await params;
  if (!hasLocale(lang)) notFound();
  requireUuids(listingId);
  const dict = await getDictionary(lang);
  const { review: labels, listings, labels: enums } = dict;
  const errors = dict.business.errors;
  const returnTo = `/${lang}/admin/reviews/${listingId}`;
  const session = await requireSession(lang, returnTo);
  const details = await reviewerApi<ReviewDetails>(
    session,
    lang,
    returnTo,
    `/api/v1/admin/reviews/${listingId}`,
  );
  if (details === "forbidden") {
    return <EmptyState icon={<ShieldAlert />} title={labels.notAllowed} />;
  }
  if (details === "not-found") notFound();
  const { listing, seller, photos, problems, history } = details;
  const statuses = dict.business.statuses as Record<string, string>;
  const date = new Intl.DateTimeFormat(`${lang}-IN`, {
    dateStyle: "medium",
    timeStyle: "short",
  });

  return (
    <>
      <Link
        href={`/${lang}/admin/reviews`}
        className="inline-flex items-center gap-2 self-start font-semibold underline underline-offset-4"
      >
        <ArrowLeft aria-hidden="true" className="size-5" />
        {labels.back}
      </Link>
      <div className="flex flex-col gap-3">
        <h1 className="text-display font-bold">
          {productLine(listing, listings, enums)}
        </h1>
        <div className="flex flex-wrap gap-2">
          <ListingStatusBadge status={listing.status} enums={enums} />
          <Badge>
            {labels.seller}: {seller.name}
          </Badge>
          <Badge
            variant={
              seller.verificationStatus === "VERIFIED" ? "success" : "warning"
            }
          >
            {labels.verification}:{" "}
            {statuses[seller.verificationStatus] ?? seller.verificationStatus}
          </Badge>
        </div>
      </div>

      <section aria-labelledby="photos-heading" className="flex flex-col gap-3">
        <h2 id="photos-heading" className="text-heading font-bold">
          {listings.photosHeading}
        </h2>
        <PhotoGrid
          photos={photos}
          srcOf={(photo) => `/api/review-photos/${listingId}/${photo.id}`}
          labels={listings}
          kinds={enums.photoKinds}
        />
      </section>

      <section aria-labelledby="stock-heading" className="flex flex-col gap-3">
        <h2 id="stock-heading" className="text-heading font-bold">
          {listings.batchHeading}
        </h2>
        <Card>
          <ListingFacts
            listing={listing}
            lang={lang}
            labels={listings}
            enums={enums}
          />
        </Card>
      </section>

      <section aria-labelledby="checks-heading" className="flex flex-col gap-3">
        <h2 id="checks-heading" className="text-heading font-bold">
          {labels.problemsHeading}
        </h2>
        <ProblemList
          problems={problems}
          messages={enums.problems}
          allGood={labels.noProblems}
        />
      </section>

      {history.length > 0 && (
        <section
          aria-labelledby="history-heading"
          className="flex flex-col gap-3"
        >
          <h2 id="history-heading" className="text-heading font-bold">
            {labels.historyHeading}
          </h2>
          <ul className="flex flex-col gap-2">
            {history.map((decision) => (
              <li
                key={decision.decidedAt}
                className="rounded-card border-2 border-border p-3"
              >
                <span className="font-semibold">
                  {labels.decisions[decision.decision]}
                </span>{" "}
                ·{" "}
                <span className="text-sm text-muted">
                  {date.format(new Date(decision.decidedAt))}
                </span>
                {decision.reason && <p className="mt-1">{decision.reason}</p>}
              </li>
            ))}
          </ul>
        </section>
      )}

      {listing.status === "PENDING_REVIEW" && (
        <section
          aria-labelledby="decision-heading"
          className="flex flex-col gap-3"
        >
          <h2 id="decision-heading" className="text-heading font-bold">
            {labels.approveHeading}
          </h2>
          <Card className="flex flex-col gap-6">
            <ActionForm
              label={labels.approve}
              errors={errors}
              variant="accent"
              disabled={problems.length > 0}
              action={approveListing.bind(null, lang, listingId)}
            />
            <RejectForm
              labels={labels}
              errors={errors}
              action={rejectListing.bind(null, lang, listingId)}
            />
          </Card>
        </section>
      )}
    </>
  );
}

export default function ReviewPage({
  params,
}: PageProps<"/[lang]/admin/reviews/[listingId]">) {
  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-8">
      <Suspense fallback={<Skeleton className="h-96" />}>
        <ReviewContent params={params} />
      </Suspense>
    </div>
  );
}
