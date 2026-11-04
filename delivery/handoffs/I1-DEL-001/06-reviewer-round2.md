---
role: reviewer
taskId: I1-DEL-001
resultingState: FIX_REQUIRED (recommended)
baselineCommit: 811b81ed39ca954217cf785f5f9c3135f14b2263
impactLevel: L3
date: 2026-11-04
---

Independent subagent output (second round), saved unedited except for this header and the replacement of any local temp path with `<session scratchpad>`. It reviews the fix-round candidate.

# I1-DEL-001 Second-Round Independent General Review

```text
Task ID: I1-DEL-001
Role: Reviewer (general, independent; did not author this work or the first review)
Task state: SELF_VERIFIED at review; recommended FIX_REQUIRED
Baseline commit: 811b81ed39ca954217cf785f5f9c3135f14b2263 (HEAD; candidate is the uncommitted worktree)
Impact level: L3
Documents and sections read: AGENTS.md §7, §10-12, §15; delivery/tasks/I1-DEL-001.yaml; delivery/README.md; 01, 02, 03, 05 handoffs in full; 04-security-reviewer.md header, tail and SEC-14; agent-permissions.md; all 7 commands; I1-IAM-002 packet scope and tests; BOOTSTRAP-01 header; RECON-01; 03-orchestrator.md; both status entries and the status diff; opencode.json; orchestrator.md in full; tester, reviewer and debugger bodies; duty sections of the other agents; ARC-016 §19, §32
Files changed: none in the repository. Status is still 27 entries, nothing staged, HEAD unchanged. Scratch files are in the session scratchpad only.
```

## Result

**REVIEW_STATUS: FAIL_FINDINGS. RECOMMENDED_STATE: FIX_REQUIRED.** There are 0 BLOCKER, 6 MAJOR, 6 MINOR and 8 NOTE.

The first-round enforcement defects are closed. F-01 to F-04 and F-09 to F-13 all hold in both configurations. What remains is mostly the workflow past `/ship-task`:
- `/verify-task` cannot pass its fingerprint check on any task that adds a file.
- `/close-task` still rejects the CI evidence that `/verify-task` accepts.
- `/verify-task`'s result is stored nowhere in the repository.
- `/close-task` has no F-07 stop.

Two gate problems also matter:
- The generic-secret search misses the lowercase forms this repository uses.
- Answering "Allow always" once overrides the deny rules.

**Limits:**
- No live OpenCode session ran, and no staging, commit or push ran.
- "Allow" and "ask" below are rule evaluations using the script's matcher against the resolved `permission` arrays.
- I read the matcher's evaluation half (`findLast`, fallback ask, approved rules last) in the 1.19.0 binary. For the wildcard half I relied on the first review's reading.
- Statements about the shell tool are code-derived, from strings in the binary.

## 1. First-round findings F-01 to F-15

| Finding | Status | What closes it, or what remains |
|---|---|---|
| F-01 | RESOLVED | `"git push origin task/* ?*": deny`, `"git push -u origin task/* ?*": deny`. All 8 listed forms plus `git fetch . task/x:main` evaluate to deny in both configs. |
| F-02 | RESOLVED | `"git add -- *": ask`, `"git commit -m *": ask`, then `"git add*": deny` and the sweep denies. All 8 listed forms evaluate to deny, including `git add -f node_modules` and `git commit -m x -am y`. |
| F-03 | RESOLVED | `"*.pem": deny`, `"*.key": deny`, `"secrets/**": deny`. Coder and debugger edit of root `x.pem`, `x.key`, `secrets/x` is deny. Read of `x.pem`, `secrets/x` is deny for all 11 agents probed, in both configs. |
| F-04 | RESOLVED | Allows are now `.\mvnw.cmd -pl * test *` style, with `".\\mvnw.cmd *:*": deny` and the deploy and `-Dexec` denies. All 4 listed forms evaluate to deny on reviewer. |
| F-05 | PARTIAL | `/ship-task`: "The candidate is the work commit." `/verify-task` step 7: "Accept that head for the candidate only if...". Step 8 covers path-filtered CI. `/verify-task` procedure 2 and `/close-task` were not aligned: see G-02. |
| F-06 | PARTIAL | `/review-task` defines the fingerprint and exclusions. `/ship-task` step 5 recomputes it, and the "no files changed" contradiction is gone. `/verify-task` step 6 cannot use it after commit: see G-01. |
| F-07 | Left to the owner; handled safely in `/ship-task` only | `/ship-task` gate step 3: "If the packet prohibits `delivery/status.yaml` and the owner has recorded no exception, return `BLOCKED` and ask the owner... do not decide this yourself." It stops with a reason and is not silent. I confirmed 18 of 20 parseable packets prohibit the file; 2 allow it. `/close-task` has no such stop, and earlier commands can trip the same rule: see G-04. |
| F-08 | PARTIAL | `/close-task` precondition 3 accepts `CI_PENDING`, and `/verify-task` does not commit. The state path is now legal. It tells close to "Check the verification evidence again", but no repository location holds that evidence: see G-03. |
| F-09 | RESOLVED for the listed gaps | M1, M7, M8, M9 and M10 are all detected (see section 5). New workflow gaps: G-09. |
| F-10 | RESOLVED, residual disclosed | `"*_*": "ask"`. `mcp_github_merge_pull_request` and `some_custom_tool` evaluate to ask. `codesearch` still allows, and `some-tool` allows everywhere except explore. The evidence discloses this: "Tool names with no underscore still default to allow. None exists today." My grep of `permission:"..."` in the binary found no such name among the built-ins. It is not listed as an owner-accepted risk: see N-6. |
| F-11 | RESOLVED | `.env.example` and `**/.env.example` are `ask` after the denies, with expectations added. |
| F-12 | RESOLVED | The script now uses `'si'` and normalizes backslashes, with an `ask` fallback and wildcard permission match. `git checkout -B task/x` and `git switch -C task/x` evaluate to ask, not allow. |
| F-13 | RESOLVED | `git fetch` and `git fetch origin` are the only allows. Refspec and `--upload-pack` forms are deny on reviewer and tester. Global config adds only earlier `git status*` style allows and read rules. All probed commands gave identical results in both configs. |
| F-14 | RESOLVED | (a) Title now reads "the one with the most self-reported progress". (b) The status line quotes the deviation title accurately, and `recordsDisagree` was added. (c) 03-orchestrator specifies `/start-task` from `BLOCKED` to `CLAIMED`. (d) `recordedState` now says "an unmaintained status entry". |
| F-15 | RESOLVED except timestamp | I re-ran secretlint on all 31 current files: exit 0. 05 says "Self-reported only until re-review". The evidence admits "local timestamps not captured per run", while the README asks for a timestamp: see N-7. |

