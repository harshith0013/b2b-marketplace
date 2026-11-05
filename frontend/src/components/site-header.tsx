import { Suspense } from "react";
import Link from "next/link";
import { AuthLink } from "@/components/auth/auth-link";
import type { Locale } from "@/i18n/config";
import type { Dictionary } from "@/i18n/dictionaries";
import { LanguageSwitcher, StaticLanguageLinks } from "./language-switcher";

interface SiteHeaderProps {
  lang: Locale;
  dict: Pick<Dictionary, "meta" | "nav" | "language" | "auth">;
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
        <div className="flex flex-wrap items-center gap-2">
          {/* The switcher reads the current path, which is per request on some pages. */}
          <Suspense fallback={<StaticLanguageLinks current={lang} label={dict.language.label} />}>
            <LanguageSwitcher current={lang} label={dict.language.label} />
          </Suspense>
          {/* The rest of the header is static; only this part depends on who is signed in. */}
          <Suspense fallback={<span aria-hidden="true" className="inline-flex min-h-12 w-28" />}>
            <AuthLink lang={lang} labels={dict.auth} />
          </Suspense>
        </div>
      </div>
    </header>
  );
}
