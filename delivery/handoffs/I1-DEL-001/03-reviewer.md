---
role: reviewer
taskId: I1-DEL-001
resultingState: FIX_REQUIRED (recommended)
baselineCommit: 811b81ed39ca954217cf785f5f9c3135f14b2263
impactLevel: L3
date: 2026-11-04
---

Independent subagent output, saved unedited except for this header and the replacement of any local temp path with `<session scratchpad>`. It reviews the first candidate, before the fix round.

# I1-DEL-001 Independent General Review

```text
Task ID: I1-DEL-001
Role: Reviewer (general, independent; did not author this work)
Task state: SELF_VERIFIED at review; recommended FIX_REQUIRED
Baseline commit: 811b81ed39ca954217cf785f5f9c3135f14b2263 (HEAD; work is uncommitted)
Impact level: L3
Documents and sections read: AGENTS.md; delivery/tasks/I1-DEL-001.yaml; delivery/README.md; full diff plus all 8 untracked files; all 7 commands in full; validate.mjs (header and SHA/state rules); DEC-AGENT-01 register row; ARC-016 grep for commit/push rules; .github/workflows triggers; I1-IAM-002 packet, deviations and handoffs on main, -ci and phase3
Files changed: none in the repository. Temporary probe scripts and config copies are in the session scratchpad only.
```

## Method and limits

- **Rule evaluation:** I evaluated commands against the rules from `opencode debug agent <name>`, extracting only the `permission` array. I did this with the global config and with `XDG_CONFIG_HOME` pointing at an empty directory.
- **Commands not executed:** no probed command was run. "Allow" below means OpenCode's rule evaluation returns allow, not that the command was observed running.
- **Real matcher:** I read it from the installed binary, `opencode-windows-x64/bin/opencode.exe` (v1.19.0):
  - Wildcard match: `e.replaceAll("\\","/")`, the same on the pattern, `*`→`.*`, `?`→`.`, trailing ` .*`→`( .*)?`, regex flags `"si"`.
  - Evaluation: `findLast(match(permission) && match(pattern)) ?? {action:"ask"}`.
  - So last match wins and the fallback is ask, as documented. Two things the check script does not model: matching is **case-insensitive** and backslashes are normalized.
- **Not examined:** how the bash tool splits compound commands, and any live OpenCode session.

## Commands executed

| Command | Result |
|---|---|
| `node scripts/delivery/check-agent-permissions.mjs` (global config) | PASS 811/811, 17 agents |
| Same, empty config directory | PASS 811/811, 17 agents |
| Same expectations using OpenCode's real matcher (scratchpad copy) | PASS 811/811 |
| Same script against `git archive 811b81e` config | FAIL 480/811, 29 agents; reproduces the evidence "before" row |
| `node scripts/delivery/validate.mjs` | PASS |
| `node scripts/delivery/self-test.mjs` | PASS 8/0 |
| `npm exec --no -- secretlint --secretlintrc scripts/contracts/secretlintrc.json <all 28 changed files>` | PASS, exit 0 |
| 11 config variants of scratchpad copies (control plus 10 mutations) | See F-09 |
| `git`/`gh` branch, PR, run and protection queries | See section 3 |
| Live session, `/ship-task` end to end, Maven suites, CI | NOT_RUN (outside this read-only review) |

## Findings

### F-01 BLOCKER: orchestrator allow rules still permit pushes to main and force pushes

- **LOCATION:** `.opencode/agents/orchestrator.md`, `"git push origin task/*": allow` and `"git push -u origin task/*": allow`.
- **OBSERVATION:** `*` spans spaces, so anything after the first task ref is allowed unless a later deny matches. The later denies only catch ` main`, ` -f` and similar exact tokens. Evaluated as **allow, no prompt**, in both configurations:
  - `git push origin task/x refs/heads/main`
  - `git push -u origin task/x refs/heads/main`
  - `git push origin task/x refs/heads/master`
  - `git push origin task/x -fu`
  - `git push origin task/x -uf`
  - `git push origin task/x refs/heads/main -fu`
  - `git push origin task/x --tags`
  - `git push origin task/x other-branch`
