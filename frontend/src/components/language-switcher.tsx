import Link from "next/link";
import { localeLabels, locales, type Locale } from "@/i18n/config";
import { cn } from "@/lib/utils";

interface LanguageSwitcherProps {
  current: Locale;
  label: string;
}

export function LanguageSwitcher({ current, label }: LanguageSwitcherProps) {
  return (
    <nav aria-label={label}>
      <ul className="flex flex-wrap gap-2">
        {locales.map((locale) => {
          const active = locale === current;
          return (
            <li key={locale}>
              <Link
                href={`/${locale}`}
                lang={locale}
                hrefLang={locale}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "inline-flex min-h-12 items-center rounded-lg border-2 px-4 font-semibold",
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
