import { describe, expect, it } from "vitest";
import { defaultLocale, hasLocale, negotiateLocale } from "./config";

describe("hasLocale", () => {
  it("accepts supported locales only", () => {
    expect(hasLocale("en")).toBe(true);
    expect(hasLocale("te")).toBe(true);
    expect(hasLocale("hi")).toBe(true);
    expect(hasLocale("fr")).toBe(false);
    expect(hasLocale(undefined)).toBe(false);
  });
});

describe("negotiateLocale", () => {
  it("prefers the saved cookie over the browser header", () => {
    expect(negotiateLocale("hi-IN,hi;q=0.9", "te")).toBe("te");
  });

  it("ignores an unsupported cookie value", () => {
    expect(negotiateLocale("hi-IN", "xx")).toBe("hi");
  });

  it("matches a regional tag to its base language", () => {
    expect(negotiateLocale("te-IN,en;q=0.5")).toBe("te");
  });

  it("respects q-weights rather than header order", () => {
    expect(negotiateLocale("en;q=0.3,hi;q=0.8")).toBe("hi");
  });

  it("skips unsupported languages", () => {
    expect(negotiateLocale("ta-IN,kn;q=0.9,hi;q=0.5")).toBe("hi");
  });

  it("falls back to the default", () => {
    expect(negotiateLocale(null)).toBe(defaultLocale);
    expect(negotiateLocale("fr-FR,de;q=0.8")).toBe(defaultLocale);
    expect(negotiateLocale("hi;q=0")).toBe(defaultLocale);
  });
});
