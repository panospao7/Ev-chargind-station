---
role: coder
taskId: I1-MSG-001
previousState: CLAIMED
resultingState: SELF_VERIFIED
baselineCommit: fd9559833d89c8a7b02c7332c67f2255eb8d0cea
impactLevel: L3
date: 2026-09-12T09:30:00Z
---

# I1-MSG-001 — Coder handoff

## Implemented

1. **`V3__integration_tables.sql`** — the four ARC-022 §8 tables with exact
   field lists, CHECKed states (PENDING/PUBLISHED/QUARANTINED;
   PROCESSING/COMPLETED/FAILED/SKIPPED; IN_FLIGHT/COMPLETED/EXPIRED),
   §8.1 unique event-fact constraint, §8.2 inbox PK (consumer_name,
   message_id), §8.3 scope uniqueness with retention-as-data (expires_at;
   no deletion behavior), and append-only audit_event enforced by REVOKE
   UPDATE/DELETE from the runtime role.
2. **`OutboxWriter`** (@Component) — participates in the caller's
   transaction; validates the CloudEvents envelope's required fields and
   specversion before persisting (conformance by construction).
3. **`OutboxDispatcher`** — polling publisher: pending batch ordered by
   aggregate ref/version/occurrence, sends via Spring AMQP with mandatory
   flag + synchronous publisher-confirm wait; ack → PUBLISHED; failure →
   attempt count + backoff (available_at) → QUARANTINED at exhaustion with
   failure category. Routing key derived from the registry naming convention
   (com.evplatform.<domain>.<event>.v1 → <domain>.<event>). A @Scheduled
   wrapper drives it in production; tests call dispatchOnce() directly.
4. **`RabbitTopologyConfiguration`** — the five registry anchor exchanges as
   lazy Exchange beans (declared by RabbitAdmin on first connection so the
   context starts without a broker); RabbitTemplate with correlated confirms
   + mandatory.
5. **Seed** — now transactional (TransactionTemplate): data plus one
   StationPublished outbox fact per published station commit atomically.
6. **Reset** — extended to the four integration tables (privileged migrator
   path, unchanged pattern).
7. **Config** — spring-boot-starter-amqp + explicit jackson-databind (Boot
   4.1 web starter no longer carries it); rabbitmq + outbox properties in
   application.yml with env overrides and dev-only defaults.

## POC-04 behaviour suite (7 tests)

Tables exist (AC-01) · seed emits 2 StationPublished facts atomically +
unique-fact idempotency (AC-02) · fact-uniqueness protection · dispatcher
publish-with-confirm to a quorum queue bound on ev.domain.v1 + POC consumer
deduplicates duplicates via the inbox (AC-03) · broker-failure retry
exhaustion → QUARANTINED with failure category · audit append-only for
runtime (AC-04) · idempotency scope uniqueness.

## Disclosed notes

- Publisher confirms are awaited synchronously per message — correct and
  simple for W1-S1 volumes; higher-throughput batching is a later
  optimization, not a semantics change.
- The AsyncAPI exchange-name drift (from I1-CON-003) remains flagged for the
  contract reviewer.
- Reset deletes audit/idempotency rows via the privileged migrator path —
  local/CI fixture operation only (documented in the class).
