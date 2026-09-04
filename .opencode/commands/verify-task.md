---
description: Verify reviewed work against CI, contracts, tests, evidence, and documentation before human approval
agent: orchestrator
subtask: false
---

Verify delivery task `$1`.

Full command arguments: `$ARGUMENTS`

If `$1` is empty, ask for the task ID and stop.

This command verifies evidence. It does not merge, approve risk, deploy, or mark the task `VERIFIED`.

This command implements ARC-016 Gate F (CI verification): required checks must
pass for the exact candidate commit. Gate G (promotion approval) and the merge
itself are satisfied only by a human.

## Preconditions

1. Read `AGENTS.md`.
2. Read:
   - task packet;
   - delivery status;
   - all planner, coder, tester, debugger, reviewer, specialist, and documentation handoffs;
   - evidence and deviations.
3. Confirm the task state is `CI_PENDING`.
4. Record:
   - baseline commit;
   - candidate commit;
   - branch;
   - worktree status.
5. Require a committed candidate with a clean worktree.
6. Confirm the candidate has not changed since independent review.
7. Require exact CI evidence for that candidate commit.

If the candidate changed after review, return `SELF_VERIFIED` and require `/review-task $1` again.

If CI evidence is missing or still running, retain `CI_PENDING`.

## Required evidence

The verification package must include:

- exact candidate commit SHA;
- CI workflow/run reference;
- required-check names;
- final status of every required check;
- tester evidence;
- reviewer evidence;
- specialist-review evidence;
- contract validation where applicable;
- migration validation where applicable;
- security/privacy validation where applicable;
- traceability status;
- known deviations and approvals.

A screenshot alone is insufficient.

## Procedure

### 1. Verify review integrity

Confirm:

- reviewed diff equals candidate commit;
- no required review is missing;
- no unresolved `BLOCKER` or `MAJOR` exists;
- no test or validator was disabled;
- no prohibited file changed;
- no unresolved specification conflict exists.

### 2. Verify CI

For the exact candidate commit, require:

- all mandatory checks completed;
- all mandatory checks passed;
- no allowed-failure result for a required gate;
- no skipped required job without a valid path-applicability reason;
- no stale CI result from another commit.

Never infer green CI from local tests.

### 3. Final independent verification

Invoke `tester` in verification-only mode.

Tell the tester:

- do not edit files;
- run the final task-level or repository-required gates;
- verify actual acceptance-criteria evidence;
- report `PASS`, `FAIL`, `NOT_RUN`, or `BLOCKED`;
- compare results with CI evidence.

If this run changes files, verification is invalid and the task returns to review.

### 4. Documentation and traceability check

Confirm:

- implementation status is factual;
- contracts and prose agree;
- requirement traceability points to real artifacts and tests;
- no unsupported `APPROVED`, `VERIFIED`, or completion claim exists;
- evidence contains no secrets or personal data.

If factual synchronization is missing, invoke `documentation`.

If `documentation` changes files:

1. do not continue to human review;
2. set the task to `SELF_VERIFIED`;
3. require fresh review and CI.

### 5. Determine result

Set:

- `FIX_REQUIRED` for failed tests, CI, contracts, or review findings;
- `BLOCKED` for unavailable evidence or environment;
- `SPEC_CONFLICT` for contradictory authority;
- `CI_PENDING` while checks remain incomplete;
- `HUMAN_REVIEW` only when all required evidence is green and unchanged.

Do not mark the task `MERGED` or `VERIFIED`.

## Required output

```text
TASK_ID:
VERIFICATION_RESULT:
RESULTING_STATE:
BASELINE_COMMIT:
CANDIDATE_COMMIT:
IMPACT_LEVEL:

CI_RUN_REFERENCE:
CI_CHECK_MATRIX:
FINAL_TESTER_RESULT:
REVIEW_INTEGRITY:
CONTRACT_STATUS:
MIGRATION_STATUS:
SECURITY_STATUS:
TRACEABILITY_STATUS:
DOCUMENTATION_STATUS:
OPEN_FINDINGS:
RESIDUAL_RISKS:
HUMAN_DECISIONS_REQUIRED:
EVIDENCE_PATHS:
RECOMMENDED_NEXT_STEP:
```

Allowed resulting states:

```text
HUMAN_REVIEW
CI_PENDING
SELF_VERIFIED
FIX_REQUIRED
BLOCKED
CLARIFICATION_REQUIRED
SPEC_CONFLICT
```

When successful, instruct the human to review, approve, and merge through the normal repository process. After merge, recommend:

```text
/close-task $1 MERGE_SHA=<sha> HUMAN_APPROVAL=<reference> FINAL_VERIFY=YES
```