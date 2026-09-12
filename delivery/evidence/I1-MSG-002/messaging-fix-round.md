# I1-MSG-002 — Messaging fix round evidence

- **Task ID:** I1-MSG-002 (L3; packet approved via owner merge of PR #25)
- **Baseline commit:** 1372d8182b1a9747ed1c662117445d78d5ee0c3b
- **Candidate commits:** 7316e08c (implementation) + c6acfd59 (review micro-fixes)
- **Branch:** task/i1-msg-002-fix-round
- **Environment:** Windows 10.0.26200 x64, Docker Desktop 27.3.1, JDK 21.0.1 (diagnostic, `-Dmaven.compiler.release=21`), Maven wrapper 3.9.16
- **Date:** 2026-09-12

## Findings closed (from I1-MSG-001 independent reviews)

| Finding | Fix | Proof |
|---|---|---|
| M1 silent loss on unroutable publish | `getReturned()` check → UNROUTABLE → retry → QUARANTINED | Order 8 (real unbound-exchange test, per-pass assertions) |
| M2 vacuous dedup test | forced duplicate via basicNack requeue | Order 9 (`isRedeliver()` true, first=false, 1 inbox row) |
| M3 no claim protocol | SKIP LOCKED claim + `available_at` lease + CAS guards | Order 10 (two instances, queue drain = 3/3 distinct); data-reviewer protocol analysis (a)–(e) |
| M4 schema validation untested | networknt 1.5.9 (owner-approved) against executable contracts | ContractSchemaValidationTest + negative control |
| m1 unnamed constraints | V4 RENAME CONSTRAINT | SeedTest catalogue + old-names-absent assertions |
| m2 bare ON CONFLICT | targeted conflict columns | Order 11 (no-op vs PK-violation, post-review non-vacuous fixture) |
| m4 inert config keys | top-level `outbox:` block | Order 12 (`OUTBOX_MAX_ATTEMPTS:7` → field == 7) |
| m5 missing expiry index | V4 `ix_idempotency_expiry` | SeedTest pg_indexes assertion |
| m6 seed-reset docs | javadoc: migrator role required | SeedRunnerConfiguration javadoc |
| m7 atomicity not failure-proven | shared-DataSource ambient-tx injection | SeedTest Order 10 (all 17 tables roll back empty) |

## Local gate results (diagnostic JDK 21)

- Focused reactor: **24/24 STA tests + 16/16 test-support, BUILD SUCCESS**
- MessagingFoundationTest 12 · ContractSchemaValidationTest 1 ·
  StationOperationsSeedTest 10 · ApplicationTests 1
- Independent reviews: data **PASS_WITH_FINDINGS** (protocol reasoned correct
  (a)–(e); MINOR failure_category bound — fixed), general
  **PASS_WITH_FINDINGS** (AC-01..06 PASS; MINORs fixed in c6acfd59)

## Authoritative CI status

**GREEN at merge commit 1afa126e (PR #26, owner merge 2026-09-12), verified
via GitHub API check-runs:**

- Service Tests (Station Operations Service, S1-01 seed, JDK 25 temurin): **success**
- Flyway Migrations and Role Separation (PostgreSQL 18): **success**

These are the authoritative Java-25 executions for AC-01..AC-06. Local
release-21 run was diagnostic only.

## Residual disclosures

- `CorrelationData.Confirm.isAck()` deprecation warning (spring-rabbit
  4.1.1) — future dependency-bump decision, unchanged here.
- Remaining V3 auto-named PK/CHECK constraints are a tracked §10 follow-up
  (data reviewer finding 2) — out of this packet's bounded scope.
- Cross-instance per-aggregate reordering is possible by design; consumers
  order by aggregate version (documented in dispatcher javadoc; I1-DSC-001
  must implement version-based ordering).
- No secrets or personal data in this evidence.
