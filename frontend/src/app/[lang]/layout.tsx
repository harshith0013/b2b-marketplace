import type { Metadata } from "next";
import { Noto_Sans, Noto_Sans_Devanagari, Noto_Sans_Telugu } from "next/font/google";
import { notFound } from "next/navigation";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { ToastProvider } from "@/components/ui/toast";
import { hasLocale, locales } from "@/i18n/config";
import { getDictionary } from "@/i18n/dictionaries";
import "../globals.css";

// Downloaded at build time and served from this site: no requests to Google at runtime.
const notoSans = Noto_Sans({ subsets: ["latin"], variable: "--font-noto-sans" });
// Not preloaded: the browser fetches these only when a page contains Telugu or Devanagari text.
const notoSansTelugu = Noto_Sans_Telugu({
  subsets: ["telugu"],
  variable: "--font-noto-sans-telugu",
  preload: false,
});
const notoSansDevanagari = Noto_Sans_Devanagari({
  subsets: ["devanagari"],
  variable: "--font-noto-sans-devanagari",
  preload: false,
});

const fontVariables = [notoSans, notoSansTelugu, notoSansDevanagari]
  .map((font) => font.variable)
  .join(" ");

export function generateStaticParams() {
  return locales.map((lang) => ({ lang }));
}

export async function generateMetadata({ params }: LayoutProps<"/[lang]">): Promise<Metadata> {
  const { lang } = await params;
  if (!hasLocale(lang)) return {};
  const dict = await getDictionary(lang);
  return { title: dict.meta.title, description: dict.meta.description };
}

export default async function RootLayout({ children, params }: LayoutProps<"/[lang]">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const dict = await getDictionary(lang);

  return (
    <html lang={lang} className={fontVariables}>
      <body className="flex min-h-dvh flex-col antialiased">
        <a
          href="#main-content"
          className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:rounded-control focus:bg-accent focus:px-4 focus:py-3 focus:font-semibold focus:text-accent-foreground"
        >
          {dict.nav.skipToContent}
        </a>
        <ToastProvider dismissLabel={dict.common.dismiss}>
          <SiteHeader lang={lang} dict={dict} />
          <main id="main-content" className="mx-auto w-full max-w-5xl flex-1 px-4 py-6 sm:py-8">
            {children}
          </main>
          <SiteFooter lang={lang} labels={dict.footer} />
        </ToastProvider>
      </body>
    </html>
  );
}
