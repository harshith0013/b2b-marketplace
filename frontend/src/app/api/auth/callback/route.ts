import { NextResponse, connection, type NextRequest } from "next/server";
import { authConfig } from "@/lib/auth/config";
import { completeLogin } from "@/lib/auth/oidc";
import { localeOfPath } from "@/lib/auth/return-to";
import {
  LOGIN_COOKIE,
  SESSION_COOKIE,
  cookieOptions,
  sealSession,
  unsealLogin,
} from "@/lib/auth/session";

const MAX_SESSION_COOKIE_SECONDS = 60 * 60 * 24;

/** The provider sends the browser back here with a one-time code. */
export async function GET(request: NextRequest) {
  await connection(); // Per request, never prerendered: it depends on cookies and runtime config.
  const config = authConfig();
  const secure = config.appBaseUrl.protocol === "https:";
  const transaction = await unsealLogin(
    request.cookies.get(LOGIN_COOKIE)?.value,
    config.sessionSecret,
  );
  const lang = localeOfPath(transaction?.returnTo ?? "/");
  const fail = (reason: string) => {
    const response = NextResponse.redirect(
      new URL(`/${lang}/login?error=${reason}`, config.appBaseUrl),
    );
    response.cookies.delete(LOGIN_COOKIE);
    return response;
  };

  if (!transaction) return fail("expired");
  if (request.nextUrl.searchParams.has("error")) return fail("cancelled");

  try {
    const session = await completeLogin(request.nextUrl, transaction);
    const response = NextResponse.redirect(
      new URL(transaction.returnTo, config.appBaseUrl),
    );
    response.cookies.delete(LOGIN_COOKIE);
    response.cookies.set(
      SESSION_COOKIE,
      await sealSession(session, config.sessionSecret),
      // The cookie outlives the 1-hour token so an expired session can sign in again silently.
      cookieOptions(secure, MAX_SESSION_COOKIE_SECONDS),
    );
    return response;
  } catch (error) {
    console.error("Sign-in callback failed", error);
    return fail("failed");
  }
}
