# I1-DAT-001 — Persistence foundation validation evidence

- **Task ID:** I1-DAT-001 (L3, owner-authorized 2026-09-10 "authorize DAT-001")
- **Baseline commit:** 548f314cc57b40cd14c562ea2aa290e61d76b96c (origin/main after PR #12)
- **Environment:** Windows 10.0.26200 x64, Git Bash; Docker Desktop 27.3.1 (engine running); JDK 21.0.1 (diagnostic); Maven wrapper 3.9.16; Node 22.12.0
- **Date:** 2026-09-10
- **Result:** PASS (authoritative Java-25 execution delegated to the new CI workflow)

## Provisioned topology (compose path, AC-01)

`docker compose down -v && up -d --wait` — volume wiped, all 4 containers
Healthy. Provisioning executed `infra/local/postgres/01-provision.sh` on
first init. Machine probe of the running container:

- databases: exactly **9** (`account_db`, `station_operations_db`,
  `booking_session_db`, `device_integration_db`, `discovery_insights_db`,
  `notification_db`, `governance_support_db`, `bff_session_db`,
  `keycloak_db`)
- roles: exactly the **27** provisioned `<base>_{owner,migrator,runtime}`
  (PostgreSQL's built-in `pg_database_owner` also matches name patterns and
  is documented)
- hardening: `CONNECT` revoked from PUBLIC on all nine; migrator holds
  database-level `CREATE`

## Test suites (Testcontainers PostgreSQL 18, digest-pinned)

`./mvnw -pl libraries/test-support clean test` → **16/16 PASS**:

| Suite | Tests | Covers |
|---|---|---|
| ProvisioningTest | 5 | 9 DBs; 27 explicit roles; runtime→own DB connect; cross-service connect denied; PUBLIC-revoke proof |
| MigrationWorkflowTest | 8 | fresh install ×7 (exactly V1); validate ×7; runtime no-DDL; runtime DML on migrator table; V2 upgrade path; tampered checksum rejected; duplicate version rejected; unique numbering on disk |
| LocalDependenciesTest | 2 | real PostgreSQL 18 + RabbitMQ 4.3 (digest-pinned) |
| BoundaryGuardTest | 1 | no forbidden imports in test-support |

Full reactor `./mvnw test` → BUILD SUCCESS (13 modules).

## Gates

`npm run contracts:verify` exit 0 · delivery validator ALL CHECKS PASSED ·
self-test 8/8 · secretlint 0 findings · `git diff --check` clean.

## Limitations

- Local Java execution is the disclosed diagnostic release 21; the new
  `db-migrations.yml` workflow runs the same suite authoritatively on
  JDK 25 temurin in CI.
- Retention-job registry is a template only — no deletion behavior exists.
- No secrets or personal data in this evidence; all role passwords are the
  documented development-only value.
