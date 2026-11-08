"use server";

import { refresh } from "next/cache";
import { redirect } from "next/navigation";
import type { CartLine, Order, OrderAction, Quote } from "@/lib/api/types";
import { ORDER_ACTIONS } from "@/lib/api/types";
import { api, requireSession } from "@/lib/auth/current-user";
import { rupeesToPaise } from "@/lib/money";
import { actionPath, parsePincodes, paymentFor } from "@/lib/orders";
import { field, toFormState } from "./form-errors";
import type { FormState } from "./form-state";
import { checkIds, isUuid } from "./ids";

const MAX_QUANTITY = 10_000_000;
const KEY = /^[0-9a-f-]{36}$/i;

const businessPath = (lang: string, businessId: string) =>
  `/${lang}/business/${businessId}`;

/** A whole positive quantity from the form, or null. */
function quantityOf(form: FormData): number | null {
  const value = Number(field(form, "quantity"));
  return Number.isInteger(value) && value > 0 && value <= MAX_QUANTITY
    ? value
    : null;
}

/** The buyer business chosen in the form; the API checks the person belongs to it. */
function buyerOf(form: FormData): string | null {
  const id = field(form, "businessId");
  return isUuid(id) ? id : null;
}

/** Delivery choice shared by cart checkout and ordering a quote. Null if the form is incomplete. */
function deliveryChoice(form: FormData) {
  const method = field(form, "deliveryMethod");
  const idempotencyKey = field(form, "idempotencyKey");
  if (!KEY.test(idempotencyKey)) return null;
  if (method === "PICKUP")
    return {
      deliveryMethod: method,
      paymentMethod: paymentFor(method),
      idempotencyKey,
    } as const;
  if (method !== "SELLER_DELIVERY") return null;
  const deliveryAddress = field(form, "deliveryAddress");
  const deliveryPincode = field(form, "deliveryPincode");
  if (!deliveryAddress || !/^[1-9][0-9]{5}$/.test(deliveryPincode)) return null;
  return {
    deliveryMethod: method,
    deliveryAddress,
    deliveryPincode,
    paymentMethod: paymentFor(method),
    idempotencyKey,
  } as const;
}

export async function addToCart(
  lang: string,
  listingId: string,
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  checkIds(lang, listingId);
  const buyer = buyerOf(form);
  const quantity = quantityOf(form);
  if (!buyer || !quantity) return { error: "orderProblem" };
  const returnTo = `/${lang}/offers/${listingId}`;
  const session = await requireSession(lang, returnTo);
  try {
    await api<CartLine[]>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${buyer}/cart`,
      { method: "POST", body: { listingId, quantity } },
    );
  } catch (error) {
    return toFormState(error, "notEnoughStock", "orderProblem");
  }
  return { ok: true };
}

export async function requestQuote(
  lang: string,
  listingId: string,
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  checkIds(lang, listingId);
  const buyer = buyerOf(form);
  const quantity = quantityOf(form);
  if (!buyer || !quantity) return { error: "orderProblem" };
  const returnTo = `/${lang}/offers/${listingId}`;
  const session = await requireSession(lang, returnTo);
  let quote: Quote;
  try {
    quote = await api<Quote>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${buyer}/quotes`,
      {
        method: "POST",
        body: { listingId, quantity, note: field(form, "note") || null },
      },
    );
  } catch (error) {
    return toFormState(error, "notEnoughStock", "orderProblem");
  }
  redirect(`${businessPath(lang, buyer)}/quotes/${quote.id}`);
}

export async function removeFromCart(
  lang: string,
  businessId: string,
  listingId: string,
): Promise<void> {
  checkIds(lang, businessId, listingId);
  const returnTo = `${businessPath(lang, businessId)}/cart`;
  const session = await requireSession(lang, returnTo);
  await api<CartLine[]>(
    session,
    lang,
    returnTo,
    `/api/v1/businesses/${businessId}/cart/${listingId}`,
    { method: "DELETE" },
  );
  refresh();
}

