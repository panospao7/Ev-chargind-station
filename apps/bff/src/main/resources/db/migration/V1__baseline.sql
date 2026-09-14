-- =============================================================================
-- BFF — V1 baseline (I1-IAM-001)
-- Ownership per ARC-022 §4: bff_session_db (this service is the sole owner of
-- opaque application sessions and encrypted server-side OAuth material).
-- Run by the "bff_session_migrator" role via Flyway; the runtime role receives
-- DML-only grants through default privileges. NO session tables here — the
-- physical model is delivered by V2 (SEC-001 §5.3).
-- =============================================================================
CREATE SCHEMA IF NOT EXISTS bff_session;

GRANT USAGE ON SCHEMA bff_session TO bff_session_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA bff_session
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO bff_session_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA bff_session
    GRANT USAGE, SELECT ON SEQUENCES TO bff_session_runtime;
