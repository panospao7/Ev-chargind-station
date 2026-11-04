---
role: coder
taskId: I1-DEL-001
previousState: FIX_REQUIRED
resultingState: SELF_VERIFIED
baselineCommit: 811b81ed39ca954217cf785f5f9c3135f14b2263
impactLevel: L3
date: 2026-11-04
---

# I1-DEL-001 — Coder fix-round handoff

This round responds to `03-reviewer.md` and `04-security-reviewer.md`. Work remains
uncommitted on `task/i1-del-001-opencode-controls`.

## Approach change

Both reviews showed that patching deny patterns could not work, because allow rules ending
in `*` accept extra arguments and redirects. Permissions were rebuilt around exact allows,
one shared deny list last for every agent, and a check using OpenCode's real matching.

## Findings addressed

| Findings | Change |
|---|---|
| F-01, SEC-01, SEC-02, SEC-09 | Single-branch push only; `git push*` denied otherwise, for all agents |
| F-02, SEC-06 | `git add` and `git commit` prompt; sweep, force, amend, hook-skip forms denied |
| SEC-07, F-12 | Branch switch limited to task branches; creation prompts because matching ignores case |
| F-03, SEC-11, SEC-12 | Secret patterns cover the root and keystores; denies come last; read rules match |
| F-04, SEC-13 | Maven allows anchored to test and verify; other goals, `-f`, and `--settings` denied |
| SEC-03, F-13 | `git fetch` limited to bare and `origin`; tool-launching git commands denied |
| SEC-04 | `*>*` and `git *--output*` denied |
| SEC-05, SEC-10 | `.git/**` denied; control paths prompt or deny |
| SEC-08 | Added denies for plumbing, `gh api`, shells, interpreters, and file deletion |
| F-10, SEC-17 | Added `"*_*": "ask"`; residual disclosed in evidence |
| SEC-16 | Explicit `task` denies for 15 legacy agent names |
| F-11, SEC-19 | `.env.example` asks again |
| F-05 | Candidate defined; `/verify-task` accepts the head only for a record-only difference |
| F-06 | `/review-task` records a fingerprint; `/ship-task` compares it |
| F-08 | `/verify-task` does not commit; `/close-task` accepts `CI_PENDING` on the merged branch |
| SEC-18 | `/ship-task` adds filename and generic-assignment checks |
| F-09, F-12, SEC-15 | Check uses the real matcher and now has 4188 expectations |
| F-14 | Corrected RECON-01, status line and next step |

## Not fixed; needs the owner

- **F-07:** `delivery/status.yaml` is prohibited by 18 of 20 parseable packets, and DEC-AGENT-01
  limits commits to `allowedFiles`. `/ship-task` now stops and asks. It will block on those
  packets until the owner decides.
- **SEC-06 option:** a validating wrapper script instead of prompts.
- **SEC-14:** possible permission bypass; confirm in a disposable repository.
- **N-4:** this packet waives the tester, while `/review-task` invokes the tester by default.
- **Branch protection and `--auto`.**

## Not addressed

Findings noted as out of scope: `delivery/tasks/I1-DSC-001.yaml` has a YAML error at line 408;
agent prompts still mention `SCOPE_EXPANSION_REQUIRED` and `HUMAN_APPROVAL_REQUIRED` as statuses
(N-5), which are not workflow states.

## Commands and results

See `delivery/evidence/I1-DEL-001/agent-permissions.md`.

## Acceptance criteria

Self-reported only until re-review. AC-01 to AC-04 PASS by the extended check in both
configurations. AC-05 is implemented with end-to-end NOT_RUN. AC-06 and AC-07 PASS by review of
the text and the validator.

## Recommended next agent

Fresh general and security reviewers. The previous reviewers should not re-review their own
correction requests alone.
