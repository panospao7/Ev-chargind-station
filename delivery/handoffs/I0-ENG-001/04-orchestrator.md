---
role: orchestrator
taskId: I0-ENG-001
previousState: SELF_VERIFIED
resultingState: HUMAN_REVIEW
baselineCommit: 3d17040635565f3608cfe208d928ca81c35d577f
candidateCommit: 4ff1249b
impactLevel: L2
date: 2026-09-09T22:10:00Z
---

# I0-ENG-001 — Orchestrator handoff (owner decisions applied, ready for human review)

## Owner decisions recorded (2026-09-09)

1. **DEC-001 (mTLS encoding): Option A** — remove the invalid `mutualTLS` securityScheme
   from all seven internal OpenAPI 3.0.3 documents, declare `serviceToken` (http bearer)
   as the scheme, document transport-layer mTLS in `info.description`. Implemented and
   `contracts:openapi` passes (0 errors, 71 non-failing warnings).
2. **SPLIT-001 (registry schemas): follow-up task approved** — the message registry's 78
   nonexistent `schemaPath` targets and pre-consolidation content are to be remediated by a
   new task (working title `I0-CON-002: rebuild executable registries against DOM-002 /
   ARC-022`). The packet, backlog entry, and status transitions are control-plane files
   outside this branch's allowedFiles; they will be committed on a control-plane branch for
   the owner to merge after this task. `contracts:registries` stays honestly red until then.

## Final validator matrix (local, Node v22.12.0; CI executes Node 24 from .nvmrc)

| Validator | Result |
|---|---|
| contracts:openapi | PASS (0 errors) |
| contracts:asyncapi | PASS |
| contracts:schemas | PASS (7 schemas, draft-2020-12 compiled) |
| contracts:privacy | PASS |
| contracts:docs | PASS |
| contracts:secrets | PASS (fail closed) |
| contracts:self-test | PASS (21/21) |
| contracts:registries | FAIL — expected red per SPLIT-001 (approved) |

## Review state

Planner, tester, general, contract, and security review sign-offs required per packet
(`reviews:`). Coder and tester roles were executed by one agent session (disclosed in
02/03-coder/tester handoffs); independent review is the PR review. Human review = PR merge
by the Project Owner / Contract Owner.

## After merge (control-plane follow-ups, separate branch)

1. Create `delivery/tasks/I0-CON-002.yaml` + backlog entry (SPLIT-001 remediation).
2. Record I0-ENG-001 candidate/merge SHAs, CI run reference, and final task state in
   `delivery/status.yaml`.
3. Note for the registry task: GOV-006 CON-175/176 close only when a fully green G3 run
   exists on main, which requires SPLIT-001's remediation first.
