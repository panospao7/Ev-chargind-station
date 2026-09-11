---
role: orchestrator
taskId: I1-MSG-001
previousState: null
resultingState: READY
baselineCommit: 5945c502c22a1a396c0fa3814dccec31694697e0
impactLevel: L3
date: 2026-09-12T00:20:00Z
---

# I1-MSG-001 — Planning handoff (messaging foundation + POC-04)

## What happened

Following the owner's standing "continue" direction after I1-CON-003 reached
VERIFIED (PR #21, G3 9/9 at 5945c502), the messaging foundation packet is
drafted: ARC-022 §8 integration tables (outbox_message, inbox_message,
idempotency_record, audit_event — exact §8 field/states/uniqueness specs) as
STA V3, a transactional outbox writer + polling dispatcher (publisher
confirms, mandatory, bounded retry → QUARANTINED), RabbitMQ topology per
ENG doc §6.4 via approved Spring AMQP, the seed emitting StationPublished
atomically with seed data, and the POC-04 behaviour proofs (at-least-once +
inbox dedupe, per-aggregate ordering, retry→quarantine, DLQ, append-only
audit).

## Readiness

- Dependencies VERIFIED: I1-DAT-001, I1-STA-001, I1-CON-003 (StationPublished
  registered with schema).
- L3 (migration + broker behavior): merging this planning PR records the
  owner's packet approval (standing precedent); claim follows the merged
  planning branch.

## Scope fences

No Discovery consumption (I1-DSC-001), no Booking capacity events (EPIC-10),
no other services' §8 copies, no retention deletion (W3). The POC-04 consumer
is a test/POC component, not Discovery.

## Recommended next agent

Owner: merge this planning PR (stacked on task/i1-con-003-closeout) →
orchestrator claims on the owner's go-ahead per standing cadence.
