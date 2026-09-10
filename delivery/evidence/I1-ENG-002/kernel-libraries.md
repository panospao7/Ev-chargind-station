# I1-ENG-002 — Kernel libraries validation evidence

- **Task ID:** I1-ENG-002
- **Baseline commit:** 58b7eae5426389858037ee85d554656662264508 (origin/main after PR #9)
- **Environment:** Windows 10.0.26200 x64, Git Bash; Docker Desktop 27.3.1 (engine running); JDK 21.0.1; Node 22.12.0; Maven wrapper 3.9.16
- **Date:** 2026-09-10
- **Result:** PASS with disclosed JDK-25 constraint (authoritative build NOT_RUN locally; diagnostic release-21 green)

## Test suites

| Module | Tests | Result | Covers |
|---|---|---|---|
| correlation | 6 | PASS | UUID gen/validation; thread-scoped context isolation; causation binding; clear discipline |
| secure-logging | 7 | PASS | §9 never-log denylist masked (tokens/secrets/passwords/authorization/private keys/QR/email); separator variants; nested redaction; malformed-JSON never-echo; fail-closed unnamed values |
| event-envelope | 8 | PASS | required attr enforcement; specversion const; RFC 3339 time; blank source; extension tolerance; JSON round-trip; **parity guard vs contracts/schemas/common/cloud-event.json** |
| test-support | 3 | PASS | boundary guard; **real PostgreSQL 18 + RabbitMQ 4.3 start via Testcontainers 2.0.5** (digest-pinned, AC-03 of I1-ENG-001 closed) |

Full reactor: `./mvnw test` (diagnostic release 21) — BUILD SUCCESS, 13/13
modules, all summaries failures=0.

## Gates

`npm run contracts:verify` exit 0 · delivery validator ALL CHECKS PASSED ·
self-test 8/8 · secretlint clean · `git diff --check` clean.

## Limitations

Local-only, diagnostic Java release (21) as disclosed in both ENG-001 and
ENG-002 handoffs; CI verification of the authoritative release happens via
the PR's G3 run (contracts scope) and future EPIC-02 service CI. No secrets
or personal data in this evidence.
