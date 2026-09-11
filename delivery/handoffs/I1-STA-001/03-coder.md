---
role: coder
taskId: I1-STA-001
previousState: CLAIMED
resultingState: SELF_VERIFIED
baselineCommit: b73faffacedd3dd2e80cd3422aa3e9463a81bc9c
impactLevel: L3
date: 2026-09-11T20:45:00Z
---

# I1-STA-001 — Coder handoff

## Implemented (packet `delivery/tasks/I1-STA-001.yaml`)

1. **`V2__station_operations_w1s1.sql`** — exactly the thirteen ARC-022 §9
   W1-S1 Station Operations tables in schema `station_operations`, with
   lifecycle CHECK constraints mirroring the executable lifecycles registry
   (OPERATOR_ORGANIZATION: ACTIVE/SUSPENDED/CLOSED; STATION:
   DRAFT/PUBLISHED/TEMPORARILY_CLOSED/DEACTIVATED; EVSE_ADMINISTRATION:
   ACTIVE/DISABLED/DEACTIVATED), Greek fixture validation bounds, and the
   S1-03 constants as CHECK-constrained columns of booking_policy_version.
   ACTIVE-version immutability enforced by triggers on tariff_version,
   booking_policy_version and their components (INSERT/UPDATE/DELETE
   rejected while the parent version is ACTIVE — ARC-022 §5.7 snapshot
   semantics). Functions precede triggers; no table outside the §9 list.
2. **Seed/reset** — `SeedDataset` (fixed deterministic identifiers, fixture
   data clearly named "Seed Fixture", zero credentials/personal data);
   `StationOperationsSeeder` (JdbcClient, ON CONFLICT DO NOTHING +
   existence guards where protection triggers interact with re-runs);
   `StationOperationsReset` (FK-safe child-first delete; disables the three
   protection triggers as the MIGRATOR role — the tables' owner, the same
   privilege path as Flyway — and re-enables them in a finally block);
   `SeedRunnerConfiguration` (`seed` / `seed-reset` profiles, JVM exits
   after run). Canonical activation ordering: version inserted DRAFT,
   components inserted, then a guarded UPDATE to ACTIVE.
3. **Tests** — `StationOperationsSeedTest` (ordered, real provisioned
   PostgreSQL 18 via the shared factories): 13-table set equality against
   ARC-022 §9, full S1-01 dataset invariants incl. every S1-03 constant,
   idempotent re-seed, five immutability negatives, runtime-no-DDL,
   reset-then-reseed restoration. Plus the DAT-001 suite correction noted
   below.
4. **CI** — `.github/workflows/service-tests.yml` (JDK 25 temurin,
   paths-scoped, G3-style pinned actions).

## Cross-suite correction (disclosed)

DAT-001's `MigrationWorkflowTest.freshInstallAppliesExactlyOneMigration
PerService` asserted `==1` migration per service — a DAT-era snapshot that
STA's legitimate V2 broke (with cascading aborts). Corrected to the actual
acceptance invariant: `>=1` migration applies cleanly from empty; comment
records the evolution. No assertion weakening: the flyway-history and
integrity checks are unchanged.

## Defects caught by tests during the round (all fixed, none hidden)

1. Trigger/function ordering in V2 (function referenced before creation).
2. Tariff activation ordering vs the protection trigger (DRAFT → components
   → guarded activation; ON CONFLICT does not suppress BEFORE-row triggers,
   so component inserts are existence-guarded).
3. Non-hex UUID literal (`g1`) in the dataset.
4. Reset needed the privileged migrator path (trigger bypass with guaranteed
   re-enable) — runtime/migrator separation preserved and tested.

## Usage

```bash
java -jar station-operations-service.jar --spring.profiles.active=seed       # apply dataset
java -jar station-operations-service.jar --spring.profiles.active=seed-reset # reset + reseed
```
