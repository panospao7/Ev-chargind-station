---
role: tester
taskId: I1-STA-001
previousState: IMPLEMENTING
resultingState: SELF_VERIFIED
baselineCommit: b73faffacedd3dd2e80cd3422aa3e9463a81bc9c
impactLevel: L3
date: 2026-09-11T20:55:00Z
---

# I1-STA-001 — Tester handoff

## Executed (this worktree, 2026-09-11; diagnostic release 21 — JDK 25 authoritative in the new service-tests CI)

| # | Check | Result |
|---|---|---|
| 1 | `./mvnw -pl services/station-operations-service,libraries/test-support -am test` | PASS — test-support 16/16 (incl. corrected DAT suite over all seven services incl. STA V1+V2); station-operations 8/8 |
| 2 | AC-01 | PASS — exactly the 13 §9 tables exist after migration; fresh install clean; unique numbering + checksums via existing DAT suite |
| 3 | AC-02 | PASS — dataset invariants asserted: 1 org ACTIVE, 2 members, 2 PUBLISHED Greek stations (coordinate bounds), 4 ACTIVE EVSEs, 8 connectors (CCS 150 kW + TYPE2 22 kW per EVSE), 14 opening periods, 1 ACTIVE tariff version + 2 components (480/10 minor), 1 ACTIVE policy version with all eight S1-03 constants asserted individually, 4 simulator assignments |
| 4 | AC-03 | PASS — re-seed changes no counts; reset empties all 13 tables; reseed restores canonical state; seeder operates as the STA runtime role |
| 5 | AC-04 | PASS — five immutability negatives (UPDATE state, DELETE version, DELETE component, content UPDATE, component INSERT into ACTIVE) all rejected at database level |
| 6 | `./mvnw test` (full reactor, 13 modules) | PASS — BUILD SUCCESS |
| 7 | `npm run contracts:verify` | PASS (exit 0) |
| 8 | delivery validator + self-test | PASS — ALL CHECKS PASSED; 8/8 |
| 9 | secretlint over service + workflow | PASS — 0 findings |
| 10 | `git diff --check` | PASS |

## Notes for the reviewer

- The immutability trigger bypass exists ONLY in the reset's privileged
  maintenance path (migrator role, finally-block re-enable); tested that
  runtime role cannot create tables and every mutation attempt against
  ACTIVE rows fails even for the migrator.
- DAT suite `==1 → >=1` correction documented in the coder handoff — review
  that you accept this invariant evolution.
- The reset test runs last (ordered suite); single-test execution of order-7
  alone fails by design without the migrations of order-1 (documented).

## Honest AC status

AC-01..AC-05 PASS (AC-05's workflow run materializes on the PR — paths
match `services/station-operations-service/**` and `libraries/test-support/**`).
No test deleted, skipped, or weakened; the one corrected assertion is
recorded above with rationale.
