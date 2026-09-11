-- =============================================================================
-- governance-support-service — V1 baseline (I1-DAT-001)
-- Ownership per ARC-022 §4: governance_support (this service is the sole owner).
-- Run by the "governance_support_migrator" role via Flyway; the runtime role receives
-- DML-only grants through default privileges. NO business tables here —
-- ARC-022 §5–§9 physical models are delivered by owning-service tasks.
-- =============================================================================
CREATE SCHEMA IF NOT EXISTS governance_support;

GRANT USAGE ON SCHEMA governance_support TO governance_support_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA governance_support
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO governance_support_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA governance_support
    GRANT USAGE, SELECT ON SEQUENCES TO governance_support_runtime;
