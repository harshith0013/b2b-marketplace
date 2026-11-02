import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { Dialog } from "./dialog";

// jsdom does not implement the modal methods of <dialog>.
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

function renderDialog(open: boolean, onClose = vi.fn()) {
  const utils = render(
    <Dialog open={open} onClose={onClose} title="Shopping assistant" closeLabel="Close">
      <p>Coming soon</p>
    </Dialog>,
  );
  return { ...utils, onClose };
}

describe("Dialog", () => {
  it("opens as a modal labelled by its title", () => {
    renderDialog(true);

    expect(screen.getByRole("dialog", { name: "Shopping assistant" })).toBeTruthy();
    expect(screen.getByText("Coming soon")).toBeTruthy();
  });

  it("stays closed until asked to open", () => {
    renderDialog(false);

    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("asks to close from the close button", () => {
    const { onClose } = renderDialog(true);

    fireEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(onClose).toHaveBeenCalled();
  });

  it("closes when the open prop turns false", () => {
    const { rerender } = renderDialog(true);

    rerender(
      <Dialog open={false} onClose={vi.fn()} title="Shopping assistant" closeLabel="Close">
        <p>Coming soon</p>
      </Dialog>,
    );
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
