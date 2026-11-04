---
role: security-reviewer
taskId: I1-DEL-001
resultingState: FIX_REQUIRED (recommended)
baselineCommit: 811b81ed39ca954217cf785f5f9c3135f14b2263
impactLevel: L3
date: 2026-11-04
---

Independent subagent output, saved unedited except for this header, the replacement of any local temp path with `<session scratchpad>`, and masking one fake test value so the secret scan passes. It reviews the first candidate, before the fix round.

Task ID: I1-DEL-001
Role: Independent security reviewer (read-only)
Task state: FIX_REQUIRED (recommended; the orchestrator controls state)
Baseline commit: 811b81ed39ca954217cf785f5f9c3135f14b2263 (candidate is the uncommitted worktree on task/i1-del-001-opencode-controls)
Impact level: L3

# Result

SECURITY_REVIEW_STATUS: FAIL_FINDINGS. There are 7 BLOCKER, 8 MAJOR, 4 MINOR and 1 NOTE.

The orchestrator can still reach remote `main` with no human prompt, because `git push origin task/*` lets extra arguments through. Every agent, including the read-only ones, can overwrite protected files and run arbitrary commands through allow rules. The regression script passes 811/811 while all of this is open, because it tests only a fixed list of literal command forms.

Pattern rules cannot be the main barrier here. The owner should enable branch protection on `main` before first live use, whatever else is fixed.

# Method and limits

- **Rules tested:** the `permission` array from `opencode debug agent <name>` for 14 agents, in three setups:
  - the owner's global config;
  - an empty `XDG_CONFIG_HOME`;
  - the baseline `opencode.json` and `.opencode`, extracted with `git archive` to the scratchpad.
