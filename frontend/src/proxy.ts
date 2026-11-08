import { NextResponse, type NextRequest } from "next/server";
import { LOCALE_COOKIE, locales, negotiateLocale, type Locale } from "@/i18n/config";
import { SESSION_COOKIE, isExpired, unsealSession } from "@/lib/auth/session";

const ONE_YEAR_SECONDS = 60 * 60 * 24 * 365;

/** Pages that need a signed-in person. The buyer home and other public pages are not listed. */
const PROTECTED = /^\/(?:en|te|hi)\/(?:sell|account|onboarding|business|admin)(?:\/|$)/;

export function isProtectedPath(pathname: string): boolean {
  return PROTECTED.test(pathname);
}

type SessionState = "valid" | "expired" | "none";

async function sessionState(request: NextRequest): Promise<SessionState> {
  const sealed = request.cookies.get(SESSION_COOKIE)?.value;
  if (!sealed) return "none";
  const secret = process.env.SESSION_SECRET;
  if (!secret) {
    console.error("SESSION_SECRET is not set; treating every visitor as signed out");
    return "none";
  }
  const session = await unsealSession(sealed, secret);
  if (!session) return "none";
  return isExpired(session, Math.floor(Date.now() / 1000)) ? "expired" : "valid";
}

/**
 * Sends signed-out visitors of protected pages to the sign-in page. If they were signed in and
 * the session simply ran out, goes straight back through the provider, which signs them in again
 * without asking while they are still signed in there.
 */
async function guard(request: NextRequest, lang: Locale): Promise<NextResponse | null> {
  const { pathname, search } = request.nextUrl;
  if (!isProtectedPath(pathname)) return null;
  const state = await sessionState(request);
  if (state === "valid") return null;

  const returnTo = encodeURIComponent(pathname + search);
  const url = request.nextUrl.clone();
  url.search = "";
  if (state === "expired") {
    url.pathname = "/api/auth/login";
    url.search = `?lang=${lang}&returnTo=${returnTo}`;
  } else {
    url.pathname = `/${lang}/login`;
    url.search = `?returnTo=${returnTo}`;
  }
  return NextResponse.redirect(url);
}

export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const savedLocale = request.cookies.get(LOCALE_COOKIE)?.value;
  const pathLocale = locales.find(
    (locale) => pathname === `/${locale}` || pathname.startsWith(`/${locale}/`),
  );

  if (pathLocale) {
    const response = (await guard(request, pathLocale)) ?? NextResponse.next();
    // Remember the language the visitor is reading so "/" sends them back to it.
    if (savedLocale !== pathLocale) {
      response.cookies.set(LOCALE_COOKIE, pathLocale, {
        path: "/",
        maxAge: ONE_YEAR_SECONDS,
        sameSite: "lax",
        httpOnly: true,
        secure: request.nextUrl.protocol === "https:",
      });
    }
    return response;
  }

  const locale = negotiateLocale(request.headers.get("accept-language"), savedLocale);
  const url = request.nextUrl.clone();
  url.pathname = `/${locale}${pathname === "/" ? "" : pathname}`;
  return NextResponse.redirect(url);
}

export const config = {
  // Skip API routes, Next internals and any request for a file (has a dot).
  matcher: ["/((?!api|_next|.*\\..*).*)"],
};
