---
taskId: I0-DEL-001
evidenceType: static-validation
validator: scripts/delivery/self-test.mjs
date: 2026-07-13T10:37:00Z
status: PASS
---

# Control plane integrity evidence

## Self-test runner

The self-test runner (`scripts/delivery/self-test.mjs`) executes the Node.js
YAML validator (`scripts/delivery/validate.mjs`) against each fixture and
asserts both exit code and expected diagnostic message.

### Test results

Command:
```
node scripts/delivery/self-test.mjs
```

| Test | Fixture | Expected | Actual | Status |
|------|---------|----------|--------|--------|
| Duplicate YAML keys | `tests/duplicate-key.yaml` | exit 1, "duplicated mapping key" | exit 1, match | PASS |
| Invalid SHA | `tests/invalid-sha.yaml` | exit 1, "SHA length violation" | exit 1, match | PASS |
| Unknown state | `tests/unknown-state.yaml` | exit 1, "Unknown task state" | exit 1, match | PASS |
| Valid status.yaml | `delivery/status.yaml` | exit 0, "ALL CHECKS PASSED" | exit 0, match | PASS |

### Validator capabilities

- Strict YAML parsing via `js-yaml` library, which throws on duplicate mapping keys
- SHA hex validation for all SHA fields: `baselineCommit`, `candidateCommit`, `mergeCommit`, `approvedCandidateCommit`, `observedRemoteBaseline`
- Task state consistency check: all 15 valid states are enumerated, unknown states are rejected
- Summary count consistency: declared vs actual task state counts must match
- Handoff file reference existence
- Iteration task reference existence
