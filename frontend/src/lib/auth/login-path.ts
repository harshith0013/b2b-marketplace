import type { Locale } from "@/i18n/config";

/** The route that starts sign-in and comes back to `returnTo` afterwards. */
export function loginPath(lang: Locale, returnTo: string): string {
  return `/api/auth/login?lang=${lang}&returnTo=${encodeURIComponent(returnTo)}`;
}
