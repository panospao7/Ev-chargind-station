# I1-DEL-002 — Agent model evidence

- **Task ID:** I1-DEL-002
- **Baseline commit:** e02db33e82eb0c30788cd9d12c8374bd4891e255
- **Date:** 2026-11-04
- **Environment:** Windows 11, OpenCode 1.19.0, Git Bash

## Result

| Model | Setting | Agents |
|---|---|---|
| `zai/glm-5.3-flash` | `max` | `orchestrator`, `coder`, `debugger`, `documentation`, `build`, `plan`, `general`, `explore`; also root `model` and `small_model` |
| `zai/gemini-4-argon` | `high` | `planner`, `tester`, `reviewer`, `security-reviewer`, `data-reviewer`, `contract-reviewer` |

These appear to be each model's highest setting, judged from the names. `opencode models --verbose`, filtered to print only
model IDs and variant names, lists `low`, `high`, `max` for Flash and `low`, `medium`, `high` for Argon. No provider settings
or keys were printed.

`glm-5.3-flash` replaces the requested `glm-5.3-flashx`, which returned `model not found` on `zai`, with or without a thinking
setting. `opencode models zai` does list `zai/glm-5.3-flashx`, so the listing and the live calls disagree. The reason is unknown,
and `zai` would have to explain it.

## Why project agents set models in their own files

`opencode.json` alone does not work. With the model set there, `orchestrator` and `debugger` still resolved to
`merge-gateway/glm-5.3-flash`, because the owner's global `orchestrator.md` and `debugger.md` override it. Frontmatter in
the project's agent files wins, so the 10 project agents set it there. The 4 built-ins have no such file, so they stay in
`opencode.json`.

## Commands and results

| Check | Result |
|---|---|
| `opencode debug agent <name>`, 14 agents, owner's global config | PASS: all as in the table |
| `node scripts/delivery/check-agent-permissions.mjs`, global config | PASS: 5187/5187 permission expectations, and no model failure |
| The same with `XDG_CONFIG_HOME` set to an empty directory | PASS: as above, comparing configured names only. See the provider dependency below |
| Scratch copy with `reviewer` moved to the writer model | Correctly fails: `FAIL reviewer model: zai/glm-5.3-flash high` |
| Scratch copy with `small_model` in `.opencode/opencode.json` | Correctly fails: `FAIL unexpected project config file: .opencode/opencode.json` |
| `opencode run --title smoke "Reply with exactly the single word: ok"` in this repository | PASS: `orchestrator · glm-5.3-flash` replied |
| The orchestrator invoking the real `reviewer` subagent with `opencode run --format json`, checked with `opencode export` (model fields only) | PASS: recorded as orchestrator on `zai/glm-5.3-flash` at `max`, and `reviewer` on `zai/gemini-4-argon` at `high`. Child session `ses_e593a05ccffeYrzdXamf0uxzq1`, local to the owner's machine |
| `opencode run --model zai/gemini-4-argon --variant high`, disposable repository | PASS |
| `opencode run --model zai/glm-5.3-flash --variant max`, disposable repository | PASS |
| `opencode run --model zai/glm-5.3-flashx`, with and without `--variant max` | FAIL: `model not found` |

Reviewer mutations and permission comparisons are in `delivery/handoffs/I1-DEL-002/02-reviewer.md`. Re-checks of the later
fixes are in `04-reviewer-recheck.md`.

## Limit of 2 concurrent requests

Tested in a disposable repository with very short prompts. Its config had a primary agent `lead` on Flash at `max`, and
subagents `sub1` to `sub3` on Argon at `high`, with every permission allowed.

| Test | Command shape | Result |
|---|---|---|
| 5 separate calls at once | 5 background `opencode run --model zai/glm-5.3-flash --variant max` | All succeeded, finishing at 9, 9, 15, 15 and 25 seconds |
| One lead starting 3 parallel subagents on Argon | `opencode run --agent lead`, asking for 3 `task` calls in one message | All succeeded in 36 seconds |

The finishing times suggest requests wait for a slot rather than fail. That is an inference from one run. Long requests,
heavier traffic or a stricter provider response may behave differently, so agents run subagents one at a time anyway.

## Not tested

- A full `/review-task`, or any project subagent other than `reviewer`.
- Whether the limit applies per key or per model, and whether OpenCode's `zai` login is the key used by the Cost-agregator
  project. The key was not read.
- Which model actually serves title and compaction calls. Reading the OpenCode binary suggests `title` follows `small_model`
  and `compaction` follows the session's model, but those calls were not observed. Whether `summary` makes calls at all was
  not established.
- Whether the provider applies the requested thinking setting. OpenCode records it, and that is all that was checked.
- Config sources outside the project's files, such as the `OPENCODE_CONFIG` environment variable. The check does not cover them.
- Selecting another provider with `--model` or a model picker. This was not tried with `merge-gateway`.

## Remaining points

- **Provider dependency.** With an empty global config, `opencode models zai` prints `Provider not found: zai`. The `zai`
  provider therefore comes from the owner's global config, not this repository. The check compares configured names, so it
  passes either way, but these agents would not run on another machine without that setup.
- **What the split separates.** Flash-written production code, docs and records are read by Argon. Tester-written tests and
  planner plans come from Argon and are read by Argon, and no Flash agent reviews anything. AGENTS.md §8 defines independence
  by agent and role, so one model on all agents would still meet it. The split adds model diversity only where Flash wrote
  the work.
- **Other routes to `merge-gateway`.** This repository does not disable other providers, so `--model` or a model picker can
  probably still select one.
- **Global files.** The owner's global `orchestrator.md` and `debugger.md` still add a `color` and 9 permission rules. Other
  projects keep their own settings, and the global default is still `merge-gateway`.
- **ARC-AI-OQ-01** in the rules document remains open. Only the owner can close it.
