-- =============================================================================
-- station-operations-service — V1 baseline (I1-DAT-001)
-- Ownership per ARC-022 §4: station_operations (this service is the sole owner).
-- Run by the "station_operations_migrator" role via Flyway; the runtime role receives
-- DML-only grants through default privileges. NO business tables here —
-- ARC-022 §5–§9 physical models are delivered by owning-service tasks.
-- =============================================================================
CREATE SCHEMA IF NOT EXISTS station_operations;

GRANT USAGE ON SCHEMA station_operations TO station_operations_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA station_operations
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO station_operations_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA station_operations
    GRANT USAGE, SELECT ON SEQUENCES TO station_operations_runtime;
