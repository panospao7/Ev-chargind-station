---
role: orchestrator
taskId: I1-STA-001
previousState: null
resultingState: READY
baselineCommit: 6050685ac77a4f0e056a232b37a729771ff168f0
impactLevel: L3
date: 2026-09-11T09:40:00Z
---

# I1-STA-001 — Planning handoff (S1-01 infrastructure seed)

## What happened

Owner selected the orchestrator recommendation ("let's do the
recommendation") after I1-DAT-001 reached VERIFIED: draft the S1-01
infrastructure seed packet. Planning records created:

- `delivery/tasks/I1-STA-001.yaml` — L3 packet: Station Operations W1-S1
  physical tables (EXACTLY the ARC-022 §9 list of 13) + deterministic,
  idempotent, service-owned seed/reset producing the GOV-007 §3 S1-01
  dataset with the S1-03 constants baked into the active booking-policy
  version. No REST APIs; identity seeding explicitly deferred to the
  identity task.
- Iteration I1 record, backlog and status updated (task BACKLOG/READY,
  counts adjusted).

## Readiness (Definition of Ready)

- Dependencies VERIFIED: I1-ENG-001, I1-DAT-001 (the seed builds directly on
  the provisioning, roles and migration-test factories DAT-001 delivered).
- Packet complete; acceptance criteria measurable (AC-01..AC-05).
- L3: merging this planning PR records the owner's packet approval
  (I1-DAT-001 precedent); implementation starts on the owner's go-ahead.

## Key scoping decisions (orchestrator authority; flagged for review)

1. **Station Operations only.** S1-01's test identities (driver/operator/
   admin) live in Keycloak + account_db and belong to the identity task;
   seeding them here would cross service-implementation boundaries before
   those tasks exist.
2. **Data model is in scope, APIs are not.** ARC-022 §9 fixes the exact
   W1-S1 table list; FR-OPS-02's management APIs stay W1-S2.
3. **Immutability at the database level** for ACTIVE tariff/policy versions
   (mirrors ARC-022 §5.7 snapshot semantics) — a constraint/trigger, tested
   negatively.
4. Greek station metadata is clearly-marked invented fixture data.

## Branch topology

`task/i1-sta-001-planning` is stacked on `task/i1-dat-001-closeout` (the
DAT-001 VERIFIED flip is not yet merged to main). Merge order: close-out
first, then this planning PR.

## Required human action

1. Merge `task/i1-dat-001-closeout` (if not already).
2. Review + merge this planning PR → packet approved (L3).
3. Say "go" → I claim I1-STA-001 and implement.
