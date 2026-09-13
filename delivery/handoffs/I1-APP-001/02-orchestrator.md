---
role: orchestrator
taskId: I1-APP-001
previousState: CI_PENDING
resultingState: VERIFIED
baselineCommit: 7389191d
candidateCommit: 4f293db9d8680d17ff6849492e5710c2827f4369
mergeCommit: f6fea123 (PR #39, owner merge, verified via git fetch + GitHub API)
impactLevel: L2
date: 2026-09-13T15:10:00Z
---

# I1-APP-001 — Orchestrator closeout handoff

## Verification chain

1. Owner merged PR #39 → merge commit `f6fea123` on `origin/main`; candidate
   `4f293db9` contained.
2. **CI note (disclosed at handoff):** no workflow covers `apps/**` or this
   PR's delivery-only paths, so no CI runs were owed or executed — the
   packet's evidence stands on the local suites (web 44/44, BFF 9/9, build,
   G3 exit 0, greps 0/0) plus the independent general and security reviews.
   The frontend CI gap is I1-ENG-004's scope (P0 follow-up).
3. Independent reviews: general FAIL→fixed (4 MAJORs + 4 MINORs closed:
   locale-switch route preservation, map-movement no-search, honest BFF
   header test, stationRef charset gate); security PASS_WITH_FINDINGS (no
   blockers; F-S1 dot-segments + F-S2 comment accuracy closed in the fix
   round; proxy exposure audit PASS; data-in-UI audit PASS).

## Live bring-up validation (owner-observed)

After the merge, the full stack was brought up manually and then automated:
docker compose core profile healthy → Flyway migrations (STA V4, Discovery
V3) as migrator role → seed (15 facts) → services on 8080/8090/8081/4200 →
end-to-end chain verified (browser → UI → BFF → Discovery → projections).
Two operational learnings captured in `scripts/dev/run-platform.ps1`:
Discovery must start before seeding (else publishes quarantine UNROUTABLE —
the I1-MSG-002 fix behaving as designed), and the BFF's
`DISCOVERY_BASE_URL` must point at Discovery's actual port.

The owner exercised the UI (station details for SEEDSTA0001) and saved
rendered-DOM snapshots; the DOM audit confirmed correct Greek rendering
(Παροχές (EVSE), Φόρτιση title), complete EVSE/connector/tariff/freshness
content, and no Angular errors. A `<style>undefined</style>` head artifact
was observed — cosmetic, audit found no missing component CSS files;
recorded as a NOTE for the UI-foundation follow-up.

## State transitions

- `I1-APP-001: CI_PENDING → VERIFIED` (human gate = owner merge of PR #39).
- 16 tasks VERIFIED, zero open.

## Follow-up backlog (from reviews + bring-up)

1. **I1-ENG-004 (P0):** frontend CI — extend workflows to cover apps/**
   (web vitest + build; BFF already covered via service-tests? no — BFF is
   under apps/ too; add a frontend workflow), plus dependency-vulnerability
   scanning for apps/**.
2. UI foundation pass (ARC-023 work packages): visual polish, Angular
   Material/CDK decision, `<style>undefined</style>` cleanup, real-browser
   contrast checks, E2E (Playwright) harness.
3. PUB-04 (EVSE details) + PUB-05 (help) screens.
4. Rate limiting for the BFF public proxy — MUST land before production
   exposure (EPIC-04).
5. openapi-generator client setup (EPIC-06 scope item).

## Recommended next step

I1-ENG-004 (frontend CI) first — tiny task, closes the last coverage gap
so every future UI change is machine-verified. Then the next product
slice: PUB-04/05 screens or STA status events (roadmap order).
