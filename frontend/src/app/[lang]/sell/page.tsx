import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import {
  Building2,
  ChevronRight,
  ClipboardList,
  Plus,
  ShieldCheck,
  Wallet,
} from "lucide-react";
import { HomeTile } from "@/components/home-tile";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { hasLocale, type Locale } from "@/i18n/config";
import { getDictionary, type Dictionary } from "@/i18n/dictionaries";
import type { Me } from "@/lib/api/types";
import { api, requireSession } from "@/lib/auth/current-user";

/** Which business to sell for. Reads the session, so it renders inside <Suspense>. */
async function Businesses({ lang, dict }: { lang: Locale; dict: Dictionary }) {
  const session = await requireSession(lang, `/${lang}/sell`);
  const me = await api<Me>(session, lang, `/${lang}/sell`, "/api/v1/me");
  const { sell, account } = dict;

  if (me.businesses.length === 0) {
    return (
      <EmptyState
        icon={<Building2 />}
        title={sell.noBusinessTitle}
        description={sell.noBusinessDescription}
        action={
          <Button asChild variant="accent">
            <Link href={`/${lang}/onboarding`}>
              <Plus aria-hidden="true" className="size-5" />
              {sell.createBusiness}
            </Link>
          </Button>
        }
      />
    );
  }
  return (
    <ul className="flex flex-col gap-2">
      {me.businesses.map((business) => (
        <li key={business.businessId}>
          <Link
            href={`/${lang}/business/${business.businessId}/listings`}
            className="flex min-h-14 items-center justify-between gap-3 rounded-card border-2 border-border p-4 hover:bg-surface"
          >
            <span className="flex flex-col gap-1">
              <span className="font-semibold">{business.name}</span>
              <Badge className="self-start">
                {account.roles[business.role]}
              </Badge>
            </span>
            <span className="inline-flex items-center gap-1 font-semibold">
              {sell.manageListings}
              <ChevronRight aria-hidden="true" className="size-5" />
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

export default async function SellerHomePage({
  params,
}: PageProps<"/[lang]/sell">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const dict = await getDictionary(lang);
  const { sell, common } = dict;
  const tiles = [
    { key: "orders", icon: <ClipboardList /> },
    { key: "payouts", icon: <Wallet /> },
    { key: "verification", icon: <ShieldCheck /> },
  ] as const;

  return (
    <div className="flex flex-col gap-10">
      <section className="flex flex-col gap-4">
        <h1 className="text-display font-bold">{sell.title}</h1>
        <p className="max-w-prose text-lg text-muted">{sell.intro}</p>
      </section>

      <section
        aria-labelledby="businesses-heading"
        className="flex flex-col gap-4"
      >
        <h2 id="businesses-heading" className="text-heading font-bold">
          {sell.businessesHeading}
        </h2>
        <Suspense fallback={<Skeleton className="h-24" />}>
          <Businesses lang={lang} dict={dict} />
        </Suspense>
      </section>

      <section aria-labelledby="tools-heading" className="flex flex-col gap-4">
        <h2 id="tools-heading" className="text-heading font-bold">
          {sell.toolsHeading}
        </h2>
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {tiles.map((tile) => (
            <li key={tile.key}>
              <HomeTile
                icon={tile.icon}
                title={sell.tiles[tile.key].title}
                description={sell.tiles[tile.key].description}
                comingSoonLabel={common.comingSoon}
              />
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
