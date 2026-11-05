import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import en from "@/i18n/dictionaries/en.json";
import { ApiError } from "@/lib/api/backend";
import { toFormState } from "@/lib/actions/form-errors";
import { BusinessForm } from "@/components/business/business-form";
import { InviteForm } from "@/components/business/invite-form";
import { WarehouseForm } from "@/components/warehouses/warehouse-form";
import { FormMessage } from "./form-message";

afterEach(cleanup);

const noAction = async () => ({});

describe("FormMessage", () => {
  it("announces errors as alerts in the reader's language", () => {
    render(<FormMessage state={{ error: "gstinTaken" }} errors={en.business.errors} />);
    expect(screen.getByRole("alert").textContent).toBe(en.business.errors.gstinTaken);
  });

  it("announces success politely and shows nothing before a submit", () => {
    const { rerender } = render(<FormMessage state={{}} errors={en.business.errors} success="Saved" />);
    expect(screen.queryByRole("status")).toBeNull();
    rerender(<FormMessage state={{ ok: true }} errors={en.business.errors} success="Saved" />);
    expect(screen.getByRole("status").textContent).toBe("Saved");
  });
});

describe("toFormState", () => {
  it.each([
    [400, "invalid"],
    [403, "notAllowed"],
    [409, "gstinTaken"],
    [500, "generic"],
  ])("maps API status %i to %s", (status, key) => {
    expect(toFormState(new ApiError(status, "detail"), "gstinTaken")).toEqual({ error: key });
  });

  it("rethrows anything that is not an API error, such as a redirect", () => {
    const redirect = new Error("NEXT_REDIRECT");
    expect(() => toFormState(redirect, "generic")).toThrow(redirect);
  });
});

describe("BusinessForm", () => {
  it("labels every field and prefills when editing", () => {
    render(
      <BusinessForm
        labels={en.business}
        action={noAction}
        submitLabel="Save"
        initial={{
          id: "1",
          legalName: "Asha Stores",
          tradeName: null,
          businessType: "KIRANA",
          gstin: null,
          city: "Guntur",
          state: "Andhra Pradesh",
          pincode: "522001",
          verificationStatus: "UNVERIFIED",
          yourRole: "OWNER",
        }}
      />,
    );

    expect(screen.getByLabelText<HTMLInputElement>(en.business.fields.legalName).value).toBe("Asha Stores");
    expect(screen.getByLabelText<HTMLSelectElement>(en.business.fields.businessType).value).toBe("KIRANA");
    const pincode = screen.getByLabelText<HTMLInputElement>(en.business.fields.pincode);
    expect(pincode.required).toBe(true);
    expect(pincode.pattern).toBe("[1-9][0-9]{5}");
  });
});

describe("InviteForm", () => {
  it("offers only manager and staff roles", () => {
    render(<InviteForm labels={en.business} roles={en.account.roles} action={noAction} />);

    const roles = screen.getByLabelText<HTMLSelectElement>(en.business.inviteRole);
    expect([...roles.options].map((o) => o.value)).toEqual(["STAFF", "MANAGER"]);
    expect(screen.getByLabelText<HTMLInputElement>(en.business.inviteEmail).type).toBe("email");
  });
});

describe("WarehouseForm", () => {
  it("labels every field and asks for a 6-digit PIN code", () => {
    render(<WarehouseForm labels={en.warehouses} errors={en.business.errors} action={noAction} />);

    for (const label of [
      en.warehouses.fields.name,
      en.warehouses.fields.addressLine,
      en.warehouses.fields.city,
      en.warehouses.fields.state,
    ]) {
      expect(screen.getByLabelText(label, { exact: false })).toHaveProperty("required", true);
    }
    const pincode = screen.getByLabelText(en.warehouses.fields.pincode, { exact: false });
    expect(pincode.getAttribute("pattern")).toBe("[1-9][0-9]{5}");
    expect(pincode.getAttribute("inputmode")).toBe("numeric");
    expect(screen.getByRole("button", { name: en.warehouses.add })).toBeTruthy();
  });

  it("shows the duplicate-name error in the reader's language", () => {
    render(
      <FormMessage state={{ error: "warehouseNameTaken" }} errors={en.business.errors} />,
    );
    expect(screen.getByRole("alert").textContent).toBe(en.business.errors.warehouseNameTaken);
  });
});
