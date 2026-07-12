---
taskId: I0-DEL-001
evidenceType: static-validation
validator: scripts/delivery/validate.ps1
date: 2026-07-12T23:50:00Z
status: PASS
---

# Control plane integrity evidence

## Positive validation

Command:
```
powershell -File scripts/delivery/validate.ps1 -StatusFile delivery/status.yaml
```

Result: ALL CHECKS PASSED

Checks performed:
- SHA hex content and length
- Task state summary consistency
- Handoff file reference existence
- Iteration task reference existence

## Negative validation

Three controlled-invalid fixtures demonstrate that the validator rejects violations:

| Fixture | Violation | Result |
|---------|-----------|--------|
| `scripts/delivery/tests/duplicate-key.yaml` | Duplicate YAML key `state:` | REJECTED |
| `scripts/delivery/tests/invalid-sha.yaml` | Non-hex and short SHA values | REJECTED |
| `scripts/delivery/tests/unknown-state.yaml` | Unknown task state `GARBAGE` | REJECTED |
