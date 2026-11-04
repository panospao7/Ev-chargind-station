---
role: security-reviewer
taskId: I1-DEL-001
resultingState: FIX_REQUIRED (recommended)
baselineCommit: 811b81ed39ca954217cf785f5f9c3135f14b2263
impactLevel: L3
date: 2026-11-04
---

Independent subagent output (second round), saved unedited except for this header, the replacement of any local temp path with `<session scratchpad>`, and any home-directory path with `~`. It reviews the fix-round candidate.

Task ID: I1-DEL-001
Role: Independent security reviewer, second round (read-only)
Task state: FIX_REQUIRED (recommended; the orchestrator controls state)
Baseline commit: 811b81ed39ca954217cf785f5f9c3135f14b2263 (candidate is the uncommitted worktree on task/i1-del-001-opencode-controls)
Impact level: L3

# Result

SECURITY_REVIEW_STATUS: FAIL_FINDINGS. There are 4 BLOCKER, 7 MAJOR, 5 MINOR and 2 NOTE.

The literal first-round forms are closed: every listed attack string I re-tested resolves as intended in both configurations. The design still fails for a reason the check cannot see. OpenCode matches patterns extracted by a shell parser, not the raw command string, and the check tests raw strings.

I ran OpenCode's own grammars over the attack strings, which no earlier review did. Three results stand out:

- **PowerShell:** ordinary commands such as `git checkout -- .` and `git push origin -- main` produce no patterns, so no rule is consulted and no prompt appears. PowerShell is the shell OpenCode picks on this machine unless `SHELL` is set in the launching environment or a `shell` config key is set.
- **Both shells:** `(git status) > opencode.json` resolves to `allow` for all 14 agents.
- **Bash:** the orchestrator can reach remote `main` at `allow` through word splitting.

Passing 4188/4188 does not show that AC-01, AC-03 or AC-04 hold.

# Method and limits

- **Rules:** the `permission` array from `opencode debug agent <name>` for 17 agents, in two configurations. "Global" is the owner's config; "empty" sets `XDG_CONFIG_HOME` to scratchpad/emptycfg. I did not read any provider block.
- **Matcher, confirmed from the 1.19.0 binary:** function `ql` normalizes backslashes, uses flags `si`, gives ` *` an optional tail, and evaluates with `findLast` falling back to `ask`.
- **Parser, new in this round:** I extracted web-tree-sitter 0.25.10 and the bash and PowerShell grammars from the installed binary, and reimplemented the pattern-extraction functions (`St`, `Pt`, `vt`, the `Xe` skip set) as they read in the binary. Separately, `ShellTool.ask` returns without asking when `patterns.size===0`.
- **Notation:** `NOCHECK` means no pattern was extracted, so no rule applies and no prompt appears. `deny` on any extracted pattern denies the whole command.
- **Nothing tested was executed.** No listed git, gh, npm, Maven or shell attack command ran. Statements about what git, Maven or npm then do come from local git documentation or known tool behavior, and are NOT_RUN unless stated otherwise. No live OpenCode session was run.
- **Harmless checks I did run:**
  - `fs.stat` on Windows path aliases;
  - `dir /x`;
  - ripgrep on a fake marker file in the scratchpad;
  - the delivered check, validator and self-test.
- **Interruption:** one of my responses in this review was stopped by a safety classifier partway through. I resumed with string evaluation only and rely on nothing from it.
- **Not reviewed in depth:** I read only the diff of `/review-task` and the other existing commands, not their full text. I read the agent frontmatter and the prompt bodies were out of scope.

# Shell selection (closes the question SEC-14 left open)

Code from the binary: `Go.acceptable(config.shell)`, otherwise `SHELL` if it names an acceptable shell, otherwise `pwsh`, `powershell`, Git Bash, then `COMSPEC`.

| Fact on this machine | Value |
|---|---|
| `SHELL` at User and Machine level | empty |
| `SHELL` in this Git Bash session | `/bin/bash.exe` |
| `pwsh` | absent |
| `shell` key in project `opencode.json` | absent |
| `shell` key in global config | not read (it sits alongside provider blocks) |
| Past-session log lines naming a shell | none found |

Unless the global config sets a shell, launching OpenCode from a PowerShell or Windows terminal gives PowerShell 5.1, and launching from Git Bash gives bash. Nothing pins this.

# First-round re-test

"Listed forms" means the commands and paths in the first report, sampled by me and covered by the check. Results were identical in both configurations.

| ID | Listed forms | Actually closed? |
|---|---|---|
| SEC-01 | deny | NO: R2-03 reaches main at `allow` (bash); R2-01 reaches main with NOCHECK (PowerShell) |
| SEC-02 | deny | NO: same two routes |
| SEC-03 | `git fetch`, `fetch-pack`, `difftool` deny | PARTIAL: R2-04 |
| SEC-04 | deny | NO: R2-02 |
| SEC-05 | `.git/**` deny | PARTIAL: R2-09 (8.3 alias) |
| SEC-06 | No form stays at `allow`. Staging is `ask` or deny | PARTIAL: R2-01 (no prompt on PowerShell), R2-15 (some forms `ask`, not deny) |
| SEC-07 | deny | PARTIAL: R2-03 (`git switch task/$B`, bash), R2-01 |
| SEC-08 | Deny, except wrappers such as `env git ...` at disclosed `ask` | Closed on bash; R2-01 on PowerShell |
| SEC-09 | `git push -f origin task/x` deny in both | Closed |
| SEC-10 | Listed paths `ask` or deny | Closed as listed; remainder in R2-10 |
| SEC-11 | Listed paths deny | Closed as listed; other key types in R2-07 |
| SEC-12 | Listed forms deny; `docker compose config` `ask` | NO: R2-07 |
| SEC-13 | Listed forms deny | PARTIAL: R2-08 |
| SEC-14 | Disclosed as unconfirmed | Confirmed at parser level, and far broader than disclosed: R2-01 |
| SEC-15 | Real matcher adopted, 4188 expectations | PARTIAL: R2-11 |
| SEC-16 | Delegation deny for all 15 names | Closed; see N-01 |
| SEC-17 | `*_*` gives `ask`; no-underscore names at disclosed `allow` | Closed as disclosed |
| SEC-18 | Filename and assignment checks added | Closed; new masking gap in R2-14 |
| SEC-19 | `.env.example` `ask` for coder and debugger | Closed |

