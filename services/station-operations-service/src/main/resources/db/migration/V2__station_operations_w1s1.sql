-- =============================================================================
-- station-operations-service — V2: W1-S1 physical tables (I1-STA-001)
--
-- Table set is EXACTLY ARC-022 §9 "Station Operations Service / W1-S1".
-- Column designs derive from DOM-002 lifecycles (states), GOV-007 §3 S1-03
-- (booking-policy constants) and ARC-022 §5.7 (snapshot/immutability
-- semantics). No business-table additions, no cross-service references.
--
-- Lifecycle states mirror contracts/registries/lifecycles-v1.yaml:
--   OPERATOR_ORGANIZATION : ACTIVE | SUSPENDED | CLOSED
--   STATION               : DRAFT | PUBLISHED | TEMPORARILY_CLOSED | DEACTIVATED
--   EVSE_ADMINISTRATION   : ACTIVE | DISABLED | DEACTIVATED
-- Tariff and booking-policy versions are versioned reference data (no DOM-002
-- lifecycle): DRAFT | ACTIVE | RETIRED with database-enforced immutability
-- while ACTIVE (ARC-022 §5.7 snapshot semantics).
-- =============================================================================

-- ── operator organization ───────────────────────────────────────────────────

CREATE TABLE station_operations.operator_organization (
    organization_ref uuid PRIMARY KEY,
    legal_name       varchar(160) NOT NULL,
    state            varchar(24)  NOT NULL
        CHECK (state IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    version          bigint       NOT NULL DEFAULT 0,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE station_operations.organization_member (
    member_ref       uuid PRIMARY KEY,
    organization_ref uuid         NOT NULL
        REFERENCES station_operations.operator_organization (organization_ref),
    display_name     varchar(120) NOT NULL,
    member_role      varchar(32)  NOT NULL
        CHECK (member_role IN ('OPERATOR', 'ADMINISTRATOR')),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (organization_ref, display_name)
);

-- ── stations ────────────────────────────────────────────────────────────────

CREATE TABLE station_operations.station (
    station_ref      uuid PRIMARY KEY,
    organization_ref uuid         NOT NULL
        REFERENCES station_operations.operator_organization (organization_ref),
    public_ref       varchar(32)  NOT NULL UNIQUE,
    display_name     varchar(160) NOT NULL,
    state            varchar(24)  NOT NULL
        CHECK (state IN ('DRAFT', 'PUBLISHED', 'TEMPORARILY_CLOSED', 'DEACTIVATED')),
    address_line     varchar(200) NOT NULL,
    city             varchar(80)  NOT NULL,
    postal_code      varchar(12)  NOT NULL,
    country_code     char(2)      NOT NULL DEFAULT 'GR',
    latitude         numeric(9, 6) NOT NULL
        CHECK (latitude  BETWEEN -90  AND 90),
    longitude        numeric(9, 6) NOT NULL
        CHECK (longitude BETWEEN -180 AND 180),
    version          bigint       NOT NULL DEFAULT 0,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE station_operations.station_opening_period (
    period_ref  uuid PRIMARY KEY,
    station_ref uuid        NOT NULL
        REFERENCES station_operations.station (station_ref),
    weekday     smallint    NOT NULL CHECK (weekday BETWEEN 0 AND 6),
    opens_at    time        NOT NULL,
    closes_at   time        NOT NULL,
    UNIQUE (station_ref, weekday, opens_at)
);

CREATE TABLE station_operations.station_schedule_exception (
    exception_ref  uuid PRIMARY KEY,
    station_ref    uuid         NOT NULL
        REFERENCES station_operations.station (station_ref),
    exception_date date         NOT NULL,
    is_closed      boolean      NOT NULL DEFAULT true,
    note           varchar(200),
    UNIQUE (station_ref, exception_date)
);

-- ── equipment ───────────────────────────────────────────────────────────────

CREATE TABLE station_operations.evse (
    evse_ref     uuid PRIMARY KEY,
    station_ref  uuid        NOT NULL
        REFERENCES station_operations.station (station_ref),
    evse_uid     varchar(64) NOT NULL UNIQUE,
    state        varchar(24) NOT NULL
        CHECK (state IN ('ACTIVE', 'DISABLED', 'DEACTIVATED')),
    version      bigint      NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE station_operations.connector (
    connector_ref uuid PRIMARY KEY,
    evse_ref      uuid         NOT NULL
        REFERENCES station_operations.evse (evse_ref),
    connector_type varchar(24) NOT NULL
        CHECK (connector_type IN ('CCS', 'TYPE2')),
    max_power_w   integer      NOT NULL CHECK (max_power_w > 0),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (evse_ref, connector_type, max_power_w)
);

-- ── tariffs ─────────────────────────────────────────────────────────────────

CREATE TABLE station_operations.tariff (
    tariff_ref       uuid PRIMARY KEY,
    organization_ref uuid         NOT NULL
        REFERENCES station_operations.operator_organization (organization_ref),
    display_name     varchar(160) NOT NULL,
    currency         char(3)      NOT NULL DEFAULT 'EUR',
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE station_operations.tariff_version (
    tariff_version_ref uuid PRIMARY KEY,
    tariff_ref         uuid        NOT NULL
        REFERENCES station_operations.tariff (tariff_ref),
    version_number     integer     NOT NULL CHECK (version_number > 0),
    state              varchar(16) NOT NULL
        CHECK (state IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    valid_from         timestamptz NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tariff_ref, version_number)
);

CREATE TABLE station_operations.tariff_component (
    component_ref      uuid PRIMARY KEY,
    tariff_version_ref uuid         NOT NULL
        REFERENCES station_operations.tariff_version (tariff_version_ref),
    component_kind     varchar(48)  NOT NULL
        CHECK (component_kind IN ('ENERGY_PER_KWH', 'OCCUPANCY_PER_MINUTE')),
    unit               varchar(16)  NOT NULL
        CHECK (unit IN ('KWH', 'MINUTE')),
    amount_minor       bigint       NOT NULL CHECK (amount_minor >= 0),
    UNIQUE (tariff_version_ref, component_kind)
);

-- ── booking policies ────────────────────────────────────────────────────────

CREATE TABLE station_operations.booking_policy (
    policy_ref       uuid PRIMARY KEY,
    organization_ref uuid         NOT NULL
        REFERENCES station_operations.operator_organization (organization_ref),
    display_name     varchar(160) NOT NULL,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);

-- Policy constants carry the GOV-007 §3 S1-03 interval rules.
CREATE TABLE station_operations.booking_policy_version (
    policy_version_ref            uuid PRIMARY KEY,
    policy_ref                    uuid        NOT NULL
        REFERENCES station_operations.booking_policy (policy_ref),
    version_number                integer     NOT NULL CHECK (version_number > 0),
    state                         varchar(16) NOT NULL
        CHECK (state IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    hold_duration_seconds         integer     NOT NULL CHECK (hold_duration_seconds > 0),
    slot_increment_minutes        integer     NOT NULL CHECK (slot_increment_minutes > 0),
    min_duration_minutes          integer     NOT NULL CHECK (min_duration_minutes > 0),
    max_duration_minutes          integer     NOT NULL CHECK (max_duration_minutes >= min_duration_minutes),
    advance_booking_days          integer     NOT NULL CHECK (advance_booking_days > 0),
    near_term_horizon_minutes     integer     NOT NULL CHECK (near_term_horizon_minutes >= 0),
    freshness_threshold_seconds   integer     NOT NULL CHECK (freshness_threshold_seconds > 0),
    late_arrival_grace_minutes    integer     NOT NULL CHECK (late_arrival_grace_minutes > 0),
    created_at                    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (policy_ref, version_number)
);

-- ── simulator assignment (STA owns the assignment; Device Integration
--    projects it; no credentials here — FR-SIM-01 enrollment is the
--    device-integration task) ────────────────────────────────────────────────

CREATE TABLE station_operations.simulator_assignment (
    assignment_ref       uuid PRIMARY KEY,
    evse_ref             uuid        NOT NULL UNIQUE
        REFERENCES station_operations.evse (evse_ref),
    simulator_device_ref uuid        NOT NULL,
    assigned_at          timestamptz NOT NULL DEFAULT now()
);

-- ── immutability of ACTIVE versions (ARC-022 §5.7 snapshot semantics) ──────

CREATE FUNCTION station_operations.sta_reject_active_version_change()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% on an ACTIVE version is prohibited (%)', TG_OP, TG_TABLE_NAME;
END;
$$;

CREATE FUNCTION station_operations.sta_reject_component_change_if_version_active()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_state varchar(16);
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT state INTO v_state FROM station_operations.tariff_version
            WHERE tariff_version_ref = NEW.tariff_version_ref;
    ELSE
        SELECT state INTO v_state FROM station_operations.tariff_version
            WHERE tariff_version_ref = OLD.tariff_version_ref;
    END IF;
    IF v_state = 'ACTIVE' THEN
        RAISE EXCEPTION '% on components of an ACTIVE tariff version is prohibited', TG_OP;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER tariff_version_active_immutable
    BEFORE UPDATE OR DELETE ON station_operations.tariff_version
    FOR EACH ROW WHEN (OLD.state = 'ACTIVE')
    EXECUTE FUNCTION station_operations.sta_reject_active_version_change();

CREATE TRIGGER tariff_component_active_protected
    BEFORE INSERT OR UPDATE OR DELETE ON station_operations.tariff_component
    FOR EACH ROW
    EXECUTE FUNCTION station_operations.sta_reject_component_change_if_version_active();

CREATE TRIGGER booking_policy_version_active_immutable
    BEFORE UPDATE OR DELETE ON station_operations.booking_policy_version
    FOR EACH ROW WHEN (OLD.state = 'ACTIVE')
    EXECUTE FUNCTION station_operations.sta_reject_active_version_change();
