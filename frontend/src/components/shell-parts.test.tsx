import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import en from "@/i18n/dictionaries/en.json";
import { AssistantDialog } from "./assistant-dialog";
import { HomeTile } from "./home-tile";
import { SearchBox } from "./search-box";
import { SiteFooter } from "./site-footer";
import { SiteHeader } from "./site-header";

vi.mock("next/navigation", () => ({ usePathname: () => "/en" }));

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    this.removeAttribute("open");
    this.dispatchEvent(new Event("close"));
  };
});

afterEach(cleanup);

describe("HomeTile", () => {
  it("links to its page when it has one", () => {
    render(<HomeTile icon={null} title="Sell" description="List stock" href="/en/sell" comingSoonLabel="Coming soon" />);

    expect(screen.getByRole("link", { name: /Sell/ }).getAttribute("href")).toBe("/en/sell");
    expect(screen.queryByText("Coming soon")).toBeNull();
  });

  it("is not a link and says coming soon when it has no page yet", () => {
    render(<HomeTile icon={null} title="My orders" description="Track orders" comingSoonLabel="Coming soon" />);

    expect(screen.queryByRole("link")).toBeNull();
    expect(screen.getByText("Coming soon")).toBeTruthy();
  });
});

describe("SearchBox", () => {
  it("is a plain search form that opens the offers page", () => {
    render(<SearchBox lang="te" labels={en.home} defaultValue="soap" />);

    const form = screen.getByRole("search");
    expect(form.getAttribute("action")).toBe("/te/offers");
    expect(form.getAttribute("method")).toBe("get");
    expect(screen.getByLabelText(en.home.searchLabel)).toHaveProperty("name", "q");
    expect(screen.getByLabelText(en.home.searchLabel)).toHaveProperty("value", "soap");
  });
});

describe("AssistantDialog", () => {
  it("opens a dialog that says what the assistant will and will not do", () => {
    render(<AssistantDialog labels={en.home.assistant} comingSoonLabel="Coming soon" closeLabel="Close" />);

    expect(screen.queryByRole("dialog")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: en.home.assistant.open }));

    const dialog = screen.getByRole("dialog", { name: en.home.assistant.title });
    expect(within(dialog).getByText(en.home.assistant.note)).toBeTruthy();

    fireEvent.click(within(dialog).getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});

describe("SiteHeader and SiteFooter", () => {
  it("link to the buyer and seller homes and the status page in the current language", () => {
    render(
      <>
        <SiteHeader lang="hi" dict={en} />
        <SiteFooter lang="hi" labels={en.footer} />
      </>,
    );

    const menu = screen.getByRole("navigation", { name: en.nav.mainMenu });
    expect(within(menu).getByRole("link", { name: en.nav.sell }).getAttribute("href")).toBe("/hi/sell");
    expect(within(menu).getByRole("link", { name: en.nav.buy }).getAttribute("href")).toBe("/hi");
    expect(screen.getByRole("link", { name: en.footer.status }).getAttribute("href")).toBe("/hi/status");
    expect(screen.getByText(en.footer.sampleNotice)).toBeTruthy();
  });
});
