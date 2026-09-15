---
role: orchestrator
taskId: I1-IAM-002
previousState: READY
resultingState: CLAIMED
baselineCommit: 8475f5cd
impactLevel: L3
date: 2026-09-15T09:00:00Z
---

# I1-IAM-002 — Orchestrator claim handoff

Owner merged PR #48 (planning, L3 approval recorded) and directed
"continue". Claimed on `task/i1-iam-002-token-exchange`.

## Phase plan (IAM-001 cadence)

1. **Phase 1 (this round): problem-code registry (AC-07 registry part)** —
   TOKEN_AUDIENCE_INVALID (401) + INSUFFICIENT_SCOPE (403) added;
   TOKEN_INVALID note updated to reflect actual emission. G3 green.
2. Phase 2: realm `svc-*` finalization (AC-06) — audience mappers, exchange
   permissions, JWKS/key procedure; fresh-import Admin REST assertions.
3. Phase 3: BFF Standard Token Exchange V2 client + per-session encrypted
   cache (AC-01/03) + §7.3 negative matrix (AC-02).
4. Phase 4: resource-server validation in Discovery + STA internal APIs
   (AC-04/05).
5. Phase 5: close-out with honest PASS/NOT_RUN accounting; CI on PR.

Baseline: current origin/main at claim (see status.yaml). Environment: JDK
21 local diagnostic; CI workflows (Frontend Tests bff, G3, DB Migrations)
run JDK 25 on the PR.
