import Link from "next/link";
import { notFound } from "next/navigation";
import { ClipboardList, PackageOpen, Plus, ShieldCheck, Wallet } from "lucide-react";
import { HomeTile } from "@/components/home-tile";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";

export default async function SellerHomePage({ params }: PageProps<"/[lang]/sell">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const { sell, common } = await getDictionary(lang);

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
        <Button asChild variant="accent" size="lg" className="self-start">
          <Link href={`/${lang}/sell/new`}>
            <Plus aria-hidden="true" className="size-5" />
            {sell.newListing}
          </Link>
        </Button>
      </section>

      <section aria-labelledby="listings-heading" className="flex flex-col gap-4">
        <h2 id="listings-heading" className="text-heading font-bold">
          {sell.listingsHeading}
        </h2>
        <EmptyState
          icon={<PackageOpen />}
          title={sell.emptyTitle}
          description={sell.emptyDescription}
        />
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
