-- =============================================================================
-- discovery-insights-service — V2: discovery first slice (I1-DSC-001)
--
-- Projection and consumer integration tables per ARC-022 §9 (station search
-- projection) and §8 (common integration persistence; Discovery owns its own
-- copies as a message-consuming service). Public reference data ONLY — no
-- account, driver or vehicle identifiers (ARC-022 §9).
-- Explicit stable constraint names per §10.
-- =============================================================================

-- ── 9. station_search_projection ────────────────────────────────────────────

CREATE TABLE discovery_insights.station_search_projection (
    station_ref        uuid         CONSTRAINT pk_station_search_projection PRIMARY KEY,
    public_ref         varchar(64)  NOT NULL,
    display_name       varchar(160) NOT NULL,
    address_line       varchar(200),
    city               varchar(96),
    postal_code        varchar(16),
    country_code       varchar(2),
    latitude           numeric(9,6) NOT NULL,
    longitude          numeric(9,6) NOT NULL,
    source_version     bigint       NOT NULL,
    source_ref         varchar(160),
    effective_at       timestamptz,
    received_at        timestamptz  NOT NULL DEFAULT now(),
    projection_state   varchar(24)  NOT NULL DEFAULT 'ACTIVE',
    last_reconciled_at timestamptz,
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_station_search_public_ref UNIQUE (public_ref),
    CONSTRAINT ck_station_search_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_station_search_lon CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_station_search_state
        CHECK (projection_state IN ('ACTIVE', 'STALE', 'REMOVED'))
);

CREATE INDEX ix_station_search_source_version
    ON discovery_insights.station_search_projection (source_version);

CREATE INDEX ix_station_search_location
    ON discovery_insights.station_search_projection (latitude, longitude);

-- ── 9. projection_checkpoint ────────────────────────────────────────────────

CREATE TABLE discovery_insights.projection_checkpoint (
    projection_name       varchar(96)  NOT NULL,
    source_ref            varchar(160) NOT NULL DEFAULT 'station-operations-service',
    last_applied_version  bigint       NOT NULL DEFAULT -1,
    last_applied_message_id uuid,
    gap_from_version      bigint,
    gap_recorded_at       timestamptz,
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_projection_checkpoint PRIMARY KEY (projection_name, source_ref)
);

-- ── 8.2 inbox_message ───────────────────────────────────────────────────────

CREATE TABLE discovery_insights.inbox_message (
    consumer_name      varchar(96)  NOT NULL,
    message_id         uuid         NOT NULL,
    message_type       varchar(96)  NOT NULL,
    aggregate_type     varchar(48),
    aggregate_version  bigint,
    received_at        timestamptz  NOT NULL DEFAULT now(),
    completed_at       timestamptz,
    processing_outcome varchar(24)  NOT NULL
        CHECK (processing_outcome IN ('PROCESSING', 'COMPLETED', 'FAILED', 'SKIPPED')),
    attempt_count      integer      NOT NULL DEFAULT 0,
    failure_category   varchar(48),
    -- primary uniqueness (ARC-022 §8.2); completion and business effects
    -- commit in one transaction
    CONSTRAINT pk_inbox_message PRIMARY KEY (consumer_name, message_id)
);

-- ── 8.4 audit_event (append-only; runtime role may insert only) ────────────

CREATE TABLE discovery_insights.audit_event (
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

REVOKE UPDATE, DELETE ON discovery_insights.audit_event
    FROM discovery_insights_runtime;
