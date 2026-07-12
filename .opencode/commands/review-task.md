---
description: Run independent testing, specialist review, and general review for a self-verified task
agent: orchestrator
subtask: false
---

Review delivery task `$1`.

Full command arguments: `$ARGUMENTS`

If `$1` is empty, ask for the task ID and stop.

Do not edit implementation files or fix findings during this command.

## Preconditions

1. Read `AGENTS.md`.
2. Read:
   - task packet;
   - delivery status;
   - planner handoff;
   - coder handoff;
   - debugger handoff, if any;
   - existing evidence and deviations.
3. Inspect:
   - branch and baseline commit;
   - worktree status;
   - complete task diff;
   - changed and untracked files.
4. Confirm the task is:
   - `SELF_VERIFIED`; or
   - `FIX_REQUIRED` with a newer `FIXED_SELF_VERIFIED` debugger handoff.
5. Confirm changed files remain within approved scope.
6. Confirm no unrelated human changes would be overwritten.
7. Confirm acceptance criteria and required reviewers are known.

If these conditions fail, do not invoke reviewers.

## Procedure

### 1. Independent testing

Build a bounded context package containing:

- task ID;
- baseline commit;
- objective and non-goals;
- authoritative references;
- acceptance criteria;
- complete changed-file list;
- planner and coder handoffs;
- required test categories;
- relevant commands;
- known risks.

Invoke `tester`.

The tester must independently run relevant tests and produce an acceptance-criteria coverage matrix.

If the tester returns:

- `FIX_REQUIRED`: record findings and stop;
- `BLOCKED`: record the blocker and stop;
- `SPEC_CONFLICT`: record exact conflicting authorities and stop;
- `CLARIFICATION_REQUIRED`: request the missing information and stop.

Do not continue to review when required tests fail.

### 2. Specialist security review

Invoke `security-reviewer` when the task affects any of:

- authentication or BFF sessions;
- authorization or membership;
- CSRF, OAuth, tokens, or service identity;
- account, driver, organization, or tenant data;
- secrets, credentials, or cryptography;
- audit or privileged operations;
- rate limits or abuse controls;
- external input or provider integrations;
- security/privacy documents or registries;
- L3 security/privacy scope.

Provide the complete task context, diff, and tester evidence.

If a required specialist agent is unavailable, return `BLOCKED`.

Any unresolved `BLOCKER` or `MAJOR` specialist finding produces `FIX_REQUIRED`.

### 3. General independent review

Invoke `reviewer` with:

- task packet;
- authoritative references;
- planner/coder/tester handoffs;
- specialist findings;
- complete task diff;
- actual command/test evidence;
- acceptance criteria;
- impact level.

The reviewer must assess scope, correctness, architecture, persistence, contracts, security, maintainability, and test sufficiency.

### 4. Determine result

Set the recommended result to:

- `FIX_REQUIRED` for any unresolved `BLOCKER` or `MAJOR`;
- `BLOCKED` for unavailable dependencies or required evidence;
- `SPEC_CONFLICT` for contradictory authority;
- `CLARIFICATION_REQUIRED` for missing task information;
- `CI_PENDING` only when:
  - independent tests pass;
  - required specialist reviews pass;
  - general review has no unresolved `BLOCKER` or `MAJOR`;
  - scope compliance is confirmed;
  - no files changed during review.

Do not invoke `debugger` automatically. Recommend a debugger handoff with exact failure IDs when appropriate.

Do not commit, push, merge, deploy, or mark the task verified.

## Required output

```text
TASK_ID:
REVIEW_RESULT:
RESULTING_STATE:
BASELINE_COMMIT:
CANDIDATE_COMMIT_OR_DIFF:
IMPACT_LEVEL:

TESTER_RESULT:
SECURITY_REVIEW_RESULT:
GENERAL_REVIEW_RESULT:

FILES_REVIEWED:
ACCEPTANCE_CRITERIA_STATUS:
COMMANDS_EXECUTED:
TEST_RESULTS:
FINDINGS:
UNRESOLVED_BLOCKERS:
HUMAN_DECISIONS_REQUIRED:
EVIDENCE_PATHS:
RECOMMENDED_NEXT_COMMAND:
```

Allowed resulting states:

```text
CI_PENDING
FIX_REQUIRED
BLOCKED
CLARIFICATION_REQUIRED
SPEC_CONFLICT
```

When successful, recommend:

```text
/verify-task $1
```