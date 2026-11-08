import { describe, expect, it } from "vitest";
import { format } from "./format";

describe("format", () => {
  it("fills placeholders in any word order", () => {
    expect(format("Step {current} of {total}", { current: 2, total: 5 })).toBe(
      "Step 2 of 5",
    );
    expect(format("{total} లో దశ {current}", { current: 2, total: 5 })).toBe(
      "5 లో దశ 2",
    );
  });

  it("leaves unknown placeholders untouched", () => {
    expect(format("Hello {name}", {})).toBe("Hello {name}");
  });
});
