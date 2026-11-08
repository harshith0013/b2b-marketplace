import { NextResponse, connection, type NextRequest } from "next/server";
import { hasLocale, defaultLocale } from "@/i18n/config";
import { authConfig } from "@/lib/auth/config";
import { beginLogin } from "@/lib/auth/oidc";
import { safeReturnTo } from "@/lib/auth/return-to";
import {
  LOGIN_COOKIE,
  LOGIN_TTL_SECONDS,
  cookieOptions,
  sealLogin,
} from "@/lib/auth/session";

/** Starts sign-in: remembers where to come back to, then sends the browser to the provider. */
export async function GET(request: NextRequest) {
  await connection(); // Per request, never prerendered: it depends on cookies and runtime config.
  const params = request.nextUrl.searchParams;
  const langParam = params.get("lang");
  const lang = hasLocale(langParam) ? langParam : defaultLocale;
  const returnTo = safeReturnTo(params.get("returnTo"), lang);
  const config = authConfig();

  try {
    const { url, transaction } = await beginLogin(returnTo);
    const response = NextResponse.redirect(url);
    response.cookies.set(
      LOGIN_COOKIE,
      await sealLogin(transaction, config.sessionSecret),
      cookieOptions(config.appBaseUrl.protocol === "https:", LOGIN_TTL_SECONDS),
    );
    return response;
  } catch (error) {
    console.error("Could not start sign-in", error);
    return NextResponse.redirect(
      new URL(`/${lang}/login?error=unavailable`, config.appBaseUrl),
    );
  }
}
