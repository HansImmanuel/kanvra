import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ActivityPanel from "./ActivityPanel";
import * as actions from "@/lib/actions";
import { realtime } from "@/lib/websocket";
import type { Activity } from "@/types";

vi.mock("@/lib/actions", () => ({
  listActivity: vi.fn()
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

const row = (id: number, message: string): Activity => ({
  id,
  projectId: 10,
  actorId: 1,
  type: "TASK_MOVED",
  message,
  createdAt: new Date().toISOString()
});

describe("ActivityPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders activity rows and loads the next page on demand", async () => {
    vi.mocked(actions.listActivity)
      .mockResolvedValueOnce({
        content: [row(1, "Hans created task 'Fix auth'")],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 2
      })
      .mockResolvedValueOnce({
        content: [row(2, "Jane moved task 'Fix auth' to DONE")],
        page: 1,
        size: 20,
        totalElements: 2,
        totalPages: 2
      });

    render(<ActivityPanel projectId={10} />);

    expect(await screen.findByText("Hans created task 'Fix auth'")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Load more" }));

    expect(await screen.findByText("Jane moved task 'Fix auth' to DONE")).toBeInTheDocument();
    expect(actions.listActivity).toHaveBeenLastCalledWith(10, 1);
  });

  it("reloads when a realtime domain event arrives", async () => {
    vi.mocked(actions.listActivity).mockResolvedValue({
      content: [row(1, "Hans created task 'Fix auth'")],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1
    });

    render(<ActivityPanel projectId={10} />);
    await screen.findByText("Hans created task 'Fix auth'");
    expect(actions.listActivity).toHaveBeenCalledTimes(1);

    const handler = vi.mocked(realtime.on).mock.calls.at(-1)?.[0] as () => void;
    handler();

    await waitFor(() => expect(actions.listActivity).toHaveBeenCalledTimes(2));
  });
});