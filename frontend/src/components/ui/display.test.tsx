import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Badge } from "./badge";
import { Card, CardDescription, CardTitle } from "./card";
import { EmptyState } from "./empty-state";
import { ErrorState } from "./error-state";
import { Skeleton } from "./skeleton";

afterEach(cleanup);

describe("Badge", () => {
  it("uses the tinted colours of its variant", () => {
    render(<Badge variant="warning">Sample data</Badge>);

    expect(screen.getByText("Sample data").className).toContain("bg-warning-soft");
  });
});

describe("Card", () => {
  it("renders a heading and description", () => {
    render(
      <Card>
        <CardTitle>Bath soap</CardTitle>
        <CardDescription>Minimum 10 boxes</CardDescription>
      </Card>,
    );

    expect(screen.getByRole("heading", { level: 3, name: "Bath soap" })).toBeTruthy();
    expect(screen.getByText("Minimum 10 boxes")).toBeTruthy();
  });
});

describe("Skeleton", () => {
  it("is hidden from assistive technology", () => {
    const { container } = render(<Skeleton className="h-6" />);

    expect(container.firstElementChild?.getAttribute("aria-hidden")).toBe("true");
  });
});

describe("EmptyState", () => {
  it("shows the title, description and action", () => {
    render(
      <EmptyState title="No listings yet" description="Stock will appear here." action={<button>Add</button>} />,
    );

    expect(screen.getByText("No listings yet")).toBeTruthy();
    expect(screen.getByText("Stock will appear here.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Add" })).toBeTruthy();
  });
});

describe("ErrorState", () => {
  it("is announced as an alert and retries on click", () => {
    const onRetry = vi.fn();
    render(<ErrorState title="Something went wrong" onRetry={onRetry} retryLabel="Try again" />);

    expect(screen.getByRole("alert").textContent).toContain("Something went wrong");
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it("has no retry button without a handler", () => {
    render(<ErrorState title="Something went wrong" retryLabel="Try again" />);

    expect(screen.queryByRole("button")).toBeNull();
  });
});
