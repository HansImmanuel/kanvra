import { describe, it, expect, vi, beforeEach } from "vitest";
import * as api from "./api";
import {
  getTask,
  updateTask,
  listComments,
  createComment,
  updateComment,
  deleteComment,
  listActivity,
  listNotifications,
  markNotificationRead,
  markAllNotificationsRead,
  createLabel,
  updateLabel,
  deleteLabel,
  listMembers,
  addMember,
  removeMember
} from "./actions";

// The wrappers only delegate to lib/api, so stub the underlying api module and
// assert each action produces the correct route + body. (ApiError class spreads
// through `...actual`.)
vi.mock("./api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./api")>();
  return {
    ...actual,
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    del: vi.fn()
  };
});

const apiGet = vi.mocked(api.get);
const apiPost = vi.mocked(api.post);
const apiPatch = vi.mocked(api.patch);
const apiDel = vi.mocked(api.del);

describe("task actions", () => {
  beforeEach(() => vi.clearAllMocks());

  it("getTask hits GET /tasks/{id}", () => {
    apiGet.mockResolvedValue({ id: 7 } as never);
    getTask(7);
    expect(apiGet).toHaveBeenCalledWith("/api/v1/tasks/7");
  });

  it("updateTask sends the full body including version", () => {
    apiPatch.mockResolvedValue({} as never);
    updateTask(7, {
      title: "Rename",
      description: null,
      priority: "HIGH",
      assigneeId: null,
      dueDate: null,
      labelIds: [1, 2],
      version: 4
    });
    expect(apiPatch).toHaveBeenCalledWith("/api/v1/tasks/7", {
      title: "Rename",
      description: null,
      priority: "HIGH",
      assigneeId: null,
      dueDate: null,
      labelIds: [1, 2],
      version: 4
    });
  });
});

describe("comment actions", () => {
  beforeEach(() => vi.clearAllMocks());

  it("createComment POSTs content to the task comments route", () => {
    apiPost.mockResolvedValue({} as never);
    createComment(5, "looks good");
    expect(apiPost).toHaveBeenCalledWith("/api/v1/tasks/5/comments", { content: "looks good" });
  });

  it("listComments paginates", () => {
    apiGet.mockResolvedValue({} as never);
    listComments(5, 2, 50);
    expect(apiGet).toHaveBeenCalledWith("/api/v1/tasks/5/comments?page=2&size=50");
  });

  it("updateComment/deleteComment hit the comment route", () => {
    apiPatch.mockResolvedValue({} as never);
    apiDel.mockResolvedValue(undefined as never);
    updateComment(9, "edited");
    deleteComment(9);
    expect(apiPatch).toHaveBeenCalledWith("/api/v1/comments/9", { content: "edited" });
    expect(apiDel).toHaveBeenCalledWith("/api/v1/comments/9");
  });
});

describe("activity/notification actions", () => {
  beforeEach(() => vi.clearAllMocks());

  it("listActivity paginates per project", () => {
    apiGet.mockResolvedValue({} as never);
    listActivity(3, 1, 25);
    expect(apiGet).toHaveBeenCalledWith("/api/v1/projects/3/activity?page=1&size=25");
  });

  it("notification actions hit the notification routes", () => {
    apiGet.mockResolvedValue({} as never);
    apiPost.mockResolvedValue({} as never);
    listNotifications();
    markNotificationRead(2);
    markAllNotificationsRead();
    expect(apiGet).toHaveBeenCalledWith("/api/v1/notifications?page=0&size=20");
    expect(apiPost).toHaveBeenCalledWith("/api/v1/notifications/2/read");
    expect(apiPost).toHaveBeenCalledWith("/api/v1/notifications/read-all");
  });
});

describe("label/member actions", () => {
  beforeEach(() => vi.clearAllMocks());

  it("label CRUD routes", () => {
    apiPost.mockResolvedValue({} as never);
    apiPatch.mockResolvedValue({} as never);
    apiDel.mockResolvedValue(undefined as never);
    createLabel(3, "backend", "#2563EB");
    updateLabel(8, "renamed", "#111111");
    deleteLabel(8);
    expect(apiPost).toHaveBeenCalledWith("/api/v1/projects/3/labels", { name: "backend", color: "#2563EB" });
    expect(apiPatch).toHaveBeenCalledWith("/api/v1/labels/8", { name: "renamed", color: "#111111" });
    expect(apiDel).toHaveBeenCalledWith("/api/v1/labels/8");
  });

  it("member routes", () => {
    apiGet.mockResolvedValue({} as never);
    apiPost.mockResolvedValue({} as never);
    apiDel.mockResolvedValue(undefined as never);
    listMembers(3);
    addMember(3, 9, "MEMBER");
    removeMember(3, 9);
    expect(apiGet).toHaveBeenCalledWith("/api/v1/projects/3/members?page=0&size=50");
    expect(apiPost).toHaveBeenCalledWith("/api/v1/projects/3/members", { userId: 9, role: "MEMBER" });
    expect(apiDel).toHaveBeenCalledWith("/api/v1/projects/3/members/9");
  });
});