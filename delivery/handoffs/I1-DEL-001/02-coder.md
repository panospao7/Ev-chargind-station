---
role: coder
taskId: I1-DEL-001
previousState: IMPLEMENTING
resultingState: SELF_VERIFIED
baselineCommit: 811b81ed39ca954217cf785f5f9c3135f14b2263
impactLevel: L3
date: 2026-11-04
---

# I1-DEL-001 — Coder handoff

Work is uncommitted on `task/i1-del-001-opencode-controls`.

## Documents and sections read

`AGENTS.md`; DEC-AGENT-01 in GOV-001; ARC-016; `delivery/README.md`; all agents,
commands and skills under `.opencode/`; `delivery/tasks/I1-IAM-002.yaml`; I1-IAM-002
records on `main` and both unmerged branches; OpenCode documentation for permissions,
agents, skills, rules, commands and plugins (accessed 2026-11-04; CLI 1.19.0).

## Changes

- `opencode.json`: removed the root catch-all; root edit now asks; root bash keeps
  read-only allows and universal denies; disabled 12 global agents for this project.
- `.opencode/agents/orchestrator.md`: allowlisted task-branch creation and push;
  denied refspec, force, delete, amend and sweep forms; allowed read-only `gh`,
  the validator and the permission check; `gh pr create` asks.
- Other agents: removed the bash catch-all; added `.\mvnw.cmd` test rules with a
  `flyway:clean` deny; added validator and read-only `gh` rules where needed.
- `coder.md`, `debugger.md`: normative documents, dependency manifests and validators
  ask; `archive/`, backlog and iterations denied; secret denies moved last.
- `documentation.md`: `docs/**` asks.
- Commands: added `/ship-task`; removed the commit conflict; review results must be
  saved unedited; removed `HUMAN_APPROVAL_REQUIRED` and `SCOPE_EXPANSION_REQUIRED` as
  workflow states (`/plan-task` keeps the first as a planning result mapped to `BLOCKED`).
- Records: corrected I1-IAM-002 status; added deviation RECON-01 and a reconciliation
  handoff; added this packet, status and backlog entries.
- New `scripts/delivery/check-agent-permissions.mjs`.

## Commands executed and results

See `delivery/evidence/I1-DEL-001/agent-permissions.md`. In summary, the permission
check went from FAIL (480/811) to PASS (811/811) in both configurations, and the
validator, self-test and secret scan PASS. No product suite ran because no product
code changed.

## Acceptance criteria

| AC | Status |
|---|---|
| AC-01 denied operations | PASS by permission check; no live session |
| AC-02 agent isolation | PASS in both configurations |
| AC-03 edit protection | PASS by permission check |
| AC-04 test and CI commands | PASS by permission check; OpenCode's backslash handling NOT_RUN |
| AC-05 ship step | Implemented; secret-scan command run; end-to-end NOT_RUN |
| AC-06 valid states | PASS by grep and validator |
| AC-07 I1-IAM-002 record | PASS by validator; awaiting review of accuracy |

## Decisions made

- `HUMAN_APPROVAL_REQUIRED` and scope-expansion cases map to `BLOCKED`.
- I1-IAM-002 moved to `BLOCKED` because line selection is an owner decision.
- `archive/**` is denied for coder and debugger because it is non-authoritative.

## Assumptions

OpenCode applies the rule order it reports through `opencode debug agent`.

## Findings and residual risks

Pattern matching is not a complete git parser, so branch protection is still needed.
The global agent files still exist, and the project disables them by name; a new
global agent would need the same entry, which the permission check would flag. The
unresolved `make` and `npm run` rules remain pending the owner's ENG-001 decision.

## Blockers

None for review.

## Recommended next agent

General reviewer and security reviewer, then owner review.
