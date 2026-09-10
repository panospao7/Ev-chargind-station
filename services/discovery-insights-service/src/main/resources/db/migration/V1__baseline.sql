-- =============================================================================
-- discovery-insights-service — V1 baseline (I1-DAT-001)
-- Ownership per ARC-022 §4: discovery_insights (this service is the sole owner).
-- Run by the "discovery_insights_migrator" role via Flyway; the runtime role receives
-- DML-only grants through default privileges. NO business tables here —
-- ARC-022 §5–§9 physical models are delivered by owning-service tasks.
-- =============================================================================
CREATE SCHEMA IF NOT EXISTS discovery_insights;

GRANT USAGE ON SCHEMA discovery_insights TO discovery_insights_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA discovery_insights
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO discovery_insights_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA discovery_insights
    GRANT USAGE, SELECT ON SEQUENCES TO discovery_insights_runtime;
