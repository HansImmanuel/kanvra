import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import TaskDetailModal from "./TaskDetailModal";
import * as actions from "@/lib/actions";
import { ApiError } from "@/lib/api";
import type { TaskResponse } from "@/types";

vi.mock("@/lib/actions", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/actions")>()),
  getTask: vi.fn(),
  updateTask: vi.fn(),
  // Comment-thread + picker stubs — CommentsThread and the pickers mount inside.
  listComments: vi.fn().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
  createComment: vi.fn(),
  updateComment: vi.fn(),
  deleteComment: vi.fn(),
  listMembers: vi.fn().mockResolvedValue({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
  listProjectLabels: vi.fn().mockResolvedValue([])
}));
vi.mock("@/lib/auth", () => ({
  currentUserSafe: vi.fn().mockResolvedValue(null)
}));
vi.mock("@/lib/websocket", () => ({
  noteLocalEventId: vi.fn(),
  realtime: {
    on: () => {},
    off: () => {},
    onResync: () => {},
    offResync: () => {},
    connect: () => {},
    disconnect: () => {}
  }
}));

const task = (over: Partial<TaskResponse> = {}): TaskResponse => ({
  id: 7,
  columnId: 1,
  title: "Fix auth",
  description: "original",
  priority: "HIGH",
  assigneeId: null,
  dueDate: null,
  labelIds: [1, 2],
  position: 0,
  version: 2,
  createdAt: "2026-01-01T00:00:00Z",
  eventId: null,
  ...over
});

const mockedGetTask = vi.mocked(actions.getTask);
const mockedUpdateTask = vi.mocked(actions.updateTask);

function renderModal(over: Partial<Parameters<typeof TaskDetailModal>[0]> = {}) {
  const onClose = vi.fn();
  const onChanged = vi.fn();
  render(
    <TaskDetailModal
      projectId={10}
      taskId={7}
      onClose={onClose}
      onChanged={onChanged}
      {...over}
    />
  );
  return { onClose, onChanged };
}

describe("TaskDetailModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetTask.mockResolvedValue(task());
  });

  it("loads the authoritative task into the form", async () => {
    renderModal();
    await waitFor(() => expect(mockedGetTask).toHaveBeenCalledWith(7));
    const titleInput = screen.getByLabelText(/Title/i) as HTMLInputElement;
    await waitFor(() => expect(titleInput.value).toBe("Fix auth"));
    expect((screen.getByLabelText(/Description/i) as HTMLTextAreaElement).value).toBe("original");
    expect(screen.getByText(/version 2/i)).toBeInTheDocument();
  });

  it("saves a version-gated PATCH and notifies the board", async () => {
    mockedUpdateTask.mockResolvedValue(task({ version: 3, title: "Renamed" }));
    const { onChanged } = renderModal();

    const titleInput = await screen.findByLabelText(/Title/i);
    fireEvent.change(titleInput, { target: { value: "Renamed" } });
    fireEvent.click(screen.getByRole("button", { name: /^Save$/i }));

    await waitFor(() => expect(mockedUpdateTask).toHaveBeenCalledTimes(1));
    expect(mockedUpdateTask).toHaveBeenCalledWith(7, {
      title: "Renamed",
      description: "original",
      priority: "HIGH",
      assigneeId: null,
      dueDate: null,
      // Pre-selected from the task's own labels — lossless round-trip.
      labelIds: [1, 2],
      version: 2
    });
    await waitFor(() => expect(onChanged).toHaveBeenCalled());
  });

  it("reconciles from currentState on TASK_VERSION_CONFLICT instead of staying stale", async () => {
    mockedUpdateTask.mockRejectedValue(
      new ApiError(409, "TASK_VERSION_CONFLICT", "conflict", {
        status: 409,
        code: "TASK_VERSION_CONFLICT",
        message: "conflict",
        currentState: task({ title: "Server current", version: 5 })
      })
    );
    const { onChanged } = renderModal();

    await screen.findByLabelText(/Title/i);
    fireEvent.click(screen.getByRole("button", { name: /^Save$/i }));

    // SPEC §7.2: the conflict body carries the server's current task — the modal
    // must adopt it (title + version) so the next save can succeed.
    const titleInput = await waitFor(() => {
      const el = screen.getByLabelText(/Title/i) as HTMLInputElement;
      if (el.value !== "Server current") throw new Error("not yet reconciled");
      return el;
    });
    expect(titleInput.value).toBe("Server current");
    expect(screen.getByText(/version 5/i)).toBeInTheDocument();
    expect(await screen.findByText(/changed by someone else/i)).toBeInTheDocument();
    await waitFor(() => expect(onChanged).toHaveBeenCalled());
  });

  it("close button calls onClose", async () => {
    const { onClose } = renderModal();
    await screen.findByLabelText(/Title/i);
    fireEvent.click(screen.getByRole("button", { name: /^Close$/i }));
    expect(onClose).toHaveBeenCalled();
  });
});