import { rupeesToPaise } from "@/lib/money";
import {
  CLEARANCE_REASONS,
  DATE_TYPES,
  GST_RATES_BP,
  PACK_UNITS,
  STOCK_UNITS,
} from "@/lib/api/types";

/** Everything the listing wizard collects, as typed (strings). Shared by the form and the action. */
export interface DraftValues {
  productMode: "existing" | "new";
  variantId: string;
  categoryId: string;
  brand: string;
  name: string;
  packSize: string;
  packUnit: string;
  gtin: string;
  warehouseId: string;
  batchNumber: string;
  dateType: string;
  expiresOn: string;
  manufacturedOn: string;
  mrp: string;
  quantity: string;
  quantityUnit: string;
  packsPerCase: string;
  moq: string;
  tiers: { quantity: string; price: string }[];
  gstRateBp: string;
  clearanceReason: string;
  visibility: string;
  notes: string;
}

export const MAX_TIERS = 5;

export const EMPTY_DRAFT: DraftValues = {
  productMode: "new",
  variantId: "",
  categoryId: "",
  brand: "",
  name: "",
  packSize: "",
  packUnit: "G",
  gtin: "",
  warehouseId: "",
  batchNumber: "",
  dateType: "EXPIRY",
  expiresOn: "",
  manufacturedOn: "",
  mrp: "",
  quantity: "",
  quantityUnit: "PIECE",
  packsPerCase: "",
  moq: "",
  tiers: [{ quantity: "", price: "" }],
  gstRateBp: "1800",
  clearanceReason: "NEAR_EXPIRY",
  visibility: "PUBLIC",
  notes: "",
};

export type DraftStep = "product" | "batch" | "price";
export type FieldProblem = "required" | "invalidNumber" | "invalidMoney";
export type DraftErrors = Partial<Record<string, FieldProblem>>;

const WHOLE = /^[1-9]\d{0,8}$/;
const DECIMAL = /^\d{1,9}(\.\d{1,3})?$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

function oneOf(value: string, allowed: readonly string[]): boolean {
  return allowed.includes(value);
}

/** Field problems for one step; empty when the step can move on. */
export function validateStep(step: DraftStep, v: DraftValues): DraftErrors {
  const errors: DraftErrors = {};
  const need = (key: string, value: string) => {
    if (!value.trim()) errors[key] = "required";
  };
  const whole = (key: string, value: string) => {
    if (!value.trim()) errors[key] = "required";
    else if (!WHOLE.test(value.trim())) errors[key] = "invalidNumber";
  };
  const money = (key: string, value: string) => {
    if (!value.trim()) errors[key] = "required";
    else if (rupeesToPaise(value) === null || rupeesToPaise(value) === 0)
      errors[key] = "invalidMoney";
  };

  if (step === "product") {
    if (v.productMode === "existing") {
      need("variantId", v.variantId);
    } else {
      need("categoryId", v.categoryId);
      need("brand", v.brand);
      need("name", v.name);
      if (!v.packSize.trim()) errors.packSize = "required";
      else if (!DECIMAL.test(v.packSize.trim()) || Number(v.packSize) <= 0) {
        errors.packSize = "invalidNumber";
      }
      if (!oneOf(v.packUnit, PACK_UNITS)) errors.packUnit = "required";
    }
  }
  if (step === "batch") {
    need("warehouseId", v.warehouseId);
    need("batchNumber", v.batchNumber);
    if (!oneOf(v.dateType, DATE_TYPES)) errors.dateType = "required";
    if (!DATE.test(v.expiresOn)) errors.expiresOn = "required";
    money("mrp", v.mrp);
    whole("quantity", v.quantity);
    if (!oneOf(v.quantityUnit, STOCK_UNITS)) errors.quantityUnit = "required";
    if (v.quantityUnit === "CASE") whole("packsPerCase", v.packsPerCase);
  }
  if (step === "price") {
    whole("moq", v.moq);
    v.tiers.forEach((tier, i) => {
      if (i > 0) whole(`tier${i}Quantity`, tier.quantity);
      money(`tier${i}Price`, tier.price);
    });
    if (!oneOf(v.gstRateBp, GST_RATES_BP.map(String)))
      errors.gstRateBp = "required";
    if (!oneOf(v.clearanceReason, CLEARANCE_REASONS))
      errors.clearanceReason = "required";
    if (!oneOf(v.visibility, ["PUBLIC", "CONFIDENTIAL"]))
      errors.visibility = "required";
  }
  return errors;
}

/** The first tier always starts at the minimum order. */
export function tiersForApi(
  v: DraftValues,
): { minQuantity: number; unitPricePaise: number }[] {
  return v.tiers.map((tier, i) => ({
    minQuantity: Number(i === 0 ? v.moq : tier.quantity),
    unitPricePaise: rupeesToPaise(tier.price) ?? 0,
  }));
}
