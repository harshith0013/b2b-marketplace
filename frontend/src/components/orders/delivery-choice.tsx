"use client";

import { useActionState, useState } from "react";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { Dictionary } from "@/i18n/dictionaries";
import { format } from "@/i18n/format";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";
import type { DeliveryMethod } from "@/lib/api/types";

interface DeliveryChoiceProps {
  /** Distinguishes several forms on one page (one per seller). */
  formId: string;
  city: string;
  pickupAvailable: boolean;
  /** Seller delivery is offered (own delivery and cash on delivery). */
  deliveryAvailable: boolean;
  /** Shown as a hint under the PIN code. */
  pincodes: string[];
  /** One fresh key per form: a resubmit never places a second order. */
  idempotencyKey: string;
  submitLabel: string;
  labels: Dictionary["orders"];
  errors: Dictionary["business"]["errors"];
  action: (previous: FormState, form: FormData) => Promise<FormState>;
}

/** Pickup or seller delivery, then place the order. Payment follows from the choice. */
export function DeliveryChoice({
  formId,
  city,
  pickupAvailable,
  deliveryAvailable,
  pincodes,
  idempotencyKey,
  submitLabel,
  labels,
  errors,
  action,
}: DeliveryChoiceProps) {
  const [method, setMethod] = useState<DeliveryMethod>(
    pickupAvailable ? "PICKUP" : "SELLER_DELIVERY",
  );
  const [state, formAction, pending] = useActionState(
    action,
    INITIAL_FORM_STATE,
  );
  const options: [DeliveryMethod, boolean, string][] = [
    ["PICKUP", pickupAvailable, format(labels.pickup, { city })],
    ["SELLER_DELIVERY", deliveryAvailable, labels.sellerDelivery],
  ];

  return (
    <form action={formAction} className="flex flex-col gap-4">
      <input type="hidden" name="idempotencyKey" value={idempotencyKey} />
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 font-semibold">{labels.delivery}</legend>
        {options
          .filter(([, available]) => available)
          .map(([value, , label]) => (
            <label key={value} className="flex items-center gap-3">
              <input
                type="radio"
                name="deliveryMethod"
                value={value}
                checked={method === value}
                onChange={() => setMethod(value)}
                className="size-5"
              />
              {label}
            </label>
          ))}
      </fieldset>
      {method === "SELLER_DELIVERY" && (
        <>
          <Input
            id={`${formId}-address`}
            name="deliveryAddress"
            label={labels.address}
            required
            maxLength={300}
            autoComplete="street-address"
          />
          <Input
            id={`${formId}-pincode`}
            name="deliveryPincode"
            label={labels.pincode}
            hint={pincodes.join(", ")}
            required
            inputMode="numeric"
            pattern="[1-9][0-9]{5}"
            maxLength={6}
            autoComplete="postal-code"
          />
        </>
      )}
      <FormMessage state={state} errors={errors} />
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
