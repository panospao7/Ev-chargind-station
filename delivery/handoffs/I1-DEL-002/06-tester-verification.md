---
role: tester
taskId: I1-DEL-002
mode: verification-only
candidateCommit: fd6180ba67d54129ba96a3eeb26726a862d11394
pullRequestHead: 202d954002af84a951febbb8b67c80d7baa52d36
baselineCommit: e02db33e82eb0c30788cd9d12c8374bd4891e255
impactLevel: L2
date: 2026-11-04
verdict: PASS_NO_BLOCKERS
---

# I1-DEL-002 - Independent verification

**Verdict: PASS_NO_BLOCKERS.** All four acceptance criteria pass on the commands I ran. I found no BLOCKER, MAJOR or MINOR findings, and four NOTEs. Nothing contradicts a result in the evidence file. Live results in that file remain author-reported, because live model calls were out of scope.

**Independence disclosure:** I am a Claude Code subagent (Opus 5.5), the same model as the author, so I am independent by role only.

## AGENTS.md §14 handoff

```text
Task ID: I1-DEL-002
Role: Independent tester, verification-only
Task state: CI_PENDING as recorded in delivery/status.yaml; unchanged by me
Baseline commit: e02db33e82eb0c30788cd9d12c8374bd4891e255
Impact level: L2 (as packeted; owner to confirm)
Documents and sections read: AGENTS.md (provided in full, including §8, §11, §14, §15);
  delivery/tasks/I1-DEL-002.yaml; delivery/evidence/I1-DEL-002/agent-models.md and review-fingerprint.md;
  delivery/handoffs/I1-DEL-002/01 to 05 and pr-body.md (long lines of 02 and 04 were cut at 260 characters);
  git diff of opencode.json, .opencode/agents, review-task.md, delivery/README.md and the check script;
  the I1-DEL-002 entry in delivery/status.yaml; trigger and secret-scan sections of
  .github/workflows/g3-contract-validation.yml; package.json
Files changed: None in the repository. Temporary files are in the session scratchpad only
Commands executed: See the commands-and-results table
Test results: PASS for AC-01 to AC-04, validators, scope and worktree; NOT_RUN as listed under "Not checked"
Acceptance criteria status: AC-01 PASS, AC-02 PASS, AC-03 PASS, AC-04 PASS
Decisions made: None
Assumptions: Unsetting nothing beyond XDG_CONFIG_HOME isolates the global config. No OPENCODE_* variable is set, no
  ~/.opencode directory exists, and m7 below shows the owner's global override disappears under the empty directory
Findings and residual risks: 4 NOTEs. Residual: no /review-task has run end to end, and the model check runs locally only, not in CI
Blockers: None
Recommended next agent: Orchestrator, to record this handoff and the CI result, then the Project Owner for human review
```

## Acceptance criteria

| AC | Result | Basis |
|---|---|---|
| AC-01 resolved models, with and without global config | **PASS** | 14 of 14 agents match, 0 mismatches, in both configurations. These are configured names only: under the empty config the provider itself does not resolve (NOTE N1) |
| AC-02 check fails on drift, permission results unchanged | **PASS** | 5187/5187 and exit 0 in both configurations. Every mutation below exits 1 and leaves 5187/5187. The script diff adds only the model block and does not touch the expectations list |
| AC-03 one-at-a-time rule | **PASS** | `orchestrator.md:541`, `review-task.md:68`, `delivery/README.md:88` |
| AC-04 no provider key or secret | **PASS** | secretlint on all 25 changed files exits 0, and a positive control proves the scan detects a key. Pattern and entropy searches found no key shape. A wider scan found only false positives outside this diff (NOTE N4) |

### AC-01 detail

Filtered to `model` and `variant` only. Results were identical with the owner's config and with `XDG_CONFIG_HOME` set to an empty scratch directory.

