---
description: Commit the reviewed diff under the DEC-AGENT-01 gate, push the task branch, and open a pull request
agent: orchestrator
subtask: false
---

Ship delivery task `$1`.

Full command arguments: `$ARGUMENTS`

If `$1` is empty, ask for the task ID and stop.

This command publishes reviewed work for human review. It does not merge, approve, deploy, or mark the task verified.

Staging, committing, and opening the pull request prompt a human. **Answer "once" to each prompt, never "always".** An "always" answer can override deny rules for the rest of the session.

## Preconditions

1. Read `AGENTS.md` §12, including DEC-AGENT-01.
2. Read the task packet, `delivery/status.yaml`, and `delivery/evidence/$1/review-fingerprint.md`.
3. Confirm the task state is `CI_PENDING`, set by `/review-task $1`.
4. Confirm the current branch is the task's dedicated `task/*` branch, not `main` or a protected branch.
5. Recompute the fingerprint using `/review-task`'s saved path list. Also run its new-file list command. If a hash differs, or the current list has a path the saved list lacks, return `SELF_VERIFIED` and require `/review-task $1` again. If a fingerprint command fails, return `BLOCKED`.

## Pre-commit gate

Return `BLOCKED` and commit nothing if any check fails. You cannot unstage files, so ask the human to do it if staging must be undone.

1. Run `git status --short -uall` and `git diff --stat`.
2. Confirm every changed and untracked file matches `allowedFiles` and no `prohibitedFiles` entry. Stop on any unrelated change.
3. Owner exception (2026-11-04): the orchestrator may commit its own records, namely this task's `delivery/status.yaml` entry and this task's handoffs and evidence, even when the packet prohibits `delivery/status.yaml`. Nothing else outside `allowedFiles` may be committed. The diff to `delivery/status.yaml` must change only this task's entry and the summary counts.
4. List `.git/hooks` and stop if it holds any file other than a `*.sample` file. Commits would run that file.
5. Stage files by explicit path: `git add -- <paths>`. Never stage a directory or use a sweep form. Rules deny git commands that name `.env.example`, including staging and scanning it, so a task that changes it stops here with `BLOCKED` for a human to handle.
6. Review `git diff --cached --name-only`. Stop on `node_modules/`, production data, or any of these names: `.env*`, `*.pem`, `*.key`, `*.p12`, `*.jks`, `*.pfx`, `*.keystore`, `*.ppk`, `id_rsa*`, `id_ed25519*`, `credentials.json`, `secrets/`.
7. Scan the content of each staged file that still exists:

   ```text
   npm exec --no -- secretlint --maskSecrets --secretlintrc scripts/contracts/secretlintrc.json <staged-files>
   ```

   A missing path can pass silently, so confirm from step 6 that each listed path exists. This tool catches provider tokens and private keys, not generic passwords.
8. Find generic secret assignments, then inspect every file listed:

   ```text
   git diff --cached --name-only -i -G"(SECRET|PASSWORD|PASSWD|TOKEN|PRIVATE[_-]?KEY|API[_-]?KEY|CREDENTIAL)[A-Z_-]*[\"']?[ ]*[=:]"
   ```

   Stop if any match holds a real value rather than a placeholder or a reference. Expect matches in configuration files such as `application.yml` and the realm JSON; each one still needs inspection.
9. Run `node scripts/delivery/validate.mjs`.

## Commit and push

1. Commit with `git commit -m "<TASK-ID>: <summary under 72 characters>"`. Put details in the pull request. Permission rules treat some text as unsafe, including `>`, `.env`, `.pem`, `.key`, and option-like words such as ` -a `. If a commit is denied, reword the message.
2. Record `branch` and `candidateCommit` in `delivery/status.yaml` using that commit's SHA, then commit only that file by explicit path. Change nothing else in the file, including `updatedAt`.
3. Push with `git push -u origin <task-branch>`. Do not add other arguments. Never force-push and never target `main`.

The candidate is the work commit. The tip differs from it only by the `candidateCommit` record, and `/verify-task` checks that.

## Pull request

If a pull request already exists for this branch, the push updates it, so skip this step and report its URL.

Otherwise, write the body to `delivery/handoffs/$1/pr-body.md`, because inline `--body` text containing `>` is denied, and open it with `gh pr create --title "<TASK-ID>: <summary>" --body-file delivery/handoffs/$1/pr-body.md`. Use the ARC-016 §19 handoff format, and disclose AI assistance and the model used for each role.

Do not run `gh pr merge` or `gh pr review`. Only a human merges.

## Required output

```text
TASK_ID:
RESULTING_STATE:
BRANCH:
CANDIDATE_COMMIT:
PULL_REQUEST_HEAD:
PULL_REQUEST:
GATE_RESULTS:
FILES_COMMITTED:
BLOCKERS:
RECOMMENDED_NEXT_COMMAND:
```

Allowed resulting states:

```text
CI_PENDING
SELF_VERIFIED
BLOCKED
```

When successful, recommend `/verify-task $1` once CI finishes.
