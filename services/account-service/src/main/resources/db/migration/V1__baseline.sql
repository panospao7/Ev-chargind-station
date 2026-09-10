-- =============================================================================
-- account-service — V1 baseline (I1-DAT-001)
-- Ownership per ARC-022 §4: account (this service is the sole owner).
-- Run by the "account_migrator" role via Flyway; the runtime role receives
-- DML-only grants through default privileges. NO business tables here —
-- ARC-022 §5–§9 physical models are delivered by owning-service tasks.
-- =============================================================================
CREATE SCHEMA IF NOT EXISTS account;

GRANT USAGE ON SCHEMA account TO account_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA account
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO account_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA account
    GRANT USAGE, SELECT ON SEQUENCES TO account_runtime;
