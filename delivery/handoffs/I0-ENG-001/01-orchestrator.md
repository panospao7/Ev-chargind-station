# I0-ENG-001 — Initialization Handoff

## Task state
- **State:** CLAIMED
- **Assigned agent:** orchestrator
- **Baseline commit:** ba4b6e5
- **Branch:** main
- **Worktree:** clean

## Definition of Ready — VALIDATED
1. Task packet complete: YES (465 lines, all sections filled)
2. Dependencies: NONE (empty task dependency list)
3. Infrastructure available: GitHub CI, Node 24, npm
4. Authoritative documents: referenced with exact sections
5. Acceptance criteria: 10 measurable criteria (AC-01 through AC-10)
6. Allowed/prohibited files: clearly defined
7. Tests: positive, negative, boundary, security, contract cases specified
8. Impact level: L2 (contract/cross-module)
9. No unresolved W1-critical blockers

## Scope notes
- Objective: Replace global tooling with lockfile-backed Node 24 toolchain; fix contract defects until G3 gate passes green
- Non-goals include changing business semantics, creating migrations, approving governance changes
- Maximum expected diff: 800 lines across max 20 contract files
- Allowed files: package.json, package-lock.json, .nvmrc, .spectral.yaml, scripts/contracts/, .github/workflows/g3-contract-validation.yml, contracts/**
- Prohibited files: governance docs, architecture docs, services/, apps/, infra/, db/migrations, AGENTS.md, opencode.json, .opencode/**, delivery/status.yaml, delivery/tasks/**

## Required reviewers
- planner: REQUIRED
- tester: REQUIRED
- generalReviewer: REQUIRED
- contractReviewer: REQUIRED
- dataReviewer: NOT_APPLICABLE
- securityReviewer: REQUIRED
- humanApproval: REQUIRED

## Next step
Proceed to /plan-task I0-ENG-001
