"use server";

import { refresh } from "next/cache";
import { redirect } from "next/navigation";
import { hasLocale, type Locale } from "@/i18n/config";
import type { BusinessDetails, SentInvitation } from "@/lib/api/types";
import { api, requireSession } from "@/lib/auth/current-user";
import { field, toFormState } from "./form-errors";
import type { FormState } from "./form-state";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function checkIds(lang: string, ...ids: string[]): asserts lang is Locale {
  // Server Actions are public endpoints: never trust their arguments.
  if (!hasLocale(lang) || ids.some((id) => !UUID.test(id)))
    throw new Error("Invalid request");
}

function businessFields(form: FormData) {
  return {
    legalName: field(form, "legalName"),
    tradeName: field(form, "tradeName"),
    businessType: field(form, "businessType"),
    gstin: field(form, "gstin"),
    city: field(form, "city"),
    state: field(form, "state"),
    pincode: field(form, "pincode"),
  };
}

export async function createBusiness(
  lang: string,
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  checkIds(lang);
  const returnTo = `/${lang}/onboarding`;
  const session = await requireSession(lang, returnTo);
  let created: BusinessDetails;
  try {
    created = await api<BusinessDetails>(
      session,
      lang,
      returnTo,
      "/api/v1/businesses",
      {
        method: "POST",
        body: businessFields(form),
      },
    );
  } catch (error) {
    return toFormState(error, "gstinTaken");
  }
  redirect(`/${lang}/business/${created.id}`);
}

export async function updateBusiness(
  lang: string,
  businessId: string,
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  checkIds(lang, businessId);
  const returnTo = `/${lang}/business/${businessId}`;
  const session = await requireSession(lang, returnTo);
  try {
    await api(session, lang, returnTo, `/api/v1/businesses/${businessId}`, {
      method: "PATCH",
      body: businessFields(form),
    });
  } catch (error) {
    return toFormState(error, "gstinTaken");
  }
  refresh();
  return { ok: true };
}

export async function inviteMember(
  lang: string,
  businessId: string,
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  checkIds(lang, businessId);
  const returnTo = `/${lang}/business/${businessId}`;
  const session = await requireSession(lang, returnTo);
  try {
    await api<SentInvitation>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${businessId}/invitations`,
      {
        method: "POST",
        body: { email: field(form, "email"), role: field(form, "role") },
      },
    );
  } catch (error) {
    return toFormState(error, "alreadyInvited");
  }
  refresh();
  return { ok: true };
}

export async function revokeInvitation(
  lang: string,
  businessId: string,
  invitationId: string,
): Promise<void> {
  checkIds(lang, businessId, invitationId);
  const returnTo = `/${lang}/business/${businessId}`;
  const session = await requireSession(lang, returnTo);
  await api(
    session,
    lang,
    returnTo,
    `/api/v1/businesses/${businessId}/invitations/${invitationId}`,
    { method: "DELETE" },
  );
  refresh();
}
