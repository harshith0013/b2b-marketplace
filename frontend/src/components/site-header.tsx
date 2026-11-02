import Link from "next/link";
import type { Locale } from "@/i18n/config";
import type { Dictionary } from "@/i18n/dictionaries";
import { LanguageSwitcher } from "./language-switcher";

interface SiteHeaderProps {
  lang: Locale;
  dict: Pick<Dictionary, "meta" | "nav" | "language">;
}

const navLinkClassName =
  "inline-flex min-h-12 items-center rounded-control px-3 font-semibold hover:bg-surface";

export function SiteHeader({ lang, dict }: SiteHeaderProps) {
  return (
    <header className="border-b-2 border-border">
      <div className="mx-auto flex max-w-5xl flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center justify-between gap-4">
          <Link href={`/${lang}`} className="inline-flex min-h-12 items-center text-xl font-bold">
            {dict.meta.title}
          </Link>
          <nav aria-label={dict.nav.mainMenu}>
            <ul className="flex gap-1">
              <li>
                <Link href={`/${lang}`} className={navLinkClassName}>
                  {dict.nav.buy}
                </Link>
              </li>
              <li>
                <Link href={`/${lang}/sell`} className={navLinkClassName}>
                  {dict.nav.sell}
                </Link>
              </li>
            </ul>
          </nav>
        </div>
        <LanguageSwitcher current={lang} label={dict.language.label} />
      </div>
    </header>
  );
}
