---
role: coder
taskId: I1-DAT-001
previousState: CLAIMED
resultingState: SELF_VERIFIED
baselineCommit: 548f314cc57b40cd14c562ea2aa290e61d76b96c
impactLevel: L3
date: 2026-09-10T22:10:00Z
---

# I1-DAT-001 — Coder handoff

## Implemented (packet `delivery/tasks/I1-DAT-001.yaml`)

1. **`infra/local/postgres/01-provision.sh`** — the real entrypoint init:
   nine databases (ARC-022 §4 / ENG doc §6.3, incl. `bff_session_db` and
   `keycloak_db`); per-database `owner`/`migrator`/`runtime` LOGIN roles
   (shared dev-only password, env-overridable via `PLATFORM_ROLE_PASSWORD`);
   `REVOKE ALL ON DATABASE … FROM PUBLIC` followed by explicit
   CONNECT/CREATE grants — which is what makes cross-service access genuinely
   fail (AC-02). Idempotent (safe on reused volumes). Keycloak→`keycloak_db`
   wiring itself remains the identity task's.
2. **`V1__baseline.sql` ×7** — one per canonical service: service schema +
   USAGE + DML default-privileges for the runtime role. NO business tables
   (ARC-022 §5–§9 belong to owning-service tasks). `apps/bff` has no
   migration dir yet (outside this packet's allowedFiles): its baseline
   arrives with the identity task that owns `bff_session_db`.
3. **Migration/role-separation suite** in `libraries/test-support`
   (16 tests total with the module): the container copies the REAL init dir
   (`withCopyFileToContainer`, hermetic across Windows/CI), so tests execute
   the same provisioning code path as compose. Covers: 9 DBs, 27 roles,
   runtime connect-own/cannot-connect-cross, PUBLIC-revoke proof, fresh
   install ×7, validate ×7, runtime-no-DDL, runtime-DML-on-migrator-table,
   upgrade path (V2 fixture), tampered-checksum rejection, duplicate-version
   rejection, on-disk unique numbering (AC-01–AC-04).
4. **`scripts/db/`** — migration pipeline docs + destructive local recreate
   command + backup compatibility notes (single-volume pg_dump story);
   `retention-job-registry.template.sql` — registration/execution contract
   explicitly NOT applied (no deletion behavior; W3) (AC-05).
5. **`.github/workflows/db-migrations.yml`** — new paths-scoped workflow
   (G3-style pinned actions, `permissions: contents: read`), running the
   suite on **JDK 25 temurin** — the first authoritative Java-25 execution
   of any test suite in this repo.

## Decisions and discoveries (for the reviewer)

- **Flyway history lives in the service schema** (`.schemas(schema)
  .defaultSchema(schema)`): PG15+ denies CREATE in `public` to non-owners —
  configuring the service schema is the canonical pattern; the platform BOM
  brings Flyway 12.4.0.
- **TC 2.x manages `POSTGRES_DB` itself** (withDatabaseName): raw `withEnv`
  overrides are ignored, which initially made the provisioning script fail
  with `database "evplatform_bootstrap" does not exist`. The script now
  derives the bootstrap DB from the entrypoint-provided `$POSTGRES_DB`, so
  it behaves identically under compose and Testcontainers.
- Init dir is **copied, not bind-mounted**, into TC containers (Windows
  file-sharing variance eliminated); a manual `docker run` probe and a
  `docker create/cp/start` probe confirmed provisioning parity.
- **`pg_database_owner`** (built-in) shows up in role-pattern queries — the
  suite asserts explicit role names, and the compose probe confirmed exactly
  our 27 roles.
- Local compose volume was wiped and re-initialized to prove the compose
  path end to end (this session's own dev volume; no business data exists).

## Disclosed scope notes

- Migrations run programmatically via Flyway in tests; the Spring Boot
  Flyway integration per service arrives with each service's delivery task.
- Applied-migration immutability is enforced by Flyway checksums + the new
  CI workflow; "no automatic production down migrations" is a discipline
  rule recorded in `scripts/db/README.md` (no production environment exists).
