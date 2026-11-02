import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { LanguageSwitcher } from "./language-switcher";

afterEach(cleanup);

describe("LanguageSwitcher", () => {
  it("lists every language in its own script and marks the current one", () => {
    render(<LanguageSwitcher current="te" label="Language" />);

    const telugu = screen.getByRole("link", { name: "తెలుగు" });
    expect(telugu.getAttribute("aria-current")).toBe("page");
    expect(telugu.getAttribute("href")).toBe("/te");

    expect(screen.getByRole("link", { name: "English" }).getAttribute("aria-current")).toBeNull();
    expect(screen.getByRole("link", { name: "हिन्दी" }).getAttribute("href")).toBe("/hi");
    expect(screen.getByRole("navigation", { name: "Language" })).toBeTruthy();
  });
});