- **Full path to main with no prompt (empty config):** `git fetch . task/x:main` then `git push origin task/x refs/heads/main`. With the global config, the fetch prompts instead.
- **IMPACT:**
  - AC-01 ("deny direct pushes to main, force pushes") fails, and so does the packet invariant that force-push stays human-only (DEC-AGENT-01).
  - `gh api .../branches/main/protection` returns `404 Branch not protected`, so nothing downstream stops it.
  - The evidence's limitation about "unanticipated forms" describes evading a deny. Here the task's own new allow rule grants the command without a prompt.
- **REQUIRED_CORRECTION:** Make the allow unable to carry extra arguments, or deny them after it. For example, add denies such as `git push *refs/*`, `git push * -*f*`, `git push *--tags*` and `git push *--follow-tags*`, and a deny or ask for a second positional ref. A narrower option is to allow only the one form `/ship-task` uses and ask for everything else. Add every command above to `FORBIDDEN_COMMANDS`.
- **REPRODUCTION:** Take `opencode debug agent orchestrator`, then apply the last matching rule to each command. The winning rule reported is `[bash | git push origin task/*]`.

### F-02 MAJOR: sweep staging and sweep commits remain auto-allowed for the orchestrator

- **LOCATION:** `orchestrator.md`, `"git add *": allow` and `"git commit -m *": allow`.
- **OBSERVATION:** These evaluate to allow:
  - `git add -- .`
  - `git add ./`
  - `git add -- :/`
  - `git add src .`
  - `git add -vA`
  - `git add -f node_modules`
  - `git commit -m x -am y`
  - `git commit -m x -av`
- **IMPACT:**
  - AC-01 ("sweep staging") and DEC-AGENT-01 are not enforced.
  - `git add -- .` differs by one token from the form `/ship-task` prescribes (`git add -- <paths>`).
  - The `-f` form reaches `node_modules/`, which DEC-AGENT-01 prohibits.
- **REQUIRED_CORRECTION:** Deny `git add *-- .*`, `git add * .`, `git add *./`, `git add *:/*`, bundled `-*A*`, and `-f`/`--force`. Deny bundled commit forms such as `git commit * -*a*`, checked against message false positives. Add these to the check.

### F-03 MAJOR: AC-03 fails for secret-pattern files at the repository root

- **LOCATION:** `coder.md` and `debugger.md` edit rules (`**/*.pem`, `**/*.key`, `**/secrets/**`); the same patterns in `opencode.json`.
- **OBSERVATION:**
  - `**/` compiles to `.*.*/`, which requires a slash.
  - Coder and debugger get **allow** for `edit x.pem`, `edit x.key` and `edit secrets/x`. Build gets ask.
  - `read secrets/x` is allow for every agent probed.
  - `read x.pem` is allow for coder, tester, reviewer, planner, documentation and build under the global config. Only orchestrator and debugger deny it, and only through global `*.pem` rules. I did not re-probe `read x.pem` under the empty config, but those global-only rules would not apply there.
- **IMPACT:** AC-03 says "cannot edit secret-pattern files anywhere in the repository", and the packet's `securityCases` depends on it. The coder handoff records AC-03 as PASS, but the check only tests `.env`, `infra/local/.env` and `infra/local/dev.pem`.
- **REQUIRED_CORRECTION:** Use `*.pem` and `*.key` (`*` already spans `/`) and add `secrets/**`, in the edit and read sections of root, coder and debugger. Add root-level subjects to the check.

### F-04 MAJOR: new Maven allow rules auto-approve deploy, migration and arbitrary execution

- **LOCATION:** `.\mvnw.cmd *test*` and `.\mvnw.cmd *verify*` in coder, debugger, tester, reviewer, contract-reviewer, data-reviewer and security-reviewer.
- **OBSERVATION:** Any Maven invocation containing "test" anywhere is allowed, case-insensitively, including for read-only reviewers. All of these evaluate to allow (the last three were probed on reviewer; the rule is identical in all seven agents):
  - `.\mvnw.cmd deploy -DskipTests`
  - `.\mvnw.cmd flyway:migrate -Dx=test`
  - `.\mvnw.cmd flyway:repair -Dtest=x`
  - `.\mvnw.cmd exec:exec -Dexec.executable=git -Dexec.args=x -Dtest=y`

  The only deny added is `flyway:clean`.
