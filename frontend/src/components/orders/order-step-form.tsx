"use client";

import { useActionState } from "react";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { Dictionary } from "@/i18n/dictionaries";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";
import type { OrderAction } from "@/lib/api/types";

interface OrderStepFormProps {
  step: OrderAction;
  askReason: boolean;
  askDeliveryPerson: boolean;
  labels: Dictionary["orders"];
  errors: Dictionary["business"]["errors"];
  action: (previous: FormState, form: FormData) => Promise<FormState>;
}

const BACKWARD: readonly OrderAction[] = ["DECLINE", "CANCEL"];

/** One step on an order (accept, dispatch, ...), with the details that step needs. */
export function OrderStepForm({
  step,
  askReason,
  askDeliveryPerson,
  labels,
  errors,
  action,
}: OrderStepFormProps) {
  const [state, formAction, pending] = useActionState(
    action,
    INITIAL_FORM_STATE,
  );
  const id = `step-${step.toLowerCase()}`;
  return (
    <form
      action={formAction}
      aria-label={labels.actions[step]}
      className="flex flex-col gap-3"
    >
      {askReason && (
        <Input
          id={`${id}-reason`}
          name="reason"
          label={labels.reason}
          hint={labels.reasonHint}
          required
          maxLength={300}
          autoComplete="off"
        />
      )}
      {askDeliveryPerson && (
        <>
          <Input
            id={`${id}-person`}
            name="deliveryPersonName"
            label={labels.deliveryPerson}
            hint={labels.deliveryPersonNote}
            required
            maxLength={100}
            autoComplete="off"
          />
          <Input
            id={`${id}-phone`}
            name="deliveryPersonPhone"
            type="tel"
            label={labels.deliveryPhone}
            required
            pattern="[0-9+ ]{10,15}"
            maxLength={15}
            autoComplete="off"
          />
        </>
      )}
      <FormMessage state={state} errors={errors} />
      <Button
        type="submit"
        variant={BACKWARD.includes(step) ? "outline" : "accent"}
        disabled={pending}
        className="self-start"
      >
        {labels.actions[step]}
      </Button>
    </form>
  );
}
