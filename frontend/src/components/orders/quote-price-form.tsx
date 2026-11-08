"use client";

import { useActionState } from "react";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { Dictionary } from "@/i18n/dictionaries";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";

/** The seller's answer to a quote request: a price per unit in rupees, valid for 48 hours. */
export function QuotePriceForm({
  labels,
  errors,
  action,
}: {
  labels: Dictionary["orders"];
  errors: Dictionary["business"]["errors"];
  action: (previous: FormState, form: FormData) => Promise<FormState>;
}) {
  const [state, formAction, pending] = useActionState(
    action,
    INITIAL_FORM_STATE,
  );
  return (
    <form action={formAction} className="flex flex-col gap-3">
      <Input
        id="quote-price"
        name="price"
        label={labels.quotePrice}
        required
        inputMode="decimal"
        autoComplete="off"
      />
      <FormMessage state={state} errors={errors} />
      <Button
        type="submit"
        variant="accent"
        disabled={pending}
        className="self-start"
      >
        {labels.offerPrice}
      </Button>
    </form>
  );
}