First-round notes:
- **N-1:** fixed; secretlint now allows.
- **N-2:** partly addressed; see G-10.
- **N-3:** still applies, and the human prompt covers it.
- **N-4:** open for the owner.
- **N-5:** left as noted.
- **N-6:** fixed by the explicit legacy-agent denies.
- **N-7:** `git merge-base` and `git diff --output` are fixed. The I1-DSC-001 YAML error is out of scope and still present.
- **N-8:** unchanged.

## 2. Workflow walk-through

### Command evaluation

The orchestrator's results are identical with the global config and with empty `XDG_CONFIG_HOME`.

| Command | Result | Winning rule |
|---|---|---|
| `git diff <sha> -- . ":(exclude)..." ...` | allow | `git diff *` |
| `git hash-object --stdin` | allow | exact |
| `git ls-files --others --exclude-standard -- . ":(exclude)..."` | allow | `git ls-files *` |
| `git hash-object --stdin-paths` | allow | exact |
| `git status --short`, `git diff --stat` | allow | |
| `git add -- <real paths including .opencode/...>` | ask | `git add -- *` |
| `git add -- infra/local/.env.example` | **deny** | `git *.env*` (G-10) |
| `git diff --cached --name-only` | allow | |
| `git diff --cached --name-only -G"(SECRET\|...)[A-Z_]*[ ]*[=:]"` | allow | `git diff --cached *` |
| `npm exec --no -- secretlint --secretlintrc scripts/contracts/secretlintrc.json <files>` | allow | |
| `node scripts/delivery/validate.mjs` | allow | |
| `git commit -m "<id>: <summary>"` | ask | `git commit -m *` |
| `git push -u origin task/...`, `git push origin task/...` | allow | |
| `gh pr create --title x --body y` | ask | `gh pr create *` |
| `gh pr create ... --body "a -> b"` | **deny** | `*>*` (G-10) |
| `gh pr view`, `gh pr checks`, `gh run list`, `gh run view` | allow | |
| `git switch -c task/x`, `git checkout -b task/x` | ask | |
| `git switch -c task/x origin/main` | **deny** | `git switch -c task/* ?*` |
| `git switch main` | ask | |
| `git switch task/x`, `git fetch origin`, `git merge-base --is-ancestor a b`, `git diff --name-only a b` | allow | |

**Do `git *.env*` or `*>*` block required commands?**
- Neither blocks a fingerprint command, the `-G` search, secretlint, or a normal commit or push.
- They do block staging `.env.example`, and any commit message or PR body containing the listed tokens.
- Pipelines: `ShellTool.collect` walks `descendantsOfType("command")`, so each side is evaluated separately. Both sides allow, and I also evaluated the whole strings as allow.

### Fingerprint

**Stability, run twice in the real worktree:**
- Git Bash, `git diff`: `82122fe85aab...` both times.
- Git Bash, `ls-files` piped to `hash-object`: the same 5 blob hashes both times.
- Windows PowerShell with an inherited UTF-8 console: `435a03c8dd7b...` both times, a different value from bash. The second command fails there (G-08).

**Excludes:** correct. `git ls-files --others` lists exactly the 5 untracked non-record files. The diff lists the 19 tracked non-record files. `:(exclude)...**` works without glob magic.

