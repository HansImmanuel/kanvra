// API response shapes mirroring the Spring Boot DTOs (docs/SPEC.md)
// ---------------------------------------------------------------
// §3 (auth) & /me
export interface User {
  id: number;
  name: string;
  email: string;
  avatarUrl: string | null;
}

// §4 (projects)
export interface Project {
  id: number;
  name: string;
  description: string | null;
  status: string;
  ownerId: number;
  createdAt: string;
}

// §6 / Appendix A — board detail
export interface TaskCard {
  id: number;
  title: string;
  priority: string | null;
  position: number;
  version: number;
  assignee: { id: number; name: string; avatarUrl: string | null } | null;
  labels: { id: number; name: string; color: string }[];
  dueDate: string | null;
  commentCount: number;
}

export interface ColumnDetail {
  id: number;
  name: string;
  position: number;
  tasks: TaskCard[];
}

export interface BoardDetail {
  id: number;
  projectId: number;
  name: string;
  status: string;
  columns: ColumnDetail[];
}

export interface BoardRef {
  id: number;
  projectId: number;
  name: string;
}

// §6 column mutation responses (ColumnResponse)
export interface ColumnInfo {
  id: number;
  boardId: number;
  name: string;
  position: number;
}

// §7 (task mutations)
export interface TaskResponse {
  id: number;
  columnId: number;
  title: string;
  description: string | null;
  priority: string | null;
  assigneeId: number | null;
  dueDate: string | null;
  /** Current labels — lets a version-gated PATCH round-trip them losslessly. */
  labelIds: number[] | null;
  position: number;
  version: number;
  createdAt: string;
  eventId: string | null;
}

// Paginated list shape (§17.1)
export interface ApiPage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// §17.1 error body. currentState is present only on TASK_VERSION_CONFLICT so the
// UI can re-sync a stale card against the server's current task.
export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  code: string;
  message: string;
  errors?: { field: string; message: string }[] | null;
  currentState?: TaskResponse | null;
}

// §4 project members (MemberResponse)
export interface Member {
  id: number;
  name: string;
  role: string;
  joinedAt: string;
}

// §9 labels (LabelResponse)
export interface Label {
  id: number;
  projectId: number;
  name: string;
  color: string;
}

// §8 comments (CommentResponse)
export interface CommentAuthor {
  id: number;
  name: string;
  avatarUrl: string | null;
}
export interface Comment {
  id: number;
  taskId: number;
  author: CommentAuthor;
  content: string;
  createdAt: string;
  updatedAt: string;
}

// §7 task update body — version-gated (optimistic concurrency). The client
// always sends the version it last saw; the server rejects mismatches with
// TASK_VERSION_CONFLICT + currentState.
export interface TaskUpdate {
  title: string;
  description?: string | null;
  priority?: string | null;
  assigneeId?: number | null;
  dueDate?: string | null;
  labelIds?: number[];
  version: number;
}

// §11 activity (ActivityResponse)
export interface Activity {
  id: number;
  projectId: number;
  actorId: number | null;
  type: string;
  message: string;
  createdAt: string;
}

// §12 notifications (NotificationResponse)
export interface Notification {
  id: number;
  type: string;
  referenceId: number | null;
  referenceType: string;
  /** Denormalized project scope (Sprint 4) for deep-linking. */
  projectId: number | null;
  message: string;
  readAt: string | null;
  createdAt: string;
}

// §12.5 analytics (ProjectAnalyticsResponse — post-MVP Sprint 5, FR-017)
export interface AnalyticsCounters {
  tasksCreated: number;
  tasksCompleted: number;
  tasksMoved: number;
  tasksDeleted: number;
  commentsCreated: number;
}

export interface CardsPerColumn {
  columnId: number;
  columnName: string;
  position: number;
  count: number;
}

export interface ProjectAnalytics {
  projectId: number;
  counters: AnalyticsCounters;
  cardsPerColumn: CardsPerColumn[];
}