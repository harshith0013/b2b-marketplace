import "server-only";
import { cookies } from "next/headers";
import { connection } from "next/server";
import { redirect } from "next/navigation";
import type { Locale } from "@/i18n/config";
import { ApiError, callBackend, callBackendMultipart } from "@/lib/api/backend";
import { authConfig } from "./config";
import { loginPath } from "./login-path";
import {
  SESSION_COOKIE,
  isExpired,
  unsealSession,
  type Session,
} from "./session";

/** The signed-in session, or null. Reads the request cookie, so call it inside <Suspense>. */
export async function getSession(): Promise<Session | null> {
  const sealed = (await cookies()).get(SESSION_COOKIE)?.value;
  if (!sealed) return null;
  await connection(); // The expiry check below reads the clock, which must happen per request.
  const session = await unsealSession(sealed, authConfig().sessionSecret);
  if (!session || isExpired(session, Math.floor(Date.now() / 1000)))
    return null;
  return session;
}

/** The session for a page that requires sign-in; otherwise sends the person to sign in. */
export async function requireSession(
  lang: Locale,
  returnTo: string,
): Promise<Session> {
  const session = await getSession();
  if (!session)
    redirect(`/${lang}/login?returnTo=${encodeURIComponent(returnTo)}`);
  return session;
}

/**
 * Calls the API as the signed-in person. If the API no longer accepts the token (it expired a
 * moment ago), signs in again and comes back to the same page.
 */
export async function api<T>(
  session: Session,
  lang: Locale,
  returnTo: string,
  path: string,
  init?: { method?: string; body?: unknown },
): Promise<T> {
  try {
    return await callBackend<T>(session.idToken, path, init);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401)
      redirect(loginPath(lang, returnTo));
    throw error;
  }
}

/** Like {@link api}, for a multipart upload (a photo). */
export async function apiUpload<T>(
  session: Session,
  lang: Locale,
  returnTo: string,
  path: string,
  body: FormData,
): Promise<T> {
  try {
    return await callBackendMultipart<T>(session.idToken, path, body);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401)
      redirect(loginPath(lang, returnTo));
    throw error;
  }
}
