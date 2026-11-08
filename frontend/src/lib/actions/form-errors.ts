import "server-only";
import { ApiError } from "@/lib/api/backend";
import type { FormErrorKey, FormState } from "./form-state";

/**
 * Turns an API failure into a message key the form can show in the reader's language. The API's
 * own English explanations are not shown to users. Anything that is not an API error is rethrown,
 * which also lets Next.js redirects pass through.
 */
export function toFormState(
  error: unknown,
  conflict: FormErrorKey,
  invalid: FormErrorKey = "invalid",
): FormState {
  if (!(error instanceof ApiError)) throw error;
  if (error.status === 400) return { error: invalid };
  if (error.status === 403) return { error: "notAllowed" };
  if (error.status === 409) return { error: conflict };
  if (error.status === 422) return { error: "notReady" };
  console.error("API call failed", error.status, error.message);
  return { error: "generic" };
}

/** A form field as a trimmed string; empty when missing. */
export function field(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value.trim() : "";
}
