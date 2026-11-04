---
role: reviewer
taskId: I1-DEL-002
baselineCommit: e02db33e82eb0c30788cd9d12c8374bd4891e255
impactLevel: L2
date: 2026-11-04
---

# I1-DEL-002 - Independent review

**Overall verdict: PASS_NO_BLOCKERS.** There are no BLOCKER or MAJOR findings, and all four acceptance criteria hold as written. There are 7 MINOR findings. Two of them, F1 and F2, should be read before merge, because the checks stay green in situations a reader of the evidence would expect them to catch.

Local temp paths are shown as `<session scratchpad>`.

## Results by check

| # | Check | Result |
|---|---|---|
| 1 | Scope, agent-file changes, permission rules | PASS |
| 2 | Resolved model and variant for 14 agents, both configurations | PASS |
| 3 | Permission check, validator, self-test, non-vacuous assertion | PASS for the 14 agents; gaps outside them in F1 |
| 4 | merge-gateway reachability; 15 disabled names | PASS by configuration and static reading only; live routing NOT_RUN; `summary` unresolved |
| 5 | New rules read correctly and contradict nothing | PASS, with wording issues in F3 |
| 6 | Writer and reviewer split | PASS for production code; does not hold for tester-written tests; F4 |
| 7 | Secret scan | PASS |
| 8 | Evidence accuracy | PASS for what I could reproduce; overclaims in F2, F3, F4, F5; live results NOT_RUN |

### 1. Scope: PASS

- All 19 changed or new paths match `allowedFiles`, and none matches `prohibitedFiles`. HEAD is the stated baseline.
- Agent-file diff against the baseline: 10 `model:` lines (6 Argon, 4 Flash), 10 `variant:` lines (6 `high`, 4 `max`), one rule and one blank line in `orchestrator.md:541`. No line was removed.
- `opencode.json` only gains root `model`, root `small_model` and the 4 built-in model blocks.
- Resolved permission rules were compared for 17 agents between a baseline extract (`git archive`) and a worktree copy:
  - Owner's global config: 9924 rules compared in order, 0 differences.
  - Empty config: 9906 rules compared in order, 0 differences.
  - 306 `external_directory` skill-path rules were compared as a set, 0 differences. Their order changes between runs, so an ordered comparison is not meaningful for them. This is unrelated to the task.
- Prompts are identical for 16 of the 17 agents. `orchestrator` differs, as expected from the new rule. `steps` and `temperature` are unchanged.

### 2. Resolved models: PASS

All 14 agents match AC-01 in both configurations (0 mismatches each). Only model and variant were kept from the output.

The empty configuration really was isolated. `orchestrator` and `debugger` lost the `color` key and 9 permission rules from the global files (837 to 828, and 812 to 803), and `opencode debug paths` showed the scratch directory as `config`.

A correct string does not mean the agent can run without the global config; see F2.

### 3. Checks and mutations: PASS for the 14 agents

| Command | Configuration | Result |
|---|---|---|
| `node scripts/delivery/check-agent-permissions.mjs` | global | PASS, 5187/5187, 17 agents, exit 0 |
| same | empty | PASS, 5187/5187, 17 agents, exit 0 |
| `node scripts/delivery/validate.mjs` | n/a | PASS, exit 0 |
| `node scripts/delivery/self-test.mjs` | n/a | PASS, 8 of 8, exit 0 |

Scratch-copy mutations, with no repository change. M0 is an unmutated copy and passes in both configurations.

