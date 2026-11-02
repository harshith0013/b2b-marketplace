import { NextResponse, type NextRequest } from "next/server";
import { LOCALE_COOKIE, locales, negotiateLocale } from "@/i18n/config";

const ONE_YEAR_SECONDS = 60 * 60 * 24 * 365;

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const savedLocale = request.cookies.get(LOCALE_COOKIE)?.value;
  const pathLocale = locales.find(
    (locale) => pathname === `/${locale}` || pathname.startsWith(`/${locale}/`),
  );

  if (pathLocale) {
    // Remember the language the visitor is reading so "/" sends them back to it.
    const response = NextResponse.next();
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
