import { NextResponse, type NextRequest } from "next/server";
import { defaultLocale, hasLocale } from "@/i18n/config";
import { authConfig } from "@/lib/auth/config";
import { SESSION_COOKIE } from "@/lib/auth/session";

/** POST only, so a link or image on another site cannot sign people out. */
export async function POST(request: NextRequest) {
  const origin = request.headers.get("origin");
  if (origin && origin !== authConfig().appBaseUrl.origin) {
    return new NextResponse(null, { status: 403 });
  }
  const form = await request.formData().catch(() => null);
  const langValue = form?.get("lang");
  const lang = typeof langValue === "string" && hasLocale(langValue) ? langValue : defaultLocale;
  const response = NextResponse.redirect(new URL(`/${lang}`, authConfig().appBaseUrl), 303);
  response.cookies.delete(SESSION_COOKIE);
  return response;
}
