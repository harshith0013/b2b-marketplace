"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import type { Dictionary } from "@/i18n/dictionaries";
import type { FormErrorKey, FormState } from "@/lib/actions/form-state";
import type { StartResult } from "@/lib/actions/orders";

export const CHECKOUT_SCRIPT = "https://checkout.razorpay.com/v1/checkout.js";

/** What Razorpay's checkout hands to the success handler. */
interface CheckoutSuccess {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

interface RazorpayCheckout {
  open(): void;
  on(event: "payment.failed", handler: () => void): void;
}

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => RazorpayCheckout;
  }
}

/** Loads Razorpay's checkout script once, when the buyer first wants to pay. */
export function loadCheckout(): Promise<boolean> {
  if (window.Razorpay) return Promise.resolve(true);
  return new Promise((resolve) => {
    const existing = document.querySelector<HTMLScriptElement>(
      `script[src="${CHECKOUT_SCRIPT}"]`,
    );
    const script = existing ?? document.createElement("script");
    script.addEventListener("load", () => resolve(Boolean(window.Razorpay)));
    script.addEventListener("error", () => resolve(false));
    if (!existing) {
      script.src = CHECKOUT_SCRIPT;
      script.async = true;
      document.body.appendChild(script);
    }
  });
}

interface PayOnlineProps {
  amountLabel: string;
  siteName: string;
  labels: Dictionary["orders"];
  errors: Dictionary["business"]["errors"];
  start: () => Promise<StartResult>;
  confirm: (result: {
    razorpayOrderId: string;
    razorpayPaymentId: string;
    razorpaySignature: string;
  }) => Promise<FormState>;
}

/** "Pay ₹X online": opens Razorpay's checkout, then has the server verify the payment. */
export function PayOnline({
  amountLabel,
  siteName,
  labels,
  errors,
  start,
  confirm,
}: PayOnlineProps) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const fail = (text: string) => {
    setMessage(text);
    setBusy(false);
  };
  const failWith = (key: FormErrorKey | undefined) =>
    fail(key ? errors[key] : labels.paymentFailed);

  async function pay() {
    setBusy(true);
    setMessage(null);
    const started = await start();
    if (!started.ok) return failWith(started.error);
    if (!(await loadCheckout()) || !window.Razorpay)
      return failWith("paymentUnavailable");
    const checkout = new window.Razorpay({
      key: started.start.keyId,
      order_id: started.start.razorpayOrderId,
      amount: started.start.amountPaise,
      currency: started.start.currency,
      name: siteName,
      handler: async (response: CheckoutSuccess) => {
        const result = await confirm({
          razorpayOrderId: response.razorpay_order_id,
          razorpayPaymentId: response.razorpay_payment_id,
          razorpaySignature: response.razorpay_signature,
        });
        if (result.error === "orderStepRefused")
          return fail(labels.paymentPending);
        if (result.error) return failWith(result.error);
        setBusy(false);
        router.refresh();
      },
      modal: { ondismiss: () => setBusy(false) },
    });
    checkout.on("payment.failed", () => fail(labels.paymentFailed));
    checkout.open();
  }

  return (
    <div className="flex flex-col gap-3">
      {message && (
        <p
          role="alert"
          className="rounded-control bg-danger-soft px-4 py-3 font-semibold text-danger"
        >
          {message}
        </p>
      )}
      <Button
        type="button"
        variant="accent"
        size="lg"
        disabled={busy}
        onClick={pay}
        className="self-start"
      >
        {busy ? labels.paying : amountLabel}
      </Button>
      <p className="text-sm text-muted">{labels.payNote}</p>
      <p className="text-sm text-muted">{labels.payDue}</p>
    </div>
  );
}
