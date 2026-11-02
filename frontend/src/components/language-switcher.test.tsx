import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LanguageSwitcher, pathForLocale } from "./language-switcher";

const navigation = vi.hoisted(() => ({ pathname: "/te" }));
vi.mock("next/navigation", () => ({ usePathname: () => navigation.pathname }));

afterEach(cleanup);

describe("LanguageSwitcher", () => {
  it("lists every language in its own script and marks the current one", () => {
    navigation.pathname = "/te";
    render(<LanguageSwitcher current="te" label="Language" />);

    const telugu = screen.getByRole("link", { name: "తెలుగు" });
    expect(telugu.getAttribute("aria-current")).toBe("page");
    expect(telugu.getAttribute("href")).toBe("/te");

    expect(screen.getByRole("link", { name: "English" }).getAttribute("aria-current")).toBeNull();
    expect(screen.getByRole("link", { name: "हिन्दी" }).getAttribute("href")).toBe("/hi");
    expect(screen.getByRole("navigation", { name: "Language" })).toBeTruthy();
  });

  it("keeps the visitor on the same page when switching language", () => {
    navigation.pathname = "/en/sell/new";
    render(<LanguageSwitcher current="en" label="Language" />);

    expect(screen.getByRole("link", { name: "తెలుగు" }).getAttribute("href")).toBe("/te/sell/new");
  });
});

describe("pathForLocale", () => {
  it("swaps only the leading locale segment", () => {
    expect(pathForLocale("/en/sell", "en", "hi")).toBe("/hi/sell");
    expect(pathForLocale("/en", "en", "te")).toBe("/te");
    expect(pathForLocale("/en/tender", "en", "te")).toBe("/te/tender");
  });

  it("falls back to the language home for unexpected paths", () => {
    expect(pathForLocale(null, "en", "te")).toBe("/te");
    expect(pathForLocale("/english", "en", "te")).toBe("/te");
  });
});
