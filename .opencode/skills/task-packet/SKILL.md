---
name: task-packet
description: Create or validate a repository-visible implementation task packet with authority references, bounded scope, acceptance criteria, dependencies, tests, review gates, and evidence requirements.
compatibility: opencode
metadata:
  project: ev-charging-platform
  category: delivery-governance
---

# Task packet skill

## Purpose

Create a new task packet YAML under `delivery/tasks/<TASK-ID>.yaml` or validate an existing one. Task packets are the single authoritative scope definition for an implementation unit.

## When to use

- A new task is defined and approved for the backlog.
- An existing task needs scope validation before planning.
- A task is split or consolidated.

## Procedure

1. Read the authoritative documents referenced in the task's iteration or epic.
2. Identify the task ID, iteration, epic, and release wave.
3. Set a precise objective and explicit non-goals.
4. List exact authority references (governance, requirements, use cases, domain, architecture, security, contracts).
5. Define owning service and data owner.
6. List affected artifacts: services, modules, tables, projections, migrations, API operations, commands, events, schemas, registries, screens, infrastructure.
7. Record dependencies on tasks, infrastructure, and human decisions.
8. Set allowed and prohibited files with glob patterns.
9. Write measurable acceptance criteria with authority references, evidence requirements, and test types.
10. Define required tests: focused commands, required suites, positive/negative/boundary/concurrency/security/contract/migration/accessibility cases.
11. Classify persistence, contract, and security/privacy impact.
12. Set required reviews and specialist reviewers.
13. Define evidence requirements.
14. Record assumptions and open questions.

## Validation rules

- Task ID must match `delivery/status.yaml` entries.
- allReferences must resolve to existing file paths.
- allowedFiles and prohibitedFiles must not overlap.
- Each acceptance criterion must have at least one evidence requirement.
- Tests section must include at least one positive case.
