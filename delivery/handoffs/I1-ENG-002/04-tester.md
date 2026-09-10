---
role: tester
taskId: I1-ENG-002
previousState: IMPLEMENTING
resultingState: SELF_VERIFIED
baselineCommit: 58b7eae5426389858037ee85d554656662264508
impactLevel: L2
date: 2026-09-10T20:40:00Z
---

# I1-ENG-002 — Tester handoff

## Executed (this worktree, 2026-09-10, diagnostic release 21 — JDK-25 caveat from I1-ENG-001 persists)

| Command | Result |
|---|---|
| `./mvnw -Djava.version=21 -Dmaven.compiler.release=21 -pl libraries/* -am test` | PASS — correlation 6/6, secure-logging 7/7, event-envelope 8/8, test-support 3/3 |
| `./mvnw -Djava.version=21 -Dmaven.compiler.release=21 test` (full reactor, 13 modules) | PASS — BUILD SUCCESS; 30 surefire summaries, all failures 0 |
| `./mvnw -pl libraries/test-support clean test` | PASS — **Testcontainers smoke: real PostgreSQL 18 answers `select version()`, real RabbitMQ 4.3 exposes AMQP** (digest-pinned) |
| `npm run contracts:verify` (G3) | PASS (exit 0) |
| `node scripts/delivery/validate.mjs` | PASS |
| `node scripts/delivery/self-test.mjs` | PASS — 8/8 |
| secretlint over `libraries/**` | PASS — clean |
| `git diff --check` | PASS |

## Notable test outcomes (defects found and fixed during the round — none hidden)

1. `Redactor.redactJson` initially never redacted: `readTree` yields
   `JsonNode`, not `Map` — caught by the test's first assertion, fixed via
   `ObjectNode` conversion, re-proven by tests.
2. Testcontainers 1.21.4 tests were **silently absent** under the Jupiter 6
   engine (compiled but not discovered). Confirmed via explicit `-Dtest=`
   ("No tests matching pattern"); resolved by the 2.0.5 migration documented
   in the coder handoff. This failure mode (silent non-discovery) is now a
   known risk for all future module test suites on Boot 4.1.

## Honest status against packet ACs

- AC-01 boundary tests: PASS (4/4 libraries guarded)
- AC-02 correlation: PASS
- AC-03 secure-logging redaction: PASS (fail-safe malformed input proven)
- AC-04 envelope validation + parity: PASS
- AC-05 test-support + no-production-dependency rule: PASS
- Java-25 authoritative build: NOT_RUN locally (same disclosed constraint as
  I1-ENG-001; poms unchanged at `java.version=25`)

No test was deleted, skipped, or weakened; no validator loosened.
