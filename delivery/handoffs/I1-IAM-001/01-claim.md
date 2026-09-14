---
role: orchestrator
taskId: I1-IAM-001
previousState: READY
resultingState: CLAIMED
baselineCommit: 78a8ae9c (origin/main after owner merge of planning PR #44)
impactLevel: L3
date: 2026-09-13T21:40:00Z
---

# I1-IAM-001 — Claim handoff

## Authorization chain

- Planning PR #44 merged by owner → `78a8ae9c` on origin/main; packet
  `delivery/tasks/I1-IAM-001.yaml` approved (L3: authentication/session
  security, bff_session_db V1 migration, driver-bff contract extension).
- Owner approvals recorded via the planning merge: the S1-04 three-task
  split (I1-IAM-001/002/003) and the packet's recorded decisions.
- OQ-IAM-1 (local HTTPS): no owner preference stated → packet default
  applies — cookie-contract proof via test-emitted attribute assertions;
  local HTTPS deferred to the UI slice (recorded as assumption).

## Scope reminder (from approved packet)

- Allowed: realm import JSON, BFF pom/config/migrations/session+security
  Java, BFF tests, bounded web exception, driver-bff contract + problem
  codes + traceability registry, delivery records for this task.
- Prohibited: docs/**, services/**, compose changes (deviation needed),
  other contracts, other workflows.
- Seven ACs; SEC-P01 §5.4 and SEC-P02 §6.4 test mappings required; L3
  reviews: general + contract + data + security; human gate = owner merge.

## Next

Planner handoff, then coder.