- **IMPACT:**
  - AC-04 asked for test commands to stop prompting. This also removes the prompt for deploy and migration, which are human-only.
  - The `exec:exec` form can route any command around every bash deny.
  - The `./mvnw` form has the same shape in the baseline, so the pattern is inherited. This task extended it to the form that works on this Windows host.
- **REQUIRED_CORRECTION:** Add denies after the allows for `*deploy*`, `*exec:*`, `*-Dexec*`, `*flyway:migrate*`, `*flyway:repair*`, `*flyway:baseline*`, `*spring-boot:run*`, `*install*` where not needed, and `*release:*`, in both Maven forms. Data-reviewer's `flyway:validate` and `flyway:info` allows need the same guard. Add these as negative expectations.

### F-05 MAJOR: the two-commit ship step makes /verify-task's CI rule unsatisfiable

- **LOCATION:** `ship-task.md` Commit and push step 4; `verify-task.md` preconditions 6-7, procedure 1 ("reviewed diff equals candidate commit") and 2 ("no stale CI result from another commit").
- **OBSERVATION:**
  - All four workflows trigger only on `push: branches: [main]` and `pull_request: branches: [main]`.
  - Ship pushes both commits, then opens the PR. CI therefore runs only on the record-update tip.
  - `candidateCommit` is the first commit, which never gets a run. It cannot name the tip, because a commit cannot contain its own SHA.
- **IMPACT:** `/verify-task` must either retain `CI_PENDING` forever or accept CI "from another commit", which the command forbids. This answers review question 2: yes, the two-commit approach breaks the "exact CI evidence" rule, and its "reviewed diff equals candidate commit" check as well.
- **REQUIRED_CORRECTION:** Define the candidate explicitly. One option: the candidate is the PR head SHA, recorded by `/verify-task` in its evidence rather than in the committed tree. Another: keep the two commits and state in `/verify-task` that CI on the tip is accepted only when `git diff <candidate> <tip>` is limited to the `candidateCommit` field in `delivery/status.yaml`.
- **Related path-filter gap:** the workflows are path-filtered, and `delivery/**`, `.opencode/**` and `opencode.json` trigger nothing. A control-plane-only PR gets no CI run. `/verify-task` needs a stated rule for packets with `requiredCiChecks: []`. This PR triggers G3 only because it adds a file under `scripts/**`.

### F-06 MAJOR: "no file changed since review" contradicts itself and cannot be checked

- **LOCATION:** `review-task.md` section 6 (`CI_PENDING` only when "no files changed during review") and the new paragraph requiring handoffs and status to be saved "before determining the result"; `ship-task.md` precondition 5; `verify-task.md` precondition 6.
- **OBSERVATION:**
  - `/review-task` must write handoffs and `status.yaml` during review, which breaks its own condition as written.
  - Review runs on an uncommitted worktree and records no fingerprint of what was reviewed, because `CANDIDATE_COMMIT_OR_DIFF` is undefined. `/ship-task` has nothing to compare against.
- **IMPACT:** The check that protects the reviewed content is either always false or unverifiable. Edits made after review could ship unnoticed.
- **REQUIRED_CORRECTION:** Have `/review-task` record a fingerprint before review starts, for example the SHA-256 of `git diff <baseline>` plus untracked file hashes, excluding `delivery/handoffs/<id>/**` and `delivery/status.yaml`. Compare it in `/ship-task`. State the exclusion explicitly in all three commands.

### F-07 MAJOR: the ship gate fails on its own status edit for most packets

- **LOCATION:** `ship-task.md` Pre-commit gate step 2 and Commit and push step 4; `delivery/tasks/*.yaml`.
- **OBSERVATION:**
  - Of 20 parseable packets, **18 list `delivery/status.yaml` in `prohibitedFiles`**, including I1-IAM-002, and only 2 allow it. `I1-DSC-001.yaml` does not parse (see Notes).
  - `/review-task` changes `status.yaml` before ship. Gate step 2 then requires every changed file to match `allowedFiles` and no prohibited entry, so the result is `BLOCKED`.
  - Committing it anyway breaks DEC-AGENT-01's "only for files in the current task packet `allowedFiles`". The same applies to the `/close-task` closeout branch.
