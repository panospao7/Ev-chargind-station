-- =============================================================================
-- station-operations-service — V3: common integration persistence (I1-MSG-001)
--
-- Table set is EXACTLY ARC-022 §8 (outbox_message, inbox_message,
-- idempotency_record, audit_event). Every message-producing or consuming
-- service owns its own copies; this is the Station Operations copy.
-- =============================================================================

-- ── 8.1 outbox_message ──────────────────────────────────────────────────────

CREATE TABLE station_operations.outbox_message (
    message_id        uuid PRIMARY KEY,
    kind              varchar(16)  NOT NULL
        CHECK (kind IN ('EVENT', 'COMMAND')),
    message_type      varchar(96)  NOT NULL,
    aggregate_type    varchar(48)  NOT NULL,
    aggregate_ref     uuid         NOT NULL,
    aggregate_version bigint       NOT NULL,
    workflow_ref      uuid,
    correlation_id    uuid         NOT NULL,
    causation_id      uuid,
    classification    varchar(24)  NOT NULL,
    payload           jsonb        NOT NULL,
    occurred_at       timestamptz  NOT NULL DEFAULT now(),
    available_at      timestamptz  NOT NULL DEFAULT now(),
    published_at      timestamptz,
    attempt_count     integer      NOT NULL DEFAULT 0,
    state             varchar(16)  NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'PUBLISHED', 'QUARANTINED')),
    failure_category  varchar(48),
    -- unique event-fact protection (ARC-022 §8.1)
    UNIQUE (aggregate_type, aggregate_ref, aggregate_version, message_type)
);

CREATE INDEX idx_outbox_dispatch
    ON station_operations.outbox_message (state, available_at);

-- ── 8.2 inbox_message ───────────────────────────────────────────────────────

CREATE TABLE station_operations.inbox_message (
    consumer_name     varchar(96)  NOT NULL,
    message_id        uuid         NOT NULL,
    message_type      varchar(96)  NOT NULL,
    aggregate_type    varchar(48),
    aggregate_version bigint,
    received_at       timestamptz  NOT NULL DEFAULT now(),
    completed_at      timestamptz,
    processing_outcome varchar(24) NOT NULL
        CHECK (processing_outcome IN ('PROCESSING', 'COMPLETED', 'FAILED', 'SKIPPED')),
    attempt_count     integer      NOT NULL DEFAULT 0,
    failure_category  varchar(48),
    -- primary uniqueness (ARC-022 §8.2); completion and business effects
    -- commit in one transaction
    PRIMARY KEY (consumer_name, message_id)
);

-- ── 8.3 idempotency_record ──────────────────────────────────────────────────

CREATE TABLE station_operations.idempotency_record (
    idempotency_ref   uuid PRIMARY KEY,
    principal_identity varchar(96) NOT NULL,
    operation         varchar(96)  NOT NULL,
    target_resource   varchar(160) NOT NULL,
    idempotency_key   varchar(160) NOT NULL,
    request_fingerprint varchar(128),
    state             varchar(16)  NOT NULL
        CHECK (state IN ('IN_FLIGHT', 'COMPLETED', 'EXPIRED')),
    status_code       integer,
    result_reference  varchar(160),
    created_at        timestamptz  NOT NULL DEFAULT now(),
    completed_at      timestamptz,
    expires_at        timestamptz  NOT NULL,
    -- retention values are DATA ONLY: no deletion behavior exists in W1
    -- (retention enforcement is W3 per GOV-007 §5)
    UNIQUE (principal_identity, operation, target_resource, idempotency_key)
);

-- ── 8.4 audit_event (append-only; runtime role may insert only) ────────────

CREATE TABLE station_operations.audit_event (
    audit_ref       uuid PRIMARY KEY,
    actor           varchar(96)  NOT NULL,
    calling_service varchar(96)  NOT NULL,
    action          varchar(96)  NOT NULL,
    target          varchar(160) NOT NULL,
    reason          varchar(200),
    outcome         varchar(24)  NOT NULL,
    before_summary  jsonb,
    after_summary   jsonb,
    correlation_id  uuid,
    classification  varchar(24)  NOT NULL,
    occurred_at     timestamptz  NOT NULL DEFAULT now()
);

REVOKE UPDATE, DELETE ON station_operations.audit_event
    FROM station_operations_runtime;
