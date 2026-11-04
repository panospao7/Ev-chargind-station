# I1-DEL-001 — Agent permission evidence

- **Task ID:** I1-DEL-001
- **Baseline commit:** 811b81ed39ca954217cf785f5f9c3135f14b2263
- **Candidate commit:** none yet; results are from the uncommitted worktree on `task/i1-del-001-opencode-controls`
- **Date:** 2026-11-04 (per-run timestamps not captured)
- **Environment:** Windows 11, Node 24.21.0, OpenCode 1.19.0; checks ran from Git Bash

## What this does and does not prove

**These rules are guardrails against agent mistakes. They are not a security boundary.** The owner accepted this model on 2026-11-04 (`delivery/handoffs/I1-DEL-001/12-orchestrator.md`).
An agent that deliberately tries to bypass them, or is manipulated into it through prompt
injection, has known routes around them. GitHub branch protection on `main` and human
review of pull requests are the controls that hold.

`scripts/delivery/check-agent-permissions.mjs` reads the rules OpenCode resolves
(`opencode debug agent <name>`) and applies matching copied from the OpenCode 1.19.0
binary. It tests whole command strings. It does not execute commands, run a live session,
parse git arguments, or model session approvals. OpenCode itself matches patterns extracted
by a shell parser, so a passing check does not prove live behaviour.

## Results

| Run | Configuration | Result |
|---|---|---|
| Baseline config, first check | With global config | FAIL: 480/811, 343 failures including 12 outside agents |
| First candidate, first check | Both | PASS 811/811; independent review then failed it because the check missed attack forms |
| First candidate, extended check | With global config | FAIL: 2216/4201 |
| Second candidate | Both | PASS 4188/4188; independent review then found parser-level gaps |
| Round 3 | Both | PASS 5187/5187, 17 agents listed |

## Root causes found

1. **Root catch-all position.** The global config sets `permission.edit` and `permission.bash`,
   so those keys merge first and the project's root `"*": "ask"` landed after them. Under
   last-match-wins it cancelled every root bash and edit rule.
2. **Agent catch-all.** Each agent's bash `"*": ask` overrode root denies it did not repeat.
3. **Same-named global files.** Global `orchestrator.md` and `debugger.md` merge into the project
   agents, so a missing project rule does not remove a global one.
4. **Broad allows.** A trailing `*` accepts extra arguments.
5. **Rule shape and order.** Later path rules overrode secret denies, and `**/x.pem` misses the root.

## Design

- Allow rules name exact commands. The orchestrator can push one literal task branch.
- One shared deny list comes last for every agent, because global files merge in ahead of it.
- `git add` and `git commit` prompt a human. This is stricter than DEC-AGENT-01 permits, and the owner
  chose to keep it on 2026-11-04.
- `"*_*": "ask"` covers MCP-style tool names, replacing the root catch-all.

## Other checks

| Check | Result |
|---|---|
| `node scripts/delivery/validate.mjs` | PASS |
| `node scripts/delivery/self-test.mjs` | PASS: 8 passed, 0 failed |
| `secretlint` on all changed files | PASS: exit 0, after masking one fake test value in a saved review |
| Branch protection on `main` (GitHub API) | Present: pull request required with 0 approvals, admins enforced, no force-push, no deletion, no required status checks |
| Live OpenCode session, disposable repository, started from PowerShell with `SHELL` unset, project rules plus the shell pin | PASS: `uname -a` reported MINGW64, so Git Bash and not WSL. `git checkout -- .` was denied by the rule, and an uncommitted file survived. One session only; other live behaviour remains NOT_RUN |
| `/review-task`, `/ship-task`, `/verify-task`, `/close-task` end to end | NOT_RUN |
| `npm run contracts:verify`, Maven suites, GitHub CI | NOT_RUN; no contract or product code changed, nothing committed |

## Limitations that remain

These are from the second-round reviews, which read OpenCode's code and ran its parser grammars.
None was confirmed in a live session.

| Limitation | Effect | Who can resolve it |
|---|---|---|
| **PowerShell skips checks.** On PowerShell, commands containing ` -- ` or `./x`, such as `git checkout -- .`, produce no pattern, so no rule or prompt applies. | Closed by pinning `"shell": "bash"`, approved by the owner on 2026-11-04. The check fails if the pin is removed | Resolved |
| **Grouped redirects.** `(git status) > opencode.json` matches the allow rule, because the redirect sits outside the parsed command. `*>*` only denies the ungrouped form. | Any agent can overwrite any file with command output, including permissions and git hooks | Owner accepted on 2026-11-04 |
| **Environment assignment.** `export GIT_EXTERNAL_DIFF=evil; git diff` runs another program, because assignments are not checked. | Any agent can run a program | Owner accepted on 2026-11-04 |
| **"Always" answers.** One "always" reply on a prompt can store a broad rule that outranks denies for the rest of the session, including `edit`. | Silent loss of the deny layer | Human rule: answer "once" |
| **`--auto`.** It approves anything not explicitly denied. | Prompts stop protecting anything | Human rule: do not use it here |
| **Tests run code.** Agents that run tests run repository code using the owner's credentials. | Rules cannot contain this | Owner: accept, or add an OS-level control |
| **Grep tool.** It is checked on the search pattern only, so it may read `.env` content through a glob. Tested with `rg` on a fake file, not through OpenCode. | Secrets could reach the transcript | Owner: move `grep` to `ask`, or accept |
| **Tool names without an underscore** default to allow. None exists today. | A future plugin could run unprompted | Add a rule when adding a tool |
| **Maven profiles.** `./mvnw test -Pevil` stays allowed, because matching ignores case and a `-P*` rule would also block `-pl`. | A profile already in a POM can run | Owner: accept. POM edits prompt |
| **Commit and staging forms.** `git commit -m x .` and `git add -- delivery` prompt rather than deny. | A person must spot a sweep | Human reads the prompt |
| **Global config differences.** Under the owner's global config only, the orchestrator and debugger also get allow for odd names such as `git statusx`. These are not real commands. | None known | Moving the global agents removes it |
| **Direct push protection.** A direct push to `main` should be refused by GitHub. Task branches have no such protection. | A task branch could be overwritten | Owner: extend protection if wanted |

## False positives

- Any bash command containing `>` is denied, including `2>&1` and `git log --format="a -> b"`.
- Git commands naming `.env`, `.pem`, `.key` or `secrets/` are denied, including staging `.env.example`.
- Maven: `clean`, any argument containing `:`, `-fae`, `-DskipTests`, and `-Dmaven.test.skip` are denied, and `-q` prompts. The skip denies are pending an owner decision.
- `git grep` prompts for every agent. Use the grep tool instead.
- `git diff $BASE` and other `git diff` forms using a shell variable are denied.
- `git branch --contains`, `git reflog`, `git --version`, `git --no-pager log` are denied, and `docker compose config` prompts.
- `git worktree list`, `git stash list`, `git tag --list`, `git config --get` are denied.
- `git switch -c` and `git checkout -b` prompt, because matching ignores case, so `-C` and `-B` cannot be told apart.

## Secret scanning

`/ship-task` uses secretlint for provider tokens and private keys, a filename check, and a
case-insensitive search for generic assignments that a person must inspect. This narrows the
risk and does not remove it.

No secrets or personal data are included in this file.
