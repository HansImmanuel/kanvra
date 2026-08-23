import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import Board from "./Board";
import * as api from "@/lib/api";
import * as actions from "@/lib/actions";
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

// TaskDetailModal fetches its own data; stub the action layer.
vi.mock("@/lib/actions", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/actions")>()),
  getTask: vi.fn().mockResolvedValue({
    id: 7,
    columnId: 1,
    title: "Fix auth",
    description: null,
    priority: null,
    assigneeId: null,
    dueDate: null,
    labelIds: [],
    position: 0,
    version: 2,
    createdAt: "2026-01-01T00:00:00Z",
    eventId: null
  }),
  // Pickers inside the modal must not hit the network in these tests.
  listMembers: vi.fn().mockResolvedValue({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
  listProjectLabels: vi.fn().mockResolvedValue([])
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

  it("opens the task detail modal when a card title is clicked", async () => {
    // restoreAllMocks() in beforeEach wipes factory-time implementations.
    vi.mocked(actions.getTask).mockResolvedValue({
      id: 7,
      columnId: 1,
      title: "Fix auth",
      description: null,
      priority: null,
      assigneeId: null,
      dueDate: null,
      labelIds: [],
      position: 0,
      version: 2,
      createdAt: "2026-01-01T00:00:00Z",
      eventId: null
    });
    vi.mocked(actions.listMembers).mockResolvedValue({
      content: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0
    });
    vi.mocked(actions.listProjectLabels).mockResolvedValue([]);
    render(<Board board={board} onReload={() => {}} />);

    fireEvent.click(screen.getByRole("button", { name: "Fix auth" }));

    const dialogEl = await screen.findByRole("dialog");
    await waitFor(() => expect(actions.getTask).toHaveBeenCalledWith(7));
    expect(dialogEl).toBeInTheDocument();
  });

  it("renders the moved card optimistically while the request is in flight", async () => {
    // Never-settling promise keeps the request in flight so we can observe the
    // optimistic layout (TECH_DOC §14 step 1).
    vi.spyOn(api, "post").mockImplementation(() => new Promise(() => {}));
    const { unmount } = render(<Board board={board} onReload={() => {}} />);

    fireEvent.click(screen.getByRole("button", { name: /move Fix auth to DONE/i }));

    // Optimistic view: the card now sits in DONE (prev column is TODO).
    expect(await screen.findByRole("button", { name: /move Fix auth to TODO/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /move Fix auth to DONE/i })).toBeNull();
    unmount();
  });

  it("rolls back on 409, adopts the server version, and surfaces a flash", async () => {
    vi.spyOn(api, "post").mockRejectedValue(
      new api.ApiError(409, "TASK_VERSION_CONFLICT", "conflict", {
        status: 409,
        code: "TASK_VERSION_CONFLICT",
        message: "conflict",
        currentState: {
          id: 7,
          columnId: 1,
          title: "Fix auth",
          description: null,
          priority: null,
          assigneeId: null,
          dueDate: null,
          labelIds: [],
          position: 0,
          version: 5,
          createdAt: "2026-01-01T00:00:00Z",
          eventId: null
        }
      })
    );
    const onReload = vi.fn();
    render(<Board board={board} onReload={onReload} />);

    fireEvent.click(screen.getByRole("button", { name: /move Fix auth to DONE/i }));

    expect(await screen.findByRole("status")).toHaveTextContent(/changed by someone else/i);
    // Card badge adopts the server's current version (SPEC §7.2).
    expect(screen.getByText(/v5/)).toBeInTheDocument();
    await waitFor(() => expect(onReload).toHaveBeenCalled());
  });

  it("surfaces non-conflict move errors as a flash and reloads", async () => {
    vi.spyOn(api, "post").mockRejectedValue(new api.ApiError(500, "INTERNAL_ERROR", "boom"));
    const onReload = vi.fn();
    render(<Board board={board} onReload={onReload} />);

    fireEvent.click(screen.getByRole("button", { name: /move Fix auth to DONE/i }));

    expect(await screen.findByRole("status")).toHaveTextContent("boom");
    await waitFor(() => expect(onReload).toHaveBeenCalled());
  });

  it("creates a column through POST /boards/{id}/columns", async () => {
    const postSpy = vi.spyOn(api, "post").mockResolvedValue({
      id: 3, boardId: 1, name: "QA", position: 2
    });
    const onReload = vi.fn();
    render(<Board board={board} onReload={onReload} />);

    fireEvent.click(screen.getByRole("button", { name: /\+ Add column/i }));
    fireEvent.change(screen.getByLabelText("New column name"), { target: { value: "QA" } });
    fireEvent.click(screen.getByRole("button", { name: "Create column" }));

    await waitFor(() =>
      expect(postSpy).toHaveBeenCalledWith("/api/v1/boards/1/columns", { name: "QA" })
    );
    await waitFor(() => expect(onReload).toHaveBeenCalled());
  });

  it("reorders columns by posting the full permutation", async () => {
    const postSpy = vi.spyOn(api, "post").mockResolvedValue([]);
    render(<Board board={board} onReload={() => {}} />);

    fireEvent.click(screen.getByRole("button", { name: /move column DONE left/i }));

    await waitFor(() =>
      expect(postSpy).toHaveBeenCalledWith("/api/v1/boards/1/columns/reorder", {
        columnIds: [2, 1]
      })
    );
  });

  it("renames a column through PATCH /columns/{id}", async () => {
    const patchSpy = vi.spyOn(api, "patch").mockResolvedValue({
      id: 2, boardId: 1, name: "Shipped", position: 1
    });
    render(<Board board={board} onReload={() => {}} />);

    fireEvent.click(screen.getByRole("button", { name: /rename column DONE/i }));
    fireEvent.change(screen.getByLabelText(/Rename column DONE/i), {
      target: { value: "Shipped" }
    });
    fireEvent.click(screen.getByRole("button", { name: "Save column name" }));

    await waitFor(() =>
      expect(patchSpy).toHaveBeenCalledWith("/api/v1/columns/2", { name: "Shipped" })
    );
  });
});