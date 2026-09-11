-- =============================================================================
-- notification-service — V1 baseline (I1-DAT-001)
-- Ownership per ARC-022 §4: notification (this service is the sole owner).
-- Run by the "notification_migrator" role via Flyway; the runtime role receives
-- DML-only grants through default privileges. NO business tables here —
-- ARC-022 §5–§9 physical models are delivered by owning-service tasks.
-- =============================================================================
CREATE SCHEMA IF NOT EXISTS notification;

GRANT USAGE ON SCHEMA notification TO notification_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA notification
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO notification_runtime;

ALTER DEFAULT PRIVILEGES IN SCHEMA notification
    GRANT USAGE, SELECT ON SEQUENCES TO notification_runtime;
