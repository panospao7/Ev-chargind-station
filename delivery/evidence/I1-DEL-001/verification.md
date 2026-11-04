# I1-DEL-001 — Verification

**This verification ran after the merge, not before it.** See deviation
`delivery/deviations/I1-DEL-001/CLOSE-01-verified-after-merge.yaml`.

```text
TASK_ID: I1-DEL-001
VERIFICATION_RESULT: HUMAN_REVIEW, reached after merge under deviation CLOSE-01
RESULTING_STATE: VERIFIED in the closeout pull request, effective only if the owner merges it
BASELINE_COMMIT: 811b81ed39ca954217cf785f5f9c3135f14b2263
CANDIDATE_COMMIT: 3a134b3cd7b31dbf012be097d709f089059cb2b3
PULL_REQUEST_HEAD: 2296f7dd9820f91dfcbaf4ba871d4557a7ab7c9e
MERGE_COMMIT: 60c261e37fbe7df6686fd02e1011f4dc5aa0d06b
IMPACT_LEVEL: L3

CI_RUN_REFERENCE: G3 Contract Validation 41812033525 (PR head), 41823540316 (merge commit)
FINAL_TESTER_RESULT: PASS_NO_BLOCKERS (handoff 14)
REVIEW_INTEGRITY: PARTIAL; no fingerprint exists, compensating comparison in handoff 14
CONTRACT_STATUS: NOT_APPLICABLE; no contract changed
MIGRATION_STATUS: NOT_APPLICABLE; no migration changed
SECURITY_STATUS: Last specialist result FAIL_RISK_ACCEPTED; no new full security review
TRACEABILITY_STATUS: NOT_APPLICABLE; no product requirement
DOCUMENTATION_STATUS: Evidence corrected in the closeout; findings below remain
```

## CI check matrix

The packet has `requiredCiChecks: []`.

| Check | PR head `2296f7d` |
|---|---|
| OpenAPI Validation | PASS |
| AsyncAPI Validation | PASS |
| JSON Schema Validation | PASS |
| Registry Consistency Checks | PASS |
| Privacy and Data Minimization Checks | PASS |
| Documentation Consistency Checks | PASS |
| Secret Scanning (fail closed) | PASS |
| Validator Self-Test (failure injection) | PASS |
| Required Aggregation | PASS |

The merge commit's G3 run also passed. `frontend-tests`, `service-tests` and `db-migrations` are NOT_RUN, because their
path filters do not match this change. G3 does not check `.opencode/`, `delivery/` or the permission check.

Frontend Tests failed on `main` at `811b81e`, the commit before this merge, in a run from 2026-09-30: `npm audit` reports
high-severity advisory GHSA-ff3f-86qr-9cv3 in `@angular/router`. This task did not cause it and does not fix it.

## Local gates

| Gate | Result |
|---|---|
| `node scripts/delivery/check-agent-permissions.mjs`, global and empty config | PASS 5187/5187 |
| `node scripts/delivery/validate.mjs` | PASS |
| `node scripts/delivery/self-test.mjs` | PASS 8/0 |
| Shell pin removed in a scratch copy | Fails as intended |
| secretlint on handoffs and evidence | PASS, 16 files |
| Live session or end-to-end command run in this check | NOT_RUN |

## Open findings

All are MINOR or NOTE. Details and reproductions are in `delivery/handoffs/I1-DEL-001/14-tester-verification.md`.

- MINOR-1, 3, 5 and 6 need `.opencode/` or check-script edits, so they need a separate task.
- MINOR-2 needs an owner-authorized documentation task for DEC-AGENT-01 and `AGENTS.md` §12 before `/ship-task I1-IAM-002`.
- MINOR-7 lists owner decisions that were not carried through.

## Corrections to handoff 14

Handoff 14 is saved unedited. Its author reported these corrections after saving it:

- The item 5 row cites MINOR-5 for the closeout gap. It should cite "Process gap" under Blockers.
- "`shell` is the only setting that resolves differently" was inferred from identical permission arrays, agent lists and the
  edit script. It was not a full config comparison.
- "Files changed: None" is out of date, because the tester then saved that file at the orchestrator's request.
