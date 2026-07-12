**You do not need to fork or modify OpenCode itself.** You need a **project-specific OpenCode configuration and repository-visible delivery protocol**.

OpenCode already supports committed project instructions, primary/subagents, per-agent permissions, subagent invocation through the Task tool, custom commands and reusable skills. ([opencode.ai](https://opencode.ai/docs/agents/))

## Implement this structure

```text
AGENTS.md
opencode.json

.opencode/
  agents/
    orchestrator.md
    planner.md
    coder.md
    tester.md
    reviewer.md
    debugger.md
    security-reviewer.md
    documentation.md
  commands/
    start-task.md
    plan-task.md
    implement-task.md
    review-task.md
    verify-task.md
    close-task.md
  skills/
    task-packet/SKILL.md
    architecture-check/SKILL.md
    migration-review/SKILL.md
    contract-review/SKILL.md

delivery/
  backlog.yaml
  status.yaml
  tasks/
  iterations/
  handoffs/
  evidence/
  deviations/
```

## OpenCode-specific design

- `orchestrator`: `mode: primary`; no editing; may invoke approved subagents.
- `planner`, `reviewer`, `security-reviewer`: read-only.
- `coder`: edit/build/test, but no push or merge.
- `tester`: run tests; edits limited to test files.
- `debugger`: fixes only reproduced failures.
- `documentation`: documentation and traceability edits only.

OpenCode supports these permission restrictions, including command patterns and controlling which subagents the orchestrator may invoke. ([opencode.ai](https://opencode.ai/docs/agents/))

## Do not load every planning document globally

Keep `AGENTS.md` concise. Each task packet should identify the **exact documents and sections** required. OpenCode does not automatically resolve arbitrary document references inside `AGENTS.md`; use explicit instructions or `opencode.json` instruction paths. ([opencode.ai](https://opencode.ai/docs/rules))

## Recommended initial operation

Use a supervised workflow:

```text
/start-task TASK-ID
→ planner
→ coder
→ tester
→ reviewer/security reviewer
→ debugger if required
→ documentation
→ human merge approval
```

Custom commands can target specific agents or run as subtasks. ([dev.opencode.ai](https://dev.opencode.ai/docs/commands/))

## Do you need a custom plugin?

**Not initially.** Add a plugin or external orchestrator only if you later need:

- unattended backlog execution;
- automatic worktree allocation;
- parallel task scheduling;
- automatic PR creation;
- persistent retries across sessions;
- automated state transitions.

OpenCode exposes plugins, custom tools and a headless HTTP server for that later automation. ([opencode.ai](https://opencode.ai/docs/server/))

**Recommendation:** start with configuration-only orchestration. Stabilize it over 5–10 tasks before implementing a plugin or external workflow engine.