# BLOCKER findings

## R2-01: Ordinary commands skip permission checks entirely under PowerShell

- **SEVERITY:** BLOCKER
- **LOCATION:** `opencode.json` (no `shell` key); OpenCode 1.19.0 `ShellTool.collect` and `ShellTool.ask`.
- **THREAT_SCENARIO:** the PowerShell grammar produces an ERROR root with no `command` node for very common syntax, including any ` -- ` and any `./x`. No pattern means no rule runs, including `deny`. Separately, the grammar cuts the pattern at the first `=`, so later arguments are invisible to the rules. An agent can reach this with no intent to evade, because `git checkout -- .` is everyday syntax.
- **RESOLVED RESULTS** (same in both configurations and for all 14 agents unless stated):

| Command | PowerShell | Bash |
|---|---|---|
| `git checkout -- .` | NOCHECK | deny |
| `git checkout main -- src/A.java` | NOCHECK | deny |
| `git restore -- .` | NOCHECK | deny |
| `git reset --hard -- .` | NOCHECK | deny |
| `git clean -fd -- .` | NOCHECK | deny |
| `git add -- .` | NOCHECK | deny |
| `git add -- *` | NOCHECK | `ask` for orchestrator, deny for 13 |
| `git add -- delivery/status.yaml` (the `/ship-task` form) | NOCHECK | `ask` for orchestrator, deny for 13 |
| `git rm -- x` | NOCHECK | deny |
| `git push origin -- main` | NOCHECK | deny |
| `git push --force origin -- main` | NOCHECK | deny |
| `git commit -m x -- .` | NOCHECK | `ask` for orchestrator, deny for 13 |
| `rm -rf -- x` | NOCHECK | deny |
| `gh pr merge 1 -- x` | NOCHECK | deny |
| `./mvnw deploy` | NOCHECK | deny |
| `./evil.cmd` | NOCHECK | `ask` |
| `! git push origin main` | NOCHECK | deny |
| `[Diagnostics.Process]::Start('git','push origin main')` | NOCHECK | `ask` |
| `git log --format=%H --output=AGENTS.md` | `allow` for all 14, pattern `git log --format` | deny |
| `git grep --max-depth=1 -Oevil x` | `allow` for all 14 | deny |
| `.\mvnw.cmd test -Dtest=Foo deploy` | `allow` for 7 agents, `ask` for the other 7, pattern `.\mvnw.cmd test -Dtest` | deny |
| `npm run test --x=y --script-shell=evil` | `allow` for coder, tester, reviewer, debugger, security-reviewer | deny |

- **IMPACT:** discarded work, sweep staging, secret staging and a direct or forced push to unprotected `main`, with no prompt, for every agent, including read-only ones. The packet's statement that staging prompts a human is false on this shell. AC-01 and AC-03 fail there.
- **NOT_RUN:** whether `git push` accepts `origin -- main` as written, from a live run. `git checkout -- .` is standard usage.
- **REQUIRED_CORRECTION:**
  1. Pin the shell with `"shell": "bash"` in `opencode.json`. The code reads `config.shell`. Precedence against a global `shell` key is unverified, so test it.
  2. Make the check fail if the resolved shell is not bash.
  3. Bash removes the backslash from `.\mvnw.cmd`, so the packet-specified Windows commands and their allow rules must move to `./mvnw` or `./mvnw.cmd`. `./mvnw.cmd test` does not match any current allow rule, so it would prompt.
  4. Have a tester confirm NOCHECK in a disposable repository using harmless targets.
  5. Report the empty-pattern behavior upstream, since it fails open.
  6. Correct the evidence (see R2-12). Its statement that config cannot fix this is only partly right.

  Pinning bash makes R2-03 mandatory to fix.

## R2-02: Grouped redirects write any file at `allow`, in both shells

- **SEVERITY:** BLOCKER
- **LOCATION:** `*>*` in `opencode.json` and all agents. OpenCode's `Pt` includes the redirect only when the command's direct parent is `redirected_statement`. A command inside `( )` or `{ }` has another parent.
- **THREAT_SCENARIO:** the `*>*` rule never sees the redirect, and `edit` is not consulted. This is natural syntax for saving output. An agent told that redirection is denied may reach for it by accident.
- **RESOLVED RESULTS** (same in both configurations):

