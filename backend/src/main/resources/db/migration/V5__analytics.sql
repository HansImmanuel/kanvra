-- =====================================================================
-- Kanvra analytics schema (v5)
-- Sprint 5 (post-MVP, FR-017). Source of truth: docs/SPEC.md §12.5,
-- docs/TECH_DOC.md §7.
--
-- project_analytics  - one counter row per project, accumulated by the
--                      Analytics Consumer (group kanvra-analytics).
-- analytics_events   - idempotency ledger: a unique event_id prevents a
--                      duplicate Kafka delivery from double-counting.
-- =====================================================================

CREATE TABLE project_analytics (
    project_id       BIGINT PRIMARY KEY REFERENCES projects(id),
    tasks_created    BIGINT NOT NULL DEFAULT 0,
    tasks_completed  BIGINT NOT NULL DEFAULT 0,
    tasks_moved      BIGINT NOT NULL DEFAULT 0,
    tasks_deleted    BIGINT NOT NULL DEFAULT 0,
    comments_created BIGINT NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE analytics_events (
    id          BIGSERIAL PRIMARY KEY,
    event_id    UUID NOT NULL UNIQUE,
    project_id  BIGINT NOT NULL REFERENCES projects(id),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_analytics_events_project ON analytics_events(project_id);