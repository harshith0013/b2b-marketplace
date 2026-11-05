"use server";

import { refresh } from "next/cache";
import { redirect } from "next/navigation";
import { hasLocale } from "@/i18n/config";
import { api, requireSession } from "@/lib/auth/current-user";
import { field, toFormState } from "./form-errors";
import type { FormState } from "./form-state";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function updateProfile(
  lang: string,
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  if (!hasLocale(lang)) throw new Error("Invalid request");
  const returnTo = `/${lang}/account`;
  const session = await requireSession(lang, returnTo);
  const preferredLocale = field(form, "preferredLocale");
  try {
    await api(session, lang, returnTo, "/api/v1/me", {
      method: "PATCH",
      body: { displayName: field(form, "displayName"), preferredLocale },
    });
  } catch (error) {
    return toFormState(error, "generic");
  }
  // Show the account page in the language the person just chose.
  if (hasLocale(preferredLocale) && preferredLocale !== lang) redirect(`/${preferredLocale}/account`);
  refresh();
  return { ok: true };
}

export async function acceptInvitation(lang: string, invitationId: string): Promise<void> {
  if (!hasLocale(lang) || !UUID.test(invitationId)) throw new Error("Invalid request");
  const returnTo = `/${lang}/account`;
  const session = await requireSession(lang, returnTo);
  await api(session, lang, returnTo, `/api/v1/me/invitations/${invitationId}/accept`, {
    method: "POST",
  });
  refresh();
}
