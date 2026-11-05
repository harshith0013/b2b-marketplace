import type { Dictionary } from "@/i18n/dictionaries";

export type FormErrorKey = keyof Dictionary["business"]["errors"];

/** What a form's Server Action reports back to the form. Shared by server and client code. */
export interface FormState {
  ok?: boolean;
  error?: FormErrorKey;
}

export const INITIAL_FORM_STATE: FormState = {};