| Mutation | Configuration | Exit | Output |
|---|---|---|---|
| M1 `reviewer.md` variant removed | empty | 1 | `FAIL reviewer model: zai/gemini-4-argon undefined` |
| M2 `reviewer.md` model removed | empty | 1 | `FAIL reviewer model: undefined/undefined high` |
| M3 `reviewer.md` both removed | empty | 1 | `FAIL reviewer model: undefined/undefined undefined` |
| M4 `reviewer.md` moved to the writer model | empty | 1 | `FAIL reviewer model: zai/glm-5.3-flash high` |
| M5 `tester.md` `high` changed to `max` | empty | 1 | `FAIL tester model: zai/gemini-4-argon max` |
| M6 `general` block removed | empty | 1 | `FAIL general model: undefined/undefined undefined` |
| M7 `explore` variant removed | empty | 1 | `FAIL explore model: zai/glm-5.3-flash undefined` |
| M8 `orchestrator.md`, `debugger.md` model and variant removed | global | 1 | Both `FAIL ... merge-gateway/glm-5.3-flash max` |
| **M9** root `model` and `small_model` removed | both | **0** | `ALL CHECKS PASSED` |
| **M10** `small_model` set to `merge-gateway/glm-5.3-flash` | both | **0** | `ALL CHECKS PASSED` |
| **M11** new project agent with no model, added to `PROJECT_AGENTS` and the orchestrator's `task` rules | empty | **0** | `5523/5523`, `ALL CHECKS PASSED`, agent resolves to `undefined/undefined undefined` |

M1 to M8 show the assertion cannot pass vacuously for the 14 agents. A missing field becomes the text `undefined`, which never equals the expected value, and a failed `debug agent` throws. M8 also reproduces the evidence's reason for using frontmatter (`agent-models.md:20-21`). M9 to M11 are F1.

`self-test.mjs` covers only `validate.mjs`, and `check-agent-permissions.mjs` is referenced by no workflow or script, only by `orchestrator.md:123` and the README. Drift is therefore caught only when someone runs the check, as `delivery/README.md:85` instructs.

### 4. merge-gateway: PASS by configuration and static reading only

No model calls were made, and no provider block or auth file was read.

**Selectable agents.** All 14 resolve to `zai` in both configurations. The 17 listed agents are exactly the 10 project agents, 4 checked built-ins and 3 hidden built-ins.

**The 15 disabled names** are absent from `opencode agent list` and exit 1 from `opencode debug agent` in both configurations: PASS.

**Hidden built-ins.** `opencode debug agent title`, `summary` and `compaction` show no model in either configuration, so the owner's global config does not pin them to merge-gateway. What they then use comes from a string search of the OpenCode 1.19.0 binary, not from observed calls:

| Agent | Selection found in the binary | Effect today |
|---|---|---|
| `title` | Agent model, else `getSmallModel` (`small_model`), else the session's model | `zai/glm-5.3-flash` |
| `compaction` | Agent model, else the session's user-message model | The session's model, so `zai` for any pinned agent |
| `summary` | Defined with no model. The literal `get("summary")` appears 0 times | Unresolved: I could not establish that it makes a call at all |

