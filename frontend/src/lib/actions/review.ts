"use server";

import { redirect } from "next/navigation";
import type { Listing } from "@/lib/api/types";
import { api, requireSession } from "@/lib/auth/current-user";
import { field, toFormState } from "./form-errors";
import type { FormState } from "./form-state";
import { checkIds } from "./ids";

const queuePath = (lang: string) => `/${lang}/admin/reviews`;

export async function approveListing(
  lang: string,
  listingId: string,
): Promise<FormState> {
  checkIds(lang, listingId);
  const returnTo = `${queuePath(lang)}/${listingId}`;
  const session = await requireSession(lang, returnTo);
  try {
    await api<Listing>(
      session,
      lang,
      returnTo,
      `/api/v1/admin/reviews/${listingId}/approve`,
      {
        method: "POST",
      },
    );
  } catch (error) {
    return toFormState(error, "alreadyDecided");
  }
  redirect(queuePath(lang));
}

export async function rejectListing(
  lang: string,
  listingId: string,
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  checkIds(lang, listingId);
  const reason = field(form, "reason");
  if (!reason || reason.length > 500) return { error: "invalid" };
  const returnTo = `${queuePath(lang)}/${listingId}`;
  const session = await requireSession(lang, returnTo);
  try {
    await api<Listing>(
      session,
      lang,
      returnTo,
      `/api/v1/admin/reviews/${listingId}/reject`,
      {
        method: "POST",
        body: { reason },
      },
    );
  } catch (error) {
    return toFormState(error, "alreadyDecided");
  }
  redirect(queuePath(lang));
}
