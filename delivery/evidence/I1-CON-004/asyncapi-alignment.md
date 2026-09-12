# I1-CON-004 — AsyncAPI alignment evidence

- **Task ID:** I1-CON-004 (L2; packet approved via owner merge of PR #28;
  scope extension owner-approved during implementation planning)
- **Baseline commit:** 2e33e01d3e5a5c6362c269289dc84ccdd72b6087 (PR #29 merge)
- **Candidate commits:** f8e33531 (implementation) + f5b80da4 (review fix)
- **Branch:** task/i1-con-004-asyncapi
- **Date:** 2026-09-12

## Drift closed

| Drift | Baseline | Corrected |
|---|---|---|
| 13 business-domain channels bound to `station`/`organization`/`booking`/`session` exchanges | stale side | all → `ev.domain.v1` (registry anchor) |
| `device.online` bound to `device` exchange | stale side | → `ev.device.telemetry.v1` |
| `ChargingSessionStarted` channel routingKey `charging.started` | stale side | → `charging.session-started` |
| `ChargingSessionStarted` messageType binding `charging.session.started` | stale side | → `charging.session-started` (f5b80da4) |
| `CapacityRestrictionCreated` / `DeviceOnline` not in registry, no executable schemas | gap | registered (49→51) + schemas created + `$refs` repointed |

## Primary evidence

1. **Manual 15/15 binding cross-check** (contract reviewer, independent):
   every channel's exchange+routingKey matches the registry — full table in
   the review record. This is the AC-01/AC-02 proof; G3 does not
   mechanically detect this drift class.
2. `npm run contracts:verify` exit 0 (independently re-executed by the
   reviewer): OpenAPI 0 errors; AsyncAPI valid; 55 schemas compile (2020-12);
   registries 51 messages, 51/51 schema targets; privacy scan clean;
   secretlint clean; self-test 21/21.
3. Compatibility: NON_BREAKING — shipped topology already matched the
   registry; no consumer against old bindings; new messages have no producer
   yet.

## Residual tracked items

1. **G3 tooling gap** (reviewer Finding 3): `check-registries.js` does not
   cross-check AsyncAPI bindings against the registry — book a small
   follow-up task to add the cross-check so this drift class is mechanically
   caught henceforth.
2. New registry entries omit `description:` lines (sibling style delta) —
   docs-pass follow-up.
3. AsyncAPI declares 15 of 51 registered messages — acceptable per packet
   assumption; the registry remains the complete catalogue authority.

No secrets or personal data in this evidence.
