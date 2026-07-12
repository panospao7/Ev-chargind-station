---
description: Validate, claim, and initialize a repository delivery task
agent: orchestrator
subtask: false
---

Start delivery task `$1`.

Full command arguments: `$ARGUMENTS`

If `$1` is empty, ask for the task ID and perform no state change.

Follow `AGENTS.md` and the orchestrator rules.

Perform only task initialization:

1. Read:
   - `delivery/status.yaml`;
   - the task packet for `$1`;
   - its iteration record;
   - existing handoffs, evidence, and deviations.
2. Inspect:
   - current branch;
   - baseline commit;
   - worktree status;
   - relevant recent history.
3. Validate the complete Definition of Ready.
4. Verify dependencies and blockers.
5. Validate allowed and prohibited files.
6. Classify or confirm the L0–L4 impact level.
7. Identify required specialist reviews and human approvals.
8. Detect specification conflicts using exact file and section references.
9. If valid:
   - record the task as claimed using permitted delivery records;
   - create an initialization handoff;
   - recommend `/plan-task $1`.
10. Do not invoke the coder.
11. Do not edit product code, contracts, migrations, architecture, or governance documents.

Return:

```text
TASK_ID:
RESULTING_STATE:
BASELINE_COMMIT:
IMPACT_LEVEL:
DEFINITION_OF_READY:
DEPENDENCIES:
AUTHORITY_REFERENCES:
ALLOWED_FILES:
PROHIBITED_FILES:
REQUIRED_REVIEWERS:
HUMAN_APPROVALS:
BLOCKERS:
RECOMMENDED_NEXT_COMMAND:
```

Allowed resulting states:

```text
CLAIMED
CLARIFICATION_REQUIRED
BLOCKED
SPEC_CONFLICT
HUMAN_APPROVAL_REQUIRED
```