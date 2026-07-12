---
name: contract-review
description: Read-only OpenAPI, AsyncAPI, JSON Schema, problem-code, lifecycle, message, and registry reviewer.
compatibility: opencode
metadata:
  project: ev-charging-platform
  category: review
---

# Contract review skill

## Purpose

Review all affected REST, message, schema, lifecycle, policy, problem-code, and traceability artifacts for consistency, compatibility, and correctness.

## When to use

- Any task that modifies OpenAPI, AsyncAPI, or JSON Schema documents.
- Any task that changes registries (messages, lifecycles, problem-codes, policies, traceability).
- Review gate for L2+ impact tasks with contract effects.

## Checklist

- [ ] One canonical name and meaning per entity across all contracts.
- [ ] Valid cross-references (no dead links between schemas, messages, registries).
- [ ] Operation IDs are unique and follow naming convention.
- [ ] Versions are consistent with the release wave.
- [ ] Examples validate against their schema and cover happy path and at least one error path.
- [ ] Backward compatibility is maintained unless documented breaking change is approved.
- [ ] Security surface is appropriate (no secrets in contracts, no missing auth).
- [ ] Data classification is declared and matches the classification policy.
- [ ] Idempotency is declared for all state-changing operations.
- [ ] Command outcomes (accepted vs fulfilled) are distinguished.
- [ ] Registry entries have no duplicates (unique namespaces, lifecycle states, problem codes, HTTP statuses).

## Reporting

Rank every violation as BLOCKER, MAJOR, MINOR, or NOTE.
Cite exact file locations and violated authority.
Run contract validators when available and report their output.
