---
name: migration-review
description: Read-only database migration, schema, and constraint reviewer.
compatibility: opencode
metadata:
  project: ev-charging-platform
  category: review
---

# Migration review skill

## Purpose

Review database migrations, schema changes, constraints, indexes, locking, and concurrency behavior against the service ownership and domain lifecycle documents.

## When to use

- Any task that adds or modifies database migrations.
- Any task affecting persistence logic, locking, or transaction boundaries.
- Review gate for L3 impact tasks.

## Checklist

- [ ] Migration numbering is unique and forward-only.
- [ ] No destructive changes (DROP COLUMN, DROP TABLE, TRUNCATE) without explicit human approval.
- [ ] Runtime vs migrator roles are respected.
- [ ] Constraints enforce range semantics for half-open intervals.
- [ ] Indexes support the query patterns implied by the lifecycle.
- [ ] Lock ordering matches the approved concurrency architecture.
- [ ] Deadlock scenarios are documented and tested against real PostgreSQL.
- [ ] Outbox records commit atomically with business changes.
- [ ] Projection versioning handles idempotent replay.
- [ ] Seed/reset behavior is deterministic and documented.

## Reporting

Rank every violation as BLOCKER, MAJOR, MINOR, or NOTE.
Cite exact file locations and violated authority.
Require real PostgreSQL test evidence for allocation correctness.