| Agents | Resolved |
|---|---|
| `orchestrator`, `coder`, `debugger`, `documentation`, `build`, `plan`, `general`, `explore` | `zai/glm-5.3-flash max` |
| `planner`, `tester`, `reviewer`, `security-reviewer`, `data-reviewer`, `contract-reviewer` | `zai/gemini-4-argon high` |

### AC-02 mutations

Scratch copies made with `git archive HEAD`, each initialised as its own git repository. The repository was not touched.

| Copy | Mutation | Configuration | Node exit | Permission count | Output |
|---|---|---|---|---|---|
| base | None | owner, empty | 0, 0 | 5187/5187 | `ALL CHECKS PASSED` |
| m1 | `reviewer.md` moved to the writer model | owner, empty | 1, 1 | 5187/5187 | `FAIL reviewer model: zai/glm-5.3-flash high` |
| m2 | `coder.md` variant `max` to `high` | owner | 1 | 5187/5187 | `FAIL coder model: zai/glm-5.3-flash high` |
| m3 | `build` moved to `merge-gateway` in `opencode.json` | owner | 1 | 5187/5187 | `FAIL build model: merge-gateway/glm-5.3-flash max` |
| m4 | `small_model` moved to `merge-gateway` | owner | 1 | 5187/5187 | `FAIL opencode.json small_model must be zai/glm-5.3-flash, found: merge-gateway/glm-5.3-flash` |
| m5 | `tester.md` model and variant deleted | owner | 1 | 5187/5187 | `FAIL tester model: undefined/undefined undefined` |
| m6 | Added `.opencode/opencode.json` with `small_model` | owner | 1 | 5187/5187 | `FAIL unexpected project config file: .opencode/opencode.json` |
| m8 | `title` pinned in `opencode.json` | owner | 1 | 5187/5187 | `FAIL title pins its own model: merge-gateway/glm-5.3-flash` |

Boundary case, without running the check (m7): with `orchestrator.md` frontmatter pins removed, `opencode debug agent orchestrator` gives `merge-gateway/glm-5.3-flash max` under the owner's config and `undefined/undefined undefined` under the empty one. This reproduces the evidence's reason for setting models in the agent files.

## Commands and results

| Command | Result |
|---|---|
| `git status --short`, before and after all work | PASS: empty both times; HEAD `202d954`, branch unchanged |
| `opencode --version` | 1.19.0, matches the packet |
| `opencode debug agent <name>`, 14 agents, filtered with node, owner's config | PASS: 0 mismatches |
| Same with `XDG_CONFIG_HOME` empty | PASS: 0 mismatches |
| `node scripts/delivery/check-agent-permissions.mjs`, owner's config | PASS: 5187/5187, 17 agents listed, exit 0 |
| Same with `XDG_CONFIG_HOME` empty | PASS: 5187/5187, 17 agents listed, exit 0 |
| Mutations in scratch copies | PASS: every mutation fails as intended, table above |
| `node scripts/delivery/validate.mjs` | PASS: exit 0, `ALL CHECKS PASSED` |
| `node scripts/delivery/self-test.mjs` | PASS: exit 0, 8 passed, 0 failed |
| `grep` for the rule in the three files | PASS: one line in each. No file in `.opencode/agents` or `.opencode/commands` instructs parallel work |
| `grep '^agent: orchestrator' .opencode/commands/*.md` | PASS: all 7 commands run under the orchestrator, so its rule covers them. All other project agents set `task: deny` |
| Scope script: `git diff --name-only e02db33 HEAD` against packet globs parsed from YAML | PASS: 25 files, 0 outside `allowedFiles`, 0 in `prohibitedFiles` |
| `npm exec --no -- secretlint --maskSecrets --secretlintrc scripts/contracts/secretlintrc.json <25 files>` | PASS: exit 0, no output |
| secretlint on a scratch copy of the same 25 files plus one control file holding the fake AWS-format key from `scripts/contracts/self-test.mjs` | PASS: exit 1, 26 files reported, only the control flagged |
| Key-shape search of the 25 files, counts only (`sk-`, Bearer, AWS, GitHub, Google, PEM, JWT, hex.dot keys, key assignments) | PASS: 0 matches for each |
| Entropy search of the 25 files, location and length only | PASS: 0 high-entropy mixed-case tokens, 0 32-hex tokens. 61 distinct 40-hex tokens: 56 are local git objects. The other 5 appear only in labelled hash rows and `baselineCommit` fields |
| `git diff fd6180b 202d954` | PASS: only `delivery/status.yaml`, setting `candidateCommit` to `fd6180b...` |
| `gh pr view 54 --json headRefOid,state,isDraft,statusCheckRollup,mergeable,baseRefName` | PASS: head `202d954`, open, not draft, mergeable, 9 of 9 checks SUCCESS |
| `gh run view 41844652884` | PASS: `G3 Contract Validation`, `pull_request`, `headSha` `202d954`, success |
| `opencode models zai`, filtered to 3 exact IDs, owner's config | Lists `zai/gemini-4-argon`, `zai/glm-5.3-flash` and `zai/glm-5.3-flashx` (NOTE N2) |
| `opencode models zai`, empty config | `Provider not found: zai`, as the evidence states |
| Whole-tree `secretlint ... --format=json "**/*"`, an extra check because AC-04 says "repository" | Exit 1: 622 files reported, 7 flagged, none in this diff (NOTE N4). Rule IDs and paths only were printed |

