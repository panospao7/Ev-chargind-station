---
description: Read-only scope discovery for a task or external plan.
agent: scout
subtask: true
---

Perform read-only scope discovery for:

```text
$ARGUMENTS
```

This command implements ARC-016 §11 change-impact discovery: identify the
requirements, invariants, lifecycles, owning service, contracts, database
objects, authorization surface, personal-data surface, migration needs, and
test impact before any implementation decision.

## Goal

Find the relevant files, architecture docs, tests, risk level, and recommended workflow mode before implementation.

## Instructions

1. Do not edit files.
2. Do not run bash.
3. Read architecture docs first when relevant:
   - `AGENTS.md`
   - `docs/01_scope_and_requirements/**`
   - `docs/03_domain/**`
   - `docs/05_architecture/**`
   - `docs/06_security_and_privacy/**`
   - `contracts/**`
4. Locate likely source files and tests.
5. Identify high-risk areas:
   - allocation/locking (guard rows, hold expiry, exclusion constraints)
   - privacy/security/permissions
   - Flyway migrations/schema/constraints
   - payment/refund/billing
   - booking, check-in, or charging-session lifecycles
   - discovery projections (no personal identifiers)
   - device integration and reconciliation
   - architecture guards
6. Recommend fast, standard, or strict mode.

## Output format

```markdown
Scout findings:
- Relevant files:
  - `path`: why relevant

Architecture docs/rules:
- `path`: summary

Tests likely affected:
- `path` or test filter

Risk:
- low|medium|high

Recommended mode:
- fast|standard|strict|strict imported-plan

Recommended next step:
- ...
```
