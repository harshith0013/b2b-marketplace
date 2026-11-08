"use client";

import { useActionState } from "react";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";
import { BUSINESS_TYPES, type BusinessDetails } from "@/lib/api/types";
import type { Dictionary } from "@/i18n/dictionaries";

interface BusinessFormProps {
  labels: Dictionary["business"];
  action: (previous: FormState, form: FormData) => Promise<FormState>;
  submitLabel: string;
  /** Present when editing an existing business. */
  initial?: BusinessDetails;
}

const PINCODE_PATTERN = "[1-9][0-9]{5}";

export function BusinessForm({
  labels,
  action,
  submitLabel,
  initial,
}: BusinessFormProps) {
  const [state, formAction, pending] = useActionState(
    action,
    INITIAL_FORM_STATE,
  );
  const { fields } = labels;

  return (
    <form action={formAction} className="flex flex-col gap-5">
      <Input
        id="business-legalName"
        name="legalName"
        label={fields.legalName}
        hint={fields.legalNameHint}
        defaultValue={initial?.legalName}
        required
        maxLength={200}
        autoComplete="organization"
      />
      <Input
        id="business-tradeName"
        name="tradeName"
        label={fields.tradeName}
        defaultValue={initial?.tradeName ?? ""}
        maxLength={200}
      />
      <Select
        id="business-businessType"
        name="businessType"
        label={fields.businessType}
        placeholder={fields.businessTypePlaceholder}
        options={BUSINESS_TYPES.map((type) => ({
          value: type,
          label: labels.types[type],
        }))}
        defaultValue={initial?.businessType ?? ""}
        required
      />
      <Input
        id="business-gstin"
        name="gstin"
        label={fields.gstin}
        hint={fields.gstinHint}
        defaultValue={initial?.gstin ?? ""}
        maxLength={15}
        autoCapitalize="characters"
        spellCheck={false}
      />
      <Input
        id="business-city"
        name="city"
        label={fields.city}
        defaultValue={initial?.city}
        required
        maxLength={100}
        autoComplete="address-level2"
      />
      <Input
        id="business-state"
        name="state"
        label={fields.state}
        defaultValue={initial?.state}
        required
        maxLength={100}
        autoComplete="address-level1"
      />
      <Input
        id="business-pincode"
        name="pincode"
        label={fields.pincode}
        defaultValue={initial?.pincode}
        required
        inputMode="numeric"
        pattern={PINCODE_PATTERN}
        maxLength={6}
        autoComplete="postal-code"
      />
      <FormMessage
        state={state}
        errors={labels.errors}
        success={labels.saved}
      />
      <Button
        type="submit"
        variant="accent"
        size="lg"
        disabled={pending}
        className="self-start"
      >
        {submitLabel}
      </Button>
    </form>
  );
}