- **IMPACT:** `/ship-task` dead-ends for I1-IAM-002, the next real task. This task passes only because its own packet allows the file.
- **REQUIRED_CORRECTION:** This needs an owner decision, because it touches DEC-AGENT-01's scope. The options are a recorded carve-out for orchestrator-owned control-plane records, or a packet rule that adds `delivery/status.yaml` to `allowedFiles` and restricts the coder through agent permissions instead. An agent must not resolve this by reinterpretation.

### F-08 MAJOR: dead end from /verify-task to /close-task

- **LOCATION:** `verify-task.md` section 5; `close-task.md` preconditions 3 and 7; `implement-task.md` step 15.
- **OBSERVATION:**
  - `/verify-task` sets `HUMAN_REVIEW`, but no command commits that record. Commit and push happen "only through `/ship-task`", and a commit here would change the candidate and force re-review.
  - After the human merges, main's `status.yaml` shows `CI_PENDING`, as committed by ship.
  - `/close-task` requires `HUMAN_REVIEW` or `MERGED` **and** a clean worktree.
  - If the record stays uncommitted, the worktree is dirty. If the merged file is used, the state check fails.
- **IMPACT:** Close cannot succeed as written. The removed text ("still require a human commit") used to describe this gap. The new text only covers changes `/close-task` itself makes.
- **REQUIRED_CORRECTION:** Let `/close-task` accept `CI_PENDING` on the merged branch when verification evidence and approval are supplied, or define where the `HUMAN_REVIEW` record lives (for example, verification evidence plus the first closeout commit). Document one legal path.

### F-09 MAJOR: the check misses regressions it is cited as proving

- **LOCATION:** `scripts/delivery/check-agent-permissions.mjs` `EXPECTATIONS`.
- **OBSERVATION:** Mutation results on scratchpad copies:

| Mutation | Detected? |
|---|---|
| Control copy, unmodified | PASS 811/811 (expected) |
| Baseline config | Yes, 480/811 |
| M2 root `"*":"ask"` restored | Yes, 586/811 |
| M4 coder `.pem` deny removed | Yes, but 1 subject only |
| M5 reviewer `"*":allow` added last | Yes, 752/811 |
| M6 global agent re-enabled | Yes |
| **M1 root bash `"*"` catch-all removed** | **No, 811/811** |
| **M7 `gh` read-only rules removed from reviewer, tester, security-reviewer** | **No, 811/811** |
| **M8 validator allows removed from reviewer, tester, documentation** | **No, 811/811** |
| **M9 all read-side secret denies removed** | **No, 811/811** |
| **M10 planner given `task: allow`** | **No, 811/811** |
| M3 orchestrator `git push * main` removed | Not detected, but not a gap: `git push * main *` makes it redundant |

- **IMPACT:**
  - AC-04 ("for the agents that need them") is only proven for orchestrator `gh` use and for Maven.
  - Unmatched bash falling back to ask is not asserted, and it is the property the whole design depends on. If lost, the built-in `*: allow` applies.
  - Together with F-01 to F-03, 811/811 overstates coverage.
- **REQUIRED_CORRECTION:** Add expectations for the fallback (`curl x` → ask on every agent), per-agent `gh` and validator allows, read-side secret denies, and `task` deny on non-orchestrator agents, plus the F-01, F-02, F-03, F-04, F-10 and F-11 subjects.

### F-10 MAJOR: removing the root catch-all makes unlisted tools fail open

- **LOCATION:** `opencode.json`, removed `"*": "ask"`.
- **OBSERVATION:**
  - The resolved rule list starts with the built-in `{*, *, allow}`. Permission names not listed anywhere now evaluate to **allow** on every agent in both configurations, including read-only reviewers. Probed: `codesearch`, `mcp_github_merge_pull_request`, `some_custom_tool`.
  - The baseline resolved these through the root `*: ask`.
  - Neither the evidence nor the handoff mentions this. Both describe only the bash and edit effect.
