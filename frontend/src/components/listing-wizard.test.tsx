import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import en from "@/i18n/dictionaries/en.json";
import { ListingWizard } from "./listing-wizard";

afterEach(cleanup);

const { wizard, common } = en;

function renderWizard() {
  return render(<ListingWizard labels={wizard} common={common} />);
}

const next = () => fireEvent.click(screen.getByRole("button", { name: common.next }));
const back = () => fireEvent.click(screen.getByRole("button", { name: common.back }));

describe("ListingWizard", () => {
  it("starts on the product step with no way back", () => {
    renderWizard();

    expect(screen.getByText("Step 1 of 5")).toBeTruthy();
    expect(screen.getByRole("heading", { level: 2, name: wizard.steps.product })).toBeTruthy();
    expect(screen.queryByRole("button", { name: common.back })).toBeNull();
    expect(screen.getByRole("listitem", { current: "step" }).textContent).toBe(wizard.steps.product);
  });

  it("walks forward and back through every step, moving focus to each heading", () => {
    renderWizard();

    next();
    const heading = screen.getByRole("heading", { level: 2, name: wizard.steps.batch });
    expect(document.activeElement).toBe(heading);
    expect(screen.getByLabelText(wizard.fields.date).getAttribute("type")).toBe("date");

    next();
    expect(screen.getByLabelText(wizard.fields.pricePerUnit)).toBeTruthy();
    next();
    expect(screen.getByText(wizard.photosTitle)).toBeTruthy();
    next();
    expect(screen.getByText("Step 5 of 5")).toBeTruthy();
    expect(screen.queryByRole("button", { name: common.next })).toBeNull();

    back();
    expect(screen.getByRole("heading", { level: 2, name: wizard.steps.photos })).toBeTruthy();
  });

  it("keeps answers between steps and shows them on the review step", () => {
    renderWizard();

    fireEvent.change(screen.getByLabelText(wizard.fields.productName), { target: { value: "Bath soap" } });
    fireEvent.change(screen.getByLabelText(wizard.fields.category), { target: { value: "personalCare" } });
    next();
    next();
    fireEvent.change(screen.getByLabelText(wizard.fields.quantity), { target: { value: "40" } });
    fireEvent.change(screen.getByLabelText(wizard.fields.unit), { target: { value: "boxes" } });
    back();
    next();
    expect(screen.getByLabelText<HTMLInputElement>(wizard.fields.quantity).value).toBe("40");
    next();
    next();

    const text = screen.getAllByRole("definition").map((dd) => dd.textContent);
    expect(text).toContain("Bath soap");
    expect(text).toContain(wizard.categories.personalCare);
    expect(text).toContain(`40 ${wizard.units.boxes}`);
    expect(text).toContain(wizard.notProvided);
  });

  it("cannot be submitted yet and says why", () => {
    renderWizard();
    for (let step = 1; step < 5; step++) next();

    const submit = screen.getByRole("button", { name: wizard.submit });
    expect(submit.hasAttribute("disabled")).toBe(true);
    expect(submit.getAttribute("aria-describedby")).toBe("listing-submit-note");
    expect(screen.getByText(wizard.submitNote).id).toBe("listing-submit-note");
  });
});