**Temp-repo behaviour in both shells (`fptest.mjs`):**

| Change | Detected? |
|---|---|
| Handoff, evidence or `status.yaml` changed | Unchanged, as intended |
| Tracked file changed, including a non-ASCII-only change | Detected |
| Untracked file content changed | Detected |
| Revert | Returns to the original value |
| Untracked file renamed, same content | **Not detected** (G-08) |
| After staging, and after commit | **Value changes although content did not** (G-01) |

### State chain

| Step | Works? | Notes |
|---|---|---|
| `/start-task` (READY or BLOCKED to CLAIMED) | Yes | No step creates or confirms the task branch (N-1). |
| `/plan-task` (stays CLAIMED) | Yes | Planner cannot edit; orchestrator can write the handoff. |
| `/implement-task` (IMPLEMENTING to SELF_VERIFIED) | Yes | Step 10 scope check: see G-04. |
| `/review-task` (to CI_PENDING) | Yes | Fingerprint runnable, handoffs and status writable. `SELF_VERIFIED` outcome is not in its allowed list (G-07). |
| `/ship-task` (commit, record, push, PR) | Yes for packets allowing `status.yaml`; `BLOCKED` with a clear reason for the other 18 | Order is workable. Re-ship with an open PR: see G-10. |
| `/verify-task` | **No** when the task added any file (G-01) | The head rule itself works; see section 3. |
| Human merge | Yes | |
| `/close-task` | State path legal; **evidence and CI rules conflict** (G-02, G-03, G-04) | Closeout branch creation is prompted: `git switch main`, then `git switch -c task/<id>-closeout`. |
| Fix loop from `/review-task` | Yes | Through coder via `/implement-task`, or debugger with `FIXED_SELF_VERIFIED`. No command invokes the debugger directly (N-2). |
| Fix loop from `/verify-task` | **Partly broken** | `status.yaml` still says `CI_PENDING`, so `/review-task` precondition 4 refuses until someone edits it. The text says `/close-task` records the result (G-03). |

## 3. Internal consistency

- **Candidate and head rule:** workable with commit, record, push. `git diff --name-only <candidate> <head>` lists only `delivery/status.yaml`. It depends on the record commit not bumping `updatedAt`, which `/ship-task` does not ask for (N-3).
- **Text still saying to commit where permissions prompt:** none conflict. `/ship-task` states the prompts. The evidence says prompts are "stricter than DEC-AGENT-01 permits, pending the owner's decision".
- **AGENTS.md §7:** every "Allowed resulting states" list uses valid states. `INDEPENDENT_REVIEW` is never assigned by any command (N-4).
- **DEC-AGENT-01:** `/close-task` conflicts for 18 of 20 packets (G-04).

## Findings

### G-01 MAJOR: `/verify-task` cannot pass the fingerprint check when a task adds a file

- **LOCATION:** `verify-task.md` precondition 6 and "If the candidate changed after review, return `SELF_VERIFIED`"; `review-task.md` Review fingerprint; `ship-task.md` Commit and push.
- **OBSERVATION:** The fingerprint treats tracked and untracked files differently. Committing moves new files into `git diff <baseline>` and empties `git ls-files --others`. Content that has not changed therefore produces a different result. `/verify-task` gives no alternative calculation.

| Temp-repo state (bash) | `git diff` hash | `ls-files` hash |
|---|---|---|
| Reviewed | `1a7ff6c13211` | `cc798ff5` |
| After `git add` and after commit | `ee76db549d0f` | empty |

- **IMPACT:**
  - Read literally, this task (5 new files) returns `SELF_VERIFIED`.
  - A re-review then fingerprints the committed tree, and `/ship-task` finds nothing to commit.
  - Almost every implementation task adds files.
  - The same effect applies if a `/ship-task` run stops after staging. Precondition 5 then fails on retry, even though nothing changed.
- **REQUIRED_CORRECTION:** Have `review-fingerprint.md` record tracked and untracked paths separately, and define the post-commit calculation in `/verify-task`:
  - add `":(exclude)<each formerly untracked path>"` to the diff;
  - pipe the saved untracked path list, in saved order, to `git hash-object --stdin-paths`.

  I tested this in the temp repo. It reproduced `1a7ff6c13211` and `cc798ff5` exactly. Add the post-commit case to the evidence.
- **REPRODUCTION:** `node <scratchpad>/fptest.mjs` (steps 0, 6, 7).

### G-02 MAJOR: `/close-task` and `/verify-task` procedure 2 reject the CI evidence that precondition 7 accepts

- **LOCATION:** `close-task.md` precondition 8 ("required CI passed for: the approved candidate") and "Do not accept: CI evidence from another commit"; `verify-task.md` procedure 2 ("For the exact candidate commit, require...").
- **OBSERVATION:**
  - `/ship-task` pushes two commits together, so CI runs on the record-commit head and never on the candidate. `/verify-task` precondition 7 accepts that.
  - Procedure 2 of the same command, and all of `/close-task`, still require CI on the candidate. Nothing in `/close-task` mentions the head rule. `/close-task` cannot be satisfied as written.
  - This is the F-05 problem moved one step later.
