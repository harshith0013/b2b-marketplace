import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Building2, LogOut, Plus } from "lucide-react";
import { ProfileForm } from "@/components/account/profile-form";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale, type Locale } from "@/i18n/config";
import { getDictionary, type Dictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import { acceptInvitation, updateProfile } from "@/lib/actions/account";
import type { Me, ReceivedInvitation } from "@/lib/api/types";
import { api, requireSession } from "@/lib/auth/current-user";

async function AccountDetails({ lang, dict }: { lang: Locale; dict: Dictionary }) {
  const returnTo = `/${lang}/account`;
  const session = await requireSession(lang, returnTo);
  const [me, invitations] = await Promise.all([
    api<Me>(session, lang, returnTo, "/api/v1/me"),
    api<ReceivedInvitation[]>(session, lang, returnTo, "/api/v1/me/invitations"),
  ]);
  const { account } = dict;

  return (
    <>
      {invitations.length > 0 && (
        <section aria-labelledby="invitations-heading" className="flex flex-col gap-3">
          <h2 id="invitations-heading" className="text-heading font-bold">
            {account.invitationsHeading}
          </h2>
          <ul className="flex flex-col gap-3">
            {invitations.map((invitation) => (
              <li key={invitation.id}>
                <Card className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <p>
                    {format(account.invitationText, {
                      business: invitation.businessName,
                      role: account.roles[invitation.role],
                    })}
                  </p>
                  <form action={acceptInvitation.bind(null, lang, invitation.id)}>
                    <Button type="submit" variant="accent">
                      {account.accept}
                    </Button>
                  </form>
                </Card>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section aria-labelledby="businesses-heading" className="flex flex-col gap-3">
        <h2 id="businesses-heading" className="text-heading font-bold">
          {account.businessesHeading}
        </h2>
        {me.businesses.length === 0 ? (
          <EmptyState
            icon={<Building2 />}
            title={account.noBusinesses}
            action={
              <Button asChild variant="accent">
                <Link href={`/${lang}/onboarding`}>
                  <Plus aria-hidden="true" className="size-5" />
                  {account.createBusiness}
                </Link>
              </Button>
            }
          />
        ) : (
          <ul className="flex flex-col gap-3">
            {me.businesses.map((business) => (
              <li key={business.businessId}>
                <Link
                  href={`/${lang}/business/${business.businessId}`}
                  className="flex min-h-14 items-center justify-between gap-3 rounded-card border-2 border-border p-4 font-semibold hover:bg-surface"
                >
                  <span>{business.name}</span>
                  <Badge>{account.roles[business.role]}</Badge>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="profile-heading" className="flex flex-col gap-3">
        <h2 id="profile-heading" className="text-heading font-bold">
          {account.profileHeading}
        </h2>
        <Card>
          <ProfileForm
            labels={account}
            errors={dict.business.errors}
            profile={me.profile}
            action={updateProfile.bind(null, lang)}
          />
        </Card>
      </section>
    </>
  );
}

export default async function AccountPage({ params }: PageProps<"/[lang]/account">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const dict = await getDictionary(lang);

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-display font-bold">{dict.account.title}</h1>
        <form action="/api/auth/logout" method="post">
          <input type="hidden" name="lang" value={lang} />
          <Button type="submit" variant="outline">
            <LogOut aria-hidden="true" className="size-5" />
            {dict.auth.signOut}
          </Button>
        </form>
      </div>
      <Suspense fallback={<Skeleton className="h-64" />}>
        <AccountDetails lang={lang} dict={dict} />
      </Suspense>
    </div>
  );
}
