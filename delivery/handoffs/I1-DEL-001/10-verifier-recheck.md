---
role: reviewer
taskId: I1-DEL-001
baselineCommit: 811b81ed39ca954217cf785f5f9c3135f14b2263
impactLevel: L3
date: 2026-11-04
---

# I1-DEL-001 re-check of V3-01 to V3-08 and N-A to N-D

**Result:** 11 of 12 items pass. V3-07 is partly open at MINOR, because the skip denies are not on an owner-decision list. There is no BLOCKER or MAJOR, and the former MAJOR (V3-01) is closed. The correction caused no regression in the workflow-named commands.

Rule results come from freshly dumped `permission` arrays, through the same parser harness and matcher as before, on bash. "Both" means the global and empty configurations gave the same result.

## Item results

| Item | Result | Evidence |
|---|---|---|
| V3-01 | PASS | Temp repo, exact `ship-task.md` line 45 text: lists `privateKey:`, `"apiKey":`, `API_KEY=`, `api-key:`, `clientSecret:`, `client-secret:`, `{ "password":`, `token=`, `private_key =`; excludes the clean file. The check's line 174 uses the same regex. Both BFF files from the original finding now match the regex. The new form is `allow` for all 14, both. |
| V3-02 | PASS | See table below. The forms are in the check (lines 108-109, 215). |
| V3-03 | PASS | `git grep -n Booking`, `-i token`, `-l account`, `foo -- docs/`, `-n Booking -- docs/`, `-n main`, `foo` are now `ask` for all 14, both. Evidence line 88 discloses this. R2-04's named forms remain deny for all 14 (`--open=`, `--open-files=`, `-nOevil`, `-O evil`). See R-1. |
| V3-04 | PASS | `./mvnw test -Pevil`, `./mvnw test -P evil`, `.\mvnw.cmd -pl x -am test -Pintegration` still `allow` for the 7 Maven agents, both. This now matches the evidence: line 78 discloses it, and the false "profile flags" wording is gone. See R-2. |
| V3-05 | PASS | Evidence line 79 discloses `git commit -m x .` and `git add -- delivery` (reproduced: `ask` for orchestrator, deny for 13, both). Line 80 discloses the global-only names (reproduced: `git log-x`, `git statusx`, `git rev-parsex`, `git ls-filesx` `allow` for orchestrator and debugger under global, `ask` under empty). |
| V3-06 | PASS | `ship-task.md` precondition 5 now compares the live list. Temp repo: a late untracked file gives NEW-PATH (`scripts/late.mjs`). No false alarm on record changes, a new handoff, after staging, or after commit. |
| V3-07 | FAIL (partial), MINOR | Listing part passes: evidence lines 87 and 89 list the forms, and each reproduces as stated. Owner part is open, see finding below. |
| V3-08 | PASS | `git diff $HOME/.ssh/id_rsa NUL`, `git diff NUL $HOME/.ssh/id_rsa`, the quoted and `${HOME}` forms, and `git diff $env:USERPROFILE/.ssh/id_rsa NUL` are deny for all 14, both. The PowerShell form is also deny under the PowerShell grammar. |
| N-A | PASS | The check now has skill expectations (lines 211-213). The same mutation, `skill * allow` on data-reviewer, now fails: rc=1, 5186/5187, `data-reviewer skill "task-packet": expected deny, got allow`. |
| N-B | PASS | `close-task.md` line 60 records the candidate commit as `humanApproval.approvedCandidateCommit`. |
| N-C | PASS | `ship-task.md` line 63 includes `--title`. The form resolves `ask` for all 14, both. The check covers it (line 179). Real `gh` call NOT_RUN. |
| N-D | PASS | `ship-task.md` step 5 now stops with `BLOCKED` for `.env.example`, covering staging and scanning. Reproduced: `git add` deny for 14, the secretlint form deny for orchestrator, `git hash-object` and the fingerprint exclude form deny for 14. See R-3. |

### V3-02 reproduction

| Command | Round 3 | Now (both) |
|---|---|---|
| `git diff-tree --output=AGENTS.md HEAD` | ask 14 | deny 14 |
| `git diff-files --ext-diff` | ask 14 | deny 14 |
| `git show-ref > AGENTS.md` | ask 11, deny 3 | deny 14 |
| `git diff-tree HEAD > opencode.json` | ask 11, deny 3 | deny 14 |
| `git diff-index -p HEAD -- .env` | ask 13, deny 1 | deny 14 |
| `git diff-tree -p HEAD -- secrets/x` | ask 13, deny 1 | deny 14 |
| `git show-ref`, `git show-branch`, `git diff-tree HEAD`, `git diff-index HEAD` | ask 14 | ask 14 |

Rule order: `git show-*` and `git diff-*` now sit before the shared denies in every agent. After the last `*>*`, the only non-deny rules are five exact `git branch` allows (11 agents, including built-ins) or none (orchestrator, data-reviewer, security-reviewer). None can contain `>`, so evidence lines 46 and 85 now hold at string level.

## Finding