- **IMPACT:** A careful orchestrator must return `BLOCKED` at close, or waive the rule on its own, and it has no authority to do that.
- **REQUIRED_CORRECTION:** Apply the same head rule in `/close-task` precondition 8 and in its "Do not accept" list, and reword `/verify-task` procedure 2 to say "the pull request head under precondition 7". State which SHA `humanApproval.approvedCandidateCommit` holds.
- **REPRODUCTION:** Read the three passages together.

### G-03 MAJOR: `/verify-task`'s result and evidence have nowhere to live

- **LOCATION:** `verify-task.md` ("Do not commit in this command... Report the resulting state in your output; `/close-task` records it after merge"), precondition 5, Required evidence, `EVIDENCE_PATHS`; `close-task.md` preconditions 2, 3, 7.
- **OBSERVATION:**
  - Preconditions require a clean worktree, and the command does not commit, so no file holds the final tester result, CI matrix or `HUMAN_REVIEW`.
  - If the orchestrator writes them anyway, a re-run after `CI_PENDING` fails precondition 5, and `/close-task` fails precondition 7.
  - If it writes nothing, `/close-task`'s "Check the verification evidence again" refers to chat only.
  - The same applies to bad outcomes. `FIX_REQUIRED` or `SELF_VERIFIED` from verify never reaches `status.yaml`, and "`/close-task` records it after merge" makes no sense for them. `/review-task` then refuses because the file says `CI_PENDING`.
- **IMPACT:** This conflicts with AGENTS.md §2 and the README ("Chat history is not authoritative"; "Only `delivery/status.yaml` stores the current state"). F-08 is only closed for the state check.
- **REQUIRED_CORRECTION:** Pick one defined location. One option:
  - `/verify-task` writes `delivery/handoffs/$1/**`, `delivery/evidence/$1/**` and `status.yaml` uncommitted.
  - "Clean" in `/verify-task` and `/close-task` excludes exactly those fingerprint-excluded records.
  - `/close-task` carries them onto the closeout branch.
  - A non-success result updates `status.yaml` directly.
- **REPRODUCTION:** Walk `/verify-task` to `HUMAN_REVIEW`, then `/close-task` precondition 2 and 7.

### G-04 MAJOR: the F-07 conflict is unguarded in `/close-task` and can trip earlier

- **LOCATION:** `close-task.md` final paragraph of section 4 ("commit them on a dedicated `task/<id>-closeout` branch under DEC-AGENT-01"); `implement-task.md` step 10; `review-task.md` precondition 5.
- **OBSERVATION:**
  - Every closure changes `status.yaml`, and 18 of 20 parseable packets, including I1-IAM-002, prohibit it. DEC-AGENT-01 limits commits to `allowedFiles`.
  - `/ship-task` stops and asks; `/close-task` tells the orchestrator to commit "under DEC-AGENT-01" with no stop.
  - `/implement-task` step 10 and `/review-task` precondition 5 ("changed files remain within approved scope") also see `status.yaml`, which the orchestrator itself changed. They give no rule for it, so a literal reading stops review for those packets with no explanation.
- **IMPACT:**
  - AC-05's "no command contradicts it" is not met.
  - The human prompt on commit limits harm but does not tell the owner about the conflict.
  - The owner's F-07 decision must not be pre-empted, so this only needs a stop.
- **REQUIRED_CORRECTION:** Copy gate step 3 into `/close-task`, and require the rest of the `/ship-task` gate there explicitly. In `/implement-task` and `/review-task`, say that orchestrator-owned records are excluded from the scope check until the owner decides, and that `/ship-task` raises the conflict.
- **REPRODUCTION:** `node <scratchpad>/packets.mjs <scratchpad>`: status allowed 2, prohibited 18.

### G-05 MAJOR: a single "Allow always" reply overrides the deny rules, and nothing warns about it

- **LOCATION:** `ship-task.md` ("Staging, committing, and opening the pull request prompt a human"); evidence Limitations; OpenCode 1.19.0 `Permission.ask` and `Permission.reply`.
- **OBSERVATION (code-derived; live session NOT_RUN):**
  - `ask` evaluates `c(Z.permission,Y,$,M)`, with approved rules `M` last under `findLast`.
  - `reply "always"` pushes `{permission, pattern: <always>, action: "allow"}`, and `always` is `Eo.prefix(R).join(" ")+" *"`.
  - The arity table has `git:2`, so answering "always" to `git add -- x` approves `git add *`. That then outranks `git add -A` and `git add -f .env` denies. In the same way, `git commit -m x` becomes `git commit *`, which outranks `--amend` and `--no-verify`. `git switch -c task/x` becomes `git switch *`, which outranks `git switch -f main`.
  - An unlisted command such as `.\mvnw.cmd -q ...` asks, and I expect "always" there to give `.\mvnw.cmd *`, outranking the deploy denies.
  - The state lasts for the running instance.
  - The evidence and `/ship-task` do not mention this. `grep always` in `agent-permissions.md`, `ship-task.md` and `04-security-reviewer.md` finds nothing relevant.
