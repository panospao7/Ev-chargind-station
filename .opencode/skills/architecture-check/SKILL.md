---
name: architecture-check
description: Read-only architecture-law guardian for lifecycle, worker, and boundary violations.
compatibility: opencode
metadata:
  project: ev-charging-platform
  category: review
---

# Architecture check skill

## Purpose

Review a task diff against the approved architecture invariants. Agents must not make architecture decisions or reinterpret approved documents.

## When to use

- Any implementation task that touches service boundaries, lifecycles, data ownership, or concurrency.
- Review gate for L2+ impact tasks.

## Invariant checklist

Verify each of these invariants is preserved:

- [ ] Seven canonical service boundaries remain.
- [ ] Booking and Session remain combined.
- [ ] Each service owns its database and migrations.
- [ ] No cross-service database reads, foreign keys, or joins.
- [ ] Discovery availability is advisory; Booking allocation is authoritative.
- [ ] No remote call occurs while allocation locks are held.
- [ ] Booking intervals are finite, non-empty, and half-open.
- [ ] Holds are temporary exclusive claims.
- [ ] Correctness uses authoritative database time.
- [ ] Lock ordering and constraints provide final concurrency protection.
- [ ] Business changes and outbox records commit atomically.
- [ ] Consumers are idempotent and assume at-least-once delivery.
- [ ] Device command acceptance does not prove physical charging.
- [ ] Browser authentication uses opaque BFF session; no OAuth token in browser JS.
- [ ] Discovery projections contain no account, driver, or vehicle identifiers.
- [ ] No secrets, credentials, or private keys in source, logs, or messages.

## Reporting

Rank every violation as BLOCKER, MAJOR, MINOR, or NOTE.
Cite exact file locations and violated authority.
