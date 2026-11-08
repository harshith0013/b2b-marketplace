"use client";

import { useActionState } from "react";
import { FormMessage } from "@/components/forms/form-message";
import { Button, type ButtonProps } from "@/components/ui/button";
import type { Dictionary } from "@/i18n/dictionaries";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";

interface ActionFormProps {
  label: string;
  errors: Dictionary["business"]["errors"];
  action: (previous: FormState, form: FormData) => Promise<FormState>;
  variant?: ButtonProps["variant"];
  disabled?: boolean;
  success?: string;
}

/** A single button that runs a Server Action and shows its result in the reader's language. */
export function ActionForm({
  label,
  errors,
  action,
  variant,
  disabled,
  success,
}: ActionFormProps) {
  const [state, formAction, pending] = useActionState(
    action,
    INITIAL_FORM_STATE,
  );
  return (
    <form action={formAction} className="flex flex-col gap-2">
      <FormMessage state={state} errors={errors} success={success} />
      <Button
        type="submit"
        variant={variant}
        disabled={disabled || pending}
        className="self-start"
      >
        {label}
      </Button>
    </form>
  );
}
