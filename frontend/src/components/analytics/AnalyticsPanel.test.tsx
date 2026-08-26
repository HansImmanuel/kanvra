import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import AnalyticsPanel from "./AnalyticsPanel";
import * as actions from "@/lib/actions";
import { realtime } from "@/lib/websocket";
import type { ProjectAnalytics } from "@/types";

vi.mock("@/lib/actions", () => ({
  getProjectAnalytics: vi.fn()
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

const analytics: ProjectAnalytics = {
  projectId: 10,
  counters: {
    tasksCreated: 42,
    tasksCompleted: 17,
    tasksMoved: 31,
    tasksDeleted: 3,
    commentsCreated: 58
  },
  cardsPerColumn: [
    { columnId: 1, columnName: "TODO", position: 0, count: 20 },
    { columnId: 2, columnName: "IN PROGRESS", position: 1, count: 5 },
    { columnId: 3, columnName: "DONE", position: 2, count: 17 }
  ]
};

describe("AnalyticsPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(actions.getProjectAnalytics).mockResolvedValue(analytics);
  });

  it("renders counters and cards-per-column after expanding", async () => {
    render(<AnalyticsPanel projectId={10} />);

    await waitFor(() => expect(actions.getProjectAnalytics).toHaveBeenCalledWith(10));

    // The details are rendered (data loaded) but hidden until expanded.
    expect(screen.getByText("Created")).not.toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: /Analytics/i }));

    expect(await screen.findByText("Created")).toBeVisible();
    expect(screen.getByText("Comments")).toBeInTheDocument();
    for (const col of analytics.cardsPerColumn) {
      expect(screen.getByText(col.columnName)).toBeInTheDocument();
    }
    // Stat chips: 42 created, 31 moved, 3 deleted, 58 comments ("17" is ambiguous
    // with the DONE column count, so it is omitted here).
    expect(screen.getByText("42")).toBeInTheDocument();
    expect(screen.getByText("31")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("58")).toBeInTheDocument();
  });

  it("reloads when a realtime domain event arrives", async () => {
    render(<AnalyticsPanel projectId={10} />);
    await waitFor(() => expect(actions.getProjectAnalytics).toHaveBeenCalledTimes(1));

    const handler = vi.mocked(realtime.on).mock.calls.at(-1)?.[0] as () => void;
    handler();

    await waitFor(() => expect(actions.getProjectAnalytics).toHaveBeenCalledTimes(2));
  });

  it("shows an error message when the fetch fails", async () => {
    vi.mocked(actions.getProjectAnalytics).mockRejectedValue(new Error("boom"));

    render(<AnalyticsPanel projectId={10} />);

    expect(await screen.findByText("Failed to load analytics")).toBeInTheDocument();
  });
});