### Harness corrections

- My first mutation run printed `exit=0` for every copy. That was a bug in my harness: `$?` read the exit of `basename` in the same `echo`. Its failure lines were correct. I re-ran all of them capturing Node's exit directly, and the table reports only that re-run.
- A Claude Code safety check blocked my first harness, which used `rm -rf` on a scratch subdirectory. That command did not run and nothing was removed. I used new directories instead, so the scratch copies remain in the session scratchpad. They include the fake control key, and you can delete them whenever you like.
- Two early scope-script attempts failed with syntax errors of mine, before producing any result. The scope result above is from the corrected script.

### CI comparison

CI is green on the PR head, and my results do not conflict with it. However, the two overlap on none of the acceptance criteria (NOTE N3).

## Findings

No BLOCKER, MAJOR or MINOR findings.

### N1 - NOTE: the empty-config PASS is for names only

- **File:** `delivery/evidence/I1-DEL-002/agent-models.md`, "Provider dependency".
- **Reproduce:** with `XDG_CONFIG_HOME` empty, `opencode debug agent reviewer` shows `zai/gemini-4-argon high`, while `opencode models zai` prints `Provider not found: zai`.
- **Effect:** the evidence already discloses this and it is accurate. AC-01 holds as written, but it does not show that the agents can run on another machine.

### N2 - NOTE: `zai/glm-5.3-flashx` is listed, though the evidence reports it as not found

- **File:** `delivery/evidence/I1-DEL-002/agent-models.md:19`, `:42`; `delivery/tasks/I1-DEL-002.yaml:231`.
- **Reproduce:** `opencode models zai | grep -E '^zai/glm-5\.3-flashx$'` under the owner's config prints that ID.
- **Effect:** this does not contradict the evidence. A listing is not a successful call, and the reported failure came from a live call, which I did not repeat. The evidence does not mention the listing, so a reader could wrongly conclude the ID is absent. If the owner still wants `flashx`, the listing is a reason to ask `zai` why it is rejected.

### N3 - NOTE: green CI does not evidence this task's acceptance criteria

- **File:** `.github/workflows/g3-contract-validation.yml`, `package.json` (`contracts:secrets`).
- **Reproduce:** `grep -c delivery .github/workflows/*.yml` returns 0 for all four workflows. `contracts:secrets` scans only `contracts/**/*.{yaml,json}`.
- **Effect:** the "Secret Scanning" job did not scan any file in this diff, and the model and permission check runs only when someone runs it locally. Drift merged without that run would not be caught by CI. This matches the packet's `requiredCiChecks: []`, and `02-reviewer.md` already noted that no workflow references the check.

