import { notFound } from "next/navigation";
import { BackendStatus } from "@/components/backend-status";
import { LanguageSwitcher } from "@/components/language-switcher";
import { hasLocale } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";

export default async function HomePage({ params }: PageProps<"/[lang]">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const dict = await getDictionary(lang);

  return (
    <div className="mx-auto flex min-h-dvh max-w-2xl flex-col gap-10 px-5 py-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-xl font-bold">{dict.meta.title}</p>
        <LanguageSwitcher current={lang} label={dict.language.label} />
      </header>

      <main className="flex flex-col gap-8">
        <div className="flex flex-col gap-4">
          <h1 className="text-3xl leading-tight font-bold sm:text-4xl">{dict.home.tagline}</h1>
          <p className="max-w-prose text-lg text-muted">{dict.home.comingSoon}</p>
        </div>
        <BackendStatus labels={dict.status} />
      </main>
    </div>
  );
}