- **IMPACT:**
  - Exposure today is limited, because no MCP servers appear in the global `opencode.json` or `opencode.jsonc` and I found no project tools directory.
  - The next MCP server added would get a merge tool with no prompt, which violates "only a human merges".
  - The removal itself was justified: M2 confirms root cause 1. The side effect was not handled.
- **REQUIRED_CORRECTION:** Restore a default ask for non-core tools in a way that survives the key-order problem, for example explicit `codesearch: ask` and a wildcard permission key for MCP-style names, ordered so it does not shadow `external_directory`. Add an expectation. Otherwise, record this as an owner-accepted risk.

### F-11 MINOR: `.env.example` rules became dead and the file is now denied

- **LOCATION:** `coder.md`, `debugger.md` edit rules.
- **OBSERVATION:** Moving the secret denies last means `.env.*` and `**/.env.*` now override the `.env.example: ask` lines. Coder and debugger get deny for `infra/local/.env.example`, which is a tracked file. The baseline resolved this to ask. Not disclosed.
- **IMPACT:** The failure is on the safe side, but a legitimate edit is blocked and the config reads as saying the opposite.
- **REQUIRED_CORRECTION:** Move the two `.env.example` ask rules after the denies and add an expectation.

### F-12 MINOR: the check's matcher does not match OpenCode's

