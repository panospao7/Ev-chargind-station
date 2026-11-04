## Purpose

Move this repository's OpenCode agents to the `zai` provider at each model's highest thinking setting, as the owner directed. Run subagents one at a time because of the provider's limit of 2, and make the permission check fail if the assignment drifts.

| Model | Setting | Agents |
|---|---|---|
| `zai/glm-5.3-flash` | `max` | `orchestrator`, `coder`, `debugger`, `documentation`, `build`, `plan`, `general`, `explore`, and root `model` and `small_model` |
| `zai/gemini-4-argon` | `high` | `planner`, `tester`, `reviewer`, `security-reviewer`, `data-reviewer`, `contract-reviewer` |

`glm-5.3-flashx` returned `model not found` on `zai`, so `glm-5.3-flash` replaces it, as the owner accepted.

## Your decisions at merge

Merging confirms these. They are not settled by the reviews:

1. **The split.** This is the orchestrator's recommendation. It keeps Flash-written production code reviewed by a different model. Tester-written tests and planner plans come from Argon and are read by Argon. One model on all agents would still meet AGENTS.md §8, which defines independence by role, but would lose that model diversity.
2. **L2 with no security review.** Repository content goes to `zai`, and possibly on to Google for Argon. That routing is unconfirmed, and only a human may accept this privacy risk.
3. **The key.** OpenCode's `zai` login is assumed to be the key you meant. Its value was not read.

## Changes

- `.opencode/agents/*.md`: `model` and `variant` in 10 files, and a one-subagent-at-a-time rule in the orchestrator
- `opencode.json`: root models and the 4 built-ins
- `.opencode/commands/review-task.md`, `delivery/README.md`: the same rule, plus the provider dependency
- `scripts/delivery/check-agent-permissions.mjs`: model assertions
- `delivery/`: packet, status, backlog, handoffs and evidence

No permission rule changed. No key is in this repository.

## Tests

- `node scripts/delivery/check-agent-permissions.mjs`: PASS 5187/5187 in both configurations, with no model failure
- `node scripts/delivery/validate.mjs`, `node scripts/delivery/self-test.mjs`: PASS
- Live: the orchestrator ran on Flash at `max` and invoked the real `reviewer`, which ran on Argon at `high`, as recorded by OpenCode
- Concurrency, disposable repository: 5 calls at once and 3 parallel subagents all succeeded

Not tested: a full `/review-task`, other project subagents, whether `zai` applies the thinking settings, and whether the limit is per key or per model. See `delivery/evidence/I1-DEL-002/agent-models.md`.

## Risk

Impact level: L2 (for you to confirm)

- **Global config dependency:** the `zai` provider comes from your global OpenCode config, so another machine needs that setup first.
- **Other providers:** this repository doesn't disable them, so `--model` or a model picker can probably still select one.
- **Process gap:** this task was not recorded at `SELF_VERIFIED` before review. This is disclosed in handoff 03, not recorded as a deviation.

Rollback: revert this pull request. Agents then fall back to the global `merge-gateway` setup.

## Agent contribution

Producing agent: Claude Code (Opus 5.5), orchestrator and coder roles in one session, disclosed
Reviewing agent: a separate Claude Code subagent on the same model, independent by role only. Three rounds, final verdict PASS_NO_BLOCKERS (handoffs 02, 04, 05)
Human decisions required: merge, and the three decisions above

🤖 Generated with [Claude Code](https://claude.com/claude-code)
