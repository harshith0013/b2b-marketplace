"use server";

import { refresh } from "next/cache";
import { redirect } from "next/navigation";
import type { Product, Listing, Photo } from "@/lib/api/types";
import { PHOTO_KINDS } from "@/lib/api/types";
import { api, apiUpload, requireSession } from "@/lib/auth/current-user";
import { rupeesToPaise } from "@/lib/money";
import { toFormState } from "./form-errors";
import type { FormErrorKey, FormState } from "./form-state";
import { checkIds, isUuid } from "./ids";
import {
  tiersForApi,
  validateStep,
  type DraftStep,
  type DraftValues,
} from "./listing-draft";

/** 5 MB, as the API allows; checked here too so large files fail fast. */
const MAX_PHOTO_BYTES = 5 * 1024 * 1024;

/**
 * Result of saving the wizard. Ids of what was already created are kept, so pressing Save again
 * after a failure does not create the product or batch twice.
 */
export interface DraftState {
  error?: FormErrorKey;
  step?: DraftStep;
  variantId?: string;
  batchId?: string;
}

const listingsPath = (lang: string, businessId: string) =>
  `/${lang}/business/${businessId}/listings`;

/** Creates (if needed) the product and the batch, then the draft listing; opens its page. */
export async function saveListingDraft(
  lang: string,
  businessId: string,
  previous: DraftState,
  values: DraftValues,
): Promise<DraftState> {
  checkIds(lang, businessId);
  for (const step of ["product", "batch", "price"] as const) {
    if (Object.keys(validateStep(step, values)).length > 0)
      return { ...previous, error: "invalid", step };
  }
  const returnTo = `${listingsPath(lang, businessId)}/new`;
  const session = await requireSession(lang, returnTo);
  const base = `/api/v1/businesses/${businessId}`;
  const state: DraftState = {
    variantId:
      previous.variantId && isUuid(previous.variantId)
        ? previous.variantId
        : undefined,
    batchId:
      previous.batchId && isUuid(previous.batchId)
        ? previous.batchId
        : undefined,
  };

  if (!state.variantId) {
    if (values.productMode === "existing") {
      if (!isUuid(values.variantId))
        return { ...state, error: "invalid", step: "product" };
      state.variantId = values.variantId;
    } else {
      try {
        const product = await api<Product>(
          session,
          lang,
          returnTo,
          `${base}/products`,
          {
            method: "POST",
            body: {
              categoryId: values.categoryId,
              brand: values.brand.trim(),
              name: values.name.trim(),
              variants: [
                {
                  packSize: values.packSize.trim(),
                  packUnit: values.packUnit,
                  gtin: values.gtin.trim() || null,
                },
              ],
            },
          },
        );
        state.variantId = product.variants[0].id;
      } catch (error) {
        return {
          ...state,
          ...toFormState(error, "barcodeTaken"),
          step: "product",
        };
      }
    }
  }

  if (!state.batchId) {
    try {
      const batch = await api<{ id: string }>(
        session,
        lang,
        returnTo,
        `${base}/batches`,
        {
          method: "POST",
          body: {
            variantId: state.variantId,
            warehouseId: values.warehouseId,
            batchNumber: values.batchNumber.trim(),
            dateType: values.dateType,
            expiresOn: values.expiresOn,
            manufacturedOn: values.manufacturedOn || null,
            quantity: Number(values.quantity),
            quantityUnit: values.quantityUnit,
            packsPerCase:
              values.quantityUnit === "CASE"
                ? Number(values.packsPerCase)
                : null,
            mrpPaise: rupeesToPaise(values.mrp),
          },
        },
      );
      state.batchId = batch.id;
    } catch (error) {
      return {
        ...state,
        ...toFormState(error, "batchNumberTaken"),
        step: "batch",
      };
    }
  }

  let listing: Listing;
  try {
    listing = await api<Listing>(session, lang, returnTo, `${base}/listings`, {
      method: "POST",
      body: {
        batchId: state.batchId,
        clearanceReason: values.clearanceReason,
        visibility: values.visibility,
        moq: Number(values.moq),
        gstRateBp: Number(values.gstRateBp),
        notes: values.notes.trim() || null,
        priceTiers: tiersForApi(values),
      },
    });
  } catch (error) {
    return { ...state, ...toFormState(error, "alreadyListed"), step: "price" };
  }
  redirect(`${listingsPath(lang, businessId)}/${listing.id}`);
}

export async function uploadPhoto(
  lang: string,
  businessId: string,
  batchId: string,
  listingId: string,
  _previous: FormState,
  form: FormData,
): Promise<FormState> {
  checkIds(lang, businessId, batchId, listingId);
  const kind = form.get("kind");
  const file = form.get("file");
  if (
    typeof kind !== "string" ||
    !(PHOTO_KINDS as readonly string[]).includes(kind)
  ) {
    return { error: "invalid" };
  }
  if (
    !(file instanceof File) ||
    file.size === 0 ||
    file.size > MAX_PHOTO_BYTES
  ) {
    return { error: "photoProblem" };
  }
  const returnTo = `${listingsPath(lang, businessId)}/${listingId}`;
  const session = await requireSession(lang, returnTo);
  const body = new FormData();
  body.set("kind", kind);
  body.set("file", file, "photo");
  try {
    await apiUpload<Photo>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${businessId}/batches/${batchId}/photos`,
      body,
    );
  } catch (error) {
    return toFormState(error, "photoConflict", "photoProblem");
  }
  refresh();
  return { ok: true };
}

export async function deletePhoto(
  lang: string,
  businessId: string,
  batchId: string,
  listingId: string,
  photoId: string,
): Promise<void> {
  checkIds(lang, businessId, batchId, listingId, photoId);
  const returnTo = `${listingsPath(lang, businessId)}/${listingId}`;
  const session = await requireSession(lang, returnTo);
  await api(
    session,
    lang,
    returnTo,
    `/api/v1/businesses/${businessId}/batches/${batchId}/photos/${photoId}`,
    { method: "DELETE" },
  );
  refresh();
}

const LISTING_ACTIONS = [
  "submit",
  "withdraw",
  "pause",
  "resume",
  "close",
] as const;
export type ListingAction = (typeof LISTING_ACTIONS)[number];

/** Moves a listing on: submit for review, withdraw, pause, resume or close. */
export async function changeListing(
  lang: string,
  businessId: string,
  listingId: string,
  action: ListingAction,
): Promise<FormState> {
  checkIds(lang, businessId, listingId);
  if (!LISTING_ACTIONS.includes(action)) throw new Error("Invalid request");
  const returnTo = `${listingsPath(lang, businessId)}/${listingId}`;
  const session = await requireSession(lang, returnTo);
  try {
    await api<Listing>(
      session,
      lang,
      returnTo,
      `/api/v1/businesses/${businessId}/listings/${listingId}/${action}`,
      { method: "POST" },
    );
  } catch (error) {
    return toFormState(error, "generic");
  }
  refresh();
  return { ok: true };
}
