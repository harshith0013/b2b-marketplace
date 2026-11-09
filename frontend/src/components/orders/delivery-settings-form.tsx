"use client";

import { useActionState } from "react";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { Dictionary } from "@/i18n/dictionaries";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";
import type { DeliveryOptions } from "@/lib/api/types";
import { paiseToRupeesInput } from "@/lib/money";

interface DeliverySettingsFormProps {
  delivery: DeliveryOptions;
  labels: Dictionary["orders"];
  errors: Dictionary["business"]["errors"];
  action: (previous: FormState, form: FormData) => Promise<FormState>;
}

function Check({
  name,
  label,
  checked,
}: {
  name: string;
  label: string;
  checked: boolean;
}) {
  return (
    <label className="flex items-center gap-3 font-semibold">
      <input
        type="checkbox"
        name={name}
        value="yes"
        defaultChecked={checked}
        className="size-5"
      />
      {label}
    </label>
  );
}

/** A listing's delivery and payment: pickup, own delivery to PIN codes with a charge, COD. */
export function DeliverySettingsForm({
  delivery,
  labels,
  errors,
  action,
}: DeliverySettingsFormProps) {
  const [state, formAction, pending] = useActionState(
    action,
    INITIAL_FORM_STATE,
  );
  return (
    <form
      action={formAction}
      aria-label={labels.deliveryHeading}
      className="flex flex-col gap-4"
    >
      <Check
        name="pickupAvailable"
        label={labels.pickupOption}
        checked={delivery.pickupAvailable}
      />
      <Check
        name="ownDelivery"
        label={labels.ownDeliveryOption}
        checked={delivery.ownDelivery}
      />
      <Input
        id="delivery-pincodes"
        name="deliveryPincodes"
        label={labels.pincodes}
        hint={labels.pincodesHint}
        defaultValue={delivery.deliveryPincodes.join(", ")}
        maxLength={400}
        inputMode="numeric"
        autoComplete="off"
      />
      <Input
        id="delivery-charge"
        name="deliveryCharge"
        label={labels.charge}
        defaultValue={paiseToRupeesInput(delivery.deliveryChargePaise)}
        inputMode="decimal"
        autoComplete="off"
      />
      <Check
        name="codAccepted"
        label={labels.codOption}
        checked={delivery.codAccepted}
      />
      <Check
        name="onlineAccepted"
        label={labels.onlineOption}
        checked={delivery.onlineAccepted}
      />
      <FormMessage
        state={state}
        errors={errors}
        success={labels.deliverySaved}
      />
      <Button type="submit" disabled={pending} className="self-start">
        {labels.saveDelivery}
      </Button>
    </form>
  );
}