**Root precedence**, tested with a synthetic global config in scratch (not the owner's): project `model` and `small_model` beat global values. The same test shows a global `agent.title.model` would pin `title` and win, which is not the case today but is not checked (F1).

**Remaining routes to merge-gateway:**
- `opencode.json` sets neither `enabled_providers` nor `disabled_providers`, and `opencode models` still lists merge-gateway models (count 208 with the owner's config, 207 with the empty one). An explicit `--model` or a model picker choice is therefore not blocked by this repository. I did not test this, because it would need a model call. It is outside the packet.
- M10: an edit to `small_model` would move `title`, which carries conversation content, without the check noticing (F1).

### 5. New rules: PASS

- `orchestrator.md:541`, `review-task.md:68` and `delivery/README.md:86-87` read correctly and agree on the limit of 2.
- All 7 commands set `agent: orchestrator`, so the orchestrator rule covers them, and every other project agent has `task: deny`.
- `implement-task`, `plan-task` and `verify-task` invoke subagents in sequence. No file under `.opencode/` tells anyone to run in parallel.
- `docs/08_delivery_and_ai_agents/03_ai_agent_rules_review_gates_v1.0.md` §24 and ARC-AI-15 are conditional ("when multiple agents work concurrently"), so they do not conflict.
- No file under `.opencode/` names merge-gateway. Outside the task records, `flashx` appears nowhere.

### 6. Writer and reviewer split: PASS for production code

| Author | Model | Writes | Read by |
|---|---|---|---|
| `coder`, `debugger` | Flash | Production code and tests | Argon: holds |
| `documentation` | Flash | Docs and records | Argon: holds |
| `tester` | Argon | `tests/**`, `**/src/test/**`, `**/src/testFixtures/**`, `**/e2e/**`, `contracts/tests/**`; `contracts/examples/**` on prompt | Argon `tester` and reviewers: **does not hold** |
| `planner` | Argon | Plans, returned to the orchestrator | Argon reviewers receive them: **does not hold** |

- The five reviewing and planning agents have `edit: deny`, and `tester` is denied production paths. `delivery/README.md:86` ("the model that wrote production code does not review it") is therefore accurate.
- `review-task.md:61` sends a task back for a new review if the tester adds a test, so tester-written tests do reach Argon reviewers.
- `agent-models.md:61` is half right; see F4.

### 7. Secret scan: PASS

`npm exec --no -- secretlint --maskSecrets --secretlintrc scripts/contracts/secretlintrc.json` on all 19 paths exited 0 with no findings. I also searched those files for common key shapes, counting matches only: 0. Neither search proves a key in an unusual format is absent, since `ship-task.md:41` notes the tool catches provider tokens and private keys, not generic passwords. I read every changed and new file and saw no key.

### 8. Evidence accuracy

Reproduced: the 5187/5187 results, the resolved models, the reviewer mutation, the frontmatter reason, and root `model` and `small_model` being set.

NOT_RUN, as instructed: all live results (`agent-models.md:33-35`, `:43-44`). I also did not verify that `high` is Argon's highest setting; see F6.

## Findings

No BLOCKER or MAJOR findings.

### F1 - MINOR: the drift check does not cover root models, hidden agents or newly added agents

- **Where:** `scripts/delivery/check-agent-permissions.mjs:344-349`; `opencode.json:5-6`; `delivery/evidence/I1-DEL-002/agent-models.md:12`.
- **Reproduce:** in a scratch copy, set `small_model` to `merge-gateway/glm-5.3-flash` (M10), or delete `model` and `small_model` (M9). The check exits 0. For M11, add an agent with no model to `PROJECT_AGENTS` and the orchestrator's `task` rules; it also exits 0, because `EXPECTED_MODELS` is a separate list with no coverage check.
- **Why it matters:** the evidence lists root `model` and `small_model` as part of the assignment, and the packet's threat note (`I1-DEL-002.yaml:197`) is about content leaving merge-gateway. `small_model` is what keeps `title` on `zai`.
- **Why not MAJOR:** nothing is wrong today, and AC-01 and AC-02 name agents' resolved models, which are covered for the 14. The owner may reasonably rate it higher.
- **Suggested fix:** assert both root values, assert `title`, `summary` and `compaction` have no pinned model, and fail if any `PROJECT_AGENTS` entry is missing from `EXPECTED_MODELS`.

### F2 - MINOR: the "empty config" PASS does not mean the agents can run without the global config

- **Where:** `agent-models.md:31`; `I1-DEL-002.yaml:79`, `:128`.
- **Reproduce:** with `XDG_CONFIG_HOME` set to an empty directory, `opencode models zai` prints `Provider not found: zai` and exits 1. With the owner's config it lists `zai/glm-5.3-flash` and `zai/gemini-4-argon`.
- **Effect:** `opencode.json` defines no `zai` provider, so with the global config absent, the pinned agents name a provider that does not exist. The data directory was unchanged in that run, which points to the global config as the source of `zai`. I did not open it to confirm.
- **Why it matters:** the check compares strings. AC-01 holds as written, but the evidence and packet mention only "credentials" and do not disclose this dependency. It would affect a clean machine or another contributor.
- **Suggested fix:** state it in the evidence and the README.

### F3 - MINOR: the README attributes the split to the owner, but the repository records say otherwise

- **Where:** `delivery/README.md:86` ("The owner chose this for now"); `I1-DEL-002.yaml:229`, `:231`.
- **Reproduce:** read both. The packet records the owner choosing provider, thinking level and concurrency limit, and says "The orchestrator kept the split".
- **Why it matters:** repository artifacts outrank chat (AGENTS.md §2), and only a human can approve decisions. The sentence presents an orchestrator judgement as an owner decision.
- **Suggested fix:** reword it, or have the owner confirm the split at merge and record that.

### F4 - MINOR: two statements overstate what the split gives

- **Where:** `agent-models.md:61` ("Each model reviews work the other wrote"); `01-orchestrator.md:18-19` and `I1-DEL-002.yaml:231` ("One model on all agents removes independent review"); the comment at `check-agent-permissions.mjs:341`.
- **Reproduce:** see the table in check 6. No Flash agent has a review role, so Flash reviews nothing Argon wrote. Separately, AGENTS.md §8 defines independence by agent and role, not by model, so a single model would reduce diversity, not remove independent review as defined there.
- **Why it matters:** the argument was used to decline the owner's offer of Argon on all agents, so the owner should weigh it as stated accurately. The second clause of `agent-models.md:61`, on tester-written tests, is correct.

### F5 - MINOR: the stated reason for not running a project subagent live does not match the rules

- **Where:** `agent-models.md:51-52` ("The project's `task` rule prompts a human").
- **Reproduce:** `.opencode/agents/orchestrator.md` sets `task` to `allow` for the 9 project subagents, and `check-agent-permissions.mjs:260-261` asserts this and passes in both configurations. Root `task: ask` is overridden for the orchestrator.
- **Effect:** the "not tested" statement itself is honest. The reason may be wrong, so the gap may be easier to close than it reads. I made no live call, so another cause remains possible. Closing it, after other work has freed the provider's slots, would also cover the largest remaining risk, since no project subagent has yet run on the new models.

### F6 - MINOR: "`high` is Argon's highest setting" has no recorded reproduction

- **Where:** `agent-models.md:13`; `I1-DEL-002.yaml:232`; `delivery/README.md:86`.
- **Reproduce:** the evidence lists a successful `high` call but no listing of available settings and no failed `max` call. I could not verify it: `opencode models zai --verbose` fails under the empty config, and under the owner's config it would draw on the provider definition, which I was told not to read, so I did not run it there.
- **Suggested fix:** record the command and result.

### F7 - MINOR: packet and handoff gaps

- **Security review.** `I1-DEL-002.yaml:195-197` sets `securityPrivacy.affected: true` and describes repository content going to a new third party, possibly on to Google, while `:211` sets `securityReviewer: false`. AGENTS.md §8 assigns privacy and external input to the security reviewer, `review-task.md:141` lists provider integrations, and I1-DEL-001 had one at L3. The owner did choose the provider, and human approval is required, so I rate this MINOR. Only a human may accept privacy risk, so the owner should confirm L2 with no security review, knowing the routing is unconfirmed.
- **Handoff fields.** `01-orchestrator.md` lacks these AGENTS.md §14 fields: documents and sections read, acceptance criteria status, assumptions. Commands and test results are only a pointer to the evidence. Its frontmatter goes from `READY` directly to `INDEPENDENT_REVIEW`.
- **Reproducibility.** The evidence names no exact commands for the live calls, the concurrency tests or the mutation (AGENTS.md §14 requires reproducible evidence).

### Notes

- N1: `orchestrator.md:541` says "background title and summary calls". `compaction` can also make calls, and I could not find a `summary` call (check 4). This does not change the rule.
- N2: `agent-models.md:30-31` says "5187/5187, models included". The counter covers permission expectations only; a model failure leaves it at 5187/5187 and shows in the final failure line (M1). The runs did include the model assertion.
- N3: `agent-models.md:62` mentions the global `temperature`. Resolved `temperature` is 0.1 in both configurations. What I could see differ is `color` and 9 extra permission rules, which the existing drift check covers.
- N4: this review ran as a Claude subagent (Opus 5.5) launched from the authoring session. The role is independent as AGENTS.md §8 defines it, but the handoff does not state the authoring session's model, so I cannot say whether the two models differ.
- N5: ARC-AI-OQ-01 stays open, and the packet says so. The owner direction in the packet is what serves as the "explicitly approved" required by §26 of the rules document.

## Handoff

```text
Task ID: I1-DEL-002
Role: Independent reviewer
Task state: INDEPENDENT_REVIEW; recommended next state CI_PENDING, once the owner has seen F1, F2, F3 and F7
Baseline commit: e02db33e82eb0c30788cd9d12c8374bd4891e255
Impact level: L2 (as packeted; see F7)
Documents and sections read: AGENTS.md (provided in full); delivery/tasks/I1-DEL-002.yaml;
  delivery/handoffs/I1-DEL-002/01-orchestrator.md; delivery/evidence/I1-DEL-002/agent-models.md; full git diff;
  opencode.json; scripts/delivery/check-agent-permissions.mjs; .opencode/commands/review-task.md;
  permission blocks and search hits in .opencode/agents/*.md and other commands;
  docs/08_delivery_and_ai_agents/03_ai_agent_rules_review_gates_v1.0.md §24 (by search), §26, ARC-AI-OQ-01;
  delivery/tasks/I1-DEL-001.yaml (reviews and impact only)
Files changed: delivery/handoffs/I1-DEL-002/02-reviewer.md only
Commands executed: git status, diff, ls-files, archive (read-only); opencode debug agent <name>, opencode agent list,
  opencode models (names or counts only), opencode debug paths; opencode debug config (only with empty or
  synthetic XDG_CONFIG_HOME, filtered to model fields); check-agent-permissions.mjs (repository and scratch copies);
  validate.mjs; self-test.mjs; secretlint; string search of the OpenCode binary. No model calls.
Test results:
  PASS: resolved models, global and empty, 14/14
  PASS: check-agent-permissions.mjs, global and empty, 5187/5187
  PASS: validate.mjs; self-test.mjs 8/8
  PASS: mutations M1 to M8 fail as they should
  FAIL (test gap, not current behaviour): M9, M10, M11 pass where drift should arguably fail; F1
  PASS: secretlint, 19 files; rerun on this report, exit 0
  PASS: validate.mjs rerun after this report was added, exit 0
  PASS: permission rules unchanged from baseline, both configurations
  NOT_RUN: live calls, concurrency tests, project subagent run, /review-task (live calls prohibited for this review)
  NOT_RUN: Argon's available settings (would draw on the provider definition)
  NOT_RUN: which model actually serves title, summary and compaction (static reading only)
Acceptance criteria status:
  AC-01 PASS (resolution only; F2)
  AC-02 PASS for the 14 agents (F1 outside them)
  AC-03 PASS
  AC-04 PASS
Decisions made: None normative. Severity ratings only.
Assumptions: A string search of the binary reflects what OpenCode 1.19.0 does at runtime.
Findings and residual risks: 7 MINOR (F1 to F7), 5 notes. No project subagent has run live on the new models.
Blockers: None.
Recommended next agent: Orchestrator, to apply or decline the MINOR fixes, then the Project Owner for human review.
```

## Required human decisions

1. Confirm L2 with no security review, given that repository content goes to `zai` and the onward routing is unconfirmed (F7).
2. Confirm or change the writer and reviewer split, understanding what it does and does not separate (F3, F4).
3. Decide whether F1 should be fixed before merge. It is small, and I recommend it.
