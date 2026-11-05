import { defaultLocale, hasLocale, type Locale } from "@/i18n/config";

/**
 * Where to send someone after sign-in. Only same-site paths are allowed, so a crafted link
 * cannot bounce a freshly signed-in person to another website.
 */
export function safeReturnTo(value: string | null | undefined, lang: Locale): string {
  const fallback = `/${lang}/account`;
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.includes("\\")) {
    return fallback;
  }
  try {
    // Parsing against a dummy origin catches anything that would escape to another host.
    const parsed = new URL(value, "https://same-site.invalid");
    if (parsed.origin !== "https://same-site.invalid") return fallback;
    return parsed.pathname + parsed.search;
  } catch {
    return fallback;
  }
}

/** The language of a path like /te/sell, or the default. */
export function localeOfPath(path: string): Locale {
  const first = path.split("/")[1];
  return hasLocale(first) ? first : defaultLocale;
}
