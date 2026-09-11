-- =============================================================================
-- device-integration-service — V1 baseline (I1-DAT-001)
-- Ownership per ARC-022 §4: device_integration (this service is the sole owner).
-- Run by the "device_integration_migrator" role via Flyway; the runtime role receives
-- DML-only grants through default privileges. NO business tables here —
-- ARC-022 §5–§9 physical models are delivered by owning-service tasks.
-- =============================================================================
CREATE SCHEMA IF NOT EXISTS device_integration;

GRANT USAGE ON SCHEMA device_integration TO device_integration_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA device_integration
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO device_integration_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA device_integration
    GRANT USAGE, SELECT ON SEQUENCES TO device_integration_runtime;
