---
role: orchestrator
taskId: I1-ENG-004
previousState: SELF_VERIFIED (CI evidence recorded)
resultingState: VERIFIED
baselineCommit: 23a15fe9
candidateCommit: ae72acce (implementation; 585848d7 evidence closeout)
mergeCommit: 8d9c2f0b (PR #42, owner merge, verified via git fetch + GitHub API)
impactLevel: L2
date: 2026-09-13T20:40:00Z
---

# I1-ENG-004 — Closeout handoff

## Verification chain

1. Owner merged PR #42 → merge commit `8d9c2f0b` on `origin/main`;
   candidate `ae72acce` contained (verified via git fetch + GitHub API
   pull 42: merged=True, merge_commit_sha=8d9c2f0b...).
2. CI: Frontend Tests run 34763965598 (pull_request event) SUCCESS — the
   workflow's first execution. Web job: npm ci, Unit tests, Production
   build (budgets), Dependency audit gate — all success. BFF job: JDK 25
   setup + Maven suite — all success. Run reference recorded in
   `delivery/evidence/I1-ENG-004/frontend-ci.md` §9 (commit 585848d7,
   included in the merge).
3. Independent reviews: general PASS_WITH_FINDINGS (2 MINOR — claim-commit
   convention disclosed; CI run refs — closed by §9; 4 NOTE), security
   PASS_WITH_FINDINGS (0 BLOCKER/MAJOR/MINOR, 5 NOTE: job timeouts
   repo-wide follow-up, dev-dep audit scope → EPIC-02, .nvmrc path-filter
   option, evidence list completeness N5 — noted, control-plane
   traceability). No unresolved BLOCKER/MAJOR.

## What landed

- `.github/workflows/frontend-tests.yml`: apps/** CI coverage — web job
  (npm ci, CI=true ng test, ng build with budgets, npm audit --omit=dev
  --audit-level=high gate) + bff job (Maven -pl apps/bff -am test, JDK 25
  temurin); contents: read; three pinned SHAs matching repo convention.
- maplibre-gl `^5.0.0` → `^6.9.0`: CRITICAL GHSA-jrc7-96c5-q579 (XSS
  sanitizer bypass, CVSS 10.0) remediated; lockfile diff confined to the
  maplibre subtree; adapter unchanged (checklist a–j verified against v6
  typings by coder and spot-checked by reviewer).
- ADR-016 5.24.x pin superseded by owner decision (recorded in packet);
  ADR-016 text update remains a backlog item (docs/** was prohibited).

## State transitions

- `I1-ENG-004: SELF_VERIFIED → VERIFIED` (human gate = owner merge PR #42).
- 17 tasks VERIFIED, zero open.

## Follow-up backlog (from reviews)

1. EPIC-02 pipeline hardening: ci/required aggregation, CodeQL, SBOM,
   container scanning, per-job timeout-minutes (repo-wide NOTE), dev-dep
   audit scope decision (security N3).
2. Path-filter follow-up: add `.nvmrc` to frontend-tests/service-tests
   triggers (security N4).
3. ADR-016 text update (maplibre 6.x pin) — documentation task.
4. maplibre v6 runtime staging check (WebGL rendering) — first real map
   interaction; pairs with the UI-foundation follow-up / E2E harness.
5. Claim-commit/status.yaml packet-template convention (general MINOR-1).

## Recommended next step

S1-04 authentication (Keycloak + BFF opaque session, POC-01) — the
roadmap-critical M2 gate that unblocks the booking spine (holds, bookings,
check-in, charging all require driver identity). Alternative smaller
slices: PUB-04/PUB-05 screens or STA status events.
