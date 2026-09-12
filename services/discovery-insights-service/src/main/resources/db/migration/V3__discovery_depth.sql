-- =============================================================================
-- discovery-insights-service — V3: discovery depth (I1-DSC-002)
--
-- Depth tables for the station domain facts (EVSE, connector, tariff) per
-- ARC-022 §9 (search projections; public reference data ONLY — no account,
-- driver or vehicle identifiers) and §8 (common integration persistence;
-- Discovery owns its own copies as a message-consuming service).
-- Explicit stable constraint names per §10; no foreign keys between the
-- projections (each is an independently rebuilt copy of its source family).
-- Indexes: 6 (2 EVSE + 2 connector + 2 tariff, incl. ix_tariff_public_source_version
-- added by the MINOR-5 pre-application amendment — V3 is not yet applied in
-- any shared environment, so it was amended directly per the expand–migrate
-- rule for un-applied migrations).
-- =============================================================================

-- ── 9. evse_search_projection ───────────────────────────────────────────────

CREATE TABLE discovery_insights.evse_search_projection (
    evse_ref        uuid         CONSTRAINT pk_evse_search_projection PRIMARY KEY,
    station_ref     uuid         NOT NULL,
    evse_uid        varchar(96)  NOT NULL,
    source_version  bigint       NOT NULL,
    source_ref      varchar(160),
    received_at     timestamptz  NOT NULL DEFAULT now(),
    projection_state varchar(24) NOT NULL DEFAULT 'ACTIVE',
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_evse_search_uid UNIQUE (evse_uid),
    CONSTRAINT ck_evse_search_state
        CHECK (projection_state IN ('ACTIVE', 'STALE', 'REMOVED'))
);

CREATE INDEX ix_evse_search_station
    ON discovery_insights.evse_search_projection (station_ref);

CREATE INDEX ix_evse_search_source_version
    ON discovery_insights.evse_search_projection (source_version);

-- ── 9. connector_search_projection ──────────────────────────────────────────

CREATE TABLE discovery_insights.connector_search_projection (
    connector_ref   uuid         CONSTRAINT pk_connector_search_projection PRIMARY KEY,
    evse_ref        uuid         NOT NULL,
    connector_type  varchar(24)  NOT NULL,
    max_power_w     integer      NOT NULL,
    source_version  bigint       NOT NULL,
    source_ref      varchar(160),
    received_at     timestamptz  NOT NULL DEFAULT now(),
    projection_state varchar(24) NOT NULL DEFAULT 'ACTIVE',
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_connector_power CHECK (max_power_w > 0),
    CONSTRAINT ck_connector_search_state
        CHECK (projection_state IN ('ACTIVE', 'STALE', 'REMOVED'))
);

CREATE INDEX ix_connector_search_evse
    ON discovery_insights.connector_search_projection (evse_ref);

CREATE INDEX ix_connector_search_type_power
    ON discovery_insights.connector_search_projection (connector_type, max_power_w);

-- ── 9. tariff_public_projection ─────────────────────────────────────────────

CREATE TABLE discovery_insights.tariff_public_projection (
    tariff_version_ref uuid      CONSTRAINT pk_tariff_public_projection PRIMARY KEY,
    tariff_ref         uuid      NOT NULL,
    version_number     integer   NOT NULL,
    currency           varchar(3) NOT NULL,
    components         jsonb     NOT NULL,
    source_version     bigint    NOT NULL,
    source_ref         varchar(160),
    received_at        timestamptz NOT NULL DEFAULT now(),
    projection_state   varchar(24) NOT NULL DEFAULT 'ACTIVE',
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_tariff_public_state
        CHECK (projection_state IN ('ACTIVE', 'STALE', 'REMOVED'))
);

CREATE INDEX ix_tariff_public_tariff
    ON discovery_insights.tariff_public_projection (tariff_ref, projection_state);

-- MINOR-5 (data review): the per-family version gate reads
-- tariff_public_projection by tariff_version_ref (PK) and the §7.1 guard
-- compares source_version on conflict; this index supports source-version
-- scans/rebuild bookkeeping on the tariff family, mirroring the
-- ix_*_source_version indexes on the other two depth tables.
CREATE INDEX ix_tariff_public_source_version
    ON discovery_insights.tariff_public_projection (source_version);
