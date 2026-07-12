**Yes—but these are not required by OpenCode itself.** They are required by the orchestration workflow we created because `/start-task`, `/plan-task`, `/implement-task`, `/review-task`, `/verify-task`, and `/close-task` read and update them. OpenCode provides agents, commands, skills, and permissions, but your repository must hold persistent delivery state. ([opencode.ai](https://opencode.ai/docs/rules?utm_source=openai))

Use this structure:

```text
delivery/
├── README.md
├── backlog.yaml
├── status.yaml
├── tasks/
├── iterations/
├── handoffs/
├── evidence/
└── deviations/
```

## What each item does

### Required immediately

- **`status.yaml`** — single authority for current task state, assignee, baseline/candidate SHA, blockers, PR and CI status.
- **`tasks/`** — one complete task packet per implementation task.
- **`iterations/`** — iteration goals, entry/exit gates and included task IDs.
- **`handoffs/`** — outputs from planner, coder, tester, reviewer, debugger and documentation agents.
- **`evidence/`** — executed test results, CI references, contract reports and acceptance-criteria evidence.
- **`deviations/`** — specification conflicts, approved scope changes and temporary exceptions.

### Recommended

- **`backlog.yaml`** — ordered task queue and dependencies.
- **`README.md`** — explains ownership, schemas and workflow rules.

## Avoid duplicate sources of truth

Use these ownership rules:

| Information | Authoritative location |
|---|---|
| Task objective/scope/acceptance criteria | `tasks/<TASK-ID>.yaml` |
| Current state and assignment | `status.yaml` |
| Priority and dependency ordering | `backlog.yaml` |
| Iteration goal and gates | `iterations/<ITERATION-ID>.yaml` |
| Agent output | `handoffs/<TASK-ID>/` |
| Test/CI proof | `evidence/<TASK-ID>/` |
| Approved exception/conflict | `deviations/<TASK-ID>/` |

Do **not** duplicate full task definitions or workflow states across files.

## Minimum initial contents

```text
delivery/
├── README.md
├── backlog.yaml
├── status.yaml
├── tasks/
│   └── I0-ENG-001.yaml
├── iterations/
│   └── I0-foundation.yaml
├── handoffs/
│   └── .gitkeep
├── evidence/
│   └── .gitkeep
└── deviations/
    └── .gitkeep
```

Your first task should be something bounded, for example:

```text
I0-ENG-001 — Add reproducible contract-validation toolchain
```

Without these files, your commands will repeatedly return `CLARIFICATION_REQUIRED` or fail because the referenced task packet, status and iteration records do not exist.