- **IMPACT:** `/ship-task` makes these prompts routine, so one ordinary click silently cancels the DEC-AGENT-01 sweep and amend protection. The separate permission reviewer may also report this.
- **REQUIRED_CORRECTION:**
  - Add a line to `/ship-task` and the evidence limitations: "approve once, never always".
  - Add a line to the check script's header saying the check does not model session approvals.
  - Confirm the behaviour in a disposable repository alongside SEC-14.
- **REPRODUCTION:** `node <scratchpad>/binwin.mjs 2400 'z=A.fn("Permission.reply")'`, and read `findLast` in `c()`.

### G-06 MAJOR: the generic-secret search misses this repository's common forms

- **LOCATION:** `ship-task.md` gate step 7; evidence "secretlint misses generic password assignments, which `/ship-task` covers with a separate search and human inspection".
- **OBSERVATION:** `-G` is case-sensitive and requires the keyword to run straight into `=` or `:`. Staged files in a temp repo, in both shells:

| Content | Found? |
|---|---|
| `DB_PASSWORD=abc123`, `CLIENT_SECRET: abc123` | Yes |
| `password: abc123` | **No** |
| `client-secret: abc123` | **No** |
| `String clientSecret = "abc123";` | **No** |
| `{"secret": "abc123"}` | **No** |

  Adding `-i` finds the first three misses. The JSON form still fails because of the closing quote. This repository uses those shapes: `apps/bff/src/main/resources/application.yml` has `password:`, and `infra/local/keycloak/realms/ev-local-realm.json` has 8 lines matching `"secret"` or `password`. I1-IAM-002 allows both files.
- **IMPACT:** A real value in the likeliest files passes the gate, which is a gap against AGENTS.md §4 and SEC-18. The evidence's "covers" overstates it.
- **REQUIRED_CORRECTION:** Add `-i` and allow an optional closing quote before the separator. Include lowercase, hyphenated, camelCase and JSON cases in the evidence. Reword the evidence claim.
- **REPRODUCTION:** `node <scratchpad>/fptest.mjs` (the `-G` lines), then the same command with `-i` in `<scratchpad>/grepo`.

### G-07 MINOR: `/review-task` returns a state outside its own allowed list

- **LOCATION:** `review-task.md` Review fingerprint ("return `SELF_VERIFIED`") against "Allowed resulting states".
- **OBSERVATION:** The new text adds `SELF_VERIFIED`, but the list still has only `CI_PENDING`, `FIX_REQUIRED`, `BLOCKED`, `CLARIFICATION_REQUIRED`, `SPEC_CONFLICT`. `/ship-task` and `/verify-task` list it correctly.
- **IMPACT:** The command contradicts itself. AC-06 is still met, because the state is valid under §7.
- **REQUIRED_CORRECTION:** Add it to the list.

### G-08 MINOR: the fingerprint depends on the shell and has no failure rule

- **LOCATION:** `review-task.md` Review fingerprint; evidence row "PASS: both produced hashes on this worktree".
- **OBSERVATION:**
  - **Default shell (code-derived):** `EU()` is `[pwsh, powershell, git-bash, COMSPEC]`, and the tool uses `Go.acceptable(config.shell)`, falling back to `process.env.SHELL`. `opencode.json` sets no `shell`, and `pwsh` is not installed. Launched without `SHELL`, the default on this host is `powershell.exe -NoProfile -Command`. SEC-14 had left this undetermined.
  - **Shell-dependent hash:** the `git diff` hash differs between Git Bash and PowerShell (`82122fe8` versus `435a03c8`). `/review-task` in one shell and `/ship-task` in another would report a false change. This fails safe.
  - **Second command can fail:** in Windows PowerShell 5.1 with an inherited UTF-8 console, the second command fails: `fatal: could not open '<BOM>.opencode/commands/ship-task.md'`. Spawned without a console (IBM437), it works and matches bash. If both runs fail, "the two results" are equal and empty, and untracked files are not protected. Nothing says a failed command means `BLOCKED`.
  - **Rename gap:** an untracked file renamed with the same content is not detected.
  - **Evidence gap:** the evidence does not name a shell.
- **IMPACT:** The fingerprint usually works, but the unattended failure case silently loses coverage.
- **REQUIRED_CORRECTION:** Say that a non-zero exit or missing output means `BLOCKED`. Record the shell in `review-fingerprint.md`. Compare the saved untracked file list as well as the hashes. Consider setting `shell` in config (owner choice).
- **REPRODUCTION:** Run the commands from `<scratchpad>/psbom.mjs` in a UTF-8 console, then spawned via node.

### G-09 MINOR: the check does not cover several commands the new workflow depends on

