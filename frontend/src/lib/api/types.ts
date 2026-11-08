/** Shapes of the backend API's JSON, mirroring the Java records. */

export type BusinessRole = "OWNER" | "MANAGER" | "STAFF";

export const BUSINESS_TYPES = [
  "KIRANA",
  "WHOLESALER",
  "DISTRIBUTOR",
  "MANUFACTURER",
  "RETAILER",
  "OTHER",
] as const;
export type BusinessType = (typeof BUSINESS_TYPES)[number];

export interface UserProfile {
  id: string;
  email: string;
  displayName: string | null;
  preferredLocale: "en" | "te" | "hi";
}

export interface Membership {
  businessId: string;
  name: string;
  role: BusinessRole;
}

export interface Me {
  profile: UserProfile;
  businesses: Membership[];
  platformRoles: ("ADMIN" | "REVIEWER")[];
}

export interface BusinessDetails {
  id: string;
  legalName: string;
  tradeName: string | null;
  businessType: BusinessType;
  gstin: string | null;
  city: string;
  state: string;
  pincode: string;
  verificationStatus: string;
  yourRole: BusinessRole;
}

export interface Member {
  userId: string;
  email: string;
  displayName: string | null;
  role: BusinessRole;
}

export interface SentInvitation {
  id: string;
  email: string;
  role: BusinessRole;
  invitedAt: string;
  expiresAt: string;
}

export interface ReceivedInvitation {
  id: string;
  businessId: string;
  businessName: string;
  role: BusinessRole;
  expiresAt: string;
}

export interface Warehouse {
  id: string;
  name: string;
  addressLine: string;
  city: string;
  state: string;
  pincode: string;
  active: boolean;
}

export type PlatformRole = "ADMIN" | "REVIEWER";

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  hasNext: boolean;
}

export interface Category {
  id: string;
  parentId: string | null;
  slug: string;
  name: { en: string; te: string; hi: string };
  regulated: boolean;
  leaf: boolean;
}

export const PACK_UNITS = ["G", "KG", "ML", "L", "PIECE"] as const;
export type PackUnit = (typeof PACK_UNITS)[number];

export interface Variant {
  id: string;
  packSize: number;
  packUnit: PackUnit;
  gtin: string | null;
}

export interface Product {
  id: string;
  categoryId: string;
  brand: string;
  name: string;
  description: string | null;
  reviewStatus: "UNREVIEWED" | "APPROVED" | "REJECTED";
  variants: Variant[];
}

export interface VariantSummary {
  variantId: string;
  productId: string;
  categoryId: string;
  brand: string;
  productName: string;
  packSize: number;
  packUnit: PackUnit;
}

export const STOCK_UNITS = ["PIECE", "CASE"] as const;
export type StockUnit = (typeof STOCK_UNITS)[number];

export const DATE_TYPES = ["EXPIRY", "USE_BY", "BEST_BEFORE"] as const;
export type DateType = (typeof DATE_TYPES)[number];

export type BatchStatus = "DRAFT" | "PENDING_REVIEW" | "APPROVED" | "REJECTED";

export interface BatchSummary {
  id: string;
  variant: VariantSummary;
  batchNumber: string;
  expiresOn: string;
  dateType: DateType;
  quantity: number;
  quantityUnit: StockUnit;
  packsPerCase: number | null;
  mrpPaise: number | null;
  status: BatchStatus;
}

export const CLEARANCE_REASONS = [
  "NEAR_EXPIRY",
  "OVERSTOCK",
  "DISCONTINUED",
  "PACKAGING_CHANGE",
  "SEASON_END",
  "OTHER",
] as const;
export type ClearanceReason = (typeof CLEARANCE_REASONS)[number];

export type Visibility = "PUBLIC" | "CONFIDENTIAL";
export type ListingStatus =
  "DRAFT" | "PENDING_REVIEW" | "PUBLISHED" | "PAUSED" | "CLOSED";

