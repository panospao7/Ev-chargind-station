---
role: reviewer
taskId: I1-DEL-001
resultingState: FIX_REQUIRED (recommended)
baselineCommit: 811b81ed39ca954217cf785f5f9c3135f14b2263
impactLevel: L3
date: 2026-11-04
---

Independent subagent output (round 3 verification), saved unedited except for this header and the replacement of any local temp path with `<session scratchpad>` and any home-directory path with `~`.

# I1-DEL-001 narrow verification of round 3

VERIFICATION_STATUS: FAIL_FINDINGS (0 BLOCKER, 1 MAJOR, 7 MINOR, 4 NOTE)
RECOMMENDED_STATE: FIX_REQUIRED. V3-01 alone causes the fail, and its fix is one regex change. No further attack round is needed, only a targeted re-check of the corrected items.

Severity is my judgement. V3-01 is rated by the precedent of G-06, which was MAJOR. V3-02 is rated MINOR by the precedent of R2-15, because a human prompt still applies, and because the disclosed grouped-redirect route (`(git status) > opencode.json`, `allow`) already gives the same outcome with no prompt. The owner may rate either differently.

## Findings

### V3-01 MAJOR: the G-06 fix misses `privateKey` and `apiKey`, though round 3 claims camelCase coverage
- **Location:** `.opencode/commands/ship-task.md` gate step 8; `08-coder-round3.md` row G-06; `scripts/delivery/check-agent-permissions.mjs` line 171 (same regex).
- **Reproduction (temp repo, staged files, exact command text):**
  - Not listed: `privateKey: abc`, `"apiKey": "abc"`.
  - Listed: `API_KEY=abc`, `api-key: abc`, `clientSecret: abc`, `client-secret: abc`, `{ "password": "abc" }`, `token='abc'`.
  - The alternation requires a separator in those two keywords (`PRIVATE_KEY|PRIVATE-KEY|API_KEY|API-KEY`).
- **Repository exposure:** this shape occurs in `apps/bff/src/main/java/com/evplatform/bff/security/ClientKeyConfig.java` and `apps/bff/src/test/java/com/evplatform/bff/realm/KeycloakExchangeFixture.java`. I counted matches only and did not read the values, so these are probably code rather than secrets. The search would not send them to human inspection.
- **Required correction:** use `PRIVATE[_-]?KEY|API[_-]?KEY`. I tested this variant and all four key forms are listed. Update check line 171 to match.

### V3-02 MINOR: regression, the R2-16 `ask` rules sit after the denies and downgrade them
- **Location:** `opencode.json` lines 382-383 (`git show-*`, `git diff-*`) come after line 376 (`*>*`) and the `git *--output*` and `git *--ext-diff*` denies. The agent frontmatter has the same order.
- **Reproduction (parser plus resolved rules, bash, both configurations):**

| Command | Round 2 rules | Round 3 rules |
|---|---|---|
| `git diff-tree --output=AGENTS.md HEAD` | deny 14 | ask 14 |
| `git diff-files --ext-diff` | deny 14 | ask 14 |
| `git show-ref > AGENTS.md` | deny 14 | ask 11, deny only orchestrator, data-reviewer, security-reviewer |
| `git diff-tree HEAD > opencode.json` | deny 14 | ask 11, same 3 deny |
| `git diff-index -p HEAD -- .env` | deny 14 | ask 13, deny 1 |

- **False evidence as a result:** `agent-permissions.md` line 46 ("One shared deny list comes last for every agent") and line 82 ("Any bash command containing `>` is denied").
- **Required correction:** in each agent, place these two rules after the global-merged allows and before the shared deny block. Add these forms to the check.

### V3-03 MINOR: regression, the R2-04 deny blocks ordinary `git grep`, undisclosed
- **Location:** `git grep -*O*` and `git grep * -*O*`. Matching ignores case, so any `o` after a hyphen triggers them.
- **Reproduction:** `git grep -n Booking`, `git grep -i token`, `git grep -l account`, `git grep foo -- docs/` are deny for all 14. `git grep -n Booking -- docs/` was `allow` under round 2 rules. `git grep -n main` is still `allow`.
- **Impact:** fails closed. No command text names `git grep`, and the grep tool remains available.
- **Required correction:** add this to the false-positive list, or narrow the rule.

