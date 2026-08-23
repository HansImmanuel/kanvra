import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import BoardSwitcher from "./BoardSwitcher";
import type { BoardRef } from "@/types";

const boards: BoardRef[] = [
  { id: 1, projectId: 10, name: "Sprint 1" },
  { id: 2, projectId: 10, name: "Backlog" }
];

describe("BoardSwitcher", () => {
  it("renders board chips with the active one marked", () => {
    render(<BoardSwitcher boards={boards} activeId={2} onSelect={() => {}} onCreate={() => {}} />);

    expect(screen.getByRole("button", { name: "Sprint 1" })).toHaveAttribute("aria-current", "false");
    expect(screen.getByRole("button", { name: "Backlog" })).toHaveAttribute("aria-current", "true");
  });

  it("fires onSelect when a board chip is clicked", () => {
    const onSelect = vi.fn();
    render(<BoardSwitcher boards={boards} activeId={1} onSelect={onSelect} onCreate={() => {}} />);

    fireEvent.click(screen.getByRole("button", { name: "Backlog" }));
    expect(onSelect).toHaveBeenCalledWith(2);
  });

  it("creates a board through the inline form", () => {
    const onCreate = vi.fn();
    render(<BoardSwitcher boards={boards} activeId={1} onSelect={() => {}} onCreate={onCreate} />);

    fireEvent.click(screen.getByRole("button", { name: /\+ New board/i }));
    fireEvent.change(screen.getByLabelText("New board name"), {
      target: { value: "Sprint 42" }
    });
    fireEvent.click(screen.getByRole("button", { name: "Create" }));

    expect(onCreate).toHaveBeenCalledWith("Sprint 42");
  });
});