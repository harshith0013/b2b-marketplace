import "server-only";
import type { Locale } from "@/i18n/config";
import { ApiError } from "@/lib/api/backend";
import { api } from "@/lib/auth/current-user";
import type { Session } from "@/lib/auth/session";

/** For review pages: the API's 403 (not a reviewer) becomes "not allowed" instead of an error page. */
export async function reviewerApi<T>(
  session: Session,
  lang: Locale,
  returnTo: string,
  path: string,
): Promise<T | "forbidden" | "not-found"> {
  try {
    return await api<T>(session, lang, returnTo, path);
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) return "forbidden";
    if (error instanceof ApiError && error.status === 404) return "not-found";
    throw error;
  }
}
