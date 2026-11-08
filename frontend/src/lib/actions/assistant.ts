"use server";

import { headers } from "next/headers";
import { redirect } from "next/navigation";
import { hasLocale } from "@/i18n/config";
import { postPublic } from "@/lib/api/backend";
import { paiseToRupeesInput } from "@/lib/money";
import { field } from "./form-errors";

const MAX_QUESTION = 300;
/** The API waits up to 5 seconds for the AI provider; allow for that plus the search. */
const ASSIST_TIMEOUT_MS = 8000;

/** The API's checked filters (see AssistedSearch.Understood). */
interface Understood {
  query: string | null;
  categoryId: string | null;
  state: string | null;
  city: string | null;
  maxPricePerPackPaise: number | null;
  maxOrderValuePaise: number | null;
  minDaysLeft: number | null;
  quantity: number | null;
  unit: "PIECE" | "CASE" | null;
  sort: "PRICE" | "EXPIRY" | null;
  placeNotFound: string | null;
}

interface Result {
  outcome: string;
  understood: Understood | null;
}

/** Outcomes that mean "too many questions right now" rather than "no assistant". */
const BUSY = new Set([
  "VISITOR_LIMIT",
  "DAILY_LIMIT",
  "PROVIDER_LIMIT",
  "TIMEOUT",
]);

/**
 * The shopper's question → the offers page with the assistant's checked filters, or with plain
 * text search when the assistant cannot help. Either way the offers come from the normal search.
 */
export async function askAssistant(
  lang: string,
  form: FormData,
): Promise<void> {
  if (!hasLocale(lang)) throw new Error("Invalid request");
  const question = field(form, "question").slice(0, MAX_QUESTION);
  if (!question) redirect(`/${lang}/offers`);

  // The visitor's address, for the per-visitor limit only (set by the load balancer in production).
  const forwarded = (await headers())
    .get("x-forwarded-for")
    ?.split(",")[0]
    ?.trim();
  let result: Result;
  try {
    result = await postPublic<Result>(
      "/api/v1/offers/interpret",
      { question },
      forwarded ? { "X-Forwarded-For": forwarded } : {},
      ASSIST_TIMEOUT_MS,
    );
  } catch (error) {
    console.error("Assistant request failed", error);
    result = { outcome: "PROVIDER_ERROR", understood: null };
  }

  const query = new URLSearchParams({ asked: question });
  const understood = result.outcome === "UNDERSTOOD" ? result.understood : null;
  if (!understood) {
    query.set("q", question.slice(0, 100));
    query.set("assistant", BUSY.has(result.outcome) ? "busy" : "off");
    redirect(`/${lang}/offers?${query}`);
  }
  const set = (key: string, value: string | number | null) => {
    if (value !== null && value !== "") query.set(key, String(value));
  };
  set("q", understood.query);
  set("categoryId", understood.categoryId);
  set("state", understood.state);
  set("city", understood.city);
  set(
    "maxPrice",
    understood.maxPricePerPackPaise &&
      paiseToRupeesInput(understood.maxPricePerPackPaise),
  );
  set(
    "maxOrder",
    understood.maxOrderValuePaise &&
      paiseToRupeesInput(understood.maxOrderValuePaise),
  );
  set("minDays", understood.minDaysLeft);
  if (understood.quantity && understood.unit) {
    set("quantity", understood.quantity);
    set("unit", understood.unit);
  }
  set("sort", understood.sort);
  set("place", understood.placeNotFound);
  redirect(`/${lang}/offers?${query}`);
}
