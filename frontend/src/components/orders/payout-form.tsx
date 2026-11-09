"use client";

import { useActionState } from "react";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import type { Dictionary } from "@/i18n/dictionaries";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";
import { PAYOUT_BUSINESS_TYPES } from "@/lib/api/types";

/** The seller's details for a Razorpay linked account. The bank number is not kept by this site. */
export function PayoutForm({
  labels,
  errors,
  action,
}: {
  labels: Dictionary["payouts"];
  errors: Dictionary["business"]["errors"];
  action: (previous: FormState, form: FormData) => Promise<FormState>;
}) {
  const [state, formAction, pending] = useActionState(
    action,
    INITIAL_FORM_STATE,
  );
  const f = labels.fields;
  return (
    <form
      action={formAction}
      aria-label={labels.title}
      className="flex flex-col gap-4"
    >
      <Input
        id="payout-email"
        name="email"
        type="email"
        label={f.email}
        required
        maxLength={120}
        autoComplete="email"
      />
      <Input
        id="payout-phone"
        name="phone"
        type="tel"
        label={f.phone}
        required
        pattern="[6-9][0-9]{9}"
        maxLength={10}
        inputMode="numeric"
        autoComplete="tel-national"
      />
      <Input
        id="payout-contact"
        name="contactName"
        label={f.contactName}
        required
        minLength={4}
        maxLength={120}
        autoComplete="name"
      />
      <Select
        id="payout-type"
        name="businessType"
        label={f.businessType}
        required
        options={PAYOUT_BUSINESS_TYPES.map((value) => ({
          value,
          label: f.businessTypes[value],
        }))}
      />
      <Input
        id="payout-street"
        name="street"
        label={f.street}
        required
        minLength={4}
        maxLength={100}
        autoComplete="street-address"
      />
      <Input
        id="payout-account"
        name="accountNumber"
        label={f.accountNumber}
        required
        pattern="[0-9 ]{9,22}"
        inputMode="numeric"
        autoComplete="off"
      />
      <Input
        id="payout-ifsc"
        name="ifsc"
        label={f.ifsc}
        hint={f.ifscHint}
        required
        pattern="[A-Za-z]{4}0[A-Za-z0-9]{6}"
        maxLength={11}
        autoComplete="off"
      />
      <Input
        id="payout-beneficiary"
        name="beneficiaryName"
        label={f.beneficiaryName}
        required
        minLength={4}
        maxLength={120}
        autoComplete="off"
      />
      <label className="flex items-start gap-3 font-semibold">
        <input
          type="checkbox"
          name="acceptsTerms"
          value="yes"
          required
          className="mt-1 size-5"
        />
        {f.acceptsTerms}
      </label>
      <FormMessage state={state} errors={errors} success={labels.saved} />
      <Button
        type="submit"
        variant="accent"
        disabled={pending}
        className="self-start"
      >
        {labels.save}
      </Button>
    </form>
  );
}
