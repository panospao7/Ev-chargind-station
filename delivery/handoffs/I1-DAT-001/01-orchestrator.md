---
role: orchestrator
taskId: I1-DAT-001
previousState: BACKLOG
resultingState: CLAIMED
baselineCommit: 548f314cc57b40cd14c562ea2aa290e61d76b96c
impactLevel: L3
date: 2026-09-10T21:45:00Z
---

# I1-DAT-001 — Orchestrator claim handoff

## Human authorization (recorded verbatim)

Owner directive, 2026-09-10 (this session): **"authorize DAT-001"** — the L3
authorization required by the packet (`humanDecisions`) and by AGENTS.md §6
for migration/database work. Preconditions verified before claim:

- Dependency I1-ENG-001 VERIFIED (PR #9); compose core profile healthy.
- G3 gate EXECUTABLE APPROVED via merged governance closure (PR #12):
  zero OPEN contradictions — the GOV-007 entry criterion for persistence
  work is satisfied.
- Branch: `task/i1-dat-001-persistence`, stacked on
  `task/i1-eng-002-closeout` (unmerged; status.yaml lineage stays linear).

## Implementation plan (coder handoff follows as 03-coder.md)

1. `infra/local/postgres/01-provision.sh` — the real docker-entrypoint init:
   9 databases per ARC-022 §4 / ENG doc §6.3; per-DB owner/migrator/runtime
   LOGIN roles (dev-only passwords, env-overridable); `REVOKE CONNECT … FROM
   PUBLIC` then explicit per-role grants (this is what makes cross-service
   access actually fail, and testable).
2. Per-service `V1__baseline.sql` ×7 — service schema + runtime grants via
   default privileges; NO business tables (ARC-022 §5–§9 stay with owning
   services). `keycloak_db` provisioned per §6.3; Keycloak wiring itself
   stays with the identity task.
3. `scripts/db/` — retention-job registration template + contract (no
   deletion behavior, W3), developer migration docs.
4. Migration + role-separation test suite in `libraries/test-support`
   (Testcontainers, bind-mounting the REAL init dir; Flyway fresh-install,
   validate, upgrade, tampered-checksum and duplicate-version negatives;
   runtime-no-DDL, no-cross-DB, runtime-DML positives) — closes AC-02/03/04.
5. New CI workflow `.github/workflows/db-migrations.yml` (paths-scoped,
   least-privilege, JDK 25 in CI — which also gives the first authoritative
   Java-25 execution of the persistence tests).
6. Local re-provisioning: the compose postgres volume is wiped and
   re-initialized with the real provisioning (destructive ONLY to this
   session's own dev volume; no business data exists anywhere locally).

## Verification plan (tester handoff 04)

Compose re-init → 9 DBs + roles + grants asserted; migration suite green at
diagnostic release 21; G3 + delivery gates green; new CI workflow syntax
validated; secret scan.
