---
taskId: I0-DEL-001
evidenceType: static-validation
validator: scripts/delivery/self-test.mjs
date: 2026-07-13T11:35:00Z
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

| # | Test | Fixture | Expected | Actual | Status |
|---|------|---------|----------|--------|--------|
| 1 | Duplicate YAML keys | `tests/duplicate-key.yaml` | exit 1, "duplicated mapping key" | exit 1, match | PASS |
| 2 | Invalid SHA (short) | `tests/invalid-sha.yaml` | exit 1, "SHA length violation" | exit 1, match | PASS |
| 3 | Unknown task state | `tests/unknown-state.yaml` | exit 1, "Unknown task state" | exit 1, match | PASS |
| 4 | Status-count mismatch | `tests/count-mismatch.yaml` | exit 1, "Count mismatch" | exit 1, match | PASS |
| 5 | Missing handoff file | `tests/missing-handoff.yaml` | exit 1, "Missing handoff file" | exit 1, match | PASS |
| 6 | Non-hex SHA | `tests/non-hex-sha.yaml` | exit 1, "SHA hex violation" | exit 1, match | PASS |
| 7 | Missing iteration task | `tests/missing-iteration-task.yaml` | exit 1, "Iteration" | exit 1, match | PASS |
| 8 | Valid real status.yaml | `delivery/status.yaml` | exit 0, "ALL CHECKS PASSED" | exit 0, match | PASS |

### Validator capabilities

- Strict YAML parsing via `js-yaml` library, throws on duplicate mapping keys
- SHA hex validation for all fields (traversed from parsed YAML tree):
  `baselineCommit`, `candidateCommit`, `mergeCommit`,
  `approvedCandidateCommit`, `observedRemoteBaseline`
- Task state consistency: all 15 valid states enumerated, unknown states rejected
- Summary count consistency: declared vs actual task state counts must match
- Handoff file reference existence (from parsed YAML)
- Iteration task reference existence (from parsed YAML, with `js-yaml`)
- Missing iteration directory causes failure
