import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import CommentsThread from "./CommentsThread";
import * as actions from "@/lib/actions";
import { currentUserSafe } from "@/lib/auth";
import { realtime, type RealtimeEvent } from "@/lib/websocket";
import type { Comment } from "@/types";

vi.mock("@/lib/actions", () => ({
  listComments: vi.fn(),
  createComment: vi.fn(),
  updateComment: vi.fn(),
  deleteComment: vi.fn()
}));
vi.mock("@/lib/auth", () => ({
  currentUserSafe: vi.fn()
}));
vi.mock("@/lib/websocket", () => ({
  realtime: {
    on: vi.fn(),
    off: vi.fn(),
    onResync: vi.fn(),
    offResync: vi.fn(),
    connect: vi.fn(),
    disconnect: vi.fn()
  },
  noteLocalEventId: vi.fn()
}));

const me = { id: 9, name: "Me", email: "me@example.com", avatarUrl: null };

const comment = (over: Partial<Comment>): Comment => ({
  id: 1,
  taskId: 5,
  author: { id: 4, name: "Jane", avatarUrl: null },
  content: "hello",
  createdAt: "2026-01-01T10:00:00Z",
  updatedAt: "2026-01-01T10:00:00Z",
  ...over
});

const janesComment = comment({ id: 1, author: { id: 4, name: "Jane", avatarUrl: null }, content: "from Jane" });
const myComment = comment({
  id: 2,
  author: { id: me.id, name: me.name, avatarUrl: null },
  content: "my own note"
});

const pageOf = (content: Comment[], totalPages = 1) => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages
});

describe("CommentsThread", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(currentUserSafe).mockResolvedValue(me);
    vi.mocked(actions.listComments).mockResolvedValue(pageOf([janesComment, myComment]));
  });

  it("renders the thread and exposes author-only controls for own comments", async () => {
    render(<CommentsThread taskId={5} />);

    expect(await screen.findByText("from Jane")).toBeInTheDocument();
    expect(screen.getByText("my own note")).toBeInTheDocument();
    // Only my comment carries Edit/Delete; Jane's must not be editable by me.
    expect(screen.getAllByRole("button", { name: "Edit" })).toHaveLength(1);
    expect(screen.getAllByRole("button", { name: "Delete" })).toHaveLength(1);
  });

  it("submits a new comment and refreshes the thread", async () => {
    vi.mocked(actions.createComment).mockResolvedValue(myComment);
    render(<CommentsThread taskId={5} />);
    await screen.findByText("from Jane");

    fireEvent.change(screen.getByLabelText("New comment"), { target: { value: "nice work" } });
    fireEvent.click(screen.getByRole("button", { name: "Comment" }));

    await waitFor(() => expect(actions.createComment).toHaveBeenCalledWith(5, "nice work"));
    await waitFor(() => expect(actions.listComments).toHaveBeenCalledTimes(2));
  });

  it("loads the next page and appends it", async () => {
    vi.mocked(actions.listComments)
      .mockResolvedValueOnce(pageOf([janesComment], 2))
      .mockResolvedValueOnce(pageOf([myComment], 2));

    render(<CommentsThread taskId={5} />);
    expect(await screen.findByText("from Jane")).toBeInTheDocument();
    expect(screen.queryByText("my own note")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Load more" }));

    expect(await screen.findByText("my own note")).toBeInTheDocument();
    expect(actions.listComments).toHaveBeenCalledWith(5, 1);
  });

  it("reloads when a COMMENT_* realtime event arrives for this task", async () => {
    render(<CommentsThread taskId={5} />);
    await screen.findByText("from Jane");
    expect(actions.listComments).toHaveBeenCalledTimes(1);

    const handler = vi.mocked(realtime.on).mock.calls.at(-1)?.[0] as (event: RealtimeEvent) => void;

    // Event for this task -> authoritative reload.
    handler({ type: "COMMENT_CREATED", eventId: "e1", projectId: 10, payload: { taskId: 5 } });
    await waitFor(() => expect(actions.listComments).toHaveBeenCalledTimes(2));

    // Event for a different task -> ignored.
    handler({ type: "COMMENT_UPDATED", eventId: "e2", projectId: 10, payload: { taskId: 99 } });
    expect(actions.listComments).toHaveBeenCalledTimes(2);
  });

  it("edits an own comment through updateComment", async () => {
    vi.mocked(actions.updateComment).mockResolvedValue(myComment);
    render(<CommentsThread taskId={5} />);

    fireEvent.click(await screen.findByRole("button", { name: "Edit" }));
    const editor = screen.getByLabelText("Edit comment") as HTMLTextAreaElement;
    expect(editor.value).toBe("my own note");
    fireEvent.change(editor, { target: { value: "updated text" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(actions.updateComment).toHaveBeenCalledWith(2, "updated text"));
  });

  it("deletes an own comment through deleteComment", async () => {
    vi.mocked(actions.deleteComment).mockResolvedValue(undefined as never);
    render(<CommentsThread taskId={5} />);

    fireEvent.click(await screen.findByRole("button", { name: "Delete" }));

    await waitFor(() => expect(actions.deleteComment).toHaveBeenCalledWith(2));
  });
});