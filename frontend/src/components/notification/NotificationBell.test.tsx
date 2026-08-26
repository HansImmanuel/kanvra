import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import NotificationBell from "./NotificationBell";
import * as actions from "@/lib/actions";
import { realtime } from "@/lib/websocket";

const pushMock = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock })
}));

vi.mock("@/lib/actions", () => ({
  listNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn()
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

const unread = {
  id: 1,
  type: "TASK_ASSIGNED",
  referenceId: 5,
  referenceType: "TASK",
  projectId: 10,
  message: "Hans assigned you the task 'Fix auth'",
  readAt: null,
  createdAt: new Date().toISOString()
};

const alreadyRead = {
  id: 2,
  type: "PROJECT_INVITATION",
  referenceId: 3,
  referenceType: "PROJECT",
  projectId: 3,
  message: "Hans added you to project 'Beta'",
  readAt: new Date().toISOString(),
  createdAt: new Date().toISOString()
};

describe("NotificationBell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    pushMock.mockReset();
    vi.mocked(actions.listNotifications).mockResolvedValue({
      content: [unread, alreadyRead],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1
    });
  });

  it("shows the unread badge and the notification list", async () => {
    render(<NotificationBell />);

    const badge = await screen.findByTestId("notif-badge");
    expect(badge).toHaveTextContent("1");

    // List rows live inside the dropdown — open it first.
    fireEvent.click(screen.getByRole("button", { name: /Notifications/i }));
    expect(await screen.findByText(/assigned you the task 'Fix auth'/)).toBeInTheDocument();
    expect(screen.getByText("Mark all read")).toBeInTheDocument();
    expect(screen.getByText(/added you to project 'Beta'/)).toBeInTheDocument();
  });

  it("marks an item read and deep-links to its project", async () => {
    vi.mocked(actions.markNotificationRead).mockResolvedValue({ ...unread, readAt: new Date().toISOString() });
    render(<NotificationBell />);

    fireEvent.click(await screen.findByRole("button", { name: /Notifications/i }));
    fireEvent.click(await screen.findByText(/assigned you the task 'Fix auth'/));

    await waitFor(() => expect(actions.markNotificationRead).toHaveBeenCalledWith(1));
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/projects/10/board"));
  });

  it("mark-all-read hits the bulk endpoint and refreshes", async () => {
    vi.mocked(actions.markAllNotificationsRead).mockResolvedValue(undefined as never);
    render(<NotificationBell />);

    fireEvent.click(await screen.findByRole("button", { name: /Notifications/i }));
    fireEvent.click(screen.getByRole("button", { name: "Mark all read" }));

    await waitFor(() => expect(actions.markAllNotificationsRead).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(actions.listNotifications).toHaveBeenCalledTimes(2));
  });

  it("ignores non-COMMENT realtime events for thread reload but still refreshes the badge", async () => {
    // Any domain event can produce notifications, so every realtime event
    // refreshes the badge via the registered handler.
    render(<NotificationBell />);
    const handler = vi.mocked(realtime.on).mock.calls.at(-1)?.[0] as () => void;
    expect(handler).toBeTruthy();

    handler();
    await waitFor(() => expect(actions.listNotifications).toHaveBeenCalledTimes(2));
  });
});