export interface PriceTier {
  minQuantity: number;
  unitPricePaise: number;
}

export interface Listing {
  id: string;
  status: ListingStatus;
  clearanceReason: ClearanceReason;
  visibility: Visibility;
  moq: number;
  quantityUnit: StockUnit;
  gstRateBp: number;
  notes: string | null;
  priceTiers: PriceTier[];
  batch: BatchSummary;
  submittedAt: string | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  delivery: DeliveryOptions;
}

/** How a seller hands over a listing's stock: pickup, own delivery to PIN codes, COD. */
export interface DeliveryOptions {
  pickupAvailable: boolean;
  ownDelivery: boolean;
  deliveryPincodes: string[];
  deliveryChargePaise: number;
  codAccepted: boolean;
}

export const PHOTO_KINDS = ["PACK_FRONT", "LABEL_DATE", "OTHER"] as const;
export type PhotoKind = (typeof PHOTO_KINDS)[number];

export interface Photo {
  id: string;
  kind: PhotoKind;
  contentType: string;
  width: number;
  height: number;
  sizeBytes: number;
  uploadedAt: string;
}

export const ELIGIBILITY_PROBLEMS = [
  "SHELF_LIFE_TOO_SHORT",
  "PACK_PHOTO_MISSING",
  "LABEL_PHOTO_MISSING",
  "MRP_MISSING",
  "PRICE_ABOVE_MRP",
  "REGULATED_CATEGORY",
  "NOT_ENOUGH_STOCK",
] as const;
export type EligibilityProblem = (typeof ELIGIBILITY_PROBLEMS)[number];

export interface BusinessSummary {
  id: string;
  name: string;
  verificationStatus: string;
}

export const LABEL_FLAGS = [
  "LABEL_DATE_MISMATCH",
  "LABEL_UNREADABLE",
  "LABEL_DATE_NOT_FOUND",
  "LABEL_BATCH_NUMBER_MISMATCH",
  "LABEL_MRP_MISMATCH",
] as const;
export type LabelFlag = (typeof LABEL_FLAGS)[number];

/** What the label photo seemed to show (never a certificate). expiry: "2027-03" or "2027-03-31". */
export interface LabelCheck {
  outcome: "READ" | "UNREADABLE" | "NOT_CHECKED";
  reason: string | null;
  printedDate: string | null;
  dateType: DateType | null;
  expiry: string | null;
  batchNumber: string | null;
  mrpPaise: number | null;
  readAt: string;
}

/** problems block the listing; flags need a person to look. */
export interface Readiness {
  problems: EligibilityProblem[];
  flags: LabelFlag[];
  ruleSetVersion: string;
  label: LabelCheck | null;
}

export interface PassportEntry {
  at: string;
  kind: string;
  codes: string[];
}

export interface Passport {
  entries: PassportEntry[];
}

export interface ReviewItem {
  listing: Listing;
  seller: BusinessSummary;
  problems: EligibilityProblem[];
  flags: LabelFlag[];
}

export interface Decision {
  decision: "APPROVED" | "REJECTED";
  reason: string | null;
  decidedAt: string;
}

export interface ReviewDetails {
  listing: Listing;
  seller: BusinessSummary;
  photos: Photo[];
  problems: EligibilityProblem[];
  flags: LabelFlag[];
  label: LabelCheck | null;
  ruleSetVersion: string;
  history: Decision[];
}

/** GST slabs that apply to goods since September 2025, in basis points (1800 = 18%). */
export const GST_RATES_BP = [0, 500, 1800, 4000] as const;

export const OFFER_SORTS = ["RELEVANCE", "EXPIRY", "PRICE", "NEWEST"] as const;
export type OfferSort = (typeof OFFER_SORTS)[number];