### V3-04 MINOR: evidence line 84 is false for profile flags
- **Location:** `agent-permissions.md` line 84 ("profile flags are denied or prompt").
- **Reproduction:** `./mvnw test -Pevil`, `./mvnw test -P evil` and `.\mvnw.cmd -pl x -am test -Pintegration` are `allow` for the 7 Maven agents. R2-08 named `-Pevil`. Only `--activate-profiles` is denied.
- **Required correction:** correct the line and list it as a limitation. Case-insensitive matching means a `-P*` rule would also hit `-pl`, so a deny is not simple.

### V3-05 MINOR: "remainder disclosed" is false for R2-15 and R2-16
- **Location:** `08-coder-round3.md` row R2-15, R2-16.
- **Reproduction:** these forms appear nowhere in `agent-permissions.md` or `delivery/README.md`:
  - `git commit -m x .` and `git add -- delivery`: ask for orchestrator.
  - `git log-x`, `git statusx`, `git rev-parsex`, `git ls-filesx`: `allow` for orchestrator and debugger under global config, `ask` under empty.
- **Required correction:** add the disclosure lines.

### V3-06 MINOR: `/ship-task` does not notice a new file added after review
- **Location:** `ship-task.md` precondition 5 ("using `/review-task`'s saved path list").
- **Reproduction (temp repo):** after adding an untracked file, the saved-list recomputation gives the SAME result, while the live list differs. Gate step 2 checks only `allowedFiles`, so an unreviewed file can be committed and pushed. `/verify-task` then catches it (DIFFER after commit), but only after publication.
- **Required correction:** in precondition 5, also return `SELF_VERIFIED` if `git ls-files --others` lists any path absent from the saved list.

### V3-07 MINOR: an owner-reserved Maven choice was made silently, and the G-11 list is incomplete
- **Location:** R2-08 called the test-skip denies "owner-chosen". `*-DskipTests*` and `*-Dmaven.test.skip*` are now deny, and neither the false-positive list nor the "needs the owner" list mentions this.
- **Reproduction:** `delivery/tasks/I1-ENG-001.yaml` line 214, `./mvnw -q -DskipTests package`, is now deny. That task is `VERIFIED`, so there is no live impact.
- **Also unlisted from the G-11 table:** `git branch --contains`, `git reflog`, `git --version`, `git --no-pager log`, `docker compose config`, `./mvnw -q`.
- **Required correction:** list these, and add the skip denies to the owner decisions.

### V3-08 MINOR: sibling of a named R2-07 form stays at `allow`
- **Reproduction:** `git diff $HOME/.ssh/id_rsa NUL` and `git diff NUL $HOME/.ssh/id_rsa` are `allow` for all 14, both configurations, both shells. The `/dev/null` variant is denied only because ` /dev/null` matches `git diff * /*`. NOT_RUN as a real command.
- **Required correction:** add `git diff *$*`, following the R2-03 pattern.

### Notes
- **N-A (G-09):** the first review also asked for skill-rule expectations. Adding `skill * allow` to data-reviewer in a scratch copy of the check stays 5083/5083, rc=0.
- **N-B (G-02):** the correction asked to state which SHA `humanApproval.approvedCandidateCommit` holds. No command names that field.
- **N-C:** `gh pr create --body-file ...` has no `--title`. `gh` probably needs one when not interactive. NOT_RUN.
- **N-D:** a task touching `.env.example` is denied at `/ship-task` step 7, and at the fingerprint if the file is new. The result is `BLOCKED`, which fails closed, but step 5 warns only about staging.

## Findings-addressed rows

