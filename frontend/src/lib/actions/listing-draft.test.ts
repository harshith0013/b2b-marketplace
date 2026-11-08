import { describe, expect, it } from "vitest";
import {
  EMPTY_DRAFT,
  tiersForApi,
  validateStep,
  type DraftValues,
} from "./listing-draft";

const filled: DraftValues = {
  ...EMPTY_DRAFT,
  productMode: "new",
  categoryId: "c1",
  brand: "Lifebuoy",
  name: "Total soap",
  packSize: "100",
  packUnit: "G",
  warehouseId: "w1",
  batchNumber: "B23-0456",
  expiresOn: "2027-03-31",
  mrp: "45",
  quantity: "500",
  moq: "10",
  tiers: [
    { quantity: "", price: "30" },
    { quantity: "100", price: "27.50" },
  ],
};

describe("validateStep", () => {
  it("accepts a complete draft at every step", () => {
    for (const step of ["product", "batch", "price"] as const) {
      expect(validateStep(step, filled)).toEqual({});
    }
  });

  it("asks for each missing product field", () => {
    expect(
      validateStep("product", { ...EMPTY_DRAFT, productMode: "new" }),
    ).toMatchObject({
      categoryId: "required",
      brand: "required",
      name: "required",
      packSize: "required",
    });
    expect(
      validateStep("product", { ...EMPTY_DRAFT, productMode: "existing" }),
    ).toEqual({
      variantId: "required",
    });
  });

  it("checks numbers and money as people type them", () => {
    const errors = validateStep("batch", {
      ...filled,
      quantity: "2.5",
      mrp: "45.555",
    });
    expect(errors).toEqual({ quantity: "invalidNumber", mrp: "invalidMoney" });
    expect(validateStep("batch", { ...filled, mrp: "0" })).toEqual({
      mrp: "invalidMoney",
    });
  });

  it("needs the case size only when counting in cases", () => {
    expect(validateStep("batch", { ...filled, quantityUnit: "CASE" })).toEqual({
      packsPerCase: "required",
    });
    expect(
      validateStep("batch", {
        ...filled,
        quantityUnit: "CASE",
        packsPerCase: "24",
      }),
    ).toEqual({});
  });

  it("checks every price tier and refuses unknown choices", () => {
    const errors = validateStep("price", {
      ...filled,
      tiers: [
        { quantity: "", price: "" },
        { quantity: "0", price: "abc" },
      ],
      gstRateBp: "1200",
    });
    expect(errors).toEqual({
      tier0Price: "required",
      tier1Quantity: "invalidNumber",
      tier1Price: "invalidMoney",
      gstRateBp: "required",
    });
  });
});

describe("tiersForApi", () => {
  it("starts the first tier at the minimum order and sends paise", () => {
    expect(tiersForApi(filled)).toEqual([
      { minQuantity: 10, unitPricePaise: 3000 },
      { minQuantity: 100, unitPricePaise: 2750 },
    ]);
  });
});
