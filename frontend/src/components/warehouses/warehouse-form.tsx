"use client";

import { useActionState } from "react";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";
import type { Dictionary } from "@/i18n/dictionaries";

interface WarehouseFormProps {
  labels: Dictionary["warehouses"];
  errors: Dictionary["business"]["errors"];
  action: (previous: FormState, form: FormData) => Promise<FormState>;
}

const PINCODE_PATTERN = "[1-9][0-9]{5}";

export function WarehouseForm({ labels, errors, action }: WarehouseFormProps) {
  const [state, formAction, pending] = useActionState(action, INITIAL_FORM_STATE);
  const { fields } = labels;

  return (
    <form action={formAction} className="flex flex-col gap-5">
      <Input
        id="warehouse-name"
        name="name"
        label={fields.name}
        hint={fields.nameHint}
        required
        maxLength={100}
        autoComplete="off"
      />
      <Input
        id="warehouse-addressLine"
        name="addressLine"
        label={fields.addressLine}
        required
        maxLength={300}
        autoComplete="street-address"
      />
      <Input
        id="warehouse-city"
        name="city"
        label={fields.city}
        required
        maxLength={100}
        autoComplete="address-level2"
      />
      <Input
        id="warehouse-state"
        name="state"
        label={fields.state}
        required
        maxLength={100}
        autoComplete="address-level1"
      />
      <Input
        id="warehouse-pincode"
        name="pincode"
        label={fields.pincode}
        required
        inputMode="numeric"
        pattern={PINCODE_PATTERN}
        maxLength={6}
        autoComplete="postal-code"
      />
      <FormMessage state={state} errors={errors} success={labels.added} />
      <Button type="submit" disabled={pending} className="self-start">
        {labels.add}
      </Button>
    </form>
  );
}
