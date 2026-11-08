import { hasLocale, type Locale } from "@/i18n/config";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(value: string): boolean {
  return UUID.test(value);
}

/** Server Actions are public endpoints: never trust their arguments. */
export function checkIds(
  lang: string,
  ...ids: string[]
): asserts lang is Locale {
  if (!hasLocale(lang) || ids.some((id) => !isUuid(id)))
    throw new Error("Invalid request");
}