| Row | Result |
|---|---|
| R2-03 | PASS: all 12 parser forms deny for all 14, including assignment, `${IFS}`, backtick, brace, `$(...)` |
| R2-04 | PASS on the attack forms; over-broad, see V3-03 |
| R2-07 | PASS on all named forms and key types; sibling in V3-08 |
| R2-08 | PASS for claimed forms and npm; named `-Pevil` remains `allow` (V3-04) |
| R2-09 | PASS: all 6 forms and nested forms deny for all 14 |
| R2-10 | PASS: nested denies hold; listed paths `ask` for writers |
| R2-11, G-09 | PASS for what is claimed: mutations show the drift check fires (`git clone*` removal, catch-all, `git grep` removal); header states limits; see N-A |
| R2-12 | FAIL (partial): limitations table accurate; lines 46, 82, 84 false (V3-02, V3-04) |
| R2-13 | PASS: scanner forms `ask`; hazardous `make` forms deny; `make verify` and `make test` unchanged |
| R2-14 | PASS: unmasked form `ask`; `.env` and a second `--secretlintrc` deny for orchestrator |
| R2-15, R2-16 | FAIL (partial): denies and `ask` rules present, but "remainder disclosed" is false (V3-05) and ordering regressed (V3-02) |
| N-01 | PASS: three names disabled in `opencode.json` |
| G-01, G-08 | PASS: same result after staging, commit and record commits; failure means `BLOCKED`; shell recorded |
| G-02 | PASS: same head rule in `/verify-task` and `/close-task`; see N-B |
| G-03 | PASS |
| G-04 | PASS |
| G-05, R2-06 | PASS: present in `/ship-task`, README, evidence |
| G-06 | FAIL: V3-01 |
| G-07, G-10, R2-17 | PASS; see N-C |
| G-11 | FAIL (partial): bare `git branch` forms re-allowed, list incomplete (V3-03, V3-07) |
| G-12 | PASS: I1-IAM-002 `branch` null; live protection matches evidence |
| R2-02 (partial) | PASS: step 4 present; list tool `allow`, `ls` `ask`; `.git/hooks` has no non-sample file today |

## Regression walk (bash, both configurations, orchestrator)

No workflow-named command broke. Global and empty results were identical for every command below.
- **`allow`:**
  - Status and logs: `git status`, `git status --short`, `git status --short -uall`, `git branch --show-current`, `git log` forms.
  - Fingerprint: `git ls-files --others ...`, `git diff <sha> -- ... | git hash-object --stdin`, `git hash-object -- <paths>`.
  - Diffs: `git diff --stat`, `git diff --cached --name-only`, the `-G` search, `git diff --name-only <a> <b>`, `git diff <a> <b> -- delivery/status.yaml`.
  - Gate: the secretlint command, `node scripts/delivery/validate.mjs`.
  - History: `git rev-parse` including `^{commit}`, `git merge-base --is-ancestor`, `git show -s`, `git fetch origin`.
  - Task branches: `git push -u origin task/...` including closeout, `git switch task/...`.
  - GitHub: `gh pr view`, `gh pr list`, `gh pr checks`, `gh run list`, `gh run view`.
- **`ask`:** `git add -- <paths>`, `git commit -m ...`, `gh pr create ... --body-file`, `git switch main`, `git switch -c task/...`, `git checkout -b task/...`, `ls .git/hooks`, `git cat-file -e`.
- **Deny, as documented:** `git add -- infra/local/.env.example`.
- **Fingerprint (temp repo, Git Bash, `core.autocrlf=true`, CRLF, non-ASCII and spaced filenames):**
  - SAME on rerun, record changes, staging, commit, status-record commit and clean tree.
  - DIFFER on tracked change and new-file content change.
  - The only gap is V3-06.
- **Packet commands, rule evaluation only:**
  - `./mvnw -pl apps/bff -am test "-Dmaven.compiler.release=21"` and `.\mvnw.cmd -pl apps/bff -am test "-Dmaven.compiler.release=21"`: `allow` for coder, tester, reviewer, debugger and the three specialist reviewers; `ask` for orchestrator, planner, documentation, built-ins.
  - `npm run contracts:verify`: `allow` for orchestrator, coder, tester, reviewer, debugger, contract-reviewer, security-reviewer.
  - Maven and contracts execution: NOT_RUN, as instructed.

## Scope

All 34 changed files match `allowedFiles`, and none matches `prohibitedFiles`. My scope script had a regex escaping error and did not run, so this is a manual path comparison. Agent bodies are unchanged from HEAD (hash comparison), so agent edits are frontmatter only.

## Evidence accuracy

