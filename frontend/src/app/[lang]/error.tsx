"use client";

import { useParams } from "next/navigation";
import { ErrorState } from "@/components/ui/error-state";
import { defaultLocale, hasLocale } from "@/i18n/config";
import en from "@/i18n/dictionaries/en.json";
import hi from "@/i18n/dictionaries/hi.json";
import te from "@/i18n/dictionaries/te.json";

// Error boundaries are client components and cannot load dictionaries
// asynchronously, so the three small error texts are bundled directly.
const errorLabels = { en: en.errors, te: te.errors, hi: hi.errors };

export default function LocaleError({ retry }: { retry: () => void }) {
  const params = useParams<{ lang: string }>();
  const labels =
    errorLabels[hasLocale(params.lang) ? params.lang : defaultLocale];

  return (
    <ErrorState
      title={labels.title}
      description={labels.description}
      onRetry={retry}
      retryLabel={labels.retry}
    />
  );
}
