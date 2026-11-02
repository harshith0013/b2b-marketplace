import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TOAST_DURATION_MS, ToastProvider, useToast } from "./toast";

function ShowButton() {
  const showToast = useToast();
  return <button onClick={() => showToast("Search opens soon")}>Show</button>;
}

function renderWithProvider() {
  return render(
    <ToastProvider dismissLabel="Dismiss message">
      <ShowButton />
    </ToastProvider>,
  );
}

beforeEach(() => vi.useFakeTimers());
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe("Toast", () => {
  it("announces the message in a polite live region", () => {
    renderWithProvider();

    fireEvent.click(screen.getByRole("button", { name: "Show" }));
    const region = screen.getByRole("status");
    expect(region.getAttribute("aria-live")).toBe("polite");
    expect(region.textContent).toContain("Search opens soon");
  });

  it("disappears on its own", () => {
    renderWithProvider();

    fireEvent.click(screen.getByRole("button", { name: "Show" }));
    act(() => vi.advanceTimersByTime(TOAST_DURATION_MS));
    expect(screen.queryByText("Search opens soon")).toBeNull();
  });

  it("can be dismissed early", () => {
    renderWithProvider();

    fireEvent.click(screen.getByRole("button", { name: "Show" }));
    fireEvent.click(screen.getByRole("button", { name: "Dismiss message" }));
    expect(screen.queryByText("Search opens soon")).toBeNull();
  });

  it("refuses to work outside the provider", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => render(<ShowButton />)).toThrow(/ToastProvider/);
  });
});
