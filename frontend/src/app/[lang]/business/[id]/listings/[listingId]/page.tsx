import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { ActionForm } from "@/components/forms/action-form";
import {
  ListingFacts,
  ListingStatusBadge,
  productLine,
} from "@/components/listings/listing-facts";
import { PhotoGrid } from "@/components/listings/photo-grid";
import { PhotoUploadForm } from "@/components/listings/photo-upload-form";
import { ProblemList } from "@/components/listings/problem-list";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import {
  changeListing,
  deletePhoto,
  uploadPhoto,
} from "@/lib/actions/listings";
import type {
  BusinessDetails,
  EligibilityProblem,
  Listing,
  Photo,
  PhotoKind,
} from "@/lib/api/types";
import { api, requireSession } from "@/lib/auth/current-user";
import { apiOrNotFound, canEdit, requireUuids } from "@/lib/pages";

type Params = PageProps<"/[lang]/business/[id]/listings/[listingId]">["params"];

function suggestedKind(photos: Photo[]): PhotoKind {
  const kinds = new Set(photos.map((p) => p.kind));
  if (!kinds.has("PACK_FRONT")) return "PACK_FRONT";
  if (!kinds.has("LABEL_DATE")) return "LABEL_DATE";
  return "OTHER";
}

async function ListingContent({ params }: { params: Params }) {
  const { lang, id, listingId } = await params;
  if (!hasLocale(lang)) notFound();
  requireUuids(id, listingId);
  const dict = await getDictionary(lang);
  const { listings: labels, labels: enums } = dict;
  const errors = dict.business.errors;
  const returnTo = `/${lang}/business/${id}/listings/${listingId}`;
  const session = await requireSession(lang, returnTo);
  const base = `/api/v1/businesses/${id}`;
  const [business, listing] = await Promise.all([
    apiOrNotFound<BusinessDetails>(session, lang, returnTo, base),
    apiOrNotFound<Listing>(
      session,
      lang,
      returnTo,
      `${base}/listings/${listingId}`,
    ),
  ]);
  const batchId = listing.batch.id;
  const isDraft = listing.status === "DRAFT";
  const [photos, problems] = await Promise.all([
    api<Photo[]>(session, lang, returnTo, `${base}/batches/${batchId}/photos`),
    isDraft
      ? api<EligibilityProblem[]>(
          session,
          lang,
          returnTo,
          `${base}/listings/${listingId}/eligibility`,
        )
      : Promise.resolve([] as EligibilityProblem[]),
  ]);
  const editable = canEdit(business.yourRole);
  const act = (action: Parameters<typeof changeListing>[3]) =>
    changeListing.bind(null, lang, id, listingId, action);
  const photosEditable =
    editable &&
    listing.status !== "PENDING_REVIEW" &&
    listing.status !== "CLOSED";

  return (
    <>
      <div className="flex flex-col gap-3">
        <Link
          href={`/${lang}/business/${id}/listings`}
          className="inline-flex items-center gap-2 self-start font-semibold underline underline-offset-4"
        >
          <ArrowLeft aria-hidden="true" className="size-5" />
          {labels.back}
        </Link>
        <h1 className="text-display font-bold">
          {productLine(listing, labels, enums)}
        </h1>
        <span className="self-start">
          <ListingStatusBadge status={listing.status} enums={enums} />
        </span>
      </div>

      {listing.rejectionReason && isDraft && (
        <section
          aria-labelledby="rejected-heading"
          className="flex flex-col gap-2 rounded-card border-2 border-danger bg-danger-soft p-4"
        >
          <h2
            id="rejected-heading"
            className="text-title font-bold text-danger"
          >
            {labels.rejectedHeading}
          </h2>
          <p>{listing.rejectionReason}</p>
        </section>
      )}

      <section aria-labelledby="stock-heading" className="flex flex-col gap-3">
        <h2 id="stock-heading" className="text-heading font-bold">
          {labels.batchHeading}
        </h2>
        <Card>
          <ListingFacts
            listing={listing}
            lang={lang}
            labels={labels}
            enums={enums}
          />
        </Card>
      </section>

      <section aria-labelledby="photos-heading" className="flex flex-col gap-3">
        <h2 id="photos-heading" className="text-heading font-bold">
          {labels.photosHeading}
        </h2>
        <PhotoGrid
          photos={photos}
          srcOf={(photo) => `/api/photos/${id}/${batchId}/${photo.id}`}
          labels={labels}
          kinds={enums.photoKinds}
          deleteActionOf={
            editable && isDraft && listing.batch.status === "DRAFT"
              ? (photo) =>
                  deletePhoto.bind(null, lang, id, batchId, listingId, photo.id)
              : undefined
          }
        />
        {photosEditable && photos.length < 6 && (
          <Card>
            <h3 className="mb-4 text-title font-bold">
              {labels.uploadHeading}
            </h3>
            <PhotoUploadForm
              labels={labels}
              kinds={enums.photoKinds}
              errors={errors}
              suggestedKind={suggestedKind(photos)}
              action={uploadPhoto.bind(null, lang, id, batchId, listingId)}
            />
          </Card>
        )}
      </section>

      <section aria-labelledby="next-heading" className="flex flex-col gap-3">
        <h2 id="next-heading" className="text-heading font-bold">
          {labels.checksHeading}
        </h2>
        {isDraft && (
          <>
            <ProblemList
              problems={problems}
              messages={enums.problems}
              allGood={labels.ready}
            />
            {editable && (
              <ActionForm
                label={labels.submit}
                errors={errors}
                variant="accent"
                disabled={problems.length > 0}
                action={act("submit")}
              />
            )}
          </>
        )}
        {listing.status === "PENDING_REVIEW" && (
          <>
            <p className="text-muted">{labels.inReviewNote}</p>
            {editable && (
              <ActionForm
                label={labels.withdraw}
                errors={errors}
                variant="outline"
                action={act("withdraw")}
              />
            )}
          </>
        )}
        {listing.status === "PUBLISHED" && (
          <p className="text-muted">{labels.publishedNote}</p>
        )}
        {listing.status === "PAUSED" && (
          <p className="text-muted">{labels.pausedNote}</p>
        )}
        {listing.status === "CLOSED" && (
          <p className="text-muted">{labels.closedNote}</p>
        )}
        {editable && (
          <div className="flex flex-wrap gap-3">
            {listing.status === "PUBLISHED" && (
              <ActionForm
                label={labels.pause}
                errors={errors}
                variant="outline"
                action={act("pause")}
              />
            )}
            {listing.status === "PAUSED" && (
              <ActionForm
                label={labels.resume}
                errors={errors}
                action={act("resume")}
              />
            )}
            {listing.status !== "CLOSED" &&
              listing.status !== "PENDING_REVIEW" && (
                <ActionForm
                  label={labels.close}
                  errors={errors}
                  variant="ghost"
                  action={act("close")}
                />
              )}
          </div>
        )}
        {!editable && <p className="text-muted">{labels.readOnly}</p>}
      </section>
    </>
  );
}

export default function ListingPage({
  params,
}: PageProps<"/[lang]/business/[id]/listings/[listingId]">) {
  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-8">
      <Suspense fallback={<Skeleton className="h-96" />}>
        <ListingContent params={params} />
      </Suspense>
    </div>
  );
}
