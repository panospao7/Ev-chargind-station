# Delivery Control Plane

## Purpose

This directory is the repository-visible control plane for implementation work performed by humans and AI agents.

Chat history is not authoritative delivery state. Tasks, status, handoffs, evidence, and deviations must be persisted here.

## Source-of-truth ownership

| Information | Authoritative location |
|---|---|
| Task objective, scope and acceptance criteria | `tasks/<TASK-ID>.yaml` |
| Current task state and assignment | `status.yaml` |
| Priority and dependency order | `backlog.yaml` |
| Iteration goal and gates | `iterations/<ITERATION-ID>.yaml` |
| Agent outputs | `handoffs/<TASK-ID>/` |
| Test and CI proof | `evidence/<TASK-ID>/` |
| Conflicts, exceptions and scope changes | `deviations/<TASK-ID>/` |

Current workflow state must never be inferred from the task packet, backlog, chat history, branch name, or pull request.

## Directory structure

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

## Workflow

```text
BACKLOG
→ READY
→ CLAIMED
→ IMPLEMENTING
→ SELF_VERIFIED
→ INDEPENDENT_REVIEW
→ CI_PENDING
→ HUMAN_REVIEW
→ MERGED
→ VERIFIED
```

Alternative states:

```text
FIX_REQUIRED
BLOCKED
CLARIFICATION_REQUIRED
SPEC_CONFLICT
SUPERSEDED
CANCELLED
```

Only `delivery/status.yaml` stores the current state.

## OpenCode command sequence

```text
/start-task TASK-ID
/plan-task TASK-ID
/implement-task TASK-ID
/review-task TASK-ID
/ship-task TASK-ID
/verify-task TASK-ID
/close-task TASK-ID MERGE_SHA=<sha> HUMAN_APPROVAL=<ref> FINAL_VERIFY=YES
```

## OpenCode operating rules

Permission rules are guardrails against mistakes, not a security boundary. See `delivery/evidence/I1-DEL-001/agent-permissions.md` for what they cannot stop.

- `opencode.json` pins `"shell": "bash"`, because under PowerShell everyday syntax such as `git checkout -- .` may skip permission checks entirely. Do not remove it.
- Commands run in Git Bash, so use `./mvnw`, not `.\mvnw.cmd`, even where an older packet shows the Windows form.
- Answer "once" to permission prompts, never "always". An "always" answer can override deny rules for the rest of the session.
- Do not use `opencode --auto` in this repository. It approves everything that is not explicitly denied.
- After changing `opencode.json` or `.opencode/agents/`, run `node scripts/delivery/check-agent-permissions.mjs`.
- Writers (`orchestrator`, `coder`, `debugger`, `documentation`) use `zai/glm-5.3-flash`, and `planner`, `tester` and the reviewers use `zai/gemini-4-argon`, so the model that wrote production code does not review it. Each runs at its highest thinking setting: `max` and `high`. The owner chose the provider and thinking level; the split is the orchestrator's recommendation, for the owner to confirm or change. See `delivery/tasks/I1-DEL-002.yaml`.
- The `zai` provider comes from the owner's global OpenCode config, not this repository. Without it, these agents name a provider OpenCode cannot find, so another machine or contributor needs that setup first.
- The provider allows 2 concurrent requests, probably across every session using the key. Invoke subagents one at a time, and avoid running other OpenCode sessions on the same key at the same time.
- Owner exception (2026-11-04): the orchestrator may commit its own records, namely this task's `delivery/status.yaml` entry and this task's handoffs and evidence, even when the packet prohibits `delivery/status.yaml`. Nothing else outside `allowedFiles` may be committed.

## Task IDs

Use:

```text
<ITERATION>-<AREA>-<NUMBER>
```

Examples:

```text
I0-ENG-001
I1-CON-001
I2-SEC-001
I4-BKG-001
```

Approved area codes:

- `DEL` — delivery control plane
- `ENG` — engineering foundation
- `CON` — executable contracts
- `DAT` — persistence and migrations
- `SEC` — security
- `STA` — station operations
- `ACC` — account
- `BKG` — booking/session
- `DEV` — device integration
- `DSC` — discovery
- `UX` — frontend/UX
- `OPS` — infrastructure and operations
- `TST` — cross-cutting testing
- `DOC` — documentation

## Task packet rule

A task packet is immutable in intent after implementation begins.

Permitted factual updates:

- authority-reference correction;
- accepted scope amendment;
- approved deviation reference;
- evidence links.

A material scope or acceptance-criteria change requires:

1. a deviation record;
2. human approval;
3. re-planning;
4. review of any implementation already produced.

Task packets use `initialState` only. Current state belongs exclusively in `status.yaml`.

## Handoff naming

Use:

```text
handoffs/<TASK-ID>/<SEQUENCE>-<ROLE>.md
```

Examples:

```text
handoffs/I0-ENG-001/01-orchestrator.md
handoffs/I0-ENG-001/02-planner.md
handoffs/I0-ENG-001/03-coder.md
handoffs/I0-ENG-001/04-tester.md
handoffs/I0-ENG-001/05-reviewer.md
```

## Evidence naming

Use:

```text
evidence/<TASK-ID>/<EVIDENCE-NAME>.<ext>
```

Evidence must identify:

- task ID;
- baseline commit;
- candidate or merge commit;
- command or workflow;
- execution environment;
- timestamp;
- result: `PASS`, `FAIL`, `NOT_RUN`, or `BLOCKED`;
- limitations.

Evidence must contain no secrets or unnecessary personal data.

## Deviation rules

A deviation records one of:

- `SPEC_CONFLICT`
- `SCOPE_CHANGE`
- `TEMPORARY_EXCEPTION`
- `RISK_ACCEPTANCE`
- `DEPENDENCY_BLOCKER`
- `IMPLEMENTATION_DISCOVERY`

AI agents may draft deviations but cannot approve architecture, security, privacy, migration, production, or legal exceptions.

## Baseline rule

`observedRemoteBaseline` records the repository snapshot used to create this control plane.

Every task receives its actual immutable `baselineCommit` when claimed. Never reuse the observed remote baseline automatically.

## Human authority

Only a human may:

- authorize L3/L4 work;
- approve architecture or contract-breaking changes;
- approve migrations for shared environments;
- accept security/privacy risk;
- merge;
- release;
- deploy;
- mark final verification complete.