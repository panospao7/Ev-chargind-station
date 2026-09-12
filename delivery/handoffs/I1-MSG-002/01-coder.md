---
role: coder
taskId: I1-MSG-002
previousState: CLAIMED
resultingState: SELF_VERIFIED
baselineCommit: 1372d8182b1a9747ed1c662117445d78d5ee0c3b
impactLevel: L3
date: 2026-09-12T14:20:00Z
---

# I1-MSG-002 — Coder handoff

## Implemented (candidate commits 7316e08c + c6acfd59)

1. **`V4__integration_table_naming.sql`** (m1+m5) — additive: RENAME of the two
   auto-named V3 uniques to `uq_outbox_event_fact` / `uq_idempotency_scope`
   (planner verified the truncated auto-names on real PG 18.6) + index
   `ix_idempotency_expiry` on `idempotency_record(expires_at)`. V1–V3
   untouched.
2. **`OutboxDispatcher`** — claim-then-send protocol (M3): single auto-commit
   `UPDATE … WHERE message_id IN (SELECT … FOR UPDATE SKIP LOCKED) RETURNING`
   leasing `available_at` forward (30 s default); no `attempt_count` bump at
   claim; no lock/transaction held across the broker send. M1: after
   `confirm.isAck()`, `correlation.getReturned() != null` →
   `markAttempt(id, "UNROUTABLE")` (broker acks unroutable mandatory sends
   after basic.return — return is recorded on the CorrelationData
   unconditionally in spring-rabbit 4.1.1, verified by bytecode inspection).
   CAS guards `AND state='PENDING'` on both mark paths. `contentType`
   `application/json` on sends. `failure_category` truncated to the 48-char
   column bound. Javadoc: cross-instance per-aggregate reordering possible;
   consumers order by aggregate version.
3. **`OutboxWriter`** (m2) — targeted
   `ON CONFLICT (aggregate_type, aggregate_ref, aggregate_version,
   message_type) DO NOTHING`; PK collision with a different fact now raises.
4. **`application.yml`** (m4) — `outbox:` moved to top level (env overrides
   now bind); added `claim-lease-seconds`, `confirm-timeout-ms`.
5. **`SeedRunnerConfiguration`** (m6) — javadoc: seed-reset requires the
   migrator role (`STA_DB_USER=station_operations_migrator`).
6. **pom.xml** — `com.networknt:json-schema-validator:1.5.9` test scope
   (owner-approved; recorded in packet `recordedDecisions`).

## Tests added (existing 14 untouched, all green)

- **AC-01** Order 8: unroutable publish (queue unbound) → UNROUTABLE per pass
  → QUARANTINED at attempt 3, never PUBLISHED; binding restored in finally.
- **AC-02** Order 9: forced duplicate via `basicNack(requeue)` →
  `isRedeliver()` true → inbox insert returns 0 → exactly one inbox row.
- **AC-03** Order 10: two dispatchers (latch, 10 passes, 2 s lease) → every
  fact PUBLISHED once (attempt_count=1), none QUARANTINED; queue drain =
  exactly 3 messages / 3 distinct ids (the double-publish detector).
- **AC-04** `ContractSchemaValidationTest`: both seed envelopes validate
  against `cloud-event.json`; `data` against `station-published-event.json`;
  negative control (remove `id`) fails.
- **AC-05** SeedTest Order 8/9: new names present + old auto-names absent;
  real V3→V4 upgrade on a fresh container (`target("3")` → latest).
- **AC-06a** Order 11 (post-review fix): same-fact/different-message_id →
  silent no-op via direct `writer.append` with identical aggregate_ref;
  different-fact/same-message_id → PK violation surfaces.
- **AC-06b** Order 12: `ApplicationContextRunner` + `OUTBOX_MAX_ATTEMPTS:7` →
  dispatcher field == 7 (proves the yml key fix).
- **m7** SeedTest Order 10: failure injected after `seed()` in the ambient
  transaction (shared DataSource so the seeder joins) → all 17 tables roll
  back empty.

## Adaptations disclosed

- Claim-lease design per planner (no new state/column — §8.1 preserved).
- `WITH claimed AS` fallback not needed; pgJDBC handled UPDATE..RETURNING.
- Micro-fix round after independent reviews: failure_category truncation,
  Order-11 fixture honesty, dead helper removal, Order-10 comment correction.
- Known residual: `CorrelationData.Confirm.isAck()` deprecation warning
  (spring-rabbit 4.1.1) — replacement API decision deferred to a dependency
  bump task; not weakened or bypassed here.

## Environment disclosure

Local suite executed with `-Dmaven.compiler.release=21` (no JDK 25 on this
machine; diagnostic override per I1-ENG-001/I1-MSG-001 precedent). The
authoritative Java-25 execution is the PR's Service Tests + Database
Migrations workflows (temurin 25).
