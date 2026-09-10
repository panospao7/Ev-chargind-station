-- =============================================================================
-- Retention-job registry TEMPLATE (I1-DAT-001 AC-05 skeleton) — NOT APPLIED.
--
-- This DDL is a template only. It is applied by an OWNING SERVICE's forward
-- migration when that service first schedules retention jobs. No deletion
-- behavior exists anywhere in the platform yet: retention ENFORCEMENT is W3
-- (GOV-007 §5); this table only allows REGISTERING intended jobs (AC-05:
-- "job registration, execution contract, without any data-deletion behavior").
--
-- Contract (execution semantics for the future retention worker):
--   * a job row is a declaration of intent; it never executes DML itself;
--   * any future deletion job MUST be gated by an approved retention
--     decision (W3/G6) and MUST create audit evidence per row batch;
--   * jobs reference tables in the OWNING service's own schema only
--     (cross-service retention is prohibited by AGENTS.md §4).
-- =============================================================================

CREATE TABLE IF NOT EXISTS retention_job (
    job_ref          uuid PRIMARY KEY,
    job_name         varchar(96)  NOT NULL UNIQUE,
    target_schema    varchar(64)  NOT NULL,
    target_table     varchar(64)  NOT NULL,
    retention_class  varchar(32)  NOT NULL,          -- e.g. GOV-007 §6 replay/dedup retention classes
    execution_window varchar(32)  NOT NULL,          -- e.g. 'daily 02:00-04:00 UTC'
    state            varchar(16)  NOT NULL,          -- REGISTERED | SUSPENDED (never AUTO-EXECUTED in W1)
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    CONSTRAINT retention_job_state_ck CHECK (state IN ('REGISTERED', 'SUSPENDED'))
);