### V3-07 MINOR (open part): the skip denies are not on an owner-decision list
- **Location:** `delivery/handoffs/I1-DEL-001/08-coder-round3.md` "Not fixed; needs the owner"; `delivery/status.yaml` I1-DEL-001 `unresolvedFindings` ("Owner decisions pending").
- **Reproduction:** searching both files for `skip` finds no owner-decision entry. The only hits are the R2-08 row ("test-skip forms denied") and "PowerShell skips checks". Evidence line 87 says "a choice the owner may reverse", which is accurate but is not a decision request.
- **Related claim:** the 08 correction section says all findings "were corrected". That overstates this one item.
- **Forms reproduced:** `./mvnw -q -DskipTests package`, `./mvnw -pl x test -DskipTests`, `./mvnw -pl x test -Dmaven.test.skip=true` are deny for all 14, both.
- **Required correction:** add one line to the pending owner decisions. The denies themselves can stay, since they fail closed.

## Residual notes from the corrections

Not findings against the corrections, and none blocks.
- **R-1:** narrowing the deny to `-?O*` means a longer cluster such as `git grep -inOevil x` is now `ask`, not deny. A human prompt applies, and `git grep -n -i -Oevil x` and `git grep x -nOevil` stay deny. This is the trade-off for removing the V3-03 false positives.
- **R-2:** evidence line 78 says "Accepted" for Maven profiles. AGENTS.md §6 reserves risk acceptance to a human, and I found no recorded owner acceptance. Line 75 reads similarly ("Accepted by design"). Consider "Owner: accept" under "Who can resolve it".
- **R-3:** `ship-task.md` step 6 still says "`.env.example` is the one exception", though step 5 now stops first. It is harmless.
- **R-4:** the new `git diff *$*` rule also denies `git diff $BASE`. The workflow commands use no shell variables, and it fails closed. It is not in the false-positive list.

## Regression check

I compared 192 listed command forms between the round 3 rules and the current rules, through the parser, both configurations. These were the earlier attack, workflow and probe lists, some forms repeated across lists, plus the three added below. The only differences are the `git grep` lines, the `git diff-*` and `git show-*` lines, and the two `$HOME` lines, all as described above.

- **Workflow-named commands:** `/review-task`, `/ship-task`, `/verify-task` and `/close-task` resolve exactly as in my first report (orchestrator `allow` or `ask`, listed there).
- **Added to the walk:** the new `-G` form (`allow`), `git add -- delivery/status.yaml` (`ask`), `git commit -m "I1-DEL-001: record candidate commit"` (`ask`).
- **Packet commands, rule evaluation only:** `./mvnw -pl apps/bff -am test "-Dmaven.compiler.release=21"` and `.\mvnw.cmd -pl apps/bff -am test "-Dmaven.compiler.release=21"` are `allow` for coder, tester, reviewer, debugger and the three specialist reviewers. `npm run contracts:verify` is `allow` for orchestrator, coder, tester, reviewer, debugger, contract-reviewer, security-reviewer. Execution NOT_RUN, as instructed.
- **Fingerprint (temp repo, Git Bash, `core.autocrlf=true`, CRLF, non-ASCII and spaced filenames):**
  - SAME when unchanged, on record changes, after staging, after commit, after the status commit and on a clean tree.
  - HASH-DIFFER on a tracked change and on an unreviewed committed file.
  - The `/review-task` commands read as before.

## Commands and results

| Command | Result |
|---|---|
| `node scripts/delivery/check-agent-permissions.mjs` (global) | PASS: rc=0, 5187/5187, 17 agents |
| Same with empty `XDG_CONFIG_HOME` | PASS: rc=0, 5187/5187, 17 agents |
| `node scripts/delivery/validate.mjs` | PASS: rc=0 |
| `node scripts/delivery/self-test.mjs` | PASS: rc=0, 8 passed, 0 failed |
| `opencode debug agent <name>` (`permission` array only, 17 agents, both configurations) | Saved to `<session scratchpad>\v4` |
| `opencode agent list` (via dump, both configurations) | 10 project plus 7 built-ins |
| Parser harness on the listed forms; comparison with round 3 rules | As above |
| Temp-repo fingerprint and `-G` tests | As above |
| Copy of the check with mutated rule input | No mutation: rc=0. Skill: rc=1. `git clone*` removal: rc=1, drift check fires. `git grep -*O*` removal: rc=0, a no-op because that pattern no longer exists |
| `secretlint` on changed files, live session, end-to-end commands, CI | NOT_RUN (not requested, or not possible here) |

**Scope of worktree change seen:** the only path added since my first report's list is `delivery/handoffs/I1-DEL-001/09-verifier-round3.md`, plus this file. Both are inside `allowedFiles`. In the repository I ran only read-only inspection and this one file write. Add and commit commands ran only in scratchpad temp repositories.

**Required human decision:** whether to keep the `-DskipTests` and `-Dmaven.test.skip` denies.

**Recommended next step:** add the V3-07 line, which needs no further verification, then continue to the owner decisions.

VERIFICATION_STATUS: PASS_NO_BLOCKERS
