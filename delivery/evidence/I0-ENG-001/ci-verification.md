# I0-ENG-001 — CI Verification Evidence

- Task ID: I0-ENG-001
- Baseline commit: `3d17040635565f3608cfe208d928ca81c35d577f`
- Candidate commit: this task branch; exact SHA recorded in `delivery/status.yaml` at close-out
- Timestamp: 2026-09-09T21:40:00Z

## AC-10 — clean-checkout verification and CI run

1. Local clean-install verification: `npm ci --no-audit --no-fund` executed twice against the
   final dependency state (wipes `node_modules` and installs strictly from the committed
   `package-lock.json`) — PASS, exit 0.
2. Local aggregate gate `npm run contracts:verify` — executed; result FAIL with exactly two
   blocking classes, both outside this task's correction authority:
   - `contracts:openapi`: 7 × `securitySchemes.mTLS.type: mutualTLS` (OpenAPI 3.1 type in
     3.0.3 documents) — owner decision requested in
     `delivery/deviations/I0-ENG-001/DEC-001-mtls-encoding.yaml`;
   - `contracts:registries`: 78 declared registry `schemaPath` targets do not exist —
     contract-authoring work outside this task, split requested in
     `delivery/deviations/I0-ENG-001/SPLIT-001-registry-schemas.yaml`.
   All other validators pass (see contract-validation.md matrix).
3. GitHub Actions run: the PR opened from this branch triggers the rewritten
   `G3 Contract Validation` workflow (pull_request trigger, paths matched). Expected job
   results: `openapi-validate` failure (DEC-001), `registry-checks` failure (SPLIT-001),
   and success for `asyncapi-validate`, `jsonschema-validate`, `privacy-checks`,
   `doc-consistency`, `security-scan`, `self-test`; the `required` aggregate therefore fails
   until the two decision items are resolved. Run reference: recorded in `delivery/status.yaml`
   at close-out once the candidate commit is pushed (requiredRunReference).

## Interpretation

The toolchain itself (install, parity, fail-closed checks, negative-fixture proof) is complete
and green. The aggregate gate is red solely on contract CONTENT defects that require either a
human semantic decision (mTLS encoding) or a new contract-authoring task (registry schemas).
This is the stop-and-split condition foreseen by the task packet
(`maximumExpectedDiff` clause: "requires new business decisions, stop and request task splitting").

Result: PASS for toolchain verification; aggregate gate BLOCKED on DEC-001 and SPLIT-001.