- **LOCATION:** `check-agent-permissions.mjs` `EXPECTATIONS`.
- **OBSERVATION:** These mutations all stay at 4188/4188 rc=0:
  - removing orchestrator `git hash-object --stdin-paths` (N1);
  - removing orchestrator `gh pr view` (N2);
  - removing orchestrator `gh run list` (N3);
  - removing `git fetch origin` everywhere (N4);
  - widening data-reviewer `skill` to `"*": allow` (N7).

  The exact fingerprint commands and `git ls-files --others` are not expectations either.
- **IMPACT:** A later change could break `/review-task`, `/ship-task` or `/verify-task` while the check stays green. It would surface as a prompt or a stop, not an unsafe result.
- **REQUIRED_CORRECTION:** Add expectations using the literal command-text forms and the skill rules. Consider a drift assertion that each agent's deny block equals root's. There is none today, though I found no drift: all 10 agents hold all 176 root denies.
- **REPRODUCTION:** `node <scratchpad>/mutate.mjs N1_hash_stdin_paths_removed` (and N2, N3, N4, N7).

### G-10 MINOR: `/ship-task` text and the rules disagree in small ways

- **LOCATION:** `ship-task.md` gate step 5, Commit and push step 1, Pull request.
- **OBSERVATION:**
  - **(a) `.env.example`:** step 5 exempts it, but `git add -- infra/local/.env.example` is denied, and it is the repository's one such tracked file. `git diff -- infra/local/.env.example` is denied as well.
  - **(b) Commit message tokens:** the step warns about `>`, `.env` and ` -a ` only. Also denied are ` -n `, `.key `, `.pem`, `--only`, `--all`, `--am...`, `--include` and `--no-verify`. For example, `git commit -m "I1-X: document app.key rotation"` is denied.
  - **(c) PR body:** "Put details in the pull request", but `gh pr create --body "a -> b"` is denied. A body file would need a path inside the repository, which dirties the worktree `/verify-task` requires clean.
  - **(d) Re-shipping:** after a fix, `gh pr create` fails because a PR exists, and `gh pr edit` is denied. The command does not say to skip the step.
  - **(e) Status listing:** `git status --short` collapses untracked directories, while step 2 and step 4 need individual files. `-uall` is allowed.
- **IMPACT:** Each of these fails closed, but the next task will hit them.
- **REQUIRED_CORRECTION:** Tell the human to stage `.env.example` themselves. Name the token families, or say "if denied, reword". Extend the `>` warning to the PR body. Skip `gh pr create` when a PR exists. Use `git status --short -uall`.

### G-11 MINOR: legitimate commands changed without disclosure

- **LOCATION:** all agent files; evidence "False positives are deliberate".
- **OBSERVATION:** Baseline to now, for agents that had them (`basecmp.mjs`):

| Command | Change |
|---|---|
| `./mvnw clean verify`, `./mvnw -pl x -am clean test` | allow to deny |
| `./mvnw -pl x -am test "-Dspring.datasource.url=jdbc:postgresql://x"` (any `:` in an argument) | allow to deny |
| `./mvnw -q ... test`, `./mvnw -B ... test` | allow to ask |
| `docker compose config` | allow to ask |
| `git branch`, `git branch -a`, `git branch --contains X` | ask to deny, on all agents |
| `git --version`, `git --no-pager log` | ask to deny |
| `git worktree list`, `git reflog`, `git config --get x` | ask to deny |
| `git log --format="%h -> %s"`, `git grep -n process.env` | allow to deny (`*>*` and `git *.env*`) |

  The evidence lists `2>&1` and `.env`/`.pem`/`.key` names. 05 says "other goals" are denied. `clean`, colon arguments and read-only `git branch` forms are not mentioned.
- **IMPACT:** No packet command is affected, and I found no authoritative file using them. The debugger's "run the supplied reproducer unchanged" can hit a deny if the reproducer uses `clean`, `2>&1` or a colon argument. Friction will be real but safe.
- **REQUIRED_CORRECTION:** Add these to the evidence false-positive list, or re-allow bare `git branch` and `git branch --list` if the owner wants them. Keep the denies otherwise.

### G-12 MINOR: records

- **LOCATION and OBSERVATION:**
  - **(a) Branch protection is stale:** `status.yaml` ("Owner decisions pending: ... branch protection"), `05-coder-fixround.md` and the evidence ("`main` was unprotected when this was recorded") are out of date. `gh api .../branches/main/protection` now returns pull requests required (0 approvals), `enforce_admins: true`, force-push false, deletion false, and no required status checks. The first review saw 404, so this changed since then. The owner should confirm it was deliberate.
  - **(b) I1-IAM-002 `branch` field:** it is still `task/i1-iam-002-token-exchange`, which is already merged through PR #49 (tip `946c0a73`, 11 behind main, 0 ahead). The entry otherwise points to `-ci` and `-phase3`.
- **IMPACT:** Small. (b) is the one field in the corrected entry that does not match the repository's current state of work.
- **REQUIRED_CORRECTION:** Update (a) when recording the re-review, including the zero-approval and no-required-checks settings. Set (b) to null, or note it as historical, pending the owner's line decision.

### Notes

