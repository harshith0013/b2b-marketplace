import Link from "next/link";
import type { Locale } from "@/i18n/config";
import type { Dictionary } from "@/i18n/dictionaries";
import { getSession } from "@/lib/auth/current-user";

export const authLinkClassName =
  "inline-flex min-h-12 items-center rounded-control border-2 border-border px-4 font-semibold hover:bg-surface";

/** "Sign in" or "My account". Reads the session cookie, so render it inside <Suspense>. */
export async function AuthLink({ lang, labels }: { lang: Locale; labels: Dictionary["auth"] }) {
  const session = await getSession().catch(() => null);
  return session ? (
    <Link href={`/${lang}/account`} className={authLinkClassName}>
      {labels.account}
    </Link>
  ) : (
    <Link href={`/${lang}/login`} className={authLinkClassName}>
      {labels.signIn}
    </Link>
  );
}