export async function checkout(
  lang: string,
  businessId: string,
  listingIds: string[],
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  checkIds(lang, businessId, ...listingIds);
  const choice = deliveryChoice(form);
  if (!choice || listingIds.length === 0) return { error: "orderProblem" };
  const returnTo = `${businessPath(lang, businessId)}/cart`;
  const session = await requireSession(lang, returnTo);
  let order: Order;
  try {
    order = await api<Order>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${businessId}/orders`,
      { method: "POST", body: { listingIds, ...choice } },
    );
  } catch (error) {
    return toFormState(error, "notEnoughStock", "orderProblem");
  }
  redirect(`${businessPath(lang, businessId)}/orders/${order.id}`);
}

export async function orderQuote(
  lang: string,
  businessId: string,
  quoteId: string,
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  checkIds(lang, businessId, quoteId);
  const choice = deliveryChoice(form);
  if (!choice) return { error: "orderProblem" };
  const returnTo = `${businessPath(lang, businessId)}/quotes/${quoteId}`;
  const session = await requireSession(lang, returnTo);
  let order: Order;
  try {
    order = await api<Order>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${businessId}/quotes/${quoteId}/order`,
      { method: "POST", body: choice },
    );
  } catch (error) {
    return toFormState(error, "orderStepRefused", "orderProblem");
  }
  redirect(`${businessPath(lang, businessId)}/orders/${order.id}`);
}

export async function actOnOrder(
  lang: string,
  businessId: string,
  orderId: string,
  action: OrderAction,
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  checkIds(lang, businessId, orderId);
  if (!ORDER_ACTIONS.includes(action)) throw new Error("Invalid request");
  const returnTo = `${businessPath(lang, businessId)}/orders/${orderId}`;
  const session = await requireSession(lang, returnTo);
  try {
    await api<Order>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${businessId}/orders/${orderId}/${actionPath(action)}`,
      {
        method: "POST",
        body: {
          reason: field(form, "reason") || null,
          deliveryPersonName: field(form, "deliveryPersonName") || null,
          deliveryPersonPhone: field(form, "deliveryPersonPhone") || null,
        },
      },
    );
  } catch (error) {
    return toFormState(error, "orderStepRefused");
  }
  refresh();
  return { ok: true };
}

export async function offerQuote(
  lang: string,
  businessId: string,
  quoteId: string,
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  checkIds(lang, businessId, quoteId);
  const unitPricePaise = rupeesToPaise(field(form, "price"));
  if (!unitPricePaise) return { error: "invalid" };
  const returnTo = `${businessPath(lang, businessId)}/quotes/${quoteId}`;
  const session = await requireSession(lang, returnTo);
  try {
    await api<Quote>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${businessId}/quotes/${quoteId}/offer`,
      { method: "POST", body: { unitPricePaise } },
    );
  } catch (error) {
    return toFormState(error, "orderStepRefused");
  }
  refresh();
  return { ok: true };
}

export async function declineQuote(
  lang: string,
  businessId: string,
  quoteId: string,
): Promise<FormState> {
  checkIds(lang, businessId, quoteId);
  const returnTo = `${businessPath(lang, businessId)}/quotes/${quoteId}`;
  const session = await requireSession(lang, returnTo);
  try {
    await api<Quote>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${businessId}/quotes/${quoteId}/decline`,
      { method: "POST" },
    );
  } catch (error) {
    return toFormState(error, "orderStepRefused");
  }
  refresh();
  return { ok: true };
}

export async function saveDelivery(
  lang: string,
  businessId: string,
  listingId: string,
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  checkIds(lang, businessId, listingId);
  const ownDelivery = form.get("ownDelivery") === "yes";
  const deliveryPincodes = parsePincodes(field(form, "deliveryPincodes"));
  const charge = field(form, "deliveryCharge");
  const deliveryChargePaise = charge === "" ? 0 : rupeesToPaise(charge);
  if (deliveryPincodes === null || deliveryChargePaise === null)
    return { error: "invalid" };
  const pickupAvailable = form.get("pickupAvailable") === "yes";
  // Buyers need at least one way to receive the stock, and delivery needs PIN codes.
  if (!pickupAvailable && !ownDelivery) return { error: "invalid" };
  if (ownDelivery && deliveryPincodes.length === 0) return { error: "invalid" };
  const returnTo = `${businessPath(lang, businessId)}/listings/${listingId}`;
  const session = await requireSession(lang, returnTo);
  try {
    await api(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${businessId}/listings/${listingId}/delivery`,
      {
        method: "PUT",
        body: {
          pickupAvailable,
          ownDelivery,
          deliveryPincodes,
          deliveryChargePaise,
          codAccepted: form.get("codAccepted") === "yes",
        },
      },
    );
  } catch (error) {
    return toFormState(error, "invalid");
  }
  refresh();
  return { ok: true };
}
