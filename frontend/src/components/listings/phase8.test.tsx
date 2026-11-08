import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import en from "@/i18n/dictionaries/en.json";
import hi from "@/i18n/dictionaries/hi.json";
import te from "@/i18n/dictionaries/te.json";
import { ApproveForm } from "@/components/review/approve-form";
import { LABEL_FLAGS, type BatchSummary } from "@/lib/api/types";
import { LabelCheckPanel } from "./label-check";
import { PassportTimeline } from "./passport-timeline";

afterEach(cleanup);

const batch: BatchSummary = {
  id: "b1",
  variant: {
    variantId: "v1",
    productId: "p1",
    categoryId: "c1",
    brand: "Lifebuoy",
    productName: "Soap",
    packSize: 100,
    packUnit: "G",
  },
  batchNumber: "B23-0456",
  expiresOn: "2027-03-31",
  dateType: "EXPIRY",
  quantity: 500,
  quantityUnit: "PIECE",
  packsPerCase: null,
  mrpPaise: 4500,
  status: "DRAFT",
};

describe("flag and event wording", () => {
  it("exists for every flag and passport event in all three languages", () => {
    for (const dict of [en, te, hi]) {
      for (const flag of LABEL_FLAGS)
        expect(dict.labels.flags[flag], flag).toBeTruthy();
      expect(Object.keys(dict.labels.events)).toEqual(
        Object.keys(en.labels.events),
      );
    }
  });
});

describe("LabelCheckPanel", () => {
  it("puts what was typed next to what the photo seems to show, as an aid not a certificate", () => {
    render(
      <LabelCheckPanel
        label={{
          outcome: "READ",
          reason: null,
          printedDate: "EXP 04/2027",
          dateType: "EXPIRY",
          expiry: "2027-04",
          batchNumber: "B23-0456",
          mrpPaise: 4500,
          readAt: "2026-11-08T10:00:00Z",
        }}
        batch={batch}
        lang="en"
        labels={en.listings}
        enums={en.labels}
        typedLabel={en.review.typed}
        readLabel={en.review.read}
      />,
    );
    expect(screen.getByText("“EXP 04/2027”")).toBeTruthy();
    const rows = screen.getAllByRole("row");
    expect(rows[1].textContent).toContain("31 Mar 2027");
    expect(rows[1].textContent).toContain("Apr 2027");
    expect(screen.getByText(en.listings.labelNote)).toBeTruthy();
  });

  it("says when the photo is still being read, or could not be checked", () => {
    const { rerender } = render(
      <LabelCheckPanel
        label={null}
        batch={batch}
        lang="en"
        labels={en.listings}
        enums={en.labels}
        typedLabel="T"
        readLabel="R"
      />,
    );
    expect(screen.getByText(en.listings.labelPending)).toBeTruthy();
    rerender(
      <LabelCheckPanel
        label={{
          outcome: "NOT_CHECKED",
          reason: "TIMEOUT",
          printedDate: null,
          dateType: null,
          expiry: null,
          batchNumber: null,
          mrpPaise: null,
          readAt: "2026-11-08T10:00:00Z",
        }}
        batch={batch}
        lang="en"
        labels={en.listings}
        enums={en.labels}
        typedLabel="T"
        readLabel="R"
      />,
    );
    expect(screen.getByText(en.labels.labelOutcomes.NOT_CHECKED)).toBeTruthy();
    expect(screen.queryByRole("table")).toBeNull();
  });
});

describe("PassportTimeline", () => {
  it("words each event and rule result, and says when all rules passed", () => {
    render(
      <PassportTimeline
        passport={{
          entries: [
            { at: "2026-11-08T09:00:00Z", kind: "batch.created", codes: [] },
            {
              at: "2026-11-08T09:05:00Z",
              kind: "check.SUBMIT",
              codes: ["LABEL_DATE_MISMATCH"],
            },
            { at: "2026-11-08T09:10:00Z", kind: "check.APPROVE", codes: [] },
          ],
        }}
        lang="en"
        labels={en.listings}
        enums={en.labels}
      />,
    );
    expect(screen.getByText(en.labels.events["batch.created"])).toBeTruthy();
    expect(screen.getByText(en.labels.flags.LABEL_DATE_MISMATCH)).toBeTruthy();
    expect(screen.getByText(en.listings.allRulesPassed)).toBeTruthy();
  });
});

describe("ApproveForm", () => {
  it("requires the reviewer's own confirmation only when there are flags", () => {
    const { rerender } = render(
      <ApproveForm
        labels={en.review}
        errors={en.business.errors}
        needsConfirmation
        disabled={false}
        action={async () => ({})}
      />,
    );
    const box = screen.getByRole("checkbox", { name: en.review.confirmLabel });
    expect(box).toHaveProperty("required", true);
    rerender(
      <ApproveForm
        labels={en.review}
        errors={en.business.errors}
        needsConfirmation={false}
        disabled={false}
        action={async () => ({})}
      />,
    );
    expect(screen.queryByRole("checkbox")).toBeNull();
  });
});
