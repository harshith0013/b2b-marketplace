import { notFound } from "next/navigation";
import { BackendStatus } from "@/components/backend-status";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";

export default async function StatusPage({ params }: PageProps<"/[lang]/status">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const { status } = await getDictionary(lang);

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <h1 className="text-display font-bold">{status.heading}</h1>
      <BackendStatus labels={status} />
    </div>
  );
}
