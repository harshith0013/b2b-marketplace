import type {
  CartLine,
  DeliveryMethod,
  OrderAction,
  OrderSide,
  PaymentMethod,
} from "@/lib/api/types";

/** Cart lines of one seller: they are checked out together as one order. */
export interface SellerGroup {
  sellerKey: string;
  sellerName: string | null;
  city: string;
  lines: CartLine[];
  /** Lines still on sale; only these can be ordered. */
  orderable: CartLine[];
  pickupAvailable: boolean;
  ownDelivery: boolean;
  codAccepted: boolean;
  deliveryChargePaise: number;
  deliveryPincodes: string[];
}

/**
 * Groups cart lines by seller, keeping the cart's order. Delivery is offered only when every line
 * still on sale offers it (one order travels together); the charge is the highest of the lines.
 */
export function groupBySeller(lines: readonly CartLine[]): SellerGroup[] {
  const keys = [...new Set(lines.map((line) => line.sellerKey))];
  return keys.map((sellerKey) => {
    const mine = lines.filter((line) => line.sellerKey === sellerKey);
    const orderable = mine.filter((line) => line.available);
    const all = (test: (line: CartLine) => boolean) =>
      orderable.length > 0 && orderable.every(test);
    const pincodeSets = orderable.map((line) => new Set(line.deliveryPincodes));
    return {
      sellerKey,
      sellerName: mine[0].sellerName,
      city: mine[0].city,
      lines: mine,
      orderable,
      // One pickup is from one city.
      pickupAvailable: all(
        (line) => line.pickupAvailable && line.city === orderable[0].city,
      ),
      ownDelivery: all((line) => line.ownDelivery),
      codAccepted: all((line) => line.codAccepted),
      deliveryChargePaise: Math.max(
        0,
        ...orderable.map((line) => line.deliveryChargePaise),
      ),
      deliveryPincodes: (orderable[0]?.deliveryPincodes ?? []).filter((pin) =>
        pincodeSets.every((set) => set.has(pin)),
      ),
    };
  });
}

/** How payment follows from delivery: cash to the delivery person, or at the warehouse. */
export function paymentFor(method: DeliveryMethod): PaymentMethod {
  return method === "PICKUP" ? "PAY_ON_PICKUP" : "COD";
}

const PINCODE = /^[1-9][0-9]{5}$/;

/** "522001, 522002 522003" → the PIN codes, without repeats; null if any is not a PIN code. */
export function parsePincodes(input: string): string[] | null {
  const parts = input
    .split(/[\s,]+/)
    .map((part) => part.trim())
    .filter(Boolean);
  if (!parts.every((part) => PINCODE.test(part))) return null;
  return [...new Set(parts)];
}

/** OUT_FOR_DELIVERY → "out-for-delivery", the API's path for that step. */
export function actionPath(action: OrderAction): string {
  return action.toLowerCase().replaceAll("_", "-");
}

/** Steps that need the seller to say why: the buyer reads it. */
export const NEEDS_REASON: readonly OrderAction[] = ["DECLINE"];

/** The step that needs the delivery person's name and phone. */
export const NEEDS_DELIVERY_PERSON: readonly OrderAction[] = [
  "OUT_FOR_DELIVERY",
];

export function needsReason(action: OrderAction, side: OrderSide): boolean {
  return (
    NEEDS_REASON.includes(action) || (action === "CANCEL" && side === "SELLER")
  );
}

export function isSide(value: unknown): value is OrderSide {
  return value === "BUYER" || value === "SELLER";
}
