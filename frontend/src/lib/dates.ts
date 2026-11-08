/** Today's date in India as "YYYY-MM-DD" (printed dates are judged by the calendar in India). */
export function todayInIndia(now: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(
    now,
  );
}

/** Whole days from today (in India) to a calendar date "YYYY-MM-DD"; negative when past. */
export function daysUntilInIndia(
  isoDate: string,
  now: Date = new Date(),
): number {
  const today = Date.parse(`${todayInIndia(now)}T00:00:00Z`);
  return Math.round((Date.parse(`${isoDate}T00:00:00Z`) - today) / 86_400_000);
}
