import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: () => {} }),
}));
import en from "@/i18n/dictionaries/en.json";
import hi from "@/i18n/dictionaries/hi.json";
import te from "@/i18n/dictionaries/te.json";
import {
  ORDER_ACTIONS,
  ORDER_STATUSES,
  type CartLine,
  type Order,
} from "@/lib/api/types";
import {
  actionPath,
  groupBySeller,
  needsReason,
  parsePincodes,
  paymentFor,
} from "@/lib/orders";
import { DeliveryChoice } from "./delivery-choice";
import { PayOnline } from "./pay-online";
import { canPayNow, paymentChoice } from "@/lib/orders";
import { OrderStepForm } from "./order-step-form";
import { OrderTotals, counterparty } from "./order-parts";

afterEach(cleanup);

const noop = async () => ({});

function line(overrides: Partial<CartLine>): CartLine {
  return {
    listingId: crypto.randomUUID(),
    quantity: 20,
    available: true,
    productLine: "Soap 100 g",
    unit: "PIECE",
    moq: 10,
    unitPricePaise: 3000,
    sellerKey: "s1",
    sellerName: "Godown Traders",
    city: "Guntur",
    pickupAvailable: true,
    ownDelivery: true,
    deliveryPincodes: ["522001", "522002"],
    deliveryChargePaise: 5000,
    codAccepted: true,
    onlineAccepted: false,
    ...overrides,
  };
}

describe("order wording", () => {
  it("names every status and step in all three languages", () => {
    for (const dict of [en, te, hi]) {
      for (const status of ORDER_STATUSES)
        expect(dict.orders.statuses[status], status).toBeTruthy();
      for (const action of ORDER_ACTIONS)
        expect(dict.orders.actions[action], action).toBeTruthy();
    }
  });
});

describe("groupBySeller", () => {
  it("keeps each seller's lines together, in cart order", () => {
    const groups = groupBySeller([
      line({ sellerKey: "a" }),
      line({ sellerKey: "b" }),
      line({ sellerKey: "a" }),
    ]);
    expect(groups.map((g) => [g.sellerKey, g.lines.length])).toEqual([
      ["a", 2],
      ["b", 1],
    ]);
  });

  it("offers only what every line on sale offers, at the highest charge", () => {
    const [group] = groupBySeller([
      line({
        deliveryPincodes: ["522001", "522002"],
        deliveryChargePaise: 3000,
      }),
      line({ deliveryPincodes: ["522002"], codAccepted: false }),
      line({ available: false, pickupAvailable: false }),
    ]);
    expect(group.orderable).toHaveLength(2);
    expect(group.deliveryPincodes).toEqual(["522002"]);
    expect(group.deliveryChargePaise).toBe(5000);
    expect(group.codAccepted).toBe(false);
    expect(group.pickupAvailable).toBe(true); // the line no longer on sale does not count
  });

  it("offers no pickup across two cities, and nothing when nothing is on sale", () => {
    expect(
      groupBySeller([line({ city: "Guntur" }), line({ city: "Tenali" })])[0]
        .pickupAvailable,
    ).toBe(false);
    const [gone] = groupBySeller([line({ available: false })]);
    expect([gone.pickupAvailable, gone.ownDelivery]).toEqual([false, false]);
  });
});

describe("small rules", () => {
  it("reads PIN codes and refuses anything else", () => {
    expect(parsePincodes("522001, 522002 522001")).toEqual([
      "522001",
      "522002",
    ]);
    expect(parsePincodes("")).toEqual([]);
    expect(parsePincodes("522001, 12345")).toBeNull();
    expect(parsePincodes("022001")).toBeNull();
  });

  it("maps steps to API paths and payment to delivery", () => {
    expect(actionPath("OUT_FOR_DELIVERY")).toBe("out-for-delivery");
    expect(actionPath("ACCEPT")).toBe("accept");
    expect(paymentFor("PICKUP")).toBe("PAY_ON_PICKUP");
    expect(paymentFor("SELLER_DELIVERY")).toBe("COD");
  });

  it("asks for a reason when the seller declines or cancels, not when the buyer cancels", () => {
    expect(needsReason("DECLINE", "SELLER")).toBe(true);
    expect(needsReason("CANCEL", "SELLER")).toBe(true);
    expect(needsReason("CANCEL", "BUYER")).toBe(false);
    expect(needsReason("ACCEPT", "SELLER")).toBe(false);
  });
});

