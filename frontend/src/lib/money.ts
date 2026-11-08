/** Money is held as integer paise everywhere; rupees exist only in what people type and read. */

const RUPEES = /^(\d{1,9})(?:\.(\d{1,2}))?$/;

/**
 * "45", "45.5", "1,250.00" → paise. Null for anything else (negative, more than two decimals,
 * letters), so the form can say so instead of guessing.
 */
export function rupeesToPaise(input: string): number | null {
  const cleaned = input.trim().replaceAll(",", "").replace(/^₹\s*/, "");
  const match = RUPEES.exec(cleaned);
  if (!match) return null;
  const rupees = Number(match[1]);
  const paise = Number((match[2] ?? "").padEnd(2, "0"));
  return rupees * 100 + paise;
}

/** Paise → "45.50" for prefilling an input. */
export function paiseToRupeesInput(paise: number): string {
  return `${Math.trunc(paise / 100)}.${String(paise % 100).padStart(2, "0")}`;
}

/** Paise → "₹45.50", in the reader's language and Indian digit grouping. */
export function formatPaise(paise: number, lang: string): string {
  return new Intl.NumberFormat(`${lang}-IN`, {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 2,
  }).format(paise / 100);
}

/** 1800 → "18%". */
export function formatGstRate(basisPoints: number): string {
  return `${basisPoints / 100}%`;
}
