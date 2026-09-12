# I1-MSG-001 — Messaging foundation validation evidence

- **Task ID:** I1-MSG-001 (L3; packet approved via owner merge of PR #23)
- **Baseline commit:** fd9559833d89c8a7b02c7332c67f2255eb8d0cea
- **Environment:** Windows 10.0.26200 x64, Git Bash; Docker Desktop 27.3.1; JDK 21.0.1 (diagnostic); Maven wrapper 3.9.16
- **Date:** 2026-09-12
- **Result:** PASS (authoritative Java-25 execution via Service Tests + Database Migrations workflows on the PR)

## POC-04 behaviours (real PostgreSQL 18 + RabbitMQ 4.3, digest-pinned)

1. ARC-022 §8 tables exist with exact states/uniqueness (V3 migration).
2. Seed atomicity: data + 2 StationPublished outbox facts commit in one
   transaction; re-seed produces no duplicate facts (§8.1 constraint).
3. Dispatcher: synchronous publisher confirms; rows PUBLISHED only on ack;
   per-aggregate ordering; quorum queue receives both envelopes on
   ev.domain.v1 / station.published.
4. At-least-once + inbox: forced duplicate consumption deduplicated by
   (consumer_name, message_id) — single effect.
5. Bounded retry: closed-port broker failure → attempts increment with
   failure category → QUARANTINED after exhaustion.
6. audit_event append-only for the runtime role (UPDATE/DELETE rejected).
7. idempotency_record scope uniqueness enforced.

## Gates

Full reactor BUILD SUCCESS · contracts:verify exit 0 · delivery validator
ALL CHECKS PASSED · self-test 8/8 · secretlint 0 findings · git diff --check
clean.

## Limitations

Local Java is the disclosed diagnostic release 21. The POC-04 consumer is a
test component; Discovery's real consumer is I1-DSC-001. Synchronous
per-message confirm await is a documented W1-S1 volume decision. No secrets
or personal data in this evidence.
