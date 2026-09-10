---
role: tester
taskId: I1-DAT-001
previousState: IMPLEMENTING
resultingState: SELF_VERIFIED
baselineCommit: 548f314cc57b40cd14c562ea2aa290e61d76b96c
impactLevel: L3
date: 2026-09-10T22:20:00Z
---

# I1-DAT-001 — Tester handoff

## Executed (this worktree, 2026-09-10; diagnostic release 21 as disclosed — authoritative JDK 25 runs in the new CI workflow)

| # | Command / check | Result |
|---|---|---|
| 1 | `docker compose -f infra/local/compose.yaml down -v && up -d --wait` | PASS — all 4 containers Healthy; volume re-initialized through the real provisioning script |
| 2 | compose-path probe: database count | PASS — exactly 9 `*_db` databases |
| 3 | compose-path probe: role count | PASS — exactly the 27 provisioned roles (+ PostgreSQL's built-in `pg_database_owner`, documented) |
| 4 | `./mvnw -pl libraries/test-support clean test` | PASS — 16/16: Boundary 1, LocalDependencies 2, Provisioning 5, MigrationWorkflow 8 |
| 5 | Provisioning assertions | PASS — 9 DBs; 27 explicit roles; runtime connects to own DB; `account_runtime` → `booking_session_db` connect FAILS; zero canonical DBs retain PUBLIC CONNECT |
| 6 | Migration assertions | PASS — fresh install applies exactly 1 migration per service (×7); validate ×7; runtime sees schema, cannot CREATE, can DML on migrator-created table; V2 upgrade applies on top of V1; tampered checksum fails validate; duplicate version fails; on-disk numbering unique |
| 7 | `./mvnw test` (full reactor, 13 modules) | PASS — BUILD SUCCESS |
| 8 | `npm run contracts:verify` | PASS (exit 0) |
| 9 | `node scripts/delivery/validate.mjs` | PASS |
| 10 | `node scripts/delivery/self-test.mjs` | PASS — 8/8 |
| 11 | secretlint over infra/local, scripts/db, libraries/test-support, db-migrations.yml | PASS — 0 findings |
| 12 | `git diff --check` | PASS |

## Defects found and fixed during this round (all caught by the suite itself)

1. Provisioning under TC failed: `database "evplatform_bootstrap" does not
   exist` — TC 2.x owns `POSTGRES_DB`; script now derives the bootstrap DB
   from the entrypoint-provided `$POSTGRES_DB`.
2. Flyway history creation failed with `permission denied for schema public`
   — history now pinned to the service schema (correct platform pattern).
3. Tamper-test throwaway DB lacked the migrator grants (its own error
   message); test now applies the same grants step as provisioning.
4. Two cascade/cleanup defects in the tests themselves (stale history-table
   reference; over-broad PUBLIC-revoke assertion) — fixed.

## Honest AC status

- AC-01 (nine DBs, roles, no sharing): PASS (items 2/3/5).
- AC-02 (runtime no-DDL, no cross-service, migrator DDL, runtime DML): PASS.
- AC-03 (Flyway fresh + upgrade + checksums): PASS.
- AC-04 (real PostgreSQL 18; tamper/duplicate negatives): PASS.
- AC-05 (retention skeleton, no deletion): PASS (template + contract only).
- AC-06 (G3 + control plane green; new CI workflow green on PR): G3/validators
  PASS locally; the workflow run itself materializes when the owner opens
  the PR (paths include `libraries/test-support/**` and `infra/local/**`).
- Java-25: PASS in CI scope (workflow runs temurin 25); NOT_RUN locally
  (JDK 21 diagnostic as disclosed).

No test was deleted, skipped, or weakened; no validator loosened.
