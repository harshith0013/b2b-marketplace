import { notFound } from "next/navigation";
import { ListingWizard } from "@/components/listing-wizard";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";

export default async function NewListingPage({ params }: PageProps<"/[lang]/sell/new">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const { wizard, common } = await getDictionary(lang);

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <div className="flex flex-col gap-2">
        <h1 className="text-display font-bold">{wizard.title}</h1>
        <p className="text-muted">{wizard.intro}</p>
      </div>
      <ListingWizard labels={wizard} common={common} />
    </div>
  );
}
