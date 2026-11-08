import "server-only";
import { notFound } from "next/navigation";
import type { Locale } from "@/i18n/config";
import { ApiError } from "@/lib/api/backend";
import { api } from "@/lib/auth/current-user";
import type { Session } from "@/lib/auth/session";
import { isUuid } from "@/lib/actions/ids";

/** Ids in the URL must look like ids; anything else is simply not found. */
export function requireUuids(...ids: string[]): void {
  if (!ids.every(isUuid)) notFound();
}

/** Calls the API for a page; a 404 from the API (not a member, no such thing) becomes the page's 404. */
export async function apiOrNotFound<T>(
  session: Session,
  lang: Locale,
  returnTo: string,
  path: string,
): Promise<T> {
  try {
    return await api<T>(session, lang, returnTo, path);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}

export const canEdit = (role: string) => role === "OWNER" || role === "MANAGER";
