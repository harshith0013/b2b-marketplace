"use client";

import { useActionState } from "react";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import { Field, controlClassName } from "@/components/ui/field";
import type { Dictionary } from "@/i18n/dictionaries";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";

export function RejectForm({
  labels,
  errors,
  action,
}: {
  labels: Dictionary["review"];
  errors: Dictionary["business"]["errors"];
  action: (previous: FormState, form: FormData) => Promise<FormState>;
}) {
  const [state, formAction, pending] = useActionState(action, INITIAL_FORM_STATE);
  return (
    <form action={formAction} className="flex flex-col gap-4">
      <Field id="reject-reason" label={labels.reason} hint={labels.reasonHint}>
        <textarea
          id="reject-reason"
          name="reason"
          required
          maxLength={500}
          rows={3}
          aria-describedby="reject-reason-hint"
          className={`${controlClassName} py-3`}
        />
      </Field>
      <FormMessage state={state} errors={errors} />
      <Button type="submit" variant="outline" disabled={pending} className="self-start">
        {labels.reject}
      </Button>
    </form>
  );
}
