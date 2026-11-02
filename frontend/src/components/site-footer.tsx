import Link from "next/link";
import type { Locale } from "@/i18n/config";
import type { Dictionary } from "@/i18n/dictionaries";

export function SiteFooter({ lang, labels }: { lang: Locale; labels: Dictionary["footer"] }) {
  return (
    <footer className="mt-auto border-t-2 border-border bg-surface">
      <div className="mx-auto flex max-w-5xl flex-col gap-2 px-4 py-6 text-muted sm:flex-row sm:items-center sm:justify-between">
        <p>{labels.sampleNotice}</p>
        <Link
          href={`/${lang}/status`}
          className="inline-flex min-h-12 items-center font-semibold text-foreground underline underline-offset-4"
        >
          {labels.status}
        </Link>
      </div>
    </footer>
  );
}