| Command | Bash | PowerShell |
|---|---|---|
| `(git status) > opencode.json` | `allow` for all 14 | `allow` for all 14 |
| `(git log -1) > .git/hooks/post-commit` | `allow` for all 14 | `allow` for all 14 |
| `(git rev-parse --sq-quote text) > AGENTS.md` (agent-chosen content) | `allow` for all 14 | `allow` for all 14 |
| `{ git log -1 --format=x; } > AGENTS.md` | `allow` for all 14 | `allow` by pattern; a bare script block should not run in real PowerShell |
| `for i in 1; do git log; done > AGENTS.md` | `allow` for all 14 | not a usable form |
| `(.\mvnw.cmd test) > AGENTS.md` | `allow` for 7 agents | `allow` for 7 agents |
| `(gh pr view 1) > opencode.json` | `allow` for orchestrator, tester, reviewer, security-reviewer | same |
| `(node scripts/delivery/validate.mjs) > AGENTS.md` | `allow` for orchestrator, tester, reviewer, documentation | same |

- **IMPACT:** a read-only agent can overwrite its own permissions, governance files and validators, or plant a git hook that the orchestrator's next human-approved commit runs. SEC-04 is not closed. AC-03 fails. Hook execution was not tested.
- **REQUIRED_CORRECTION:** patterns cannot fix this, because the redirect is not in the pattern. Three things are required:
  1. Correct the evidence's claim that `*>*` denies redirection.
  2. Add a `/ship-task` gate that stops on any non-sample file in `.git/hooks`, and on any change to `opencode.json`, `.opencode/**`, `AGENTS.md` or `scripts/delivery/**` that is not in `allowedFiles`. Part of this already exists through the `allowedFiles` comparison, but hooks are not covered.
  3. Get an explicit owner risk acceptance or an OS-level control (human decision 3), and report it upstream.

## R2-03: Shell expansion carries a second refspec past the single-branch rule (bash)

- **SEVERITY:** BLOCKER. Only deliberate or prompt-injected use is likely to reach this, not an accident. I rate it BLOCKER because it reaches unprotected `main` at `allow`, it directly falsifies AC-04, and the fix is small.
- **LOCATION:** `orchestrator.md`, `git push origin task/*`, `git push -u origin task/*`, `git switch task/*`. The trailing denies look for a literal space, tab, `:`, `+` or `@`.
- **RESOLVED RESULTS** (same in both configurations):

| Command | Bash | PowerShell |
|---|---|---|
| `B="x task/x:main"; git push origin task/$B` | `allow` for orchestrator, deny for 13 | `ask` for orchestrator |
| `git push origin task/x${IFS}main` | `allow` for orchestrator, deny for 13 | `allow`; pattern truncated, no word splitting expected |
| `B="x -f"; git switch task/$B` | `allow` for orchestrator, deny for 13 | `ask` for orchestrator |
| `$B='x'; git push origin task/$B` | `ask` | `allow`; a PowerShell variable stays one argument, so harmless |
| `git push origin task/x\<newline>main` | `allow` | `ask`; bash joins the words into one ref, so harmless |

  The assignment statement has no `command` node in bash, so only the second statement is checked. Word splitting then gives `git push origin task/x task/x:main`. Unquoted word splitting is standard bash behavior; the push itself is NOT_RUN.
