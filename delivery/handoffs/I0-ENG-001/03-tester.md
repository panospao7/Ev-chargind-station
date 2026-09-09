---
role: tester
taskId: I0-ENG-001
baselineCommit: 3d17040635565f3608cfe208d928ca81c35d577f
impactLevel: L2
date: 2026-09-09T21:40:00Z
---

# I0-ENG-001 — Tester handoff

Disclosure: executed by the same agent session as the coder. Independent review happens at
PR review and human review. All results below are actual executed commands on Windows
(Node v22.12.0 locally; CI executes Node 24 from `.nvmrc`).

## Focused commands

| Command | Result |
|---|---|
| `node --version` | v22.12.0 (EBADENGINE warning expected; engines declared `>=24 <25`) |
| `npm ci --no-audit --no-fund` (run twice, final state) | PASS (exit 0) |
| `npm run contracts:openapi` | FAIL — 7 errors, all `securitySchemes.*.type: mutualTLS` (DEC-001, owner decision); 71 warnings (non-failing) |
| `npm run contracts:asyncapi` | PASS |
| `npm run contracts:schemas` | PASS — 7 schemas compile under draft-2020-12 |
| `npm run contracts:registries` | FAIL — 78 declared `schemaPath` targets missing (SPLIT-001, task split); all structural checks pass otherwise |
| `npm run contracts:privacy` | PASS |
| `npm run contracts:docs` | PASS — 29 traced requirements, 22 W1, 0 W1 OPEN; CON-175/176 reported as non-W1-critical |
| `npm run contracts:secrets` | PASS — zero findings, fail-closed |
| `npm run contracts:self-test` | PASS — 21/21 negative/positive fixtures across every validator category |
| `node scripts/delivery/validate.mjs delivery/status.yaml` | PASS (control-plane check, pre-existing) |

## Negative / failure-injection coverage (AC-09)

Proven by `contracts:self-test`: every validator category demonstrably fails on controlled
invalid fixtures and passes valid ones — including duplicate message names, missing schema
references, duplicate problem codes, unknown lifecycle transitions, Discovery subject
identifiers, unannotated sensitive fields, OPEN W1-critical gaps, invalid OpenAPI/AsyncAPI,
and embedded AWS credentials.

## Boundary cases (from task packet)

- Nested schema discovery at every existing depth: exercised by recursive discovery over
  `contracts/schemas/{common,events,commands,telemetry}` (7 files) and by fixtures.
- Empty optional category reported explicitly: the registries validator fails loudly on a
  missing/empty `policies` array (fixture + real-registry execution), and `contracts:schemas`
  prints its discovered file count.

## Required suites

- `npm run contracts:self-test` — PASS
- `npm run contracts:verify` — FAIL, solely DEC-001 + SPLIT-001 (documented deviations)
- `git diff --check` — to be executed at commit time (whitespace gate)

## Test-scope note

No tests were deleted, skipped, weakened, or converted to warnings. The secret/privacy scan
gained a documented precision exemption for `type:`/`scheme:` declaration lines; its
fail-closed fixtures still fail, proving the rule remains enforced.

Result: SELF_VERIFIED with two decision-blocked, expected-red validator categories.
