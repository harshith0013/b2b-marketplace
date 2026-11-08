import { describe, expect, it } from "vitest";
import { daysUntilInIndia, todayInIndia } from "./dates";

describe("dates in India", () => {
  it("uses India's calendar, not UTC", () => {
    // 20:00 UTC on 5 November is already 6 November in India.
    const lateEvening = new Date("2026-11-05T20:00:00Z");
    expect(todayInIndia(lateEvening)).toBe("2026-11-06");
    expect(daysUntilInIndia("2026-12-06", lateEvening)).toBe(30);
    expect(daysUntilInIndia("2026-11-05", lateEvening)).toBe(-1);
  });
});
