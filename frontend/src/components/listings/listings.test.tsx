import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import en from "@/i18n/dictionaries/en.json";
import hi from "@/i18n/dictionaries/hi.json";
import te from "@/i18n/dictionaries/te.json";
import { ELIGIBILITY_PROBLEMS, PHOTO_KINDS } from "@/lib/api/types";
import { ListingWizard } from "./listing-wizard";
import { PhotoGrid } from "./photo-grid";
import { ProblemList } from "./problem-list";

afterEach(cleanup);

const wizardProps = {
  labels: en.wizard,
  enums: en.labels,
  errors: en.business.errors,
  common: en.common,
  categories: [{ value: "c1", label: "Personal care › Bath soaps" }],
  variants: [],
  warehouses: [{ value: "w1", label: "Main, Guntur" }],
  addWarehouseHref: "/en/business/1/warehouses",
  action: async () => ({}),
};

describe("ListingWizard", () => {
  it("starts with a new product when the business has none, and checks before moving on", () => {
    render(<ListingWizard {...wizardProps} />);

    expect(
      screen.getByRole("radio", { name: en.wizard.product.new }),
    ).toHaveProperty("checked", true);
    expect(
      screen.getByRole("radio", { name: en.wizard.product.existing }),
    ).toHaveProperty("disabled", true);

    fireEvent.click(
      screen.getByRole("button", { name: new RegExp(en.common.next) }),
    );

    expect(screen.getByRole("heading", { level: 2 }).textContent).toBe(
      en.wizard.steps.product,
    );
    expect(
      screen.getAllByText(en.wizard.required).length,
    ).toBeGreaterThanOrEqual(3);
  });

  it("moves to the batch step once the product is filled in", () => {
    render(<ListingWizard {...wizardProps} />);
    fireEvent.change(screen.getByLabelText(en.wizard.product.category), {
      target: { value: "c1" },
    });
    fireEvent.change(screen.getByLabelText(en.wizard.product.brand), {
      target: { value: "Lifebuoy" },
    });
    fireEvent.change(screen.getByLabelText(en.wizard.product.name), {
      target: { value: "Soap" },
    });
    fireEvent.change(screen.getByLabelText(en.wizard.product.packSize), {
      target: { value: "100" },
    });

    fireEvent.click(
      screen.getByRole("button", { name: new RegExp(en.common.next) }),
    );

    expect(screen.getByRole("heading", { level: 2 }).textContent).toBe(
      en.wizard.steps.batch,
    );
    expect(screen.getByLabelText(en.wizard.batch.mrp)).toBeTruthy();
  });

  it("offers a link to add a warehouse when there is none", () => {
    render(
      <ListingWizard
        {...wizardProps}
        variants={[{ value: "v1", label: "Soap, 100 g" }]}
        warehouses={[]}
      />,
    );
    fireEvent.change(screen.getByLabelText(en.wizard.product.choose), {
      target: { value: "v1" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: new RegExp(en.common.next) }),
    );

    expect(
      screen
        .getByRole("link", { name: en.wizard.batch.addWarehouse })
        .getAttribute("href"),
    ).toBe("/en/business/1/warehouses");
  });
});

describe("ProblemList", () => {
  it("has a message for every check code in all three languages", () => {
    for (const dict of [en, te, hi]) {
      for (const code of ELIGIBILITY_PROBLEMS) {
        expect(dict.labels.problems[code], `${code}`).toBeTruthy();
      }
      for (const kind of PHOTO_KINDS)
        expect(dict.labels.photoKinds[kind]).toBeTruthy();
    }
  });

  it("lists each open check, or says all is well", () => {
    const { rerender } = render(
      <ProblemList
        problems={["PACK_PHOTO_MISSING"]}
        messages={en.labels.problems}
        allGood="ok"
      />,
    );
    expect(
      screen.getByText(en.labels.problems.PACK_PHOTO_MISSING),
    ).toBeTruthy();
    rerender(
      <ProblemList
        problems={[]}
        messages={en.labels.problems}
        allGood="All good"
      />,
    );
    expect(screen.getByText("All good")).toBeTruthy();
  });
});

describe("PhotoGrid", () => {
  const photo = {
    id: "p1",
    kind: "LABEL_DATE" as const,
    contentType: "image/jpeg",
    width: 300,
    height: 400,
    sizeBytes: 1000,
    uploadedAt: "2026-11-07T10:00:00Z",
  };

  it("shows photos through this site's route with a descriptive alt text", () => {
    render(
      <PhotoGrid
        photos={[photo]}
        srcOf={(p) => `/api/photos/b/x/${p.id}`}
        labels={en.listings}
        kinds={en.labels.photoKinds}
      />,
    );
    const image = screen.getByRole("img");
    expect(image.getAttribute("src")).toBe("/api/photos/b/x/p1");
    expect(image.getAttribute("alt")).toContain(
      en.labels.photoKinds.LABEL_DATE,
    );
    expect(screen.queryByRole("button")).toBeNull(); // no delete without permission
  });
});