describe("DeliveryChoice", () => {
  const props = {
    formId: "f",
    city: "Guntur",
    pincodes: ["522001"],
    idempotencyKey: "0b3c2f4e-4a7d-4c1e-9a55-2f8a7f1c9d10",
    submitLabel: en.orders.placeOrder,
    labels: en.orders,
    errors: en.business.errors,
    action: noop,
  };

  it("asks for the address only when the seller delivers", () => {
    render(
      <DeliveryChoice
        {...props}
        pickupAvailable
        deliveryAvailable
        codAvailable
        onlineAvailable={false}
      />,
    );
    expect(screen.queryByLabelText(en.orders.address)).toBeNull();
    fireEvent.click(
      screen.getByRole("radio", { name: en.orders.sellerDelivery }),
    );
    expect(
      (screen.getByLabelText(en.orders.address) as HTMLInputElement).required,
    ).toBe(true);
    expect(
      (screen.getByLabelText(en.orders.pincode) as HTMLInputElement).required,
    ).toBe(true);
  });

  it("shows only the ways this seller offers", () => {
    render(
      <DeliveryChoice
        {...props}
        pickupAvailable={false}
        deliveryAvailable
        codAvailable
        onlineAvailable={false}
      />,
    );
    expect(
      screen
        .getAllByRole("radio")
        .filter((r) => (r as HTMLInputElement).name === "deliveryMethod"),
    ).toHaveLength(1);
    expect(
      (
        screen.getByRole("radio", {
          name: en.orders.sellerDelivery,
        }) as HTMLInputElement
      ).checked,
    ).toBe(true);
  });
});

