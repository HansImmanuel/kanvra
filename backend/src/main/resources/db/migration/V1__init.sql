-- =====================================================================
-- Kanvra initial schema (v1)
-- Source of truth: docs/TECH_DOC.md §7 (schema) and §7.1 (indexes).
-- The transactional outbox table is present from the FIRST migration
-- (ADR-005: outbox implemented from day one, not deferred).
-- =====================================================================

-- ---------------------------------------------------------------
-- users
-- ---------------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url    VARCHAR(2048),
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------
-- projects
-- ---------------------------------------------------------------
CREATE TABLE projects (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    owner_id    BIGINT NOT NULL REFERENCES users(id),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_projects_owner ON projects(owner_id);

-- ---------------------------------------------------------------
-- project_members
-- ---------------------------------------------------------------
CREATE TABLE project_members (
    project_id BIGINT NOT NULL REFERENCES projects(id),
    user_id    BIGINT NOT NULL REFERENCES users(id),
    role       VARCHAR(20) NOT NULL,
    joined_at  TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id)
);

-- ---------------------------------------------------------------
-- boards
-- ---------------------------------------------------------------
CREATE TABLE boards (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    name       VARCHAR(100) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_boards_project ON boards(project_id);

-- ---------------------------------------------------------------
-- columns
-- ---------------------------------------------------------------
CREATE TABLE columns (
    id         BIGSERIAL PRIMARY KEY,
    board_id   BIGINT NOT NULL REFERENCES boards(id),
    name       VARCHAR(100) NOT NULL,
    position   INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_columns_board ON columns(board_id);

-- ---------------------------------------------------------------
-- tasks
-- ---------------------------------------------------------------
CREATE TABLE tasks (
    id          BIGSERIAL PRIMARY KEY,
    column_id   BIGINT NOT NULL REFERENCES columns(id),
    assignee_id BIGINT REFERENCES users(id),
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    priority    VARCHAR(20),
    due_date    DATE,
    position    INTEGER NOT NULL DEFAULT 0,
    version     INTEGER NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_tasks_column   ON tasks(column_id);
CREATE INDEX idx_tasks_assignee ON tasks(assignee_id);
CREATE INDEX idx_tasks_due_date ON tasks(due_date) WHERE due_date IS NOT NULL;
CREATE INDEX idx_tasks_deleted  ON tasks(deleted_at) WHERE deleted_at IS NOT NULL;




-- ---------------------------------------------------------------
-- labels
-- ---------------------------------------------------------------
CREATE TABLE labels (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    name       VARCHAR(100) NOT NULL,
    color      VARCHAR(7) NOT NULL
);

CREATE INDEX idx_labels_project ON labels(project_id);

-- ---------------------------------------------------------------
-- task_labels
-- ---------------------------------------------------------------
CREATE TABLE task_labels (
    task_id  BIGINT NOT NULL REFERENCES tasks(id),
    label_id BIGINT NOT NULL REFERENCES labels(id),
    PRIMARY KEY (task_id, label_id)
);

-- ---------------------------------------------------------------
-- comments
-- ---------------------------------------------------------------
CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    task_id    BIGINT NOT NULL REFERENCES tasks(id),
    author_id  BIGINT NOT NULL REFERENCES users(id),
    content    VARCHAR(2000) NOT NULL,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_comments_task ON comments(task_id);

-- ---------------------------------------------------------------
-- activities
-- ---------------------------------------------------------------
CREATE TABLE activities (
    id         BIGSERIAL PRIMARY KEY,
    event_id   UUID UNIQUE,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    actor_id   BIGINT REFERENCES users(id),
    type       VARCHAR(50) NOT NULL,
    message    TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_activities_project ON activities(project_id);
CREATE INDEX idx_activities_created ON activities(project_id, created_at DESC);

-- ---------------------------------------------------------------
-- notifications
-- ---------------------------------------------------------------
CREATE TABLE notifications (
    id             BIGSERIAL PRIMARY KEY,
    event_id       UUID NOT NULL,
    recipient_id   BIGINT NOT NULL REFERENCES users(id),
    type           VARCHAR(50) NOT NULL,
    reference_id   BIGINT,
    reference_type VARCHAR(20),
    message        TEXT NOT NULL,
    read_at        TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (recipient_id, event_id)
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient_id, read_at);

-- ---------------------------------------------------------------
-- outbox_events  (transactional outbox transport record - no FK)
-- ---------------------------------------------------------------
CREATE TABLE outbox_events (
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id   BIGINT NOT NULL,
    payload        JSONB NOT NULL,
    project_id     BIGINT,
    actor_id       BIGINT,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    published_at   TIMESTAMP
);

CREATE INDEX idx_outbox_unpublished ON outbox_events(published_at) WHERE published_at IS NULL;
