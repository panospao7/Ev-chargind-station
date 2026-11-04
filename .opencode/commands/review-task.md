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
5. Confirm changed files remain within approved scope. Exclude the records the orchestrator owns (`delivery/status.yaml`, and this task's handoffs and evidence); `/ship-task` raises any conflict with the packet.
6. Confirm no unrelated human changes would be overwritten.
7. Confirm acceptance criteria and required reviewers are known.

If these conditions fail, do not invoke reviewers.

## Review fingerprint

This records exactly what was reviewed, so later commands can prove it did not change. It stays comparable after staging and committing because new files are hashed separately by their saved paths. It excludes the records this command writes.

1. Immediately before the first subagent, save the sorted output of this as the **new-file list**:

   ```text
   git ls-files --others --exclude-standard -- . ":(exclude)delivery/handoffs/$1/**" ":(exclude)delivery/evidence/$1/**"
   ```

2. Compute the **tracked hash**, adding one `":(exclude)<path>"` for each path on the new-file list:

   ```text
   git diff <baselineCommit> -- . ":(exclude)delivery/handoffs/$1/**" ":(exclude)delivery/evidence/$1/**" ":(exclude)delivery/status.yaml" ":(exclude)<path>" | git hash-object --stdin
   ```

3. Compute the **new-file hashes**, in list order. Skip this if the list is empty:

   ```text
   git hash-object -- <paths from the new-file list>
   ```

4. Repeat steps 1 to 3 after the last subagent. If the new-file list or a hash differs, because the tester added a test for example, return `SELF_VERIFIED` and require a new review.
5. Save the list, both hash results, and the shell used to `delivery/evidence/$1/review-fingerprint.md`.

Compare only results from the same shell. If any of these commands fails or prints nothing where output is expected, return `BLOCKED`.

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

### 2. Specialist contract review

Invoke `contract-reviewer` when the task packet's `reviews.contractReviewer` is `true` or when the task affects any of:

- OpenAPI, AsyncAPI, or JSON Schema artifacts;
- message, lifecycle, problem-code, policy, or traceability registries;
- contract examples or fixtures;
- cross-service API contracts;
- documented compatibility guarantees.

Provide the complete task context, diff, and tester evidence.

If a required specialist agent is unavailable, return `BLOCKED`.

Any unresolved `BLOCKER` or `MAJOR` specialist finding produces `FIX_REQUIRED`.

### 3. Specialist data review

Invoke `data-reviewer` when the task packet's `reviews.dataReviewer` is `true` or when the task affects any of:

- database migrations or schema changes;
- persistence logic, locking, or concurrency;
- indexes, constraints, or exclusions;
- outbox or event-store patterns;
- data retention or anonymization.

Provide the complete task context, diff, and tester evidence.

If a required specialist agent is unavailable, return `BLOCKED`.

Any unresolved `BLOCKER` or `MAJOR` specialist finding produces `FIX_REQUIRED`.

### 4. Specialist security review

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

### 5. General independent review

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

### 6. Determine result

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
  - the review fingerprint is unchanged.

Do not invoke `debugger` automatically. Recommend a debugger handoff with exact failure IDs when appropriate.

Do not commit or push during this command. Apart from the excluded records, files must not change while they are under review. Do not merge, deploy, or mark the task verified.

Save each subagent's complete result, unedited, to `delivery/handoffs/$1/<SEQUENCE>-<ROLE>.md` before determining the result, and update `reviews` in `delivery/status.yaml` to match. A summary written by the orchestrator does not count as review evidence.

## Required output

```text
TASK_ID:
REVIEW_RESULT:
RESULTING_STATE:
BASELINE_COMMIT:
CANDIDATE_COMMIT_OR_DIFF:
IMPACT_LEVEL:

TESTER_RESULT:
CONTRACT_REVIEW_RESULT:
DATA_REVIEW_RESULT:
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
SELF_VERIFIED
FIX_REQUIRED
BLOCKED
CLARIFICATION_REQUIRED
SPEC_CONFLICT
```

When successful, recommend:

```text
/ship-task $1
```