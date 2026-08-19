-- =====================================================================
-- Kanvra v2 schema (Sprint 2)
-- Source of truth: docs/TECH_DOC.md §7 (schema) and §7.1 (indexes).
--
-- Adds, together:
--   1. refresh_tokens             - server-side refresh-token rotation/revocation
--      (review finding: old refresh JWTs stayed valid for 7 days after rotation).
--   2. idempotency_keys           - Idempotency-Key dedup for task creation (ADR-008,
--      review finding: no table existed despite the SPEC §7 requirement).
--   3. project_members(user_id)   - missing FK index (review finding; V1 only had
--      the composite PRIMARY KEY (project_id, user_id)).
-- =====================================================================

-- ---------------------------------------------------------------
-- refresh_tokens
-- ---------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    jti         VARCHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    expires_at  TIMESTAMP NOT NULL,
    revoked_at  TIMESTAMP,
    replaced_by BIGINT REFERENCES refresh_tokens(id)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- ---------------------------------------------------------------
-- idempotency_keys (24h dedup window for client-generated keys)
-- ---------------------------------------------------------------
CREATE TABLE idempotency_keys (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(64) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id   BIGINT NOT NULL,
    response_body JSONB,
    status_code   INTEGER,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    expires_at    TIMESTAMP NOT NULL,
    UNIQUE (user_id, idempotency_key, resource_type)
);

CREATE INDEX idx_idempotency_keys_expiry ON idempotency_keys(expires_at);

-- ---------------------------------------------------------------
-- Missing FK index: project_members(user_id) for member lookups
-- (e.g. assignee dropdown / membership checks by user)
-- ---------------------------------------------------------------
CREATE INDEX idx_project_members_user ON project_members(user_id);

-- ---------------------------------------------------------------
-- outbox_events.event_id - the UUID eventId returned by the mutation response
-- (SPEC §7 step 8: "include its eventId (from the outbox row)") and embedded in
-- the Kafka envelope so consumers can dedup against activities/notifications.
-- V1 only had a BIGSERIAL id, which is not the event identity the spec needs.
-- ---------------------------------------------------------------
ALTER TABLE outbox_events ADD COLUMN event_id UUID;

CREATE INDEX idx_outbox_event_id ON outbox_events(event_id) WHERE event_id IS NOT NULL;
