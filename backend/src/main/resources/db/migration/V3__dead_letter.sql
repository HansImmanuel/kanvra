-- =====================================================================
-- Kanvra dead-letter table (v3)
-- Source of truth: docs/TECH_DOC.md §20 (Sprint 4: table-based DLT).
-- Consumers park permanently-failing messages here and ack them so a
-- poison pill cannot block its Kafka partition forever. Transient
-- failures still rethrow for Kafka redelivery (AGENT.md §12).
-- No FK — transport/ops record, same convention as outbox_events.
-- =====================================================================
CREATE TABLE dead_letter_events (
    id             BIGSERIAL PRIMARY KEY,
    consumer_group VARCHAR(100) NOT NULL,
    event_id       VARCHAR(64),
    event_type     VARCHAR(255),
    raw_message    TEXT,
    reason         VARCHAR(512) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_dead_letter_created ON dead_letter_events(created_at);
CREATE INDEX idx_dead_letter_group ON dead_letter_events(consumer_group);