- **False or overstated:** evidence lines 46, 82, 84; 08 rows G-06 and R2-15/R2-16 (V3-01, V3-02, V3-04, V3-05).
- **"Needs the owner" list, correct:**
  - R2-01: `where bash` shows Git, then WSL `System32\bash.exe`. `git checkout -- .` and `./mvnw clean verify` are NOCHECK on PowerShell, deny on bash.
  - R2-02: `(git status) > opencode.json` is `allow` for 14.
  - R2-05: the `export` form is `allow` for 14 on bash.
  - F-07, G-04: 21 packets, 1 parse failure, 18 prohibit `status.yaml`, 2 allow, so 18 of 20 is right.
  - Commit mechanism, `--auto`, N-4: accurate; N-4 confirmed against `/review-task` and the packet.
- **Not independently checked:** whether `"shell"` is in OpenCode's schema (07 read it from code), and any live-session behaviour.
- **Also accurate:** the no-underscore tool limit (`foo` is `allow` for 13, deny for explore), and branch protection (PR required, 0 approvals, admins enforced, no force-push or deletion, no required checks).
- **Secretlint:**
  - `--maskSecrets` is valid. 34 changed files gave rc=0; 08's 33 predates that handoff file.
  - Positive control, run outside the repo with a scratch rc copy: a fake key and token gave rc=1 with values masked. Unmasked output printed the token. This closes round-2 note N-8.

## Commands and results

| Command | Result |
|---|---|
| `node scripts/delivery/check-agent-permissions.mjs` (global) | PASS: 5083/5083, 17 agents |
| Same with empty `XDG_CONFIG_HOME` | PASS: 5083/5083, 17 agents |
| `node scripts/delivery/validate.mjs` | PASS: rc=0 |
| `node scripts/delivery/self-test.mjs` | PASS: 8 passed, 0 failed |
| `opencode agent list` (via dump, both configurations) | PASS: 10 project plus 7 built-ins |
| YAML frontmatter (17 files), records, `opencode.json` | PASS: all parse |
| `opencode debug agent <name>` (`permission` array only, 17 agents, both configurations) | Saved to scratchpad `v3/` |
| Reused `r2` parser harness, plus evaluation on round-2 rules for comparison | See findings |
| `npm exec --no -- secretlint --maskSecrets ...` on 34 files | PASS: rc=0 |
| Secretlint positive control via the repo's installed binary, outside the repo | rc=1, values masked |
| Check copy with mutated rule input | Baseline PASS; `git grep`, catch-all and `git clone*` mutations FAIL as intended; skill mutation undetected (N-A) |
| Temp-repo fingerprint and `-G` tests | See findings |
| `gh api .../branches/main/protection` (GET), `gh help pr create` | Protection matches evidence; help output inconclusive for N-C |
| Live OpenCode session, end-to-end commands, CI | NOT_RUN |

**Disclosure:** in the repository I ran only read-only git inspection (`git status`, `git diff`, `git rev-parse`, `git show HEAD:<file>`, `git remote get-url`, `git branch --show-current`), some of which also appear as rule subjects. Add, commit and branch commands ran only in the scratchpad temp repo. The worktree still has the same 27 status lines on `task/i1-del-001-opencode-controls`.

Scratchpad: `<session scratchpad>\v3`

Files reviewed:
- `~\Desktop\Ev-chargind-station\opencode.json`
- `~\Desktop\Ev-chargind-station\.opencode\agents\*.md`
- `~\Desktop\Ev-chargind-station\.opencode\commands\*.md`
- `~\Desktop\Ev-chargind-station\scripts\delivery\check-agent-permissions.mjs`
- `~\Desktop\Ev-chargind-station\delivery\README.md`
- `~\Desktop\Ev-chargind-station\delivery\status.yaml`
- `~\Desktop\Ev-chargind-station\delivery\tasks\I1-DEL-001.yaml`
- `~\Desktop\Ev-chargind-station\delivery\evidence\I1-DEL-001\agent-permissions.md`
- `~\Desktop\Ev-chargind-station\delivery\handoffs\I1-DEL-001\06-reviewer-round2.md`
- `~\Desktop\Ev-chargind-station\delivery\handoffs\I1-DEL-001\07-security-reviewer-round2.md`
- `~\Desktop\Ev-chargind-station\delivery\handoffs\I1-DEL-001\08-coder-round3.md`
