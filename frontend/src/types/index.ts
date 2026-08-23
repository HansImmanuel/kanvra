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

// §7 (task mutations)
export interface TaskResponse {
  id: number;
  columnId: number;
  title: string;
  description: string | null;
  priority: string | null;
  assigneeId: number | null;
  dueDate: string | null;
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