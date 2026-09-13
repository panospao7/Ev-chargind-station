---
role: orchestrator
taskId: I1-ENG-004
previousState: READY
resultingState: CLAIMED
baselineCommit: 23a15fe9 (origin/main after owner merge of planning PR #41)
impactLevel: L2
date: 2026-09-13T18:00:00Z
---

# I1-ENG-004 — Claim handoff

## Authorization chain

- Planning PR #41 merged by owner → `23a15fe9` on origin/main; packet
  `delivery/tasks/I1-ENG-004.yaml` approved (L2: new CI workflow +
  maplibre-gl upgrade superseding the ADR-016 5.24.x pin).
- Baseline for implementation: 23a15fe9. Branch: task/i1-eng-004-ci.

## Scope reminder (from approved packet)

- Allowed: .github/workflows/frontend-tests.yml (new),
  apps/web/package.json + package-lock.json (maplibre-gl ^6.9.0),
  apps/web/src/app/features/discovery/map/maplibre.adapter.ts (only if v6
  requires surface adjustments), delivery handoffs/evidence/deviations for
  I1-ENG-004.
- Prohibited: contracts/**, docs/**, services/**, apps/bff/**, all other
  apps/web/src/**, all other workflows, delivery control plane beyond
  handoff/evidence/deviation records.
- Six ACs; audit gate must be green after upgrade; pre-upgrade CRITICAL
  finding (GHSA-jrc7-96c5-q579) recorded in evidence.

## Next

Planner handoff, then coder.