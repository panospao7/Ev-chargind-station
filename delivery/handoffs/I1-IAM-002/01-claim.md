---
role: orchestrator
taskId: I1-IAM-002
previousState: READY
resultingState: CLAIMED
baselineCommit: 589a36f7 (origin/main after owner merge of planning PR #48)
impactLevel: L3
date: 2026-09-14T14:00:00Z
---

# I1-IAM-002 — Claim handoff

## Authorization chain

- Planning PR #48 merged by owner → `589a36f7` on origin/main; packet
  `delivery/tasks/I1-IAM-002.yaml` approved (L3: token exchange + service
  identity + resource-server validation).
- Baseline for implementation: 589a36f7. Branch: task/i1-iam-002-ci.

## Scope reminder (from approved packet)

- Allowed: realm JSON + README, BFF session/security/exchange packages,
  resource-server security packages + config + pom + tests in Discovery &
  STA services, problem-codes registry, delivery records.
- Prohibited: contracts/openapi/** (no path changes expected — STOP if one
  proves necessary), docs/**, migrations (cache rides the session row;
  STOP if a migration proves truly required), compose, apps/web.
- Seven ACs; SEC-001 §7.3 nine negative proofs + §8.3 validation set;
  reviews: general + contract + security; human gate = owner merge.

## Next

Planner handoff, then coder (phased).