- **LOCATION:** `toRegex`, `evaluate` in the script; packet assumption "reproduces OpenCode's matching"; evidence "Method".
- **OBSERVATION:**
  - The script is case-sensitive; OpenCode uses `si`.
  - The script does not normalize `\`; OpenCode converts it to `/`.
  - The script defaults to `allow`; OpenCode defaults to `ask`. This is inert today because rule 0 is `*`.
  - The script compares permission names with `===`; OpenCode uses a wildcard match.
  - Its header comment also omits the trailing ` *` rule it implements, though the implementation of that rule is correct.
  - Under OpenCode's matcher `git checkout -B task/x` and `git switch -C task/x` are **allow** through the `-b`/`-c` rules. That can reset an existing task branch. No pattern can separate them because matching ignores case.
- **What is fine:** all 811 current results are unchanged under the real matcher, so no result is wrong today.
- **Backslash result:** the evidence row "Backslash handling ... NOT_RUN" can be narrowed. The binary shows `.\mvnw.cmd` rules do match, and they also match `./mvnw.cmd`. A live session remains NOT_RUN.
- **REQUIRED_CORRECTION:** Copy OpenCode's exact matching into the script. Decide how to treat `-B`/`-C`, for example by making branch creation ask.

### F-13 MINOR: removing agent catch-alls widened read-only agents without disclosure

- **LOCATION:** all agents with the bash `"*": ask` removed; root `git fetch*`.
- **OBSERVATION:**
  - Root allows now reach agents they were previously cancelled for. Reviewer, tester, planner and others get allow for `git fetch . task/x:main` and `git fetch --upload-pack=evil origin`.
  - Behaviour also depends on global config: with it, orchestrator and debugger keep a global `*: ask` that cancels the root allows, so `git fetch` asks. Without it, `git fetch` allows.
  - Evidence and handoff report "holds in both configurations" only for the tested expectations.
- **REQUIRED_CORRECTION:** Narrow to `git fetch origin*`, deny `--upload-pack` and local-path refspecs. Disclose the difference between configurations.

### F-14 MINOR: I1-IAM-002 records contain small overstatements

- **LOCATION and OBSERVATION:**
  - **(a)** RECON-01 `title` asserts the -ci line is "the most complete one", while its own description says the results are unreviewed and unrun.
  - **(b)** The `status.yaml` deviation line says realm import "cannot express" the finalization. The deviation's own title says "cannot **deterministically** express", its `correctedRootCause` retracts the defect claim, and -ci reports import working. This contradiction between the two lines is the main technical point for the owner, and RECON-01 does not call it out.
  - **(c)** `03-orchestrator.md` recommends `/plan-task` next, but that command requires a claimed task and the state is `BLOCKED`. No transition is specified.
  - **(d)** `phase3.selfReportedState: IMPLEMENTING` comes from that branch's unchanged status entry (`assignedAgent: null`), which the handoff itself calls stale. It is not a real self-report.
- **REQUIRED_CORRECTION:** Soften the title to "the line with the most self-reported progress". Quote the deviation title accurately and add the import contradiction. State the BLOCKED→CLAIMED step. Mark (d) as a stale record.

### F-15 MINOR: evidence and handoff details

- **LOCATION:** `agent-permissions.md`, `02-coder.md`.
- **OBSERVATION:**
  - The secret scan row says 26 files, but 28 exist now. The evidence file and coder handoff appear not to have been scanned. I ran all 28: PASS.
  - The handoff records AC-01 and AC-03 as PASS "by permission check", which F-01 to F-03 show is overstated.
  - AC-06 "by grep and validator": the validator does not read command files, so only the grep supports it. My own read of the commands supports AC-06.
  - README asks for a timestamp; the evidence gives only a date.
- **REQUIRED_CORRECTION:** Rerun the scan on the final file set and correct these lines when fixes are recorded. Change AC-01 and AC-03 to reflect the re-verified result.

### Notes (no action required unless noted)

- **N-1:** `npm exec --no -- secretlint ...` resolves to **ask** for the orchestrator. The gate can still run, but each ship prompts. Add an allow if unattended gating is wanted.
- **N-2:** A commit message containing ` -a ` is denied (`git commit -m "fix option -a handling"`). Because matching is case-insensitive, ` -A ` is too. The ship template could hit this.
- **N-3:** `gh pr create` is not in DEC-AGENT-01's list of autonomous actions, but the human prompt covers it.
- **N-4:** `/review-task` invokes `tester` unconditionally while this packet sets `reviews.tester: false`. The owner should confirm that is intended, given AGENTS.md §8.
- **N-5:** Agent bodies still return `SCOPE_EXPANSION_REQUIRED` (debugger) and `HUMAN_APPROVAL_REQUIRED` (contract-reviewer, data-reviewer, documentation, planner). AC-06 covers commands only and bodies are out of scope, so this is not a defect, but `/review-task` has no mapping for them.
- **N-6:** Global `orchestrator.md` leaves `task` allow rules for 15 names after the project's `*: deny` (for example `task scout`, `coder-fast`). The 12 disabled agents cannot be called, and the others have no agent file, so AC-02 holds. This is a real dependency on the disable list, and the check would catch a new agent.
- **N-7 (pre-existing, out of scope):**
  - `git merge-base` is denied for all agents, because `git merge*` comes after its allow.
  - `git diff --output=<file>` writes files under an allow rule.
  - `delivery/tasks/I1-DSC-001.yaml` has a YAML error at line 408.
  - The -ci status records `baselineCommit: 589a36f7c0c22a26f31f761835224b48b1e0e2c9`, an object that does not exist in this clone. RECON-01 correctly uses `589a36f7e863...`, but the owner should know if -ci is chosen.
- **N-8:** `01-orchestrator.md` and `03-orchestrator.md` omit most AGENTS.md §14 handoff fields. Earlier orchestrator handoffs in this repo use the same short prose form.

## Review by category

**1. Scope: CLEAN.**
- All 28 changed files match `allowedFiles`.
- No prohibited path changed (empty diff for `docs`, `AGENTS.md`, `validate.mjs`, `.github`, `delivery/tasks/I1-IAM-002.yaml`).
- Agent-file changes are frontmatter only.
- Non-goals are respected. RECON-01 recommends a line but does not choose one, and no implementation line changed.

**2. Workflow.**
- Start → plan → implement → review → ship chains correctly on states.
- AC-06 is clean: no command assigns a state outside AGENTS.md §7.
- `/ship-task` can run under the orchestrator's rules:

| Command | Result |
|---|---|
| `git status --short`, `git diff --stat` | allow |
| `git add -- <paths>` | allow |
| `git commit -m` | allow |
| `git push -u origin task/x` | allow |
| `node scripts/delivery/validate.mjs` | allow |
| `npm exec --no -- secretlint ...` | ask |
| `gh pr create` | ask |

- It breaks at F-05, F-06, F-07 and F-08.
- Direct text contradictions of DEC-AGENT-01: the removed commit conflict is gone from the commands, and the remaining conflict is F-07. F-01 and F-02 are enforcement gaps against it.

**3. I1-IAM-002 record: facts verified accurate except F-14.**

Each item below was checked against git or `gh`:
- `-ci` tip `431392f6`, candidate `f4673ef9` (tip~1), 6 ahead / 13 behind, fork `589a36f7`, last commit 2026-09-14.
- No PR and no CI runs for `-ci`.
- Nothing on main mentions `iam-002-ci`, `f4673ef`, `431392f` or `PLAN-001`.
- Phase3 tip `0067c9c2`, 3 ahead / 1 behind, fork `dc712a15`, last commit 2026-10-01, audience gate as quoted.
- PRs #48-#51 merged as stated, with #48's merge at `589a36f7`.
- `02-orchestrator.md` baseline `8475f5cd`.
- All self-reported suite counts, the AC-05 NOT_RUN items and the typ note match `02-coder.md`. The status entry on main was stale as claimed.
- `scripts/dev/stx-attribute-put-bug-repro.mjs` is on main from `7b44916`, the packet prohibits `scripts/**`, and no deviation mentions it.
- BOOTSTRAP-01 is still open and the packet is unamended.
- RECON-01's independent point about the typ validator is a fair reading of the handoff. `JwtTypeValidator` is present in both -ci services' `JwtDecoderConfig`.

**4. Check script.** Not vacuous overall, and it fails closed if `opencode` is missing or the agent list format changes. Coverage gaps are F-09; matcher differences are F-12. Style matches `validate.mjs` (same banner, ESM, 2-space, exit code). Credentials are not printed, since only `.permission` is used.

**5. Legitimate work: nothing broken outright.**
- Coder, tester and reviewer test commands, `npm run` rules and documentation's validator work; planner reads and git inspection work.
- Orchestrator state recording works: `delivery/status.yaml`, handoffs, evidence, deviations and backlog are allow, and tasks are ask.
- New friction is prompts, not denials. The orchestrator's `git switch main`, `git checkout main` and `git fetch` (global config) ask, and `/close-task` still works with approval.
- The only regression that blocks work is F-11.

**6. Evidence.**
- No row marked PASS was unrun. I reproduced the permission, validator, self-test and scan results and the 480/811 figure.
- The 343 failure count adds up (331 failed expectations plus 12 unexpected agents).
- `main` unprotected is confirmed.
- Root causes 1, 3 and 4 are supported by my runs.
- NOT_RUN rows are honest. The problems are in what PASS was taken to mean: F-15, F-03, F-01.

## Acceptance criteria status (reviewer view)

| AC | Status |
|---|---|
| AC-01 | FAIL: F-01, F-02, F-04 |
| AC-02 | PASS in both configurations (see N-6) |
| AC-03 | FAIL: F-03 (`docs/**` ask and validators ask are correct) |
| AC-04 | PARTIAL: the rules exist and resolve correctly, the orchestrator push allowlist is too broad (F-01), proof is incomplete (F-09) |
| AC-05 | PARTIAL: command exists, no text contradiction except F-07; unusable end to end per F-05 to F-08 |
| AC-06 | PASS |
| AC-07 | PASS with F-14 corrections |

```text
Decisions made: none; severity assignments only.
Assumptions: OpenCode's permission behaviour in a live session matches the matcher and evaluator read from the 1.19.0 binary.
Residual risks: text-pattern permissions cannot fully parse git; branch protection on main remains the stronger control and is currently absent (owner action, non-goal).
Blockers: F-01 unresolved. F-07 needs an owner decision.
Human decisions required:
  1. F-07: how control-plane records relate to DEC-AGENT-01 and packet allowedFiles.
  2. F-10: fix or accept the default for unlisted tools.
  3. Enable branch protection on main (already an owner item).
  4. N-4: whether tester is intentionally waived.
Recommended next agent: coder or debugger for F-01 to F-04, F-09 to F-13; orchestrator for F-05, F-06, F-08 command wording and F-14, F-15 records; then fresh general and security review.
```

**REVIEW_STATUS:** FAIL_FINDINGS

**RECOMMENDED_STATE:** FIX_REQUIRED
