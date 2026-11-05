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