/** A published offer as anyone may see it (no seller id; no name when confidential). */
export interface OfferSummary {
  id: string;
  brand: string;
  productName: string;
  packSize: number;
  packUnit: PackUnit;
  categoryId: string;
  dateType: DateType;
  expiresOn: string;
  daysLeft: number;
  availableQuantity: number;
  quantityUnit: StockUnit;
  packsPerCase: number | null;
  moq: number;
  unitPricePaise: number;
  pricePerPackPaise: number;
  minimumOrderValuePaise: number;
  mrpPaise: number | null;
  gstRateBp: number;
  clearanceReason: ClearanceReason;
  location: { city: string; state: string; pincode: string };
  seller: {
    name: string | null;
    confidential: boolean;
    verificationStatus: string;
  };
  coverPhotoId: string | null;
  publishedAt: string | null;
}

export interface OfferDetails {
  offer: OfferSummary;
  batchNumber: string;
  notes: string | null;
  priceTiers: PriceTier[];
  photos: { id: string; kind: PhotoKind; width: number; height: number }[];
  passport: {
    approvedAt: string | null;
    labelCheck: "MATCHES_LABEL" | "REVIEWER_CHECKED";
    detailsChangedAfterApproval: boolean;
    changedAt: string | null;
  };
  delivery: DeliveryOptions;
}

export interface OfferPage {
  items: OfferSummary[];
  page: number;
  size: number;
  hasNext: boolean;
}

export interface Place {
  state: string;
  cities: string[];
}

export const ORDER_STATUSES = [
  "PLACED",
  "ACCEPTED",
  "DECLINED",
  "CANCELLED",
  "EXPIRED",
  "READY_FOR_PICKUP",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "PICKED_UP",
  "COMPLETED",
] as const;
export type OrderStatus = (typeof ORDER_STATUSES)[number];

export const ORDER_ACTIONS = [
  "ACCEPT",
  "DECLINE",
  "CANCEL",
  "READY",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "PICKED_UP",
  "PAID",
] as const;
export type OrderAction = (typeof ORDER_ACTIONS)[number];

export type DeliveryMethod = "PICKUP" | "SELLER_DELIVERY";
export type PaymentMethod = "COD" | "PAY_ON_PICKUP";
export type OrderSide = "BUYER" | "SELLER";

/** One cart line with the offer as it is now; sellerKey groups lines without naming the seller. */
export interface CartLine {
  listingId: string;
  quantity: number;
  available: boolean;
  productLine: string;
  unit: StockUnit;
  moq: number;
  unitPricePaise: number;
  sellerKey: string;
  sellerName: string | null;
  city: string;
  pickupAvailable: boolean;
  ownDelivery: boolean;
  deliveryPincodes: string[];
  deliveryChargePaise: number;
  codAccepted: boolean;
}

export interface OrderItem {
  listingId: string;
  productLine: string;
  quantity: number;
  unit: StockUnit;
  unitPricePaise: number;
  gstRateBp: number;
  linePaise: number;
  gstPaise: number;
}

export interface Order {
  id: string;
  viewerSide: OrderSide;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: "PENDING" | "PAID";
  deliveryMethod: DeliveryMethod;
  deliveryAddress: string | null;
  deliveryPincode: string | null;
  pickupCity: string;
  pickupState: string;
  buyerName: string | null;
  sellerName: string | null;
  items: OrderItem[];
  itemsPaise: number;
  gstPaise: number;
  deliveryPaise: number;
  totalPaise: number;
  statusReason: string | null;
  deliveryPersonName: string | null;
  deliveryPersonPhone: string | null;
  placedAt: string;
  updatedAt: string;
  completedAt: string | null;
  allowedActions: OrderAction[];
}

export type QuoteStatus =
  "REQUESTED" | "OFFERED" | "DECLINED" | "ORDERED" | "EXPIRED";

export interface Quote {
  id: string;
  viewerSide: OrderSide;
  listingId: string;
  productLine: string;
  quantity: number;
  unit: StockUnit;
  note: string | null;
  status: QuoteStatus;
  offeredUnitPricePaise: number | null;
  validUntil: string | null;
  buyerName: string | null;
  sellerName: string | null;
  createdAt: string;
}
