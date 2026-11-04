---
role: orchestrator
taskId: I1-DEL-001
previousState: SELF_VERIFIED
resultingState: BLOCKED
baselineCommit: 811b81ed39ca954217cf785f5f9c3135f14b2263
impactLevel: L3
date: 2026-11-04
---

# I1-DEL-001 — Orchestrator handoff

The corrections are complete and independently re-checked, and the work is uncommitted on
`task/i1-del-001-opencode-controls`. The task is `BLOCKED` on owner decisions, because the
second security review's R2-01 and R2-02 stay open and AGENTS.md §15 prevents moving to human
review with an unresolved `BLOCKER`. Configuration cannot close them.

## Review history

| Round | Result | File |
|---|---|---|
| 1 general, security | FAIL_FINDINGS | `03-reviewer.md`, `04-security-reviewer.md` |
| 2 general, security | FAIL_FINDINGS | `06-reviewer-round2.md`, `07-security-reviewer-round2.md` |
| 3 verification | FAIL_FINDINGS, 1 MAJOR | `09-verifier-round3.md` |
| 3 re-check | PASS_NO_BLOCKERS | `10-verifier-recheck.md` |

The re-check covers the listed corrections. It was not a new security review, by design.

## Owner decisions required

1. Threat model: treat these rules as guardrails against mistakes, accepting R2-02 and R2-05, or add an
   OS-level control. Only the owner may accept this risk.
2. Pin the shell (R2-01), after a live test, because `bash` can resolve to WSL's bash on this machine.
3. How `delivery/status.yaml` relates to packet `allowedFiles` under DEC-AGENT-01 (F-07, G-04).
4. Commit mechanism: prompts, or a validating wrapper script. Whether `--auto` is banned.
5. Whether to keep the `-DskipTests` and `-Dmaven.test.skip` denies (V3-07).
6. Whether reviewers and the coder use different models.
7. Accept tool names without an underscore defaulting to allow (F-10), or add a rule.
8. Whether this packet's tester waiver is intended (first-round N-4).
9. Which I1-IAM-002 line continues (RECON-01).

Live confirmation of R2-01, R2-02 and the "always" behaviour is NOT_RUN, and needs approval for a
disposable-repository test.

## Recommended next step

The owner decides item 1 and item 2, then the task can move to `/ship-task`. Nothing is committed or
pushed. `main` branch protection is present.
