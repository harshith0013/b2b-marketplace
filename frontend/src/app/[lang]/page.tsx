import { notFound } from "next/navigation";
import {
  BadgePercent,
  Boxes,
  ClipboardList,
  LayoutGrid,
  MapPin,
  ShoppingCart,
  Store,
} from "lucide-react";
import { AssistantDialog } from "@/components/assistant-dialog";
import { HomeTile } from "@/components/home-tile";
import { SampleOfferCard } from "@/components/sample-offer-card";
import { SearchBox } from "@/components/search-box";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";

export default async function BuyerHomePage({ params }: PageProps<"/[lang]">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const dict = await getDictionary(lang);
  const { home, common } = dict;

  // Only Sell has a page so far; the other tiles are marked "coming soon".
  const tiles = [
    { key: "buy", icon: <ShoppingCart /> },
    { key: "sell", icon: <Store />, href: `/${lang}/sell` },
    { key: "wholesale", icon: <Boxes /> },
    { key: "clearance", icon: <BadgePercent /> },
    { key: "categories", icon: <LayoutGrid /> },
    { key: "nearby", icon: <MapPin /> },
    { key: "orders", icon: <ClipboardList /> },
  ] as const;

  return (
    <div className="flex flex-col gap-10">
      <section className="flex flex-col gap-5">
        <h1 className="text-display font-bold">{home.tagline}</h1>
        <SearchBox labels={home} />
        <AssistantDialog
          labels={home.assistant}
          comingSoonLabel={common.comingSoon}
          closeLabel={common.close}
        />
      </section>

      <section aria-labelledby="tiles-heading" className="flex flex-col gap-4">
        <h2 id="tiles-heading" className="text-heading font-bold">
          {home.tilesHeading}
        </h2>
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {tiles.map((tile) => (
            <li key={tile.key}>
              <HomeTile
                icon={tile.icon}
                title={home.tiles[tile.key].title}
                description={home.tiles[tile.key].description}
                href={"href" in tile ? tile.href : undefined}
                comingSoonLabel={common.comingSoon}
              />
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="offers-heading" className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <h2 id="offers-heading" className="text-heading font-bold">
            {home.offersHeading}
          </h2>
          <p className="text-muted">{home.offersIntro}</p>
        </div>
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {Object.entries(home.offers).map(([key, offer]) => (
            <li key={key}>
              <SampleOfferCard {...offer} sampleLabel={common.sampleData} />
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