describe("OrderStepForm", () => {
  it("asks for the delivery person when sending out", () => {
    render(
      <OrderStepForm
        step="OUT_FOR_DELIVERY"
        askReason={false}
        askDeliveryPerson
        labels={en.orders}
        errors={en.business.errors}
        action={noop}
      />,
    );
    expect(
      (screen.getByLabelText(en.orders.deliveryPerson) as HTMLInputElement)
        .required,
    ).toBe(true);
    expect(
      (screen.getByLabelText(en.orders.deliveryPhone) as HTMLInputElement)
        .required,
    ).toBe(true);
    expect(
      (
        screen.getByRole("button", {
          name: en.orders.actions.OUT_FOR_DELIVERY,
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(false);
  });
});

describe("order parts", () => {
  const order: Order = {
    id: "o1",
    viewerSide: "BUYER",
    status: "PLACED",
    paymentMethod: "COD",
    paymentStatus: "PENDING",
    deliveryMethod: "SELLER_DELIVERY",
    deliveryAddress: "4-1 Market Street",
    deliveryPincode: "522001",
    pickupCity: "Guntur",
    pickupState: "Andhra Pradesh",
    buyerName: "Corner Kirana",
    sellerName: null,
    items: [
      {
        listingId: "l1",
        productLine: "Soap 100 g",
        quantity: 30,
        unit: "PIECE",
        unitPricePaise: 4000,
        gstRateBp: 1800,
        linePaise: 120000,
        gstPaise: 21600,
      },
    ],
    itemsPaise: 120000,
    gstPaise: 21600,
    deliveryPaise: 5000,
    totalPaise: 146600,
    statusReason: null,
    deliveryPersonName: null,
    deliveryPersonPhone: null,
    placedAt: "2026-11-09T05:00:00Z",
    updatedAt: "2026-11-09T05:00:00Z",
    completedAt: null,
    allowedActions: ["CANCEL"],
  };

  it("shows the API's totals and hides a confidential seller", () => {
    render(
      <OrderTotals
        order={order}
        lang="en"
        labels={en.orders}
        units={en.labels.stockUnitsShort}
      />,
    );
    expect(screen.getByText("₹1,466.00")).toBeTruthy();
    expect(screen.getByText("₹216.00")).toBeTruthy();
    expect(counterparty(order, en.orders)).toBe(en.orders.confidentialSeller);
    expect(counterparty({ ...order, viewerSide: "SELLER" }, en.orders)).toBe(
      "Corner Kirana",
    );
  });
});

describe("paying online", () => {
  const props = {
    formId: "f",
    city: "Guntur",
    pincodes: ["522001"],
    idempotencyKey: "0b3c2f4e-4a7d-4c1e-9a55-2f8a7f1c9d10",
    submitLabel: en.orders.placeOrder,
    labels: en.orders,
    errors: en.business.errors,
    action: noop,
  };

  it("offers online next to the offline way, and only online where COD is not taken", () => {
    render(
      <DeliveryChoice
        {...props}
        pickupAvailable
        deliveryAvailable
        codAvailable={false}
        onlineAvailable
      />,
    );
    expect(
      screen.getByRole("radio", { name: en.orders.payOffline.PICKUP }),
    ).toBeTruthy();
    fireEvent.click(
      screen.getByRole("radio", { name: en.orders.sellerDelivery }),
    );
    expect(
      screen.queryByRole("radio", {
        name: en.orders.payOffline.SELLER_DELIVERY,
      }),
    ).toBeNull();
    expect(
      (
        screen.getByRole("radio", {
          name: en.orders.payOnline,
        }) as HTMLInputElement
      ).checked,
    ).toBe(true);
    expect(screen.getByText(en.orders.payNote)).toBeTruthy();
  });

  it("works out the payment method and who can pay", () => {
    expect(paymentChoice("PICKUP", false)).toBe("PAY_ON_PICKUP");
    expect(paymentChoice("SELLER_DELIVERY", false)).toBe("COD");
    expect(paymentChoice("PICKUP", true)).toBe("ONLINE");
    const order = {
      viewerSide: "BUYER",
      paymentMethod: "ONLINE" as const,
      paymentStatus: "PENDING",
      status: "PLACED",
    };
    expect(canPayNow(order)).toBe(true);
    expect(canPayNow({ ...order, viewerSide: "SELLER" })).toBe(false);
    expect(canPayNow({ ...order, paymentStatus: "PAID" })).toBe(false);
    expect(canPayNow({ ...order, paymentMethod: "COD" })).toBe(false);
  });

  it("says so when payment cannot start, without opening Razorpay", async () => {
    render(
      <PayOnline
        amountLabel="Pay ₹100.00 online"
        siteName="B2B Marketplace"
        labels={en.orders}
        errors={en.business.errors}
        start={async () => ({ ok: false, error: "paymentUnavailable" })}
        confirm={noop}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Pay ₹100.00 online" }));
    expect(
      await screen.findByText(en.business.errors.paymentUnavailable),
    ).toBeTruthy();
    expect(document.querySelector("script[src*='razorpay']")).toBeNull();
  });
});

describe("payout wording", () => {
  it("names every payout status and business type in all three languages", () => {
    for (const dict of [en, te, hi]) {
      for (const key of Object.keys(en.payouts.statuses))
        expect(
          dict.payouts.statuses[key as keyof typeof en.payouts.statuses],
          key,
        ).toBeTruthy();
      for (const key of Object.keys(en.orders.share))
        expect(
          dict.orders.share[key as keyof typeof en.orders.share],
          key,
        ).toBeTruthy();
    }
  });
});
