import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import Board from "./Board";
import * as api from "@/lib/api";
import type { BoardDetail } from "@/types";

// The real websocket module opens a STOMP socket; stub it so the board's
// realtime wiring is a no-op inside these component tests.
vi.mock("@/lib/websocket", () => ({
  realtime: {
    on: () => {},
    off: () => {},
    onResync: () => {},
    offResync: () => {},
    connect: () => {},
    disconnect: () => {}
  },
  noteLocalEventId: () => {}
}));

const board: BoardDetail = {
  id: 1,
  projectId: 10,
  name: "Sprint",
  status: "ACTIVE",
  columns: [
    {
      id: 1,
      name: "TODO",
      position: 0,
      tasks: [
        {
          id: 7,
          title: "Fix auth",
          priority: null,
          position: 0,
          version: 2,
          assignee: null,
          labels: [],
          dueDate: null,
          commentCount: 0
        }
      ]
    },
    { id: 2, name: "DONE", position: 1, tasks: [] }
  ]
};

describe("Board", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("moves a task with the card's known version and reloads on success", async () => {
    const postSpy = vi.spyOn(api, "post").mockResolvedValue({
      id: 7,
      columnId: 2,
      title: "Fix auth",
      description: null,
      priority: null,
      assigneeId: null,
      dueDate: null,
      position: 0,
      version: 3,
      createdAt: "2026-01-01T00:00:00Z",
      eventId: "evt-1"
    } as never);
    const onReload = vi.fn();

    render(<Board board={board} onReload={onReload} />);
    const moveBtn = screen.getByRole("button", { name: /move Fix auth to DONE/i });
    fireEvent.click(moveBtn);

    await waitFor(() => expect(postSpy).toHaveBeenCalled());
    expect(postSpy).toHaveBeenCalledWith("/api/v1/tasks/7/move", {
      targetColumnId: 2,
      position: 0,
      version: 2
    });
    // Success -> reload the board so local state reflects the server.
    await waitFor(() => expect(onReload).toHaveBeenCalled());
  });

  it("re-fetches the board on a TASK_VERSION_CONFLICT (409) instead of staying stuck", async () => {
    const postSpy = vi.spyOn(api, "post").mockRejectedValue(
      new api.ApiError(409, "TASK_VERSION_CONFLICT", "conflict", {
        status: 409,
        code: "TASK_VERSION_CONFLICT",
        message: "conflict"
      })
    );
    const onReload = vi.fn();

    render(<Board board={board} onReload={onReload} />);
    const moveBtn = screen.getByRole("button", { name: /move Fix auth to DONE/i });
    fireEvent.click(moveBtn);

    // SPEC §7.2 / §15: a version conflict must trigger a board re-fetch so the
    // next action uses the server's current version.
    await waitFor(() => expect(onReload).toHaveBeenCalled());
    expect(postSpy).toHaveBeenCalledWith("/api/v1/tasks/7/move", {
      targetColumnId: 2,
      position: 0,
      version: 2
    });
  });
});