---
description: Strict review of the current uncommitted diff.
agent: reviewer
subtask: true
---

Review the current uncommitted worktree diff. This implements ARC-016 Gate D
(independent review) in strict mode: evidence-backed findings only, FAIL on any
real defect, and no self-approval of produced work. Merge remains human-only.

Additional context or plan:

```text
$ARGUMENTS
```

## Instructions

1. Inspect `git status`.
2. Inspect `git diff`.
3. Identify changed files.
4. Read surrounding code and relevant call sites.
5. Compare against the provided plan/context if any.
6. Check architecture, privacy/security, tests, and regression risk.
7. Do not edit files.
8. Report only concrete, evidence-backed issues.
9. If no approved plan is available, say so and review against the diff and repository rules.

## Strict focus

Always check carefully if the diff touches:

- allocation/locking (guard rows, hold expiry, exclusion constraints)
- privacy/security/permissions
- diagnostics/logging persistence
- Flyway migrations, schema, constraints, indexes
- device integration evidence and reconciliation
- payment/refund/billing lifecycle
- booking, check-in, or charging-session lifecycle
- discovery projections (no personal identifiers)
- static architecture guards
- cross-layer/cross-module changes

## Output format

```markdown
VERDICT: PASS | FAIL

Summary:
- Changed scope: ...
- Plan available: yes|no
- Main risk areas checked: ...
- Architecture docs/rules checked: ...

Issues:
- [ISSUE-1] [CRITICAL|MAJOR|MINOR] problem - `file` - why it matters - minimal fix

Coverage:
- Requirements met: yes|no|unknown
- Testing adequate: yes|no
- Regression risk: low|medium|high

Questions:
- ...

Notes:
- ...
```

If there are no issues:

```markdown
Issues:
- None
```