### N4 - NOTE: whole-tree secretlint flags 7 files, none a real secret on what I inspected, all outside this diff

- **Files:** `scripts/contracts/self-test.mjs`; `apps/bff/src/main/java/com/evplatform/bff/security/ClientKeyConfig.java`; five test files under `apps/bff/src/test/java/com/evplatform/bff/`.
- **Reproduce:** `npm exec --no -- secretlint --maskSecrets --secretlintrc scripts/contracts/secretlintrc.json --format=json "**/*"`.
- **What I found:**
  - `self-test.mjs` holds the deliberately fake AWS-format key for its failure-injection test.
  - `ClientKeyConfig.java` matches only on PEM markers in comments and parsing code.
  - `BffApplicationTests.java` builds the PEM from a key generated at runtime; I read that code directly.
  - The other four test files have the same shape: two marker lines and a "body" of only 72 to 78 characters, which is too short to be a key. I checked their masked shape, not their full code.
- **Effect:** none on AC-04. A future whole-repository scan would need an allowlist for these.

### Disagreements with the evidence or records

- **Evidence file:** none. Every result I could reproduce matches, including the exact failure strings and `Provider not found: zai`.
- **`delivery/status.yaml:934`, `:955`, `:956`, `:948`, stale at the PR head:**
  - It shows `pullRequest: null`, `ci.status: PENDING` and `candidateRunReference: null`, although PR #54 is open and its CI run has finished.
  - `generalReviewer` still points to `02-reviewer.md` as `PASS_WITH_FINDINGS`, whereas that handoff's verdict is `PASS_NO_BLOCKERS` and the latest review is `05-reviewer-final.md`.
  - `validate.mjs` passes regardless. I did not check whether earlier tasks update these fields only after this stage, so I raise it as an observation for the orchestrator, not a finding.

## Not checked

- **Live model calls, prohibited for this role (NOT_RUN):**
  - all `opencode run` results in the evidence;
  - the orchestrator-to-`reviewer` run and its session reference;
  - the rejection of `glm-5.3-flashx`;
  - both concurrency tests;
  - the packet's positive and concurrency test cases.
  All remain author-reported.
- **`opencode models --verbose` (NOT_RUN):** its output can draw on the provider definition, so I did not run it. The claim that `max` and `high` are each model's highest setting is not verified by me.
- **Thinking setting (NOT_RUN):** whether the provider applies it.
- **`/review-task` (NOT_RUN):** no full run, and no project subagent run.
- **Permission rules:** I did not compare resolved rules against the baseline, as `02` and `04` did. My basis is that the diff touches no permission line, the expectations list is unchanged, and the count stays 5187/5187.
- **Fingerprint:** not recomputed from `review-fingerprint.md`.
- **Other config sources:** `OPENCODE_CONFIG` as a source. It is not set in this environment, and the evidence lists it as not covered.
- **Mutations in the empty configuration:** only base and m1 ran in both configurations; m2 to m6 and m8 ran under the owner's config only. The reviewer's `04` table covers similar cases in both.
- **Secret scan coverage:**
  - git history was not scanned, apart from the one-line diff between the candidate and the PR head;
  - the whole-tree scan's 622 files were not compared with `git ls-files`, so dotfile coverage there is unconfirmed (the 25 changed files were passed by name and are covered);
  - no scan proves the absence of a key in an unusual format.
- **Owner's global config:** I read no provider block, auth file or key, and no matched value was printed. `opencode` itself loads that config when it runs under the owner's setup.
- **Owner decisions, not settled by this check:**
  - L2 with no security review;
  - the writer and reviewer split;
  - whether the `zai` login is the key meant;
  - ARC-AI-OQ-01;
  - the disclosed `SELF_VERIFIED` gap.
