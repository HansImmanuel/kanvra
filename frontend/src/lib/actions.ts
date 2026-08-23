// Typed API wrappers (docs/TECH_DOC.md §14 lib/api + lib/actions).
//
// Every mutation reuses the shared pipeline in lib/api: same-origin proxy + CSRF
// header injection, Idempotency-Key generation/reuse, and 401 → silent refresh →
// retry once. These wrappers exist so feature components (task detail, comments,
// settings, notifications, activity) don't re-implement plumbing or string-path
// the endpoints.
import { get, post, patch, del } from "./api";
import type {
  Activity,
  ApiPage,
  BoardRef,
  ColumnInfo,
  Comment,
  Label,
  Member,
  Notification,
  TaskResponse,
  TaskUpdate
} from "@/types";

// --- Tasks (SPEC §7) -----------------------------------------------------
export const getTask = (taskId: number): Promise<TaskResponse> =>
  get<TaskResponse>(`/api/v1/tasks/${taskId}`);

export const updateTask = (taskId: number, body: TaskUpdate): Promise<TaskResponse> =>
  patch<TaskResponse>(`/api/v1/tasks/${taskId}`, body);

// --- Comments (SPEC §8) --------------------------------------------------
export const listComments = (taskId: number, page = 0, size = 20): Promise<ApiPage<Comment>> =>
  get<ApiPage<Comment>>(`/api/v1/tasks/${taskId}/comments?page=${page}&size=${size}`);

export const createComment = (taskId: number, content: string): Promise<Comment> =>
  post<Comment>(`/api/v1/tasks/${taskId}/comments`, { content });

export const updateComment = (commentId: number, content: string): Promise<Comment> =>
  patch<Comment>(`/api/v1/comments/${commentId}`, { content });

export const deleteComment = (commentId: number): Promise<void> =>
  del<void>(`/api/v1/comments/${commentId}`);

// --- Labels (SPEC §9) ----------------------------------------------------
/** Membership-scoped, name-sorted list (Sprint 4) — feeds the pickers/settings. */
export const listProjectLabels = (projectId: number): Promise<Label[]> =>
  get<Label[]>(`/api/v1/projects/${projectId}/labels`);

export const createLabel = (projectId: number, name: string, color: string): Promise<Label> =>
  post<Label>(`/api/v1/projects/${projectId}/labels`, { name, color });

export const updateLabel = (labelId: number, name: string, color: string): Promise<Label> =>
  patch<Label>(`/api/v1/labels/${labelId}`, { name, color });

export const deleteLabel = (labelId: number): Promise<void> =>
  del<void>(`/api/v1/labels/${labelId}`);

// --- Members (SPEC §4) ---------------------------------------------------
export const listMembers = (projectId: number, page = 0, size = 50): Promise<ApiPage<Member>> =>
  get<ApiPage<Member>>(`/api/v1/projects/${projectId}/members?page=${page}&size=${size}`);

export const addMember = (projectId: number, userId: number, role: "OWNER" | "MEMBER"): Promise<Member> =>
  post<Member>(`/api/v1/projects/${projectId}/members`, { userId, role });

export const removeMember = (projectId: number, userId: number): Promise<void> =>
  del<void>(`/api/v1/projects/${projectId}/members/${userId}`);

// --- Boards & columns (SPEC §5–§6) ---------------------------------------
export const listBoards = (projectId: number): Promise<BoardRef[]> =>
  get<BoardRef[]>(`/api/v1/projects/${projectId}/boards`);

export const createBoard = (projectId: number, name: string): Promise<BoardRef> =>
  post<BoardRef>(`/api/v1/projects/${projectId}/boards`, { name });

export const createColumn = (boardId: number, name: string): Promise<ColumnInfo> =>
  post<ColumnInfo>(`/api/v1/boards/${boardId}/columns`, { name });

export const renameColumn = (columnId: number, name: string): Promise<ColumnInfo> =>
  patch<ColumnInfo>(`/api/v1/columns/${columnId}`, { name });

export const deleteColumn = (columnId: number, targetColumnId?: number): Promise<void> =>
  del<void>(
    `/api/v1/columns/${columnId}${targetColumnId != null ? `?targetColumnId=${targetColumnId}` : ""}`
  );

export const reorderColumns = (boardId: number, columnIds: number[]): Promise<ColumnInfo[]> =>
  post<ColumnInfo[]>(`/api/v1/boards/${boardId}/columns/reorder`, { columnIds });

// --- Activity (SPEC §11) -------------------------------------------------
export const listActivity = (projectId: number, page = 0, size = 20): Promise<ApiPage<Activity>> =>
  get<ApiPage<Activity>>(`/api/v1/projects/${projectId}/activity?page=${page}&size=${size}`);

// --- Notifications (SPEC §12) -------------------------------------------
export const listNotifications = (page = 0, size = 20): Promise<ApiPage<Notification>> =>
  get<ApiPage<Notification>>(`/api/v1/notifications?page=${page}&size=${size}`);

export const markNotificationRead = (notificationId: number): Promise<Notification> =>
  post<Notification>(`/api/v1/notifications/${notificationId}/read`);

export const markAllNotificationsRead = (): Promise<void> =>
  post<void>(`/api/v1/notifications/read-all`);