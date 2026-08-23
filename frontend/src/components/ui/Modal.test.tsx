import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import Modal from "./Modal";

describe("Modal", () => {
  it("renders nothing when closed", () => {
    render(
      <Modal open={false} onClose={() => {}} title="T">
        hi
      </Modal>
    );
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("renders children + title and moves focus into the dialog when opened", () => {
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose} title="Task details">
        content
      </Modal>
    );
    const dialogEl = screen.getByRole("dialog");
    expect(dialogEl).toHaveTextContent("content");
    expect(dialogEl).toHaveAccessibleName("Task details");
    expect(document.activeElement).toBe(dialogEl);
  });

  it("closes on Escape", () => {
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose}>
        content
      </Modal>
    );
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("closes on backdrop click", () => {
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose}>
        content
      </Modal>
    );
    fireEvent.click(screen.getByTestId("modal-backdrop"));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("traps Tab between the first and last focusable elements", () => {
    render(
      <Modal open onClose={() => {}} title="T">
        <button type="button">Ok</button>
      </Modal>
    );
    const closeBtn = screen.getByRole("button", { name: "Close dialog" });
    const okBtn = screen.getByRole("button", { name: "Ok" });

    // First focusable is the close button (header, before children).
    closeBtn.focus();
    fireEvent.keyDown(document, { key: "Tab", shiftKey: true });
    expect(document.activeElement).toBe(okBtn);

    // Loop forward from the last focusable back to the first.
    fireEvent.keyDown(document, { key: "Tab" });
    expect(document.activeElement).toBe(closeBtn);
  });
});