---
description: Execute one approved implementation-plan batch with appropriate gates.
agent: orchestrator
---

Execute one approved batch only, applying the ARC-016 §24 parallel-agent rules:
distinct task IDs, declared file ownership, coordinated shared contracts, and
isolated branches or worktrees. Merge and approval of the batch result always
remain with the human.

Batch/request:

```text
$ARGUMENTS
```

## Instructions

1. Identify the exact batch to execute.
2. Confirm an approved plan exists in the conversation or provided file path.
3. If no approved plan exists, stop and delegate to `@planner`.
4. Use the smallest safe workflow:
   - fast for trivial low-risk edits
   - standard for normal work
   - strict for allocation/locking, privacy, security, migrations, device
     integration, lifecycle, static guards, or cross-layer work
5. Do not implement unrelated batches.
6. Do not broaden scope.
7. Require targeted tests for behavior changes.
8. Require strict review for risky batches.
9. Stop on reviewer fail, test fail, privacy ambiguity, architecture ambiguity, schema surprises, or unexpected broad diff.

## Default batch loop

```text
@scout if needed
→ @coder or @specialist-coder
→ @tester
→ @reviewer
```

For risky work:

```text
@scout
→ relevant guardian or specialist (allocation-specialist for allocation/locking)
→ @coder/@specialist-coder
→ @tester
→ relevant guardian re-check if needed
→ @reviewer
```

## Output format

```markdown
## Batch Execution Plan
- Batch: ...
- Mode: fast|standard|strict
- Cost posture: cheap|balanced|quality-gated

## Steps
1. ...
2. ...

## Gates
- Test gate: required|optional
- Review gate: required|optional
- Architecture/privacy/migration gate: required|optional

## Stop Condition
- Complete when: ...
- Blocked when: ...
```
