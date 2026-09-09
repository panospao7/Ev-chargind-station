# I0-ENG-001 — Contract Validation Evidence

- Task ID: I0-ENG-001
- Baseline commit: `3d17040635565f3608cfe208d928ca81c35d577f`
- Candidate commit: this task branch; exact SHA recorded in `delivery/status.yaml` at close-out
- Executor: coding agent (ZCode session), local Windows workstation, Node v22.12.0
- Timestamp: 2026-09-09T21:40:00Z

## Validator matrix against the real repository (`npm run contracts:*`)

| Script | Result | Notes |
|---|---|---|
| `contracts:openapi` | FAIL (expected, blocked on owner decision) | 7 errors remain, all `components.securitySchemes.mTLS.type: mutualTLS` — a valid type only in OpenAPI 3.1, not the approved 3.0.3 dialect (ADR-013). Correction requires a security-scheme semantic decision: see `delivery/deviations/I0-ENG-001/DEC-001-mtls-encoding.yaml`. Fixed separately: duplicate path key `/notifications/preferences/{accountRef}` in notification-internal-api-v1.yaml (GET/PUT merged under one key). Warnings (71) do not fail the gate (`--fail-severity=error`). |
| `contracts:asyncapi` | PASS | @asyncapi/cli 2.13.0, warnings only |
| `contracts:schemas` | PASS | 7 schemas compile against the draft-2020-12 meta-schema (ajv/dist/2020); $schema dialect, $id uniqueness, relative $ref resolution enforced |
| `contracts:registries` | FAIL (expected, task split requested) | Message registry declares 78 `schemaPath` targets; none exist (see `delivery/deviations/I0-ENG-001/SPLIT-001-registry-schemas.yaml`). Structural checks now read the real registry fields (`versionedType`, `schemaPath`), enforce uniqueness, canonical namespaces, valid lifecycle transitions, one HTTP status per problem code, policy-ID uniqueness, and traceability uniqueness. |
| `contracts:privacy` | PASS | Per-line fail-closed scan; `type:`/`scheme:` declaration lines exempted as spec vocabulary (e.g. AsyncAPI `type: userPassword`), field keys and free text still scanned |
| `contracts:docs` | PASS | AC-08-literal scoping: fails on OPEN W1-critical rows; reports register meta-rows (CON-175/176, whose recorded resolution is the green CI run itself) without failing; traceability check reads the real `traceability:` schema (29 requirements, 22 W1, 0 OPEN) |
| `contracts:secrets` | PASS (fail closed) | secretlint 8.5.0 with committed config `scripts/contracts/secretlintrc.json`, invoked with `--secretlintrc` |
| `contracts:self-test` | PASS | 21/21 — see below |

## Negative fixtures (`npm run contracts:self-test`, exit 0)

21 passed, 0 failed, covering every validator category:
valid/invalid schema trees, unresolved $ref, duplicate $id, missing/wrong $schema dialect, valid
registries, duplicate message name, missing schema reference, conflicting duplicate problem code,
unknown lifecycle transition state, clean contracts, Discovery subject identifier, unannotated
sensitive field, clean docs, OPEN W1-critical contradiction, OPEN W1-critical requirement, valid/
invalid OpenAPI, valid/invalid AsyncAPI, clean secretlint file, embedded AWS access key.
(AC-05, AC-06, AC-07, AC-08, AC-09)

## Corrections made to contract artifacts (all structural, non-semantic)

1. `contracts/openapi/notification-internal-api-v1.yaml`: removed duplicate path key
   `/notifications/preferences/{accountRef}`; the `put` operation now sits under the single
   existing path key alongside `get`. No operation, schema, or semantics changed.

Result: PASS for every validator whose blocking findings are within this task's authority;
the two expected FAILs are decision/scope items recorded as deviations, not tooling defects.
