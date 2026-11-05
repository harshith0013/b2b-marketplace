import { Suspense } from "react";
import { notFound } from "next/navigation";
import { LogIn } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { hasLocale, type Locale } from "@/i18n/config";
import { getDictionary, type Dictionary } from "@/i18n/dictionaries";
import { loginPath } from "@/lib/auth/login-path";
import { safeReturnTo } from "@/lib/auth/return-to";

type LoginErrors = Dictionary["auth"]["errors"];

function isLoginError(value: unknown, errors: LoginErrors): value is keyof LoginErrors {
  return typeof value === "string" && Object.hasOwn(errors, value);
}

/** The button and any error message depend on the query string, so they render per request. */
async function LoginOptions({
  lang,
  labels,
  searchParams,
}: {
  lang: Locale;
  labels: Dictionary["auth"];
  searchParams: PageProps<"/[lang]/login">["searchParams"];
}) {
  const { error, returnTo } = await searchParams;
  const target = safeReturnTo(typeof returnTo === "string" ? returnTo : null, lang);
  return (
    <>
      {isLoginError(error, labels.errors) && (
        <p role="alert" className="rounded-control bg-danger-soft px-4 py-3 font-semibold text-danger">
          {labels.errors[error]}
        </p>
      )}
      <Button asChild variant="accent" size="lg" className="w-full">
        {/* A plain link: sign-in leaves the site for the provider and comes back. */}
        <a href={loginPath(lang, target)}>
          <LogIn aria-hidden="true" className="size-5" />
          {labels.continueWithGoogle}
        </a>
      </Button>
    </>
  );
}

export default async function LoginPage({ params, searchParams }: PageProps<"/[lang]/login">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const { auth } = await getDictionary(lang);

  return (
    <div className="mx-auto flex max-w-md flex-col gap-6">
      <h1 className="text-display font-bold">{auth.loginTitle}</h1>
      <Card className="flex flex-col gap-5">
        <p>{auth.loginIntro}</p>
        <Suspense fallback={<div className="min-h-14" />}>
          <LoginOptions lang={lang} labels={auth} searchParams={searchParams} />
        </Suspense>
        <p className="text-sm text-muted">{auth.privacyNote}</p>
      </Card>
    </div>
  );
}
