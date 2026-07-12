---
description: Record human approval, merge evidence, and final task verification
agent: orchestrator
subtask: false
---

Close delivery task `$1`.

Full command arguments: `$ARGUMENTS`

Required invocation:

```text
/close-task TASK-ID MERGE_SHA=<immutable-sha> HUMAN_APPROVAL=<reference> FINAL_VERIFY=YES
```

If `$1`, `MERGE_SHA`, `HUMAN_APPROVAL`, or `FINAL_VERIFY=YES` is missing, ask for it and make no state change.

This command records a human decision. It does not create the approval itself.

## Preconditions

1. Read `AGENTS.md`.
2. Read:
   - task packet;
   - current delivery status;
   - all handoffs;
   - CI and verification evidence;
   - deviations and accepted limitations.
3. Confirm the previous state is `HUMAN_REVIEW` or `MERGED`.
4. Confirm the human approval reference is explicit and applicable to this task.
5. Confirm the supplied merge SHA is immutable and exists in repository history.
6. Confirm the approved candidate is contained in the merge commit.
7. Confirm the worktree is clean.
8. Confirm required CI passed for:
   - the approved candidate; and
   - the merge commit, when post-merge checks are required.
9. Confirm no unresolved `BLOCKER`, `MAJOR`, `SPEC_CONFLICT`, or W1-critical gap remains.
10. Confirm documentation, traceability, and evidence are complete.

Do not accept:

- verbal approval without a reference;
- a branch name instead of a SHA;
- CI evidence from another commit;
- an approval predating material changes;
- screenshots as the only verification evidence;
- approval supplied by an AI agent.

## Closure procedure

### 1. Validate merge identity

Record:

- baseline commit;
- candidate commit;
- merge commit;
- target branch;
- human approver/reference;
- merge date;
- CI run/reference.

If the approved candidate differs materially from the merged content, return `BLOCKED`.

### 2. Validate final evidence

Require:

- acceptance criteria all satisfied;
- required tests passed;
- required reviews completed;
- contracts synchronized;
- migrations validated;
- security/privacy checks completed;
- traceability updated;
- residual risks explicitly accepted by an authorized human where required;
- no secret or personal data in evidence.

### 3. Record closure

Only after all checks pass:

- update `delivery/status.yaml`;
- create a closure handoff under `delivery/handoffs/`;
- create or update final evidence under `delivery/evidence/`;
- set the task to `VERIFIED`;
- record the immutable merge SHA and human approval reference.

Do not edit product code, contracts, migrations, architecture, or governance documents.

Do not mark a governance gate or contradiction verified unless separately and explicitly authorized.

### 4. Partial closure

If merge is proven but final post-merge verification is incomplete:

- set or retain `MERGED`;
- list missing evidence;
- do not set `VERIFIED`.

If documentation metadata changes are created by this command, report that those control-plane changes still require a human commit. Do not commit them yourself.

## Required output

```text
TASK_ID:
CLOSURE_RESULT:
RESULTING_STATE:
BASELINE_COMMIT:
CANDIDATE_COMMIT:
MERGE_COMMIT:
TARGET_BRANCH:
HUMAN_APPROVAL_REFERENCE:

CI_EVIDENCE:
ACCEPTANCE_CRITERIA_STATUS:
REVIEW_STATUS:
SECURITY_STATUS:
CONTRACT_STATUS:
MIGRATION_STATUS:
TRACEABILITY_STATUS:
RESIDUAL_RISKS:
CLOSURE_HANDOFF_PATH:
FINAL_EVIDENCE_PATH:
UNCOMMITTED_CONTROL_PLANE_CHANGES:
RECOMMENDED_NEXT_STEP:
```

Allowed results:

```text
VERIFIED
MERGED
BLOCKED
CLARIFICATION_REQUIRED
SPEC_CONFLICT
```

`VERIFIED` is allowed only because the human explicitly supplied `FINAL_VERIFY=YES` together with valid approval and immutable merge evidence.