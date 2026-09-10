-- =============================================================================
-- booking-session-service — V1 baseline (I1-DAT-001)
-- Ownership per ARC-022 §4: booking_session (this service is the sole owner).
-- Run by the "booking_session_migrator" role via Flyway; the runtime role receives
-- DML-only grants through default privileges. NO business tables here —
-- ARC-022 §5–§9 physical models are delivered by owning-service tasks.
-- =============================================================================
CREATE SCHEMA IF NOT EXISTS booking_session;

GRANT USAGE ON SCHEMA booking_session TO booking_session_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA booking_session
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO booking_session_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA booking_session
    GRANT USAGE, SELECT ON SEQUENCES TO booking_session_runtime;
