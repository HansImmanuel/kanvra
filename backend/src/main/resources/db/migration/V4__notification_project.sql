-- =====================================================================
-- Kanvra notification project scope (v4)
-- Sprint 4: denormalized project_id lets the frontend deep-link a
-- notification to its project/board (SPEC §12 reference typing).
-- Nullable: seeded/legacy rows and non-project-scoped events stay null.
-- =====================================================================
ALTER TABLE notifications ADD COLUMN project_id BIGINT;

CREATE INDEX idx_notifications_project ON notifications(project_id);