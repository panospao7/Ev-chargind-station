---
description: Produce and record an implementation plan for a claimed task
agent: orchestrator
subtask: false
---

Plan delivery task `$1`.

Full command arguments: `$ARGUMENTS`

If `$1` is empty, ask for the task ID and stop.

Do not implement the task.

Procedure:

1. Read `AGENTS.md`.
2. Read the task packet, status, initialization handoff, iteration, evidence, and deviations for `$1`.
3. Confirm the task is properly initialized and claimed.
4. Inspect current branch, baseline commit, worktree status, and relevant implementation.
5. Build a bounded context package containing:
   - objective and non-goals;
   - exact authority references;
   - acceptance criteria;
   - dependencies;
   - allowed and prohibited files;
   - impact level;
   - expected contracts, persistence, security, and test effects;
   - known findings and risks.
6. Invoke the `planner` subagent with that complete context.
7. Evaluate the planner result.
8. If the planner returns `READY_FOR_IMPLEMENTATION`:
   - record the planner handoff;
   - preserve the task as claimed and implementation-ready;
   - identify any required pre-edit human approval;
   - recommend `/implement-task $1`.
9. If the planner finds a conflict, missing dependency, or approval requirement:
   - record it;
   - do not invoke the coder;
   - return the applicable blocked state.
10. Do not silently resolve specification conflicts.
11. Do not edit implementation, contracts, migrations, or normative documentation.

Return:

```text
TASK_ID:
PLANNING_RESULT:
RESULTING_STATE:
BASELINE_COMMIT:
IMPACT_LEVEL:
PLAN_HANDOFF_PATH:
FILES_EXPECTED_TO_CHANGE:
REQUIRED_TESTS:
SPECIALIST_REVIEWS:
HUMAN_APPROVALS:
BLOCKERS:
RECOMMENDED_NEXT_COMMAND:
```

Allowed planning results:

```text
READY_FOR_IMPLEMENTATION
CLARIFICATION_REQUIRED
BLOCKED
SPEC_CONFLICT
HUMAN_APPROVAL_REQUIRED
```