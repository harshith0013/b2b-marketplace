"use client";

import { useActionState } from "react";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import type { Dictionary } from "@/i18n/dictionaries";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";

/**
 * Approve, with a required confirmation when the rules raised flags: the reviewer, not the AI,
 * compares the label photo with the typed details.
 */
export function ApproveForm({
  labels,
  errors,
  needsConfirmation,
  disabled,
  action,
}: {
  labels: Dictionary["review"];
  errors: Dictionary["business"]["errors"];
  needsConfirmation: boolean;
  disabled: boolean;
  action: (previous: FormState, form: FormData) => Promise<FormState>;
}) {
  const [state, formAction, pending] = useActionState(
    action,
    INITIAL_FORM_STATE,
  );
  return (
    <form action={formAction} className="flex flex-col gap-3">
      {needsConfirmation && (
        <label className="flex items-start gap-3 font-semibold">
          <input
            type="checkbox"
            name="confirmedLabelCheck"
            value="yes"
            required
            className="mt-1 size-5"
          />
          {labels.confirmLabel}
        </label>
      )}
      <FormMessage state={state} errors={errors} />
      <Button
        type="submit"
        variant="accent"
        disabled={disabled || pending}
        className="self-start"
      >
        {labels.approve}
      </Button>
    </form>
  );
}
