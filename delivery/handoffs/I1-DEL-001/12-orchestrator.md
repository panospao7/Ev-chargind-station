---
role: orchestrator
taskId: I1-DEL-001
previousState: BLOCKED
resultingState: BLOCKED
baselineCommit: 811b81ed39ca954217cf785f5f9c3135f14b2263
impactLevel: L3
date: 2026-11-04
---

# I1-DEL-001 — Owner risk decision recorded

The Project Owner answered "i accept" in session on 2026-11-04. The question was whether to accept
the OpenCode permission rules as guardrails only, or to require OS-level isolation as well.

## Recorded decision

- OpenCode permission rules are guardrails against agent mistakes, not a security boundary.
- No OS-level isolation is required for now.
- Accepted, as named in that question: an agent could overwrite any file with a grouped redirect
  such as `(git status) > file` (R2-02), including a commit hook, and could run another program through
  an environment variable (R2-05).

This records the owner's decision. An AI agent drafted this record and approved nothing.

## Not covered by this decision

These remain open: the shell pin (R2-01), committing and opening a pull request, the I1-IAM-002 line,
`delivery/status.yaml` versus `allowedFiles`, and the other items in `11-orchestrator.md`.

## Resulting state

`BLOCKED` on the shell pin (R2-01), the one remaining security blocker. The interim rule stays: start
OpenCode from Git Bash.
