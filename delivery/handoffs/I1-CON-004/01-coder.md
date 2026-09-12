---
role: coder
taskId: I1-CON-004
previousState: CLAIMED
resultingState: SELF_VERIFIED
baselineCommit: 2e33e01d3e5a5c6362c269289dc84ccdd72b6087
impactLevel: L2
date: 2026-09-12T19:10:00Z
---

# I1-CON-004 — Coder handoff

## Implemented (candidate commits f8e33531 + f5b80da4)

1. **AsyncAPI exchange bindings aligned to the registry (authority)** — all
   13 business-domain channels → `ev.domain.v1`; `device.online` →
   `ev.device.telemetry.v1` (registry convention for device status events).
2. **Routing-key fixes** — `session.charging.started` channel:
   `charging.started` → `charging.session-started`;
   `station.capacity.restriction.created` channel:
   `capacity.restriction.created` → `capacity.restriction-created`
   (registry-authority alignment, disclosed adaptation).
3. **Two orphans registered** — `CapacityRestrictionCreated`
   (`com.evplatform.capacity.restriction-created.v1`, `*ex-domain`,
   BUSINESS, producer booking-session-service) and `DeviceOnline`
   (`com.evplatform.device.online.v1`, `*ex-device-telemetry`, TECHNICAL,
   producer device-integration-service) added to messages-v1.yaml (49→51),
   each with an executable 2020-12 schema promoted from the AsyncAPI inline
   payloads; AsyncAPI `$refs` repointed; schemaFormat/contentType style kept
   identical to siblings.
4. **Review fix (f5b80da4)** — `ChargingSessionStarted` message binding
   `messageType: charging.session.started` → `charging.session-started`
   (reviewer-recommended fix-in-task; closes the binding-drift class
   completely).
5. Channel keys kept in the existing doubled style (owner decision).

## Honest wording note (reviewer Finding 1)

The `CapacityRestrictionCreated` schema payload was **corrected to ARC-020
§6** ("Scope, interval, type and phase": restrictionId/scopeType/scopeRef/
intervalStart/intervalEnd/restrictionType + optional phase), not merely
promoted — the baseline inline shape (`ref/stationRef/startTime/endTime/
reason`) was the stale side. No producer or consumer ever shipped against
the inline shape; no committed contract existed for it.

## Verification

- `npm run contracts:verify` exit 0 (all 8 sub-gates; 55 schemas; registries
  51/51 schema targets; self-test 21/21) — independently re-executed by the
  contract reviewer.
- Manual 15/15 binding cross-check AsyncAPI↔registry: PASS (reviewer's
  table is the primary AC-01/AC-02 evidence — G3 does not detect this drift
  class; see Finding 3 follow-up).
- Compatibility: NON_BREAKING — shipped topology
  (`RabbitTopologyConfiguration`) already matched the registry, not the
  stale AsyncAPI; no consumer exists against old bindings; both new
  messages have no producer yet.

## Disclosed environment note

Local Node v22.12.0 vs `engines >=24` — advisory only; all gates green.