- **N-1:** `/start-task` does not create or confirm a task branch, while `/ship-task` precondition 4 requires one. Recovery with `git switch -c` is possible and prompts, but a branch check early would avoid this.
- **N-2:** `/review-task` recommends a debugger handoff, but no command invokes the debugger, so the orchestrator must do it free-form. The rest of that path works.
- **N-3:** `/verify-task`'s "only the `candidateCommit` and `branch` fields" fails if the record commit also bumps `updatedAt`. `/ship-task` should say not to. The `pullRequest` field is never written before close.
- **N-4:** no command assigns `INDEPENDENT_REVIEW`. This is pre-existing and not an AC-06 defect. First-round N-4 (the tester runs unconditionally while this packet waives it) still needs the owner.
- **N-5 (dead or redundant rules, harmless):**
  - no `flyway-maven-plugin` exists in any tracked pom, so data-reviewer's `flyway:validate` and `flyway:info` allows do nothing;
  - `npm run contracts:validate*`, `docs:*`, `lint:docs*`, `lint:contracts*`, `validate:contracts*`, `lint*`, `typecheck*`, `format:check*` and `security*` match no script in the root or `apps/web` `package.json` (this repo's scripts are `contracts:*` and `ng`-based);
  - `make *` has no Makefile, which is a packet non-goal;
  - bare-plus-tail pairs such as `git status` and `git status *` repeat each other under the optional-tail rule.

  As a result, documentation's `npm run contracts:registries` and `contracts:docs` prompt rather than allow.
- **N-6:** F-10's residual (tool names with no underscore) is disclosed but not listed as an owner decision. Its required correction said to record it as accepted if not fixed.
- **N-7:** the evidence lacks per-run timestamps (README). `delivery/iterations/` does not list I1-DEL-001 or I1-IAM-002, and the packet cannot edit that directory anyway. `05-coder-fixround.md` varies the handoff naming slightly. None of this is a defect.
- **N-8:** I did not positively confirm that the gate's secretlint command detects a key. It refuses a scratchpad path as "not in cwd", and I did not write into the repository. That message does prove the arguments reach secretlint through `npm exec --no --` in both shells, and the first scan exits 0 on real files.

## Categories found clean

- **Scope:** all 31 changed files match `allowedFiles`. The diff is empty for `AGENTS.md`, `docs`, `contracts`, `services`, `apps`, `libraries`, `simulator`, `infra`, `.github`, `.opencode/skills`, `scripts/contracts`, the validators, the self-test and `delivery/tasks/I1-IAM-002.yaml`.
- **Frontmatter only:** every agent's body is byte-identical to baseline after line-ending normalization.
- **Loadability:** `opencode agent list` shows the same 17 agents in both configs. `debug agent` parses for all 14 checked agents. `opencode.json` is valid.
- **Agent duties:**
  - Agent prompt bodies name no specific git, Maven, docker or flyway commands, so no body contradicts a deny.
  - `.\mvnw.cmd -pl apps/bff -am test "-Dmaven.compiler.release=21"` and the two service forms allow for coder, tester, reviewer, debugger and the three specialist reviewers.
  - `npm run contracts:verify` allows for coder, tester, reviewer, debugger, contract-reviewer and security-reviewer. It asks for data-reviewer, which does not need it.
  - `git diff --check` and `git log` with `%` formats allow for every agent. `2>&1` is denied for every agent, as disclosed.
  - Across all 20 parseable packets' focused commands, the only deny is a `node -e` command. Others resolve to allow or ask.
  - Handoffs and evidence are writable by the agents that produce them. Reviewers cannot write, and the orchestrator can save for them.
- **Secret scan:** exit 0 on 31 files. Manual grep found only placeholder values.
- **Records:**
  - `node scripts/delivery/validate.mjs` PASS, and the summary counts match.
  - `node scripts/delivery/self-test.mjs` PASS, 8/0.
  - Handoff sequence and latest-handoff values are correct.
  - RECON-01 facts match git: `-ci` `431392f6` is 6 ahead and 13 behind, fork `589a36f7`, last commit 2026-09-14. `-phase3` `0067c9c2` is 3 ahead and 1 behind, fork `dc712a15`, last commit 2026-10-01. PRs #48-#51 are merged, and `7b44916` is the scripts/dev commit.
  - The `-ci` line's `clientProfiles`, `clientPolicies` and attribute are in its realm file, and its evidence reads them back as stated.
- **03 and 04 handoffs:** both are complete reports through their closing sections. Their declared edits are limited to a header, a path placeholder and, in 04, one masked fake value. I had no original to compare against, so "unedited" itself is unverifiable.

## 5. Check script

| Run | Result |
|---|---|
| Global config | PASS 4188/4188, 17 agents, exit 0 |
| Empty `XDG_CONFIG_HOME` | PASS 4188/4188, 17 agents, exit 0 |
| Scratch control copy (empty config) | PASS 4188/4188 |
| `opencode` removed from PATH | Fails closed, exit 1 (Node stack trace) |

| Mutation (scratch copy, empty config) | Detected? |
|---|---|
| M1 root bash `*` removed (F-09 gap) | Yes, 4167 |
| M7 `gh` read-only removed from reviewer, tester, security-reviewer (F-09 gap) | Yes, 4182 |
| M8 validator allows removed from reviewer, tester, documentation (F-09 gap) | Yes, 4185 |
| M9 read-side secret denies removed (F-09 gap) | Yes, 4006 |
| M10 planner `task: allow` (F-09 gap) | Yes, 4186 |
| N5 documentation handoff edit removed | Yes |
| N6 debugger `*>*` removed | Yes, 4181 |
| N8 new project agent added | Yes, reported as unexpected |
| N9 secretlint allow removed | Yes |
| N10 root `*_*` removed | Yes, 4168 |
| N11 orchestrator status edit denied | Yes |
| N1, N2, N3, N4, N7 | **No** (G-09) |

Style is consistent with `validate.mjs`: banner, ESM imports, exit code, `FAIL` lines. Only `.permission` is parsed, so provider data never prints. I did not inspect provider blocks.

## Commands executed

| Command | Result |
|---|---|
| `node scripts/delivery/check-agent-permissions.mjs` (both configs) | PASS 4188/4188 |
| `node scripts/delivery/validate.mjs` | PASS |
| `node scripts/delivery/self-test.mjs` | PASS 8/0 |
| `npm exec --no -- secretlint ... <31 files>` | PASS, exit 0 |
| Fingerprint commands run twice (Git Bash and PowerShell) | Stable per shell; second command FAIL in PowerShell with UTF-8 console |
| `opencode agent list`, `opencode debug agent <14 agents>` (both configs) | PASS |
| 17 scratch mutation runs plus control | See section 5 |
| Temp-repo fingerprint and `-G` tests (`fptest.mjs`) | See G-01, G-06, G-08 |
| Baseline comparison (`basecmp.mjs`), packet command evaluation (`packets.mjs`) | See G-11, clean categories |
| `git`, `gh` branch, PR and protection reads | See G-12, clean categories |
| Live OpenCode session, real `/ship-task`, Maven suites, `npm run contracts:verify`, CI | NOT_RUN (read-only review) |

## Acceptance criteria

| AC | Status | Basis |
|---|---|---|
| AC-01 | PASS for the recorded forms by rule evaluation | Adversarial coverage is the separate reviewer's. G-05 is a live-use caveat. |
| AC-02 | PASS | 17 agents in both configs; a new agent is detected. |
| AC-03 | PASS | `docs/**`, manifests and validators ask; secret patterns deny at root and in subpaths. Documentation asks for `docs/**`. |
| AC-04 | PASS | Packet Maven commands and CI inspection allow where needed; push limited to one task branch by rule. G-09 is a proof gap for some other commands. |
| AC-05 | **FAIL** | `/ship-task` itself is usable, and results are saved as handoffs, but G-01, G-02, G-03, G-04 and G-06 break it. End-to-end NOT_RUN. |
| AC-06 | PASS | All listed states are valid under §7. G-07 is an internal inconsistency. |
| AC-07 | PASS with minor corrections | Facts verified. G-12(b) is the one stale field. |

```text
Decisions made: none; severity assignments only.
Assumptions: OpenCode's live permission behaviour matches the evaluator read from the 1.19.0 binary; the fingerprint temp repo represents the real repository's git behaviour.
Findings and residual risks: above. Text patterns remain non-exhaustive, and the fix round's limitations in the evidence remain valid.
Blockers: none at BLOCKER level; 6 MAJOR findings prevent human review under AGENTS.md §15.
```

**REVIEW_STATUS:** FAIL_FINDINGS

**RECOMMENDED_STATE:** FIX_REQUIRED

**Recommended next step:** command-text fixes for G-01 to G-08 and G-10 are confined to `.opencode/commands/*.md`, plus `check-agent-permissions.mjs` for G-09 and the evidence for G-05, G-06, G-11 and G-12. All are inside `allowedFiles`. Then re-review.

## Required human decisions

1. **F-07 and G-04:** how `delivery/status.yaml` and closeout commits relate to packet `allowedFiles` under DEC-AGENT-01. It blocks `/ship-task` and `/close-task` for 18 of 20 packets.
2. **G-03:** where `/verify-task` records its result. Uncommitted records carried to closeout is my suggested option, not a decision.
3. **Commit mechanism and `--auto`:** a wrapper script versus prompts. G-05 increases the case for a wrapper, because prompts carry an "always" reply. Decide whether `--auto` is banned.
4. **Branch protection on `main`:** it is now on. Confirm this was deliberate and whether zero required approvals and no required status checks are intended.
5. **SEC-14 and G-05 confirmation:** approve a disposable-repository test.
6. **First-round N-4:** whether the tester waiver for this packet is intended, given `/review-task` invokes the tester unconditionally.
7. **F-10 residual:** accept tool names with no underscore defaulting to allow, or add a rule.
8. **Shell:** whether to set `shell` in `opencode.json`, so fingerprints are stable across launches.
