import { describe, expect, it } from "vitest";
import {
  formatGstRate,
  formatPaise,
  paiseToRupeesInput,
  rupeesToPaise,
} from "./money";

describe("rupeesToPaise", () => {
  it.each([
    ["45", 4500],
    ["45.5", 4550],
    ["45.05", 4505],
    [" 1,250.00 ", 125000],
    ["₹ 30", 3000],
    ["0.01", 1],
  ])("reads %s as %i paise", (input, paise) => {
    expect(rupeesToPaise(input)).toBe(paise);
  });

  it.each(["", "abc", "-5", "4.555", "1e3", "12.", ".5", "1234567890"])(
    "refuses %s rather than guessing",
    (input) => {
      expect(rupeesToPaise(input)).toBeNull();
    },
  );
});

describe("display", () => {
  it("round-trips paise through the input format", () => {
    expect(paiseToRupeesInput(4505)).toBe("45.05");
    expect(rupeesToPaise(paiseToRupeesInput(123456))).toBe(123456);
  });

  it("formats rupees with the symbol and two decimals", () => {
    expect(formatPaise(4550, "en")).toBe("₹45.50");
    expect(formatPaise(12345678, "en")).toBe("₹1,23,456.78"); // Indian grouping
  });

  it("formats GST basis points as a percentage", () => {
    expect(formatGstRate(1800)).toBe("18%");
    expect(formatGstRate(0)).toBe("0%");
  });
});
