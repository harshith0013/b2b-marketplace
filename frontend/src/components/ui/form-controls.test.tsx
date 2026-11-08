import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Input } from "./input";
import { Select } from "./select";

afterEach(cleanup);

describe("Input", () => {
  it("is labelled and described by its hint", () => {
    render(
      <Input id="name" label="Product name" hint="As printed on the pack" />,
    );

    const input = screen.getByLabelText("Product name");
    expect(input.getAttribute("aria-describedby")).toBe("name-hint");
    expect(screen.getByText("As printed on the pack").id).toBe("name-hint");
    expect(input.getAttribute("aria-invalid")).toBeNull();
  });

  it("marks itself invalid and points to the error text", () => {
    render(
      <Input
        id="qty"
        label="Quantity"
        hint="Whole boxes"
        error="Enter a number"
      />,
    );

    const input = screen.getByLabelText("Quantity");
    expect(input.getAttribute("aria-invalid")).toBe("true");
    expect(input.getAttribute("aria-describedby")).toBe("qty-hint qty-error");
  });
});

describe("Select", () => {
  const options = [
    { value: "boxes", label: "Boxes" },
    { value: "kg", label: "Kilograms" },
  ];

  it("renders an empty placeholder first so nothing is chosen by accident", () => {
    render(
      <Select
        id="unit"
        label="Unit"
        placeholder="Choose a unit"
        options={options}
      />,
    );

    const select = screen.getByLabelText<HTMLSelectElement>("Unit");
    expect(select.value).toBe("");
    expect([...select.options].map((option) => option.text)).toEqual([
      "Choose a unit",
      "Boxes",
      "Kilograms",
    ]);
  });

  it("omits the placeholder when none is given", () => {
    render(<Select id="unit" label="Unit" options={options} />);

    expect(
      screen.getByLabelText<HTMLSelectElement>("Unit").options,
    ).toHaveLength(2);
  });
});
