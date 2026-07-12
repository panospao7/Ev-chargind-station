You are close. Before running the first task, complete these items.

## Required now

1. **Add the two missing specialist agents**
   - `.opencode/agents/contract-reviewer.md`
   - `.opencode/agents/data-reviewer.md`

   Your task packets and orchestrator reference them, but only the corresponding skills currently exist. Also allow both in `orchestrator.md`:

   ```yaml
   task:
     "*": deny
     planner: allow
     coder: allow
     tester: allow
     reviewer: allow
     debugger: allow
     security-reviewer: allow
     contract-reviewer: allow
     data-reviewer: allow
     documentation: allow
   ```

2. **Update `review-task.md`**
   
   It must invoke:

   - `contract-reviewer` when contracts, schemas, registries or public behavior change;
   - `data-reviewer` when migrations, SQL, persistence, locking or constraints change;
   - `security-reviewer` for security/privacy-sensitive changes.

   Your first task, `I0-ENG-001`, explicitly requires contract and security review.

3. **Use one agent-definition source**

   If agents now exist under `.opencode/agents/`, remove duplicate agent definitions from `opencode.json`. Keep `opencode.json` for global defaults, instructions and permissions. OpenCode supports Markdown agents, and configuration sources are merged, so duplicate names can create confusing overrides. ([dev.opencode.ai](https://dev.opencode.ai/docs/agents/?utm_source=openai))

4. **Validate OpenCode discovery**

   Run:

   ```bash
   jq empty opencode.json
   opencode agent list
   opencode models
   ```

   Then open OpenCode and confirm:

   - `orchestrator` is the default primary agent;
   - all subagents appear;
   - `/start-task`, `/plan-task`, `/implement-task`, `/review-task`, `/verify-task`, `/close-task` appear;
   - all four skills are discoverable.

   OpenCode automatically discovers project agents, commands and skills from their `.opencode` locations. ([opencode.ai](https://opencode.ai/docs/skills?utm_source=openai))

5. **Commit the orchestration scaffold manually**

   Agents are prohibited from committing. Before `/start-task`, manually commit:

   - `AGENTS.md`
   - `opencode.json`
   - `.opencode/`
   - `delivery/`

   Example:

   ```bash
   git add AGENTS.md opencode.json .opencode delivery
   git commit -m "chore: add OpenCode delivery control plane"
   ```

   This gives tasks an immutable baseline and prevents unrelated uncommitted setup files from blocking the orchestrator.

## Strongly recommended next

Create `I0-DEL-001` to add:

- JSON Schemas for backlog, status, task, iteration and deviation YAML;
- `scripts/delivery/validate.*`;
- `npm run delivery:validate`;
- CI validation;
- state-transition and task-reference checks.

Also add `.opencode/commands/debug-task.md`; otherwise failures must be handed to `@debugger` manually.

## Human workflow requirement

Document who may provide `HUMAN_APPROVAL` and what counts as a reference—preferably a GitHub PR approval or issue comment URL/reference.

After these are complete, begin:

```text
/start-task I0-ENG-001
```

You do **not** need an OpenCode plugin or external orchestrator yet. Configuration-only orchestration is sufficient for the first supervised iterations.