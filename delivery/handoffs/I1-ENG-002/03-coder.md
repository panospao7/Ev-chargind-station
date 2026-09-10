---
role: coder
taskId: I1-ENG-002
previousState: CLAIMED
resultingState: SELF_VERIFIED
baselineCommit: 58b7eae5426389858037ee85d554656662264508
candidateCommit: pending-at-commit-time
impactLevel: L2
date: 2026-09-10T20:30:00Z
---

# I1-ENG-002 — Coder handoff

## Implemented (packet `delivery/tasks/I1-ENG-002.yaml`)

1. **libraries/correlation** — `CorrelationIds` (UUIDv4 generation/validation,
   ARC-022 §8 uuid convention) and `CorrelationContext` (thread-scoped
   correlation + causation, lazy init at entry points, explicit `clear()`
   discipline). Tests: uniqueness (10k), UUID validation, thread isolation,
   causation binding, clear semantics.
2. **libraries/secure-logging** — `SensitiveFields` (denylist mirroring the
   ENG-001 doc §9 never-log list; separator/case normalization; null-name
   fails closed), `Redactor` (recursive map masking; JSON path that never
   echoes malformed input), `SafeLogger` (slf4j facade routing every
   key-value pair through the redactor pre-appender). Tests cover each §9
   never-log category, separator variants, nested maps, malformed JSON.
3. **libraries/event-envelope** — `CloudEventEnvelope` (CloudEvents 1.0
   structured envelope per ARC-020 §2) with `validate()` enforcing required
   `specversion/type/source/id`, the `"1.0"` const, RFC 3339 `time`,
   non-blank `source`, tolerated extension attributes. **Parity test reads
   the canonical `contracts/schemas/common/cloud-event.json` and fails on
   drift** between executable schema and library.
4. **libraries/test-support** — `LocalDependencies` (Testcontainers factories
   pinned to the release-manifest digests for postgres:18 and
   rabbitmq:4.3-management), `MutableClock` (deterministic clock). Smoke test
   starts BOTH real containers (this closes ENG-001's AC-03 partial).
5. **Boundary guards** — each library carries a zero-dependency source-scan
   test forbidding application-module, Spring, and persistence imports
   (ENG-001 doc §4.1; packet AC-01).

## Key technical decision (disclosed for review)

**Testcontainers 2.0.5** (module BOM import; the platform Boot BOM does not
manage Testcontainers). The initial 1.21.4 pin was **silently incompatible**
with the JUnit Jupiter 6 that Spring Boot 4.1 manages: the 1.x extension
compiled against Jupiter 5 is compiled but never discovered by the Jupiter 6
engine — no error, just a missing test. Root cause was confirmed by explicit
`-Dtest=` run ("No tests matching pattern"). Migration to the 2.x line
involved: renamed artifacts (`testcontainers-junit-jupiter/-postgresql/
-rabbitmq`), new canonical packages (`org.testcontainers.postgresql/.rabbitmq`),
non-generic container classes, and an explicit `org.postgresql:postgresql`
test dependency (2.x no longer carries the JDBC driver transitively).
Follow-on effect for I1-DAT-001: migration tests can rely on the same
factories — now proven against real PostgreSQL 18 and RabbitMQ 4.3.

## Dependencies used (all version-managed or explicitly pinned)

- slf4j-api, jackson-databind, junit-jupiter: Spring Boot 4.1.1 BOM
- testcontainers 2.0.5 BOM: imported in the test-support module with the
  version and rationale recorded in the pom

No business authority anywhere in the libraries (boundary-guarded); no
dependency was introduced outside BOM management without a recorded pin.