- **IMPACT:** a write or force-push to remote `main` with no human and the owner's credentials.
- **REQUIRED_CORRECTION:** add, after the allow rules and with the existing trailing denies, for both the plain and `-u` push forms and for switch: `git push *$*`, ``git push *`*``, `git push *{*`, `git switch *$*`, ``git switch *`*``, `git switch *{*`. Add the forms above to the check, and add a parser step so the assignment case is really tested (see R2-11).

## R2-04: Abbreviated and clustered `git grep` options run a named program at `allow`

- **SEVERITY:** BLOCKER. Same class as SEC-03, with deliberate use only. Direct, no chain needed, and available to read-only agents.
- **LOCATION:** `git grep *` allow; denies `git grep -O*`, `git grep * -O*` and `git grep *--open-files-in-pager*`.
- **RESOLVED RESULTS** (`allow` for all 14, both configurations, both shells):
  - `git grep --open=evil x`
  - `git grep --open-files=evil x`
  - `git grep -nOevil x`
- **Confirmed clean:** `git grep -O evil x` is deny.
- **Source:** local `git-grep` docs describe `-O` and `--open-files-in-pager[=<pager>]` as opening matching files in the pager. Local `gitcli` docs describe unique-prefix abbreviation. No form was executed.
- **IMPACT:** any agent runs a program of its choice with no human, which defeats every other rule. It is also a route to the outcomes denied elsewhere.
- **REQUIRED_CORRECTION:** deny `git grep *--op*` and clustered `-O` forms. A pattern such as `git grep * -*O*` can hit legitimate search terms that contain `-...O`, so choose and test the form. Alternatively, narrow `git grep` to specific options. Add the forms to the check.

# MAJOR findings

## R2-05: An environment assignment before an allowed command runs a program at `allow`

- **SEVERITY:** MAJOR (deliberate use; config cannot fix it)
- **LOCATION:** all allow rules. Assignment and `export` are not `command` nodes in bash; `$env:X=` is not one in PowerShell.
- **RESOLVED RESULTS** (`allow` for all 14, both configurations):
  - Bash: `export GIT_EXTERNAL_DIFF=evil; git diff`
  - Bash: `GIT_EXTERNAL_DIFF=evil; git diff`
  - PowerShell: `$env:GIT_EXTERNAL_DIFF='evil'; git diff`
- **Correctly caught:** the inline form `GIT_EXTERNAL_DIFF=evil git diff` is `ask`.
- **Same class:** `GIT_SSH_COMMAND` with `git fetch origin` or the allowed push; `GIT_CONFIG_COUNT` config injection; `MAVEN_OPTS`, `NODE_OPTIONS` and `npm_config_script_shell` with allowed test commands.
- **IMPACT:** arbitrary program execution for agents with no test command, including planner, documentation and the built-ins. The evidence's "agents that run tests run code" limitation does not cover this.
- **REQUIRED_CORRECTION:** disclose it in the evidence as a separate limitation. Treat it under human decision 3. NOT_RUN: live execution.

## R2-06: One "Always" answer overrides `deny`, for every agent

- **SEVERITY:** MAJOR. `/ship-task` produces several prompts, so a human reaching for this is likely.
- **LOCATION:** OpenCode `Permission.ask` and `reply`. `evaluate(permission, pattern, ruleset, approved)` searches rules then approvals with `findLast`, so approvals win. The approved list is held per instance, not per agent. Read from code; NOT_RUN live.
- **THREAT_SCENARIO AND RESULT:**
  - Choosing "Always" on `git add -- x` stores `git add *`. `git add .` and `git add -A` then resolve `allow`, for all agents.
  - On `git commit -m x` it stores `git commit *`, which allows `--amend` and `-a`.
  - On `git switch -c task/x` it stores `git switch *`. On `git checkout -b task/x` it stores `git checkout *`.
  - On any edit prompt the edit tool stores `*`. Edit then resolves `allow` everywhere, including `AGENTS.md`, `opencode.json` and `.git/**`, including for agents with `edit: deny`.
- **IMPACT:** a routine click silently removes the deny layer, and the evidence does not say so.
- **REQUIRED_CORRECTION:** state in `/ship-task`, `delivery/README.md` and the evidence: choose "once", never "always". Record it as a human operating rule (human decision 4). Report upstream.

## R2-07: Secrets remain readable at `allow`

- **SEVERITY:** MAJOR
- **LOCATION:** `git diff *`, `git grep *`, `git blame *`, `grep: allow`, and the read and edit rules.
- **RESOLVED RESULTS** (`allow` for all 14, both configurations and both shells, unless stated):
  - Outside the repository, with no `--no-index` written:
    - `git diff ~/.ssh/id_rsa /dev/null`
    - `git diff ~/.ssh/id_rsa NUL`
    - `git diff ~/.config/gh/hosts.yml /dev/null`

    Local `git-diff` docs confirm that `--no-index` may be omitted when at least one path is outside the working tree. `external_directory` covers only a fixed list of file commands, and `git` is not on it.
  - Abbreviations:
    - `git grep --no-ind --no-exclude KEY`
    - `git grep --untr KEY`
    - `git blame --cont x y`
  - Tracked secrets not caught by `git *.env*`, `*.pem*`, `*.key`:
    - `git show HEAD:secrets/x`
    - `git log -p -- secrets/x` (NOCHECK on PowerShell)
  - Grep tool: permission is checked on the search pattern only, and `include` goes straight to `rg --hidden --glob=<include>`. I ran the same arguments on a fake `.env` that was git-ignored in the scratchpad: 0 matches without the glob and 1 with `--glob=.env`. So the tool can return `.env` content at `allow`, despite the read denies. A live tool call is NOT_RUN.
  - Key types with no read rule, `allow` for all 14: `x.keystore`, `x.ppk`, `x.pkcs12`, `.netrc`, `.git-credentials`, `credentials.json`, `id_ecdsa`, `id_dsa`, `.aws/credentials`. The same paths are `allow` for coder and debugger to edit at the root, and `infra/x.keystore` is `ask`.
- **Current exposure:** the first report found no real secret-pattern files in the repository, and I did not recheck on-disk `.env` files. `~/.ssh` and the gh config were not inspected. Their contents would go to the model provider.
- **IMPACT:** owner credentials such as the gh token and SSH keys can reach the transcript with no prompt. AGENTS.md §4 is not enforced.
- **REQUIRED_CORRECTION:**
  - Deny `git diff *:/*`, `git diff /*`, `git diff ~*`, and `git * C:*`-style absolute-path forms. Test them against false positives such as `HEAD:path`. A pattern such as `git diff *:/*` also catches pathspec magic and refs, so choose exact forms carefully.
  - Deny `git grep *--no-i*`, `git grep *--untr*`, `git grep *--no-excl*`, `git blame *--con*`.
  - Add the missing key types to read and edit, including root and nested forms.
  - For the grep tool, config can only move it to `ask`. Otherwise disclose it in the evidence.

## R2-08: Allowed Maven and npm forms still run other programs or overwrite files

- **SEVERITY:** MAJOR
- **LOCATION:** `./mvnw test *`, `.\mvnw.cmd test *` and the `verify` and `-pl` forms; `npm run test*`, `npm run build*`, `npm run contracts:*`.
- **RESOLVED RESULTS** (Maven: `allow` for coder, tester, reviewer, debugger, contract-reviewer, data-reviewer, security-reviewer, both configurations, on bash; `./mvnw` is NOCHECK on PowerShell, see R2-01):
  - Runs another program: `./mvnw test -Djvm=evil`; `.\mvnw.cmd test -Djvm=evil`.
  - Loads other code: `./mvnw test -Dmaven.ext.class.path=evil.jar`, `./mvnw test -DargLine=x`, `./mvnw test -Pevil`.
  - Overwrites a protected file with build output: `./mvnw test -l AGENTS.md`, `./mvnw test --log-file=AGENTS.md`.
  - Other build or settings file: `./mvnw test -fevil/pom.xml`, `./mvnw test -sevil.xml`, `./mvnw test -gs evil.xml`, `./mvnw test --global-settings=evil.xml`.
  - Other phase: `./mvnw test site-deploy`.
  - False green: `./mvnw -pl x test -Dmaven.test.skip=true`. The relevant deny `*-DskipTests*` is not present, so this and `-DskipTests` need owner policy.
  - `-DforkMode=never` and `-Dmaven.repo.local=x` also resolve `allow`.
- **npm:**
  - `npm run test --prefix=C:/evil`: `allow` for coder, tester, reviewer, debugger, security-reviewer.
  - `npm run build --prefix ../evil`: `allow` for coder, tester, debugger, contract-reviewer.
  - `npm run contracts:verify --script-she=evil`: `allow` for orchestrator, coder, tester, reviewer, debugger, contract-reviewer, security-reviewer. Abbreviation behavior is NOT_RUN.
  - `npm run test --node-options=--require=evil` and `npm run test --userconfig=evil`: same five as `--prefix=C:/evil`.
- **IMPACT:** read-only reviewers can run a program and overwrite protected files. The disclosed "tests run code" limitation assumes code someone wrote. `-Djvm=` and `--log-file` need no written code.
- **REQUIRED_CORRECTION:** deny `*-Djvm*`, `*-DargLine*`, `*-Dmaven.ext*`, `* -l *`, `*--log-file*`, `* -f?*`, `* -s?*`, `* -gs*`, `*--global-settings*`, `*site*`, and owner-chosen `*-Dmaven.test.skip*` and `*-DskipTests*` for Maven. Check they do not catch `-Dtest=` or `-Dmaven.compiler.release=21`. Deny `npm *--prefix*`, `npm *--script-s*`, `npm *--node-options*`, `npm *--userconfig*`, `npm *--globalconfig*`.

## R2-09: Windows path aliases bypass edit denies

- **SEVERITY:** MAJOR (deliberate use)
- **LOCATION:** coder and debugger `edit: "*": allow`. The edit and write tools build the pattern from the path as given, with `path.relative` and no normalization.
- **RESOLVED RESULTS** (coder and debugger `allow`, built-ins `ask`, others deny, both configurations):
  - `GIT~1/hooks/pre-commit`
  - `GIT~1/config`
  - `OPENCO~1.JSO`
  - `OPENCO~1/agents/coder.md`
  - `AGENTS.md::$DATA`
  - `opencode.json::$DATA`
- **Confirmed on this volume:** `dir /x` shows the `GIT~1`, `OPENCO~1` and `OPENCO~1.JSO` short names. Node `fs.stat` resolves `GIT~1/config`, `OPENCO~1/agents/coder.md`, `OPENCO~1.JSO` and `AGENTS.md::$DATA` to the real files.
- **NOT_RUN:** a write through OpenCode's Bun runtime.
- **IMPACT:** the coder can rewrite permissions, governance and hooks with no prompt, reopening SEC-05 and SEC-10.
- **REQUIRED_CORRECTION:** add `*~*` and `*::*` edit denies last for the root and every agent with an edit allow. Check a legitimate filename does not contain `~`. Add the forms to the check.

## R2-10: Broad allows override the control-file denies

- **SEVERITY:** MAJOR for the tester and orchestrator combination, because the first-round request was that a broad allow cannot override a deny.
- **LOCATION:** `tester.md`, `orchestrator.md`, `documentation.md`. Secret and `.git/**` denies are restated last in these agents; control-file denies are not.
- **RESOLVED RESULTS** (both configurations):
  - Tester `allow`:
    - `services/x/src/test/resources/AGENTS.md`
    - `services/x/src/test/.opencode/agents/x.md`
    - `tests/opencode.json`
  - Orchestrator and documentation `allow`:
    - `delivery/handoffs/AGENTS.md`
    - `delivery/handoffs/opencode.json`
    - `delivery/handoffs/.opencode/agents/x.md`
- **Other coder and debugger gaps at `allow`:**
  - `Makefile`
  - `.husky/pre-commit`
  - `.claude/settings.json` (this repository is also worked with Claude Code; whether a hook there would run without a trust prompt is not verified)
  - `CONTEXT.md`
  - `.gitmodules`
  - `.nvmrc`
  - `settings.xml`
  - `services/x/.mvn/jvm.config`
  - `services/x/mvnw`
  - `services/x/mvnw.cmd`
  - `apps/web/.yarnrc.yml`
- **IMPACT:** nested instruction or config files may load in some contexts; I did not verify nested loading. `Makefile` becomes live if the owner picks make.
- **REQUIRED_CORRECTION:** restate the `**/AGENTS.md`, `**/CLAUDE.md`, `**/opencode.json*`, `**/.opencode/**` denies last in tester, orchestrator and documentation. Add `ask` for the paths above in coder and debugger, and `**/.mvn/**`, `**/mvnw*`. Add them to `PROTECTED_EDIT_DENIED` and the writer list.

## R2-11: The check does not test what OpenCode evaluates

- **SEVERITY:** MAJOR
- **LOCATION:** `scripts/delivery/check-agent-permissions.mjs`.
- **What holds:**
  - The matcher agrees with binary function `ql` by inspection and on every sampled string.
  - Its `/` escaping is harmless.
  - Baseline failures in my copy were 0 of 4188 in both configurations.
  - It fails closed on missing agents and on `opencode` errors. Retrying 3 times with stderr hidden can mask a flake, but a persistent error still fails.
- **What does not:**
  - It feeds whole strings to the matcher, with no parser and no shell assertion. So `git add -- .` passes as deny while PowerShell gives NOCHECK. Every form in R2-01 to R2-05 and R2-07 to R2-09 passes the check today.
  - Mutation of a scratch copy, removing one deny pattern at a time across all agents:
    - 116 of 228 distinct bash denies can go undetected, in both configurations. Examples: `git *--ext-diff*`, `git *--contents*`, `git grep -O*`, `git grep *--open-files-in-pager*`, `git clone*`, `find *-exec*`, `python3 *`, `./mvnw *--settings*`, `.\mvnw.cmd *--settings*`, `trivy *--output*`, `semgrep *-o *`, `gitleaks *-r *`.
    - Edit: 2 of 33 undetected (`**/id_rsa*`, `id_ed25519*`).
    - Read: 6 of 18 in the empty configuration, 8 of 20 in global.
    - Some are covered by an overlapping rule, so this does not mean 116 protections are absent, only that the check would not notice their loss.
  - Delegation expectations are present, but `LEGACY_AGENTS` names are not asserted disabled (N-01).
  - Global `git status*` and `git diff*` differences are not sampled (R2-16).
- **IMPACT:** the green result reads as proof of AC-01 to AC-04.
- **REQUIRED_CORRECTION:**
  - Assert the resolved shell.
  - Add a parser step, or an explicit list of parser-derived forms with their extracted patterns.
  - Add a negative expectation for each deny that has none, and all forms in this report.
  - Have the evidence state plainly that the check evaluates strings, not extracted patterns, until this is done.

## R2-12: The evidence overstates several controls

- **SEVERITY:** MAJOR
- **LOCATION:** `delivery/evidence/I1-DEL-001/agent-permissions.md`; `05-coder-fixround.md`.
- **Overstated or wrong:**
  - "`*>*` denies redirection, which otherwise bypasses `edit` permissions entirely": false for grouped forms (R2-02).
  - "The orchestrator pushes only one task branch; anything following the branch name is denied": false under expansion (R2-03).
  - "`git add` and `git commit` prompt a human": false on PowerShell for `git add -- <paths>` (R2-01).
  - "Sweep, force, amend and hook-skipping forms are denied": incomplete (R2-01, R2-15).
  - "Possible full bypass ... for example a PowerShell .NET call ... unconfirmed and config cannot fix it": materially understated. Parser results show everyday commands such as `git checkout -- .`, and a `shell` key can remove the PowerShell path.
  - `05-coder-fixround.md`, "AC-01 to AC-04 PASS by the extended check": the check does not support that claim. The statement that it is "self-reported only" helps, but it should be changed.
- **Not disclosed:** R2-05, R2-06, shell selection, and the grep tool's `.env` access. The first report raised the grep tool as NOT_RUN, and it has no line in Limitations.
- **Accurate:**
  - 4188/4188 in both configurations, reproduced.
  - Validator PASS, reproduced.
  - Self-test 8 passed, reproduced.
  - NOT_RUN rows.
  - "not a git parser or a sandbox".
  - The matcher description.
  - The no-underscore tool limit.
  - Not verified by me: "main was unprotected" (I did not query GitHub), and the fingerprint commands' hash results (I did not run them, since they are permission subjects).
- **REQUIRED_CORRECTION:** rewrite these lines and the limitations from this report's results. Keep the historical table.

# MINOR findings

## R2-13: Scanner and make rules allow writes and code execution, latent today

- **SEVERITY:** MINOR, because `make`, `semgrep`, `trivy`, `gitleaks` and `flyway` are absent on this machine and no Makefile exists. Rate it MAJOR once any is installed.
- **LOCATION:** `security-reviewer.md`, and the make rules across agents.
- **RESOLVED RESULTS** (both configurations):
  - Security-reviewer `allow`:
    - `semgrep --config=https://evil.example x`
    - `semgrep --config r --autofix` (changes files)
    - `semgrep ci` (uploads)
    - `trivy fs -o AGENTS.md .` (`--output=` and `semgrep -o` are denied, `-o` here is not)
    - `gitleaks detect --no-git --source=.env -v`
  - `allow` for coder, tester, reviewer, debugger, data-reviewer, security-reviewer:
    - `make verify -f evil.mk`
    - `make verify --eval=x`
  - `allow` for coder, tester, reviewer, debugger:
    - `make test SHELL=evil`
- **REQUIRED_CORRECTION:** restrict these to exact forms, or set them to `ask` until the owner resolves the make decision and installs the scanners. Do not use `semgrep*` with no space.

## R2-14: `/ship-task` secret scan can print the secret

- **SEVERITY:** MINOR
- **LOCATION:** `.opencode/commands/ship-task.md` pre-commit step 6; orchestrator allow for `npm exec --no -- secretlint --secretlintrc scripts/contracts/secretlintrc.json *`.
- **RESULT:** the command omits `--maskSecrets`, which the local CLI help describes as an opt-in option. On a real hit, the value may enter the transcript and go to the provider. I did not trigger this with a fake token in this round.
  - The orchestrator allow also accepts `.env` on bash and a second `--secretlintrc=evil.json`. Whether that loads other rule code is not verified.
  - On PowerShell these forms are NOCHECK or `ask`.
- **REQUIRED_CORRECTION:** add `--maskSecrets` to the command and to the allow rule. Deny `npm exec --no -- secretlint *.env*` and a second `--secretlintrc`.

## R2-15: Some commit forms that evidence calls denied resolve to `ask`

- **SEVERITY:** MINOR (a human prompt applies)
- **RESOLVED RESULTS** (orchestrator `ask`, others deny, both shells unless noted):
  - `git commit -m x -qa`
  - `git commit -m x -sa`
  - `git commit -m x --no-ver`
  - `git commit -m x -qn`
  - `git commit -m x .`
  - `git add -- ".",` `git add -- ":/"`, `git add -- ..`, `git add -- delivery` (the last two are NOCHECK on PowerShell)
- **Correctly denied:** `git commit --amen -m x`.
- **REQUIRED_CORRECTION:** extend the denies where practical, or change the evidence wording.

## R2-16: Global-config-only allows for orchestrator and debugger

- **SEVERITY:** MINOR
- **LOCATION:** global agent keys `git status*`, `git diff*`, `git log*`, `git show*`, `git rev-parse*`, `git ls-files*`, which merge in without the space.
- **RESOLVED RESULT:** `git show-ref`, `git show-branch`, `git diff-tree HEAD`, `git diff-index HEAD`, `git log-x`, `git statusx`: orchestrator and debugger `allow` under global, `ask` under empty. These are read-only today. AC-02's "holds with and without" is not literally true for them, and the check does not sample them. Other agents are the same in both configurations for all resolved results tested, and the only other global-only rules are `read *.env` and `read *.env.*` deny, which are stricter. The other sequence differences are skill-directory row order, which is not config-related.
- **REQUIRED_CORRECTION:** add `git show?*`, `git diff-*`, `git log?*`, `git status?*`, `git rev-parse?*` and `git ls-files?*` rules at `ask` to the two project agents, checking they do not disturb `git show *` and the others, or disclose the difference.

## R2-17: `/ship-task` wording conflicts with the deny rules

- **SEVERITY:** MINOR
- **RESULT:**
  - Step 5 allows `.env.example` as an exception, but `git add -- infra/local/.env.example` is deny for every agent on bash. A task changing that file cannot ship without a human.
  - `gh pr create` with a body containing `>` is deny. `--body-file` works (`ask`), but the command does not mention it, and the file location needs to be inside `allowedFiles`.
- **REQUIRED_CORRECTION:** state both in `/ship-task`. Record the first as a known stop.

# NOTES

## N-01: Three legacy names are not disabled

`coder-fast`, `planner-advanced` and `reviewer-fast` have `task` denies but no `disable` entry. No such files exist today. If added, they would be selectable by a human and undelegable from the orchestrator, and the check flags them when run. A new agent with its own permission rules gets those rules last. Add the three to `agent` for consistency. An attempted fix is simple and low risk.

## N-02: Legitimate read commands now denied

`git branch -r`, `-a`, `--contains`, `git worktree list`, `git stash list`, `git tag --list` and `git config --get` are deny. `git cat-file -e` and `git ls-remote origin` are `ask`. The command files I read do not name any of them. `git merge-base --is-ancestor`, which `/close-task` containment checks can use, is `allow`.

# Tested and clean

- **Lost capability, `/ship-task` walk-through on bash, both configurations:**
  - `allow`: `git status --short`, `git diff --stat`, `git diff --cached --name-only`, the `-G` secret search, `node scripts/delivery/validate.mjs`, `git push -u origin task/...`, the secretlint form, `gh pr view`.
  - `ask`: `git add -- <paths>`, `git commit -m "..."`, `gh pr create --body-file`.
- **`/review-task` fingerprint commands:** `allow` for the orchestrator on bash (other agents `ask`). On PowerShell they are `ask`.
- **`/verify-task`:** `git diff --name-only <a> <b>` is `allow`.
- **Exact allows found safe by rule evaluation:**
  - `git hash-object --stdin` and `--stdin-paths` are exact; `-w` resolves `ask`.
  - `git fetch` and `git fetch origin` are exact.
  - `git remote get-url origin`, `gh pr view --web`, `gh pr checks --watch`, `gh run view --log` and `gh pr list --state all` are read-only as far as I know. Logs could carry unmasked values.
  - `docker compose logs -f` is `allow` for those with the rule; `docker compose config` is `ask`.
  - `npm audit` is `allow` for security-reviewer only; `npm audit fix` is deny there.
- **`git diff --ext`, `git log --ext`, `git diff --textconv`:** resolve `allow` but need a configured driver, and `git config` and `.git/**` edit are denied, so reach depends on R2-02 or R2-09. Not listed separately.
- **`git diff --outp=AGENTS.md`:** resolves `allow`, but `--output` has sibling options sharing that prefix, so git should reject it as ambiguous. NOT_RUN.
- **`git rev-parse --parseopt`:** reads stdin; no execution path found.
- **`node scripts/contracts/*`:** `..` and `\..\` forms are deny. Allowed only for contract-reviewer.
- **Chaining and wrappers on bash:** `git status && git log`, `$(git push origin main)`, `{ git push origin main; }`, `if git push origin main; then :; fi` and a trailing newline-plus-`)` all extract the push and deny. `time`, `coproc`, `exec` and `command` prefixes are `ask`.
- **Edit paths:** `.GIT/CONFIG`, `agents.md`, and `.git/hooks::$INDEX_ALLOCATION/pre-commit` are deny. Trailing dot and space forms (`AGENTS.md.`, `.git./config`, `.opencode./agents`) returned ENOENT under Node, so they are not aliases there. `.git/`, `secrets/` and `.env` under `delivery/`, `tests/` and `e2e` paths are deny.
- **Read paths:** `.env.local`, `.ENV`, `.git/CONFIG` are deny; `infra/local/.env.example` is `allow`. The read tool normalizes paths first, so I do not claim 8.3 read bypasses.
- **Delegation:**
  - Orchestrator `allow` for exactly the 9 project subagents.
  - `general`, `explore`, `build`, `plan`, `brand-new-agent` and all legacy names are deny.
  - All 9 subagents have `task: deny`.
  - Built-ins are `ask`.
  - `opencode agent list` shows the same 17 agents in both configurations, with disabled agents absent. I did not trace the task tool's own filtering.
- **Other permissions:** `external_directory` is deny, including the skill-directory rows that resolve earlier. `*_*` is `ask` for MCP-style names and `plan_exit`. `webfetch` is deny for orchestrator and `ask` otherwise. Inert-rule scan: identical-pattern overrides I found were intended (agent allow over root deny on delivery files), and I reviewed the first 30 per sampled agent.

# Documents and sections read

AGENTS.md §4, §6, §12, §15 (delivered in context); `delivery/tasks/I1-DEL-001.yaml`; `delivery/handoffs/I1-DEL-001/04-security-reviewer.md`, `05-coder-fixround.md`; `delivery/evidence/I1-DEL-001/agent-permissions.md`; `opencode.json`; frontmatter of `.opencode/agents/*.md`; `.opencode/commands/ship-task.md`; the diff of the other command files; `scripts/delivery/check-agent-permissions.mjs`; `package.json` scripts; relevant `.gitignore` lines; local git docs for `git-diff`, `git-grep` and `gitcli`.

# Files changed

None in the repository. The worktree stays at 27 status entries with 0 staged. Helpers are in `<session scratchpad>/r2`.

# Commands executed

| Command | Result |
|---|---|
| `opencode debug agent <name>` (17 agents, 2 configurations, permission array only), `opencode agent list` | Done |
| `node scripts/delivery/check-agent-permissions.mjs` | PASS 4188/4188, 17 agents, in both configurations |
| `node scripts/delivery/validate.mjs` | PASS |
| `node scripts/delivery/self-test.mjs` | PASS: 8 passed, 0 failed |
| Parser and matcher harness over attack strings | Done, results above |
| Mutation of a scratch copy of the check | 116 of 228 bash denies undetected, as described in R2-11 |
| `rg` with OpenCode's grep arguments, on a fake marker in the scratchpad | 0 without glob, 1 with glob |
| `fs.stat` and `dir /x` for Windows aliases | Aliases exist; trailing-dot forms ENOENT |
| `opencode --version`; `git status`; `git diff --stat`; `git --version` | Done |

My first mutation script was too slow, so I stopped it and replaced it. It changed nothing in the repository.

# Test results

- PASS: delivered check (string level), validator, self-test.
- FAIL: AC-01, AC-03 and the AC-04 "only task branches" clause, as tested by this review.
- NOT_RUN: live OpenCode session; every listed attack command; `/ship-task`, `/review-task` and `/verify-task` end to end; `"shell": "bash"` precedence; a write through Bun to an alias path; querying GitHub branch protection.
- BLOCKED: none.

# Acceptance criteria status

| AC | Status |
|---|---|
| AC-01 | FAIL: R2-01, R2-03, R2-04 |
| AC-02 | PASS for selectable agents; minor resolved differences in R2-16 |
| AC-03 | FAIL: R2-02, R2-09 |
| AC-04 | Test and CI allows PASS by evaluation; "only task branches" FAIL (R2-03) |
| AC-05 | Text reviewed for security only; R2-14, R2-17; end to end NOT_RUN |
| AC-06, AC-07 | Not in this review's scope |

# Decisions made

Severities only. No normative decision.

# Assumptions

The installed binary is CLI 1.19.0, and my reimplementation of its pattern extraction matches the running code. The tree-sitter results come from its own grammars but were not observed in a live tool call.

# Findings and residual risks

See above. Even after every correction, the parser-level gaps in R2-02 and R2-05 and the code-execution residual of test runs remain. Pattern rules are not a sandbox, so branch protection and any OS-level control stay essential.

# Blockers

R2-01, R2-02, R2-03, R2-04.

# Required human decisions

1. **Branch protection on `main`** before first live OpenCode use: required pull requests, no force-push, no deletion, enforced for admins. This is still outside the packet and the real control.
2. **Pin the shell:** approve `"shell": "bash"` in `opencode.json`, with the Maven command change that follows. Alternatively, ban PowerShell launches by another means. This is the single most effective fix.
3. **Risk acceptance for what config cannot fix:** grouped redirects (R2-02), environment assignment (R2-05), and the empty-pattern fail-open. The options are to accept the risk with correct disclosure, add an OS-level sandbox or a read-only worktree for reviewers, or defer live use until upstream changes. Only a human may accept this risk.
4. **Prompt policy:** adopt "allow once, never always" as an operating rule (R2-06), and decide whether `opencode --auto` is banned. Both remain open.
5. **Validating wrapper for add, commit and push:** still the strongest option for the write path, and it would also remove R2-03's exposure. It needs a packet amendment.

# Recommended next agent

RECOMMENDED_STATE: FIX_REQUIRED. Return to coder for R2-01, R2-03, R2-04 and R2-07 to R2-17 within `allowedFiles`. Have a tester confirm NOCHECK and the grouped-redirect behavior in a disposable repository with harmless targets. Then run a fresh independent security review before `/ship-task`.
