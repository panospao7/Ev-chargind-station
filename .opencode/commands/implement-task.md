---
description: Delegate implementation of an approved and fully planned task
agent: orchestrator
subtask: false
---

Implement delivery task `$1`.

Full command arguments: `$ARGUMENTS`

If `$1` is empty, ask for the task ID and stop.

Do not implement code directly. Delegate implementation to the `coder`.

This command implements ARC-016 Gate C (implementation self-check): confirm diff
scope, build/test evidence, no secrets, and no unrelated edits before review.
L3/L4 scope requires the human authorization recorded in the task packet before
the coder may edit sensitive artifacts.

Procedure:

1. Read `AGENTS.md`.
2. Read:
   - task packet;
   - current status;
   - initialization handoff;
   - approved planner handoff;
   - deviations and previous findings.
3. Inspect:
   - current branch;
   - baseline commit;
   - worktree status;
   - relevant diff.
4. Confirm:
   - planner status is `READY_FOR_IMPLEMENTATION`;
   - dependencies are complete;
   - allowed and prohibited files are explicit;
   - acceptance criteria are measurable;
   - required L3/L4 authorization exists;
   - no unresolved specification conflict exists;
   - unrelated human changes will not be overwritten.
5. If any condition fails, do not invoke the coder.
6. Transition the task to `IMPLEMENTING` only through permitted delivery records.
7. Build the coder context package containing:
   - task ID;
   - baseline commit;
   - objective and non-goals;
   - exact authority references;
   - approved implementation sequence;
   - allowed and prohibited files;
   - impact level;
   - acceptance criteria;
   - required tests and commands;
   - migration or forward-fix requirements;
   - security/privacy constraints;
   - stop conditions.
8. Invoke the `coder`.
9. Inspect the coder handoff and resulting worktree diff.
10. Confirm changed files remain within scope.
11. Record actual commands and results without embellishment.
12. If coder returns `SELF_VERIFIED`:
    - record the handoff;
    - set the task to `SELF_VERIFIED`;
    - recommend independent testing.
13. If coder returns a failure, block, or specification conflict:
    - record the exact result;
    - set the corresponding state;
    - do not claim completion.
14. Do not invoke tester or reviewer as part of this command.
15. Do not commit, merge, release, deploy, or mark work verified.

Return:

```text
TASK_ID:
CODER_RESULT:
RESULTING_STATE:
BASELINE_COMMIT:
IMPACT_LEVEL:
FILES_CHANGED:
SCOPE_COMPLIANCE:
COMMANDS_EXECUTED:
TEST_RESULTS:
ACCEPTANCE_CRITERIA_STATUS:
RESIDUAL_RISKS:
BLOCKERS:
HUMAN_DECISIONS:
RECOMMENDED_NEXT_STEP:
```

Allowed resulting states:

```text
SELF_VERIFIED
FIX_REQUIRED
BLOCKED
CLARIFICATION_REQUIRED
SPEC_CONFLICT
SCOPE_EXPANSION_REQUIRED
```