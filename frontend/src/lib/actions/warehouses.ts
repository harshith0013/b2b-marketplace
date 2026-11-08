"use server";

import { refresh } from "next/cache";
import { hasLocale, type Locale } from "@/i18n/config";
import type { Warehouse } from "@/lib/api/types";
import { api, requireSession } from "@/lib/auth/current-user";
import { field, toFormState } from "./form-errors";
import type { FormState } from "./form-state";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function checkIds(lang: string, ...ids: string[]): asserts lang is Locale {
  // Server Actions are public endpoints: never trust their arguments.
  if (!hasLocale(lang) || ids.some((id) => !UUID.test(id)))
    throw new Error("Invalid request");
}

const pagePath = (lang: string, businessId: string) =>
  `/${lang}/business/${businessId}/warehouses`;

export async function createWarehouse(
  lang: string,
  businessId: string,
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  checkIds(lang, businessId);
  const returnTo = pagePath(lang, businessId);
  const session = await requireSession(lang, returnTo);
  try {
    await api<Warehouse>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${businessId}/warehouses`,
      {
        method: "POST",
        body: {
          name: field(form, "name"),
          addressLine: field(form, "addressLine"),
          city: field(form, "city"),
          state: field(form, "state"),
          pincode: field(form, "pincode"),
        },
      },
    );
  } catch (error) {
    return toFormState(error, "warehouseNameTaken");
  }
  refresh();
  return { ok: true };
}

export async function setWarehouseActive(
  lang: string,
  businessId: string,
  warehouseId: string,
  active: boolean,
): Promise<void> {
  checkIds(lang, businessId, warehouseId);
  if (typeof active !== "boolean") throw new Error("Invalid request");
  const returnTo = pagePath(lang, businessId);
  const session = await requireSession(lang, returnTo);
  await api<Warehouse>(
    session,
    lang,
    returnTo,
    `/api/v1/businesses/${businessId}/warehouses/${warehouseId}`,
    { method: "PATCH", body: { active } },
  );
  refresh();
}