- **Matcher:** I reproduced it from the installed OpenCode 1.19.0 binary, not from the docs. It normalizes `\` to `/`, uses regex flags `si` on Windows, gives ` *` an optional tail, and the last matching rule wins (`findLast`), with `ask` if nothing matches.
- **Nothing tested was executed.** No tested git, gh or shell command was run, even in a sandbox. Statements about what git does come from `git <cmd> -h` usage text and known git behavior, so live behavior is NOT_RUN.
- **Code-derived claims:** statements about OpenCode internals come from reading code in the binary, not from a live session.
- **Result notation:** `allow` means no human is involved. `ask` needs a human, but `--auto` passes it. "Global" means the owner's global config and "empty" means the empty config directory.
- **Files:** the worktree stayed at 27 status entries with nothing staged. Helper scripts and full matrices are in `<session scratchpad>`.

# BLOCKER findings

## SEC-01: Orchestrator can push to remote main with no prompt
- **SEVERITY:** BLOCKER
- **LOCATION:** `.opencode/agents/orchestrator.md`, rules `git push origin task/*` and `git push -u origin task/*`. The denies `git push * main` and `git push * main *` only match a bare `main` token.
- **THREAT_SCENARIO:** the trailing `*` accepts more refspecs, and the denies miss any other spelling of main.
- **RESOLVED RESULTS (orchestrator: `allow`, both configs):**
  - `git push origin task/x refs/heads/main`
  - `git push -u origin task/x refs/heads/main`
  - `git push origin task/x "main"`
  - `git push origin task/x 'main'`
  - `git push origin task/x ma\in`
  - `git push origin task/x<TAB>main`
  - `git push origin task/x @` (pushes the current branch)
- **Full no-human chain:**
  1. A subagent runs `git fetch . task/x:main`, which is `allow` for coder, planner, reviewer and others. This fast-forwards local `main`.
  2. The orchestrator runs `git push origin task/x refs/heads/main`, which is `allow`.
- **IMPACT:** merge-equivalent write to unprotected `main`, violating DEC-AGENT-01 and AC-04 ("can push only task branches"). The push form was already `allow` at baseline. The fetch step is new (see SEC-03).
- **REQUIRED_CORRECTION:** after the allow rules, deny anything following the branch: `git push origin task/* ?*`, `git push -u origin task/* ?*` and `git push *<TAB>*`.
  - I ran these against the real matcher. All the attack forms above that I sampled are denied, and `git push origin task/x`, `git push -u origin task/x` and `git push origin task/main-fix` stay unmatched.
  - Do not write this as `git push origin task/* *`. The optional-tail behavior makes it match the bare form too (tested: true).

## SEC-02: Force-push and remote delete at `allow` through option clusters and abbreviations
- **SEVERITY:** BLOCKER
- **LOCATION:** `orchestrator.md` denies `git push * -f`, `git push * -f *`, `git push *--delete*` and `git push * -d *`.
- **THREAT_SCENARIO:** git accepts bundled short options and unique long-option prefixes. `git push -h` lists `-u`, `-f`, `-d`, `--delete`, `--dry-run`.
- **RESOLVED RESULTS (orchestrator: `allow`):**
  - `git push origin task/x -uf`
  - `git push origin task/x -fu`
  - `git push origin task/x -vf`
  - `git push origin task/x refs/heads/main -uf` (force-pushes main)
  - `git push origin task/x -ud`
  - `git push origin task/x --de`
  - `git push origin task/x --dele`
  - `git push origin task/x refs/heads/main --de`
  - `git push origin task/x --repo=../evil.git` (a different target)
  - `git push origin task/x --repo=../evil.git --receive-pack=evil` (runs a command for a local-path target)
- **Not counted as exploits:**
  - `--forc` resolves `allow`, but it is an ambiguous prefix that git should reject.
  - `--mir` and `--al` resolve `allow`, but git should reject those modes combined with refspecs.
- **IMPACT:** force-push and branch deletion, both human-only under DEC-AGENT-01, with no prompt. AC-01 fails. GitHub may refuse deleting the default branch, but task branches and history are exposed.
- **REQUIRED_CORRECTION:** the SEC-01 trailing deny closes all of these. Add every form to the regression list.

## SEC-03: Root `git fetch*` and `git diff*` give arbitrary command execution and local ref rewrites at `allow`
- **SEVERITY:** BLOCKER
- **LOCATION:** `opencode.json` root bash `git fetch*: allow` and `git diff*: allow`. Removing the agent-level `"*": ask` made the root rules effective for subagents.
- **RESOLVED RESULTS:**

| Command | Global | Empty | Baseline |
|---|---|---|---|
| `git fetch --upload-pack=evil .` | `allow` for all except orchestrator and debugger (`ask`) | `allow` for all | `ask` for those 12 agents |
| `git fetch-pack --upload-pack=evil .` | same | same | not sampled |
| `git fetch . task/x:main` | same | same | `ask` for those 12 agents |
| `git fetch . +task/x:refs/heads/main` | same | same | `ask` for those 12 agents |
| `git difftool -x evil`, `git difftool --extcmd=evil -y` | `allow` for all | `allow` for all | planner, reviewer, coder `ask`; orchestrator `allow` (other agents not sampled) |

- **`*` without a space also matches other git commands:** `git diff*` matches `difftool`, and `git fetch*` matches `fetch-pack`.
- **Not counted:** `git fetch "ext::sh -c evil"` resolves `allow`, but git's default `ext` protocol policy should block it.
- **IMPACT:** read-only agents get arbitrary commands, so every other deny can be bypassed. This is a regression from baseline for most agents.
- **REQUIRED_CORRECTION:**
  - Replace root `git fetch*` with exact forms such as `git fetch` and `git fetch origin`, followed by denies for extra arguments.
  - Change `git diff*`, `git log*`, `git show*` and `git status*` to the space-delimited forms the agents already use.
  - Deny `git difftool*`, `git fetch-pack*`, `git mergetool*`, `git grep *-O*` and `git grep *--open-files-in-pager*`.

## SEC-04: Every agent can overwrite protected files at `allow` through redirection and `--output`
- **SEVERITY:** BLOCKER
- **LOCATION:** all `git status *`, `git diff *`, `git log *`, `git show *` and `git rev-parse *` allow rules.
- **THREAT_SCENARIO:** the binary shows a redirected command is matched as its whole text, including the redirect (`o.parent?.type==="redirected_statement"?o.parent.text:o.text`). So `git status *` matches `git status > AGENTS.md`. The `edit` permission is not consulted. `git log --format=` lets the agent choose the content.
- **RESOLVED RESULTS (`allow` for ALL 14 agents, both configs):**
  - `git status > AGENTS.md`
  - `git log > opencode.json`
  - `git diff > .opencode/agents/orchestrator.md`
  - `git rev-parse HEAD > delivery/status.yaml`
  - `git diff --output=AGENTS.md`
  - `git log --output=opencode.json`
  - `git show --output=.opencode/agents/coder.md`
  - `git log -1 --format=evil > .git/hooks/post-commit`
  - `git log -1 --format=evil --output=.git/hooks/post-commit`
- **Chain:** a read-only agent writes a `post-commit` hook, then the orchestrator's allowed `git commit` runs it, with no human. I did not test hook execution on Windows.
- **IMPACT:** agents can rewrite their own permissions, governance files and validators. AC-03 ("need a human prompt to edit ... validators") fails. This is pre-existing for project agents and new for built-ins (baseline `ask`).
- **REQUIRED_CORRECTION:** add final denies to the root and to orchestrator and debugger: `git *>*` and `git *--output*`. Add the same deny for `.\mvnw.cmd *>*`, `./mvnw *>*`, `npm run *>*`, `node *>*` and other allow-listed commands. Add these forms to the regression list.

## SEC-05: Coder and debugger can edit `.git/**` with no prompt
- **SEVERITY:** BLOCKER
- **LOCATION:** `coder.md`, `debugger.md`, edit `"*": allow` with no `.git/**` rule.
- **RESOLVED RESULTS (coder, debugger: `allow`):**
  - `.git/config`
  - `.git/hooks/pre-commit`
  - `.git/hooks/pre-push`
  - `.git/refs/heads/main`
- **Agents affected:** orchestrator, planner, tester, reviewer, documentation and the three specialist reviewers are denied; built-ins `ask`. The edit tool's code shows no `.git` guard.
- **IMPACT:** the coder can move local `main`, install hooks, or set config that runs commands or changes targets (for example `core.fsmonitor`, `diff.external`, remote URLs). All git rules can then be bypassed.
- **REQUIRED_CORRECTION:** add root and agent-final edit denies for `.git/**` and `**/.git/**`, and test them.

## SEC-06: Sweep staging, secret force-add, sweep commit and amend at `allow`
- **SEVERITY:** BLOCKER
- **LOCATION:** `orchestrator.md`, `git add *`, `git commit -m *`, `git commit -F *` and their positional denies.
- **RESOLVED RESULTS (orchestrator: `allow`):**
  - Staging:
    - `git add *`
    - `git add ./`
    - `git add ./.`
    - `git add -- .`
    - `git add -- *`
    - `git add -- :/`
    - `git add ":/"`
    - `git add :^x`
    - `git add ":(top)"`
    - `git add delivery/..`
    - `git add -v .`
    - `git add -vA`
    - `git add -fA`
    - `git add --al`
    - `git add --no-ignore-removal`
    - `git add -vu`
    - `git add --upd`
  - Ignored or secret files:
    - `git add -f .env`
    - `git add --force .env`
    - `git add -f secret.pem`
  - Sweep commit:
    - `git commit -m x --all`
    - `git commit -m x -am y`
    - `git commit -m x -av`
    - `git commit -m x -va`
    - `git commit -F f -am y`
  - Amend:
    - `git commit -m x --amen`
    - `git commit -m x --am`
    - `git commit -F f --amen`
  - Hook skip:
    - `git commit -m x --no-verify`
    - `git commit -m x -n`
- **Not `allow` (`ask` for the orchestrator):** `git commit -nm x` and `git commit --no-verify -m x`.
- **IMPACT:** violates the DEC-AGENT-01 sweep ban and AC-01. With SEC-01 or plain `git push origin task/x`, an ignored secret file can reach the remote with no human.
- **REQUIRED_CORRECTION:** patterns cannot fully fix this. `*` has no escape, so a literal `git add *` cannot be singled out, and commit messages are free text. Choose one (human decision 2):
  - (a) deny all direct `git add`, `git commit` and `git push`, and allow one validating script that parses arguments and checks `allowedFiles`; or
  - (b) make `git add` and `git commit` `ask`.
- At minimum, deny `git add *-f*`, `git add *--force*`, `git add *--al*`, `git add *-- .`, `git commit *--am*`, `git commit *--all*`, and bundled `-a` forms.

## SEC-07: Destructive switch and checkout at `allow`
- **SEVERITY:** BLOCKER
- **LOCATION:** `orchestrator.md`, `git switch task/*`, `git switch -c task/*`, `git checkout -b task/*`. The denies are anchored to the option being first.
- **RESOLVED RESULTS (orchestrator: `allow`):**
  - `git switch task/x --discard-changes`
  - `git switch task/x -f`
  - `git switch task/x --force`
  - `git switch -c task/x --discard-changes`
  - `git switch -c task/x -f`
  - `git checkout -b task/x -f`
  - `git checkout -b task/x --force origin/main`
- **IMPACT:** uncommitted work is discarded with no prompt, breaking AC-01 and the AGENTS.md §12 rule against overwriting human changes.
- **REQUIRED_CORRECTION:** add `git switch * ?*`-style trailing denies for the existing-branch form, and deny `git switch *-f*`, `git switch *--force*`, `git switch *--discard-changes*`, `git checkout *-f*` and `git checkout *--force*`. Check that these do not catch task branch names containing `-f`.

# MAJOR findings

## SEC-08: Forbidden outcomes resolve to `ask`, not `deny`, so `--auto` passes them
- **SEVERITY:** MAJOR
- **LOCATION:** `opencode.json` root bash; the deny list misses these commands and global-option prefixes.
- **RESOLVED RESULTS (`ask` for ALL agents):**
  - Prefix forms:
    - `git -C . push origin main`
    - `git -c x=y push origin main`
    - `git --no-pager push origin main`
    - `GIT_DIR=.git git push origin main`
    - `env git push origin main`
    - `git -c x=y clean -fd`
  - Ref and config changes:
    - `git update-ref refs/heads/main task/x`
    - `git update-ref -d refs/heads/x`
    - `git symbolic-ref HEAD refs/heads/main`
    - `git remote add evil https://x`
    - `git remote set-url origin https://x`
    - `git config alias.st push`
    - `git config --global alias.x push`
    - `git config remote.origin.push x`
  - History and discard:
    - `git filter-branch --all`
    - `git reflog expire --all --expire=now`
    - `git gc --prune=now`
    - `git worktree remove --force x`
    - `git replace a b`
    - `git prune`
    - `git branch -vD x`
    - `git branch --del x`
    - `git branch --forc main x`
    - `git branch -qf main x`
    - `git apply x.patch`, `git am x`, `git cherry-pick abc`, `git revert abc`
  - Merge through gh:
    - `gh api repos/o/r/pulls/1/merge -f x=y`
    - `gh api repos/o/r/pulls/1/merge -F x=y`
    - `gh api repos/o/r/pulls/1/merge --input f`
    - `gh api -XPUT repos/o/r/pulls/1/merge`
    - `gh api --method=PUT repos/o/r/pulls/1/merge`
    - `gh api graphql -f query=mutation`
    - `gh -R o/r pr merge 1`
    - `gh alias set x "pr merge"`
  - Other gh:
    - `gh auth token`
    - `gh pr edit 1`
    - `gh repo set-default o/r`
    - `gh issue create`
    - `gh workflow disable x`
  - Deletion:
    - `powershell -c "Remove-Item x"`, `pwsh -c ...`, `cmd /c del x`, `bash -c "rm -rf x"`, `sh -c "rm -rf x"`
    - `python -c ...`, `node -e ...`
    - `ri x`, `erase x`, `rd /s /q x`
    - `find . -delete`, `xargs rm`, `/usr/bin/rm -rf x`, `git rm x`
- **Agent-specific:** `git checkout main`, `git checkout HEAD p`, `git checkout main p` and `git switch main` are `ask` for orchestrator and built-ins, and deny for the other project agents. Without `--`, the path forms overwrite files.
- **IMPACT:** these need a human unless `--auto` is used, and then nothing stops them. AC-01 requires deny for several of these.
- **REQUIRED_CORRECTION:**
  - Add root denies, restated in orchestrator and debugger: `git -*`, `git update-ref*`, `git symbolic-ref*`, `git filter-branch*`, `git reflog *`, `git gc*`, `git worktree *`, `git replace*`, `git prune*`, `git config*`, `git remote add*`, `git remote set-url*`, `gh api*`, `gh alias*`, `gh auth token*`, `gh -*`, and `git checkout *` other than the allowed task form.
  - Treat inner-shell wrappers as human-gated only, and record that limit in the evidence.

## SEC-09: Orchestrator force-push went from `deny` to `ask` under the owner's config
- **SEVERITY:** MAJOR
- **LOCATION:** `orchestrator.md`. This diff removed `git push -f*`; the root deny is cancelled by the `bash *: ask` that global `~/.config/opencode/agents/orchestrator.md` places after root rules.
- **RESOLVED RESULT (global, orchestrator):**
  - `git push -f origin task/x`: baseline `deny`, candidate `ask`
  - `git push -fu origin task/x`: baseline `deny`, candidate `ask`
  - Sampled root denies not restated, now `ask`: `git push main`, `git push HEAD`, `git push master`, `git push -f`
  - In the empty config these are all `deny`.
- **IMPACT:** a direct weakening that AC-01 forbids, hidden because the script tests only the trailing `-f` form. Evidence root cause 3 is not fully closed.
- **REQUIRED_CORRECTION:** restate `git push -f*`, `git push main*`, `git push master*` and `git push HEAD*` in `orchestrator.md`. Add a check that samples every root deny per agent in both configs. My sampling found these 8 misses and no others.

## SEC-10: Other control-affecting paths are editable by coder and debugger at `allow`
- **SEVERITY:** MAJOR
- **LOCATION:** `coder.md`, `debugger.md` edit rules.
- **RESOLVED RESULT (`allow`):**
  - `opencode.jsonc`
  - `.opencode.json`
  - `services/x/opencode.json`
  - `services/.opencode/agents/x.md`
  - `services/AGENTS.md`
  - `CLAUDE.md`
  - `services/CLAUDE.md`
  - `mvnw`
  - `mvnw.cmd`
  - `.npmrc`
  - `apps/web/.npmrc`
  - `.github/CODEOWNERS`
  - `.github/actions/x/action.yml`
  - `.github/dependabot.yml`
  - `.gitignore`
  - `.gitattributes`
  - `release-manifests/x.yaml`
  - `scripts/x.mjs`
- **Correctly protected:** `.opencode/plugins/**` is deny for all. `.github/workflows/**`, `.mvn/**`, `infra/**`, `pom.xml`, `scripts/delivery/**`, `scripts/contracts/**` and `.spectral.yaml` are `ask` for coder and debugger.
- **IMPACT:**
  - `mvnw.cmd` and `.npmrc` run under other agents' allowed commands, including read-only reviewers.
  - `.github/actions/**` and `CODEOWNERS` affect CI and review controls.
  - `.gitignore` can un-ignore secrets.
  - An extra `opencode.jsonc` may change permissions. I did not verify its merge order.
- **REQUIRED_CORRECTION:** set `ask` or `deny` for these paths, with `opencode.jsonc`, `**/opencode.json*`, `**/.opencode/**` and `**/AGENTS.md` as deny. Add them to the check.

## SEC-11: Secret-pattern edit protection has gaps
- **SEVERITY:** MAJOR
- **LOCATION:** `opencode.json` root edit; `coder.md`, `debugger.md`, `tester.md`, `orchestrator.md`, `documentation.md`.
- **THREAT_SCENARIO and RESULTS:**
  - `**/x` requires a slash because `*` is a plain regex wildcard. At the repo root, `x.pem`, `x.key`, `secrets/x` and `id_rsa` are `allow` for coder and debugger.
  - `.p12`, `.jks`, `.pfx`, `id_rsa` and `id_ed25519` have no rule. Root-level `x.p12`, `x.jks` and `x.pfx` are `allow` for coder and debugger. Under `infra/` they are only `ask`.
  - Agent-specific allows come after the root denies and override them:
    - Tester `allow`: `tests/.env`, `tests/secrets/x.key`, `services/x/src/test/resources/dev.pem`, `services/x/src/test/resources/.env`, `apps/e2e/secrets/x`
    - Orchestrator, tester, documentation `allow`: `delivery/evidence/x.pem`, `delivery/evidence/.env`
    - Orchestrator, documentation `allow`: `delivery/handoffs/secrets/x`, `delivery/deviations/.env.local`
  - `git check-ignore` matched `x.pem` and `.env.local`, but not `x.p12`, `x.jks`, `x.pfx` or `id_rsa`.
- **IMPACT:** AC-03 ("anywhere in the repository") fails for coder and debugger. The AGENTS.md §4 secret rule is not enforced for the others. The script tests only `infra/local/dev.pem`, not the root.
- **REQUIRED_CORRECTION:** add both root and nested patterns (`*.pem`, `secrets/**`, and so on), add keystore and SSH key patterns, and place secret denies last in every agent with an edit allow. Add `.gitignore` entries if the owner accepts the scope.

## SEC-12: Secrets readable through bash and other paths despite read denies
- **SEVERITY:** MAJOR
- **LOCATION:** bash allow rules; root read rules.
- **RESOLVED RESULTS:**
  - `allow` for ALL:
    - `git diff --no-index .env /dev/null`
    - `git diff --no-index C:/Users/panos/.ssh/id_rsa /dev/null`
    - `git show HEAD:infra/local/.env`
    - `git log -p -- .env`
  - `allow` for all project agents except orchestrator:
    - `git grep --no-index KEY .env`
    - `git grep --untracked KEY`
  - `allow` for reviewer, debugger, contract-reviewer, data-reviewer, security-reviewer: `git blame --contents .env x`.
  - `allow` for coder, tester, reviewer, debugger, data-reviewer, security-reviewer: `docker compose config`, `docker compose config --environment`, which can print interpolated `.env` values.
  - Read tool (repo-relative patterns, per binary):
    - `allow` for ALL: `secrets/x`, `x.p12`, `infra/x.jks`, `x.pfx`, `infra/id_rsa`, `infra/id_ed25519`, `.npmrc`, `.git/config`
    - `allow` for all except orchestrator and debugger under global: `x.pem`, `x.key`, `id_rsa`
    - In the empty config, those three are `allow` for all.
- **External reads:** the `external_directory` check runs only for a fixed set of file commands (`rm`, `cp`, `cat`, and so on), not for `git`. So the `--no-index` form leaves the worktree with no check.
- **Grep tool:** the code asks only for `grep` permission on the pattern, then passes `include` to `rg --hidden --glob=<include>`. It may read `.env` without any read rule. NOT_RUN.
- **Current exposure:** the only tracked or on-disk secret-pattern file found is `infra/local/.env.example`, so `git show` and `git log -p` have nothing to expose today. `~/.ssh` was not inspected.
- **IMPACT:** content goes to the model provider and can be exfiltrated, bypassing the read denies.
- **REQUIRED_CORRECTION:** deny `git *--no-index*`, `git blame *--contents*` and `git grep *--untracked*`. Deny bash forms naming secret patterns as a best effort. Fix root-level read patterns, add keystore and SSH patterns, and move `docker compose config*` to `ask`.

## SEC-13: Substring build rules allow deploy, migrate, clean and arbitrary execution
- **SEVERITY:** MAJOR
- **LOCATION:** `.\mvnw.cmd *test*`, `.\mvnw.cmd *verify*`, `./mvnw *test*`, `./mvnw *verify*`, `npm run test*`, `npm run build*`, `npm run lint*`, `npm run contracts:*`, data-reviewer `flyway validate*` and `flyway info*`.
- **RESOLVED RESULTS:**
  - `allow` for coder, tester, reviewer, debugger, contract-reviewer, data-reviewer, security-reviewer:
    - `.\mvnw.cmd exec:exec -Dtest=x -Dexec.executable=cmd`
    - `.\mvnw.cmd deploy -Dtest=x`
    - `.\mvnw.cmd deploy -DskipTests` (the match is case-insensitive)
    - `.\mvnw.cmd flyway:migrate -Dtest=x`
    - `.\mvnw.cmd org.flywaydb:flyway-maven-plugin:clean -Dtest=x` (bypasses `*flyway:clean*`)
    - `.\mvnw.cmd -f C:/evil/pom.xml test`
    - the matching `./mvnw` forms
  - `allow` for data-reviewer:
    - `flyway validate clean`
    - `flyway info clean`
    - `./mvnw flyway:validate org.flywaydb:flyway-maven-plugin:clean`
  - `npm run test --script-shell=evil`: `allow` for coder, tester, reviewer, debugger, security-reviewer.
  - `npm run build --script-shell=evil`: `allow` for coder, tester, debugger, contract-reviewer.
  - `npm run contracts:verify --script-shell=evil`: also `allow` for the orchestrator.
  - `node scripts/contracts/../../evil.js`: `allow` for contract-reviewer.
  - `node scripts/delivery/validate.mjs.evil.js`: `allow` for orchestrator, tester, reviewer, documentation.
- **IMPACT:** read-only reviewers can run arbitrary commands and destructive database goals. The `.\mvnw.cmd` rules are new in this diff, and AC-01's destructive-database requirement fails.
- **REQUIRED_CORRECTION:** anchor to goals (`.\mvnw.cmd test`, `.\mvnw.cmd -pl * test`, `... verify`). Deny `*exec:*`, `*deploy*`, `*flyway*` (data-reviewer exceptions by exact form), `*-f *`, `*--file*`, `npm run *--script-shell*`, `node *..*`. Make `validate.mjs` an exact form plus `validate.mjs --*`.
- **Residual that rules cannot fix:** an agent that runs tests executes repository code. A coder can write a test that runs `git push`. This is one more reason for branch protection.

## SEC-14: Commands with no `command` parse node appear to skip permission entirely
- **SEVERITY:** MAJOR (code-derived; parser result NOT_RUN)
- **LOCATION:** OpenCode 1.19.0 bash tool: `ShellTool.collect` walks `descendantsOfType("command")`, and `ShellTool.ask` has `if(e.patterns.size===0)return`.
- **THREAT_SCENARIO:** a shell statement that holds no `command` node asks for nothing. I did not confirm the tree-sitter parse for these. Candidates:
  - Bash: `> AGENTS.md`
  - PowerShell: `[System.IO.File]::Delete('AGENTS.md')`, `[Diagnostics.Process]::Start('git','push origin main')`
- **Shell in use:** not determined. `SHELL=/bin/bash.exe` in this session, and I could not find OpenCode's selection logic.
- **IMPACT:** if confirmed, no rule of any kind applies, including `deny`.
- **REQUIRED_CORRECTION:** a tester should confirm this in a disposable repo and session, using harmless targets. Record the result in the evidence. Config cannot fix it; rely on branch protection, and report it upstream if confirmed.

## SEC-15: The regression script passes while protection is missing
- **SEVERITY:** MAJOR
- **LOCATION:** `scripts/delivery/check-agent-permissions.mjs`; `delivery/evidence/I1-DEL-001/agent-permissions.md`.
- **Reproduced:** 811/811 PASS with the global config and with an empty config. The evidence claim is accurate but much narrower than AC-01 implies.
- **Gaps:**
  - No BLOCKER form above is in `FORBIDDEN_COMMANDS`.
  - The matcher is case-sensitive and does not normalize backslashes, unlike the real one. I reran a scratch copy with the real matcher and still got 811/811, so this hides nothing today, but it can.
  - `plan` and `explore` are not checked.
  - `task` checks only `general`, not `coder-fast` and similar names.
  - No check for unlisted tools resolving `allow`, root denies preserved per agent, redirection, `.git/**`, root-level secret paths, or `.env.example`.
  - The default return is `allow`, where the real default is `ask`. This is harmless today because the first resolved rule is `* * allow`.
  - The rule test uses `rule.permission === '*'`, where the real code wildcard-matches permission names.
- **What is sound:** it fails closed if `opencode` errors, if run from another directory, or if project agents are missing. One empty-config run crashed on `opencode debug agent planner` (status 1, stderr hidden), and two reruns passed. Treat that as an unexplained flake.
- **IMPACT:** a green result reads as proof of AC-01 when it is not.
- **REQUIRED_CORRECTION:** use the real matcher, add all attack forms and the checks above as negative cases, and state in the evidence that the check is not a parser and not a sandbox.

# MINOR findings

## SEC-16: Stale delegation allows survive under the global config
- **SEVERITY:** MINOR
- **LOCATION:** the global `orchestrator.md` `task` keys merge with the project's.
- **RESOLVED RESULT (orchestrator, global):**
  - `allow`: `coder-fast`, `planner-advanced`, `reviewer-fast`, `scout`, `specialist-coder`, `specialist-coder-backup`, `tester-static`, `reviewer-strict`, `architecture-guardian`, `privacy-security-guardian`, `room-migration-guardian`, `documentor`
  - `ask`: `swarm-coder`, `tester-runtime`, `ci-build-debugger`
  - In the empty config these are `deny`.
- **Current reachability:** `opencode agent list` shows only 10 project agents plus 7 built-ins. The 12 named global agent files are disabled in `opencode.json`. No file exists today for `coder-fast`, `planner-advanced` or `reviewer-fast`, and they are not in the disable list.
- **If the owner adds one:** `~/.config/opencode/agents/coder-fast.md` would be enabled and delegable at `allow` with no prompt. An unrelated new global agent is `deny` from the orchestrator (`brand-new-agent` tested) but `ask` from build, plan, general and explore. The check flags new agents only when someone runs it.
- **AC-02 status:** holds today.
- **REQUIRED_CORRECTION:** add explicit `deny` task entries in the project `orchestrator.md` for all these names, and disable the three missing names. Test them in the script.

## SEC-17: Removing root `"*": "ask"` makes unlisted tools default to `allow`
- **SEVERITY:** MINOR (fail-open with no reachable tool found today)
- **LOCATION:** `opencode.json`.
- **RESOLVED RESULT:** the first rule is `* * allow`, and no later catch-all exists. Baseline had `*=ask`. Any unlisted tool name, such as a hypothetical `mcp_x` or `custom_global_tool`, goes `ask` to `allow` for all agents except `explore`, which stays `deny`.
- **Why it is not higher:**
  - `opencode mcp list` shows no servers.
  - `codesearch`, `todoread` and `multiedit` do not appear in the 1.19.0 binary.
  - The global `tools/` folder holds `.py` and `.mjs` files, while the loader globs `{tool,tools}/*.{js,ts}`.
- **Risk:** a future MCP server or plugin gets `allow` silently. The evidence lists the catch-all's removal as a fix but does not disclose this loss.
- **Why the old rule cannot simply return:** its position cancelled root rules under the global config.
- **REQUIRED_CORRECTION:** add explicit rules for any tool added. Make the check fail when an MCP server or custom tool exists without a rule. Disclose this in the evidence.

## SEC-18: `/ship-task` secret scan catches provider tokens and keys, not generic secrets
- **SEVERITY:** MINOR
- **LOCATION:** `.opencode/commands/ship-task.md`, pre-commit gate step 4.
- **Exact command, repo as cwd, scratchpad file:** crashes with `Path ... is not in cwd`, exit 2, for absolute and relative outside paths. This fails closed and does not affect in-repo files.
- **Same tool and ruleset, scratchpad as cwd (`node <repo>/node_modules/secretlint/bin/secretlint.js --maskSecrets --secretlintrc <repo>/scripts/contracts/secretlintrc.json ...`):**

| Fake input | Result | Exit |
|---|---|---|
| `AKIAIOSFODNN7EXAMPLE` | NOT detected | 0 |
| `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY` | NOT detected | 0 |
| `<fake AWS-format test value, masked>` (non-example format) | detected | 1 |
| Non-example 40-character AWS secret | detected | 1 |
| GitHub PAT format | detected | 1 |
| RSA and EC private-key blocks | detected | 1 |
| Slack token | detected | 1 |
| `DB_PASSWORD=...`, `KEYCLOAK_CLIENT_SECRET=...` (including in a file named `.env`) | NOT detected | 0 |
| JWT-format value | not reported (only the Slack line in that file was) | n/a |
| Directory argument, including hidden files | detected recursively | 1 |
| Backslash relative path | detected | 1 |
| `clean.txt does-not-exist.txt` | silently ignores the missing file | 0 |
| Only a missing file | `Not found target files` | 2 |

- **Interpretation:** the two AWS documentation values appear to be allow-listed, since real-format values are caught. Binary keystores were not tested.
- **IMPACT:** this project's likely secrets (Keycloak client secret, DB password) pass the only automated gate. A mistyped path passes silently. The step resolves `ask` for the orchestrator, so `/ship-task` prompts there.
- **REQUIRED_CORRECTION:** add a filename check (`.env*`, `*.pem`, `*.key`, `*.p12`, `*.jks`, `*.pfx`, `id_rsa*`, excluding `.env.example`) and a value check for names like `*SECRET*=`, `*PASSWORD*=`. Fail if any listed path is missing, except recorded deletions. Scan staged content.

## SEC-19: Coder and debugger `.env.example` rule is dead
- **SEVERITY:** MINOR (fails closed)
- **LOCATION:** `coder.md`, `debugger.md`. `.env.example: ask` comes before `.env.*: deny`, because the secret denies moved last.
- **RESOLVED RESULT:** `.env.example` and `infra/local/.env.example` go from baseline `ask` to `deny` for both. The handoff does not disclose this. Build still gets `ask`.
- **IMPACT:** a tracked template can no longer be changed by the implementers, and the written rule is misleading.
- **REQUIRED_CORRECTION:** place `.env.example` and `**/.env.example: ask` after the denies, or record the deny as intended and remove the dead rules.

# NOTE

## SEC-20: Fail-closed changes worth recording
- **SEVERITY:** NOTE
- `git merge-base*` is now `deny` for all agents because `git merge*` comes after it. I found no use in commands, skills or `delivery/README.md`.
- `git branch x` and `git remote show origin` went to `ask` for all agents.
- `git stash` is `deny` for most project agents and `ask` for planner and built-ins.

# Tested and clean

- **Direct forms, `deny` for all agents:**
  - `git push origin main`, `task/x:main`, `task/x:refs/heads/main`, `task/x main`, `task/x MAIN`, `task/x HEAD`
  - `task/x -f`, `--force-with-lease`, `--delete`, `-d`
  - URL pushes with a colon
  - `git commit -am x`, `-a -m x`, `-m x -a`, `--amend`
  - `git add .`, `-A`, `-a`, `-Af`
  - `git checkout task/x -- p`
  - `git branch -D x`, `-dr x`
  - `git stash clear`, `git clean -fd`
  - `gh pr merge`, `gh repo edit`, `gh release`, `gh secret`, `gh variable`, `gh workflow run`
  - `rm`, `RM`, `rmdir`, `del`, `Remove-Item` and lowercase forms
  - terraform, tofu, kubectl, docker prune, `docker compose down -v`, `flyway clean`, `.\mvnw.cmd flyway:clean`, `npm publish`
- **Other project agents:** planner, coder, tester, reviewer, debugger, documentation and the three specialist reviewers deny every `git add`, `git commit` and `git push` form tested without a global-option prefix, including the SEC-01, SEC-02 and SEC-06 forms. `git -C`, `git -c` and similar prefixed forms are `ask` (SEC-08).
- **Task branch handling:** `git push origin task/main` and `task/main-fix` are `allow`, as intended. Other remotes and bare `git push` are `ask` for the orchestrator, not `allow`.
- **Chaining:** the 1.19.0 binary evaluates each parsed `command` node separately, including nested substitutions. So `git status && rm -rf x`, `.\mvnw.cmd test && git push origin main` and `git log $(rm -rf x)` hit their denies. The docs do not state this; I read it from the code and did not run it in a session. See SEC-14 for the gap.
- **Backslash rules:** the real matcher normalizes `\`, so `.\mvnw.cmd` rules work. This closes the evidence's NOT_RUN item at the code level.
- **Delegation:**
  - The orchestrator allows exactly the 9 project subagents plus the stale names in SEC-16.
  - `general` and `explore` are `deny`.
  - All 9 project subagents have `task: deny`.
  - Disabled agents do not appear in `opencode agent list`. I did not trace the task tool's own filtering.
- **Edit tools:** edit, write and apply_patch all ask under the `edit` permission.
- **Baseline comparison, not weakened:**
  - `webfetch`, `websearch`, `external_directory` (`deny`), `skill`, `doom_loop`
  - no edit or read path I sampled loosened
  - root `task` tightened from `allow` to `ask`
  - root edit tightened from `allow` to `ask`
- **Baseline comparison, loosened (all listed above):**
  - `git fetch` and `git difftool` (SEC-03)
  - `git push -f...` (SEC-09)
  - unlisted tools (SEC-17)
  - built-in access to read-only git commands, which is intended but carries SEC-04 and SEC-12
  - `.\mvnw.cmd` and `gh pr view` / `gh pr checks`, which are intended (see SEC-13)

# Documents and sections read

AGENTS.md §4, §6, §12, §15; `delivery/tasks/I1-DEL-001.yaml`; `delivery/handoffs/I1-DEL-001/01-orchestrator.md` (first 20 lines); `02-coder.md`; `delivery/evidence/I1-DEL-001/agent-permissions.md`; `opencode.json` (candidate and baseline); frontmatter of all `.opencode/agents/*.md`; `.opencode/commands/ship-task.md`; the diff of the other command files; `scripts/delivery/check-agent-permissions.mjs`; `scripts/contracts/secretlintrc.json`; `.gitignore`. Global config: filenames only, and no provider blocks were read.

# Files changed

None in the repository. Temporary helpers and fake-token files are in the scratchpad only.

# Commands executed

| Command | Result |
|---|---|
| `opencode debug agent <name>` for 17 agents, permission array only, in 3 setups | Done |
| `node scripts/delivery/check-agent-permissions.mjs` | PASS 811/811, global |
| Same, empty config | PASS 811/811 on 2 reruns; first run crashed once (SEC-15) |
| Scratch copy using the real matcher | PASS 811/811, both configs |
| `node scripts/delivery/validate.mjs` | PASS |
| `npm exec --no -- secretlint --secretlintrc scripts/contracts/secretlintrc.json <path>` from the repo | Exit 2 for outside paths; exit 0 on in-repo handoff paths (file, backslash form, directory) |
| Secretlint with the repo ruleset from the scratchpad | See SEC-18 |
| `opencode agent list`, `opencode mcp list` (filtered output), `git status`, `git diff`, `git archive`, `git check-ignore`, `git <cmd> -h` | Done |
| Attack matrix over all listed commands and paths | Done with the real matcher, both configs |

# Test results

- PASS: delivered check, validator.
- FAIL: AC-01, AC-03, and the AC-04 clause on task-branch-only pushes, as tested by this review.
- NOT_RUN: live OpenCode session; any tested git, gh or shell command (by instruction); `node scripts/delivery/self-test.mjs`; parser behavior in SEC-14; the grep tool's `.env` read in SEC-12.

# Acceptance criteria status

| AC | Status |
|---|---|
| AC-01 | FAIL: SEC-01, 02, 06, 07, 08, 09, 13 |
| AC-02 | PASS today; latent risk in SEC-16 |
| AC-03 | FAIL: SEC-04, SEC-11 |
| AC-04 | Test and CI allowances PASS by rule evaluation; "push only task branches" FAIL (SEC-01) |
| AC-05 | Ship step text reviewed for security only; scan gaps in SEC-18; end-to-end NOT_RUN |
| AC-06, AC-07 | Not reviewed here; general reviewer's scope |

# Decisions made

Severities only. No normative decision.

# Assumptions

The installed binary matches CLI 1.19.0. Git behaves as its usage text and documentation describe; none of the tested commands were run.

# Findings and residual risks

See above. Even after every correction, a coder or tester can run code it wrote through allowed test commands using the owner's git and gh credentials, so pattern rules cannot contain them.

# Blockers

SEC-01 through SEC-07.

# Required human decisions

1. **Branch protection on `main`:** enable it before first live OpenCode use, with required pull requests, no force-push, no deletion, and enforcement for admins. Agents use the owner's credentials, so an admin exemption would leave the gap open. This is outside this packet's scope.
2. **How the orchestrator commits and pushes:** see SEC-06.
   - Option (a): a validating wrapper script. This is the stronger control, but it needs a packet amendment because the script is outside `allowedFiles`.
   - Option (b): `ask` for `git add` and `git commit`. This is simpler, but it adds prompts and `--auto` would pass them.
3. **`--auto`:** whether it is banned for this repository. Everything in SEC-08 depends on a human answering.

# Recommended next agent

RECOMMENDED_STATE: FIX_REQUIRED. Return to coder for SEC-01 through SEC-19, within the packet's `allowedFiles`. Then rerun the extended check in both configs and repeat this security review before `/ship-task`.
