"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { localeLabels, locales, type Locale } from "@/i18n/config";
import { cn } from "@/lib/utils";

interface LanguageSwitcherProps {
  current: Locale;
  label: string;
}

/** The same page in another language: swaps the leading locale segment of the path. */
export function pathForLocale(pathname: string | null, current: Locale, target: Locale): string {
  const prefix = `/${current}`;
  if (pathname === prefix || !pathname?.startsWith(`${prefix}/`)) return `/${target}`;
  return `/${target}${pathname.slice(prefix.length)}`;
}

function LanguageLinks({ current, label, hrefFor }: LanguageSwitcherProps & { hrefFor: (locale: Locale) => string }) {
  return (
    <nav aria-label={label}>
      <ul className="flex flex-wrap gap-2">
        {locales.map((locale) => {
          const active = locale === current;
          return (
            <li key={locale}>
              <Link
                href={hrefFor(locale)}
                lang={locale}
                hrefLang={locale}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "inline-flex min-h-12 items-center rounded-control border-2 px-4 font-semibold",
                  active
                    ? "border-accent bg-accent text-accent-foreground"
                    : "border-border hover:bg-surface",
                )}
              >
                {localeLabels[locale]}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

/** Switches language and stays on the same page. */
export function LanguageSwitcher({ current, label }: LanguageSwitcherProps) {
  const pathname = usePathname();
  return (
    <LanguageLinks
      current={current}
      label={label}
      hrefFor={(locale) => pathForLocale(pathname, current, locale)}
    />
  );
}

/** Shown until the current path is known: links to each language's home page. */
export function StaticLanguageLinks({ current, label }: LanguageSwitcherProps) {
  return <LanguageLinks current={current} label={label} hrefFor={(locale) => `/${locale}`} />;
}
