"use client";

import { useActionState, useState } from "react";
import Link from "next/link";
import { FormMessage } from "@/components/forms/form-message";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import type { Dictionary } from "@/i18n/dictionaries";
import { INITIAL_FORM_STATE, type FormState } from "@/lib/actions/form-state";

type Action = (previous: FormState, form: FormData) => Promise<FormState>;

interface OrderPanelProps {
  lang: string;
  /** Businesses the person may buy for (owner or manager). */
  businesses: { id: string; name: string }[];
  moq: number;
  unit: string;
  labels: Dictionary["orders"];
  errors: Dictionary["business"]["errors"];
  addAction: Action;
  quoteAction: Action;
}

/** On an offer: add a quantity to the cart, or ask the seller for a price. */
export function OrderPanel({
  lang,
  businesses,
  moq,
  unit,
  labels,
  errors,
  addAction,
  quoteAction,
}: OrderPanelProps) {
  const [businessId, setBusinessId] = useState(businesses[0].id);
  const [added, add, adding] = useActionState(addAction, INITIAL_FORM_STATE);
  const [asked, ask, asking] = useActionState(quoteAction, INITIAL_FORM_STATE);

  const buyerField =
    businesses.length > 1 ? (
      <Select
        id="order-business"
        name="businessId"
        label={labels.buyingFor}
        options={businesses.map((b) => ({ value: b.id, label: b.name }))}
        value={businessId}
        onChange={(event) => setBusinessId(event.target.value)}
      />
    ) : (
      <input type="hidden" name="businessId" value={businessId} />
    );

  return (
    <div className="flex flex-col gap-6">
      <form action={add} className="flex flex-col gap-4">
        {buyerField}
        <Input
          id="cart-quantity"
          name="quantity"
          type="number"
          label={`${labels.quantity} (${unit})`}
          min={moq}
          step={1}
          defaultValue={moq}
          required
          inputMode="numeric"
        />
        <FormMessage state={added} errors={errors} success={labels.added} />
        <div className="flex flex-wrap gap-3">
          <Button type="submit" variant="accent" size="lg" disabled={adding}>
            {labels.addToCart}
          </Button>
          {added.ok && (
            <Button asChild variant="outline" size="lg">
              <Link href={`/${lang}/business/${businessId}/cart`}>
                {labels.goToCart}
              </Link>
            </Button>
          )}
        </div>
      </form>

      <form action={ask} className="flex flex-col gap-4">
        <h3 className="text-title font-bold">{labels.askQuote}</h3>
        <input type="hidden" name="businessId" value={businessId} />
        <Input
          id="quote-quantity"
          name="quantity"
          type="number"
          label={`${labels.quantity} (${unit})`}
          min={1}
          step={1}
          defaultValue={moq}
          required
          inputMode="numeric"
        />
        <Input
          id="quote-note"
          name="note"
          label={labels.quoteNote}
          maxLength={500}
          autoComplete="off"
        />
        <FormMessage state={asked} errors={errors} />
        <Button
          type="submit"
          variant="outline"
          size="lg"
          disabled={asking}
          className="self-start"
        >
          {labels.askQuote}
        </Button>
      </form>
    </div>
  );
}
