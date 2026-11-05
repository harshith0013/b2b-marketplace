import { notFound } from "next/navigation";
import { BusinessForm } from "@/components/business/business-form";
import { Card } from "@/components/ui/card";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import { createBusiness } from "@/lib/actions/business";

/** Create a business. Sign-in is enforced by the proxy and again by the Server Action. */
export default async function OnboardingPage({ params }: PageProps<"/[lang]/onboarding">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const { business } = await getDictionary(lang);

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <div className="flex flex-col gap-2">
        <h1 className="text-display font-bold">{business.createTitle}</h1>
        <p className="text-muted">{business.createIntro}</p>
      </div>
      <Card>
        <BusinessForm
          labels={business}
          action={createBusiness.bind(null, lang)}
          submitLabel={business.create}
        />
      </Card>
    </div>
  );
}
