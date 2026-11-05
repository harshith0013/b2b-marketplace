import { Suspense } from "react";
import { notFound } from "next/navigation";
import { BusinessForm } from "@/components/business/business-form";
import { InviteForm } from "@/components/business/invite-form";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale } from "@/i18n/config";
import { getDictionary, type Dictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import { inviteMember, revokeInvitation, updateBusiness } from "@/lib/actions/business";
import { ApiError } from "@/lib/api/backend";
import type { BusinessDetails, Member, SentInvitation } from "@/lib/api/types";
import { api, requireSession } from "@/lib/auth/current-user";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type Labels = Dictionary["business"];

function DetailsList({ business, labels }: { business: BusinessDetails; labels: Labels }) {
  const rows: [string, string][] = [
    [labels.fields.legalName, business.legalName],
    [labels.fields.tradeName, business.tradeName ?? "—"],
    [labels.fields.businessType, labels.types[business.businessType]],
    [labels.fields.gstin, business.gstin ?? "—"],
    [labels.fields.city, business.city],
    [labels.fields.state, business.state],
    [labels.fields.pincode, business.pincode],
  ];
  return (
    <dl className="divide-y-2 divide-border">
      {rows.map(([label, value]) => (
        <div key={label} className="flex flex-col gap-0.5 py-3 sm:flex-row sm:justify-between">
          <dt className="text-muted">{label}</dt>
          <dd className="font-semibold">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

/** Everything here depends on the business id, which is only known per request. */
async function BusinessContent({ params }: { params: PageProps<"/[lang]/business/[id]">["params"] }) {
  const { lang, id } = await params;
  if (!hasLocale(lang) || !UUID.test(id)) notFound();
  const dict = await getDictionary(lang);
  const returnTo = `/${lang}/business/${id}`;
  const session = await requireSession(lang, returnTo);
  const labels = dict.business;
  const roles = dict.account.roles;

  let business: BusinessDetails;
  try {
    business = await api<BusinessDetails>(session, lang, returnTo, `/api/v1/businesses/${id}`);
  } catch (error) {
    // Not a member: the API says 404, and so do we.
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
  const isOwner = business.yourRole === "OWNER";
  const canSeeTeam = isOwner || business.yourRole === "MANAGER";
  const [members, invitations] = canSeeTeam
    ? await Promise.all([
        api<Member[]>(session, lang, returnTo, `/api/v1/businesses/${id}/members`),
        api<SentInvitation[]>(session, lang, returnTo, `/api/v1/businesses/${id}/invitations`),
      ])
    : [[], []];
  const date = new Intl.DateTimeFormat(`${lang}-IN`, { dateStyle: "medium" });

  return (
    <>
      <div className="flex flex-col gap-3">
        <h1 className="text-display font-bold">{business.tradeName ?? business.legalName}</h1>
        <div className="flex flex-wrap gap-2">
          <Badge>
            {labels.yourRole}: {roles[business.yourRole]}
          </Badge>
          <Badge variant={business.verificationStatus === "VERIFIED" ? "success" : "warning"}>
            {labels.verification}:{" "}
            {labels.statuses[business.verificationStatus as keyof Labels["statuses"]] ??
              business.verificationStatus}
          </Badge>
        </div>
      </div>

      <section aria-labelledby="details-heading" className="flex flex-col gap-3">
        <h2 id="details-heading" className="text-heading font-bold">
          {labels.detailsHeading}
        </h2>
        <Card>
          {isOwner ? (
            <BusinessForm
              labels={labels}
              action={updateBusiness.bind(null, lang, id)}
              submitLabel={labels.save}
              initial={business}
            />
          ) : (
            <DetailsList business={business} labels={labels} />
          )}
        </Card>
      </section>

      {canSeeTeam && (
        <section aria-labelledby="members-heading" className="flex flex-col gap-3">
          <h2 id="members-heading" className="text-heading font-bold">
            {labels.membersHeading}
          </h2>
          <ul className="flex flex-col gap-2">
            {members.map((member) => (
              <li
                key={member.userId}
                className="flex flex-wrap items-center justify-between gap-2 rounded-card border-2 border-border p-4"
              >
                <span className="font-semibold break-all">{member.displayName ?? member.email}</span>
                <Badge>{roles[member.role]}</Badge>
              </li>
            ))}
          </ul>

          <h3 className="text-title font-bold">{labels.invitationsHeading}</h3>
          {invitations.length === 0 ? (
            <p className="text-muted">{labels.noInvitations}</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {invitations.map((invitation) => (
                <li
                  key={invitation.id}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-card border-2 border-dashed border-border p-4"
                >
                  <span className="flex flex-col">
                    <span className="font-semibold break-all">{invitation.email}</span>
                    <span className="text-sm text-muted">
                      {roles[invitation.role]} ·{" "}
                      {format(labels.expires, { date: date.format(new Date(invitation.expiresAt)) })}
                    </span>
                  </span>
                  {isOwner && (
                    <form action={revokeInvitation.bind(null, lang, id, invitation.id)}>
                      <Button type="submit" variant="outline">
                        {labels.revoke}
                      </Button>
                    </form>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {isOwner && (
        <section aria-labelledby="invite-heading" className="flex flex-col gap-3">
          <h2 id="invite-heading" className="text-heading font-bold">
            {labels.inviteHeading}
          </h2>
          <Card>
            <InviteForm labels={labels} roles={roles} action={inviteMember.bind(null, lang, id)} />
          </Card>
        </section>
      )}
    </>
  );
}

export default function BusinessPage({ params }: PageProps<"/[lang]/business/[id]">) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-8">
      <Suspense fallback={<Skeleton className="h-96" />}>
        <BusinessContent params={params} />
      </Suspense>
    </div>
  );
}
