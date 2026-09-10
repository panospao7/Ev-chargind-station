---
role: coder
taskId: I0-CON-002
baselineCommit: c86af67ff996fd7329518cff4fb6141fb12f38e4
impactLevel: L2
date: 2026-09-09T22:30:00Z
---

# I0-CON-002 — Coder handoff

## What was rebuilt

1. **`lifecycles-v1.yaml`** — full DOM-002 §1.1–§1.19 (+§1.3a) reproduction: 20 lifecycles,
   122 states, 152 transitions, 0 broken endpoints; ARC-022 §16 prohibitions verified absent
   (no ChargingService, PENDING_HOLD, HOLD_EXPIRED, no BOOKING ACTIVE→CANCELLED). Owning
   services per ARC-001 §8.2/§10 ownership matrix. `[*]` initial pseudo-state (DOM-002 §1.6)
   accepted by the validator as a legal transition source.
2. **`problem-codes-v1.yaml`** — 30 unique codes; the 22 ARC-022 §16 canonical codes with
   exact statuses (correcting 6 status drifts, e.g. VERSION_CONFLICT 409→412,
   START_ATTEMPT_LIMIT_REACHED 429→409); 8 additional codes kept from ARC-018 §7/ARC-003 §14.
   Fixed the unquoted `": "` scalar that made the old file unparseable.
3. **`policies-v1.yaml`** — 19 policies, each with `sourceRef`; corrected to the approved
   model: 5-minute hold (was 120 s), no standalone Capacity Service, no event sourcing,
   canonical service names, authorization-consumption at local commit (DOM-002 §1.2a).
4. **`messages-v1.yaml` + 35 new schemas** — the W1 message set per ARC-022 §15 (32 events +
   6 commands); 3 pre-existing schemas reused; 42 total schemas compile under 2020-12;
   all 38 registry schema targets exist; AsyncAPI doc still validates.
5. **`traceability-v1.yaml`** — 42 W1 requirement rows from REQ-001's matrix (31 W1-S1,
   11 W1-S2), all `status: PLANNED` (never VERIFIED without CI evidence — CON-176), 0 OPEN.
6. **`check-registries.js`** — accepts `[*]` initial pseudo-state.

## Judgment calls flagged for contract review

- ARC-020 vs ARC-022 device-fact lists diverge; ARC-022 §15 taken as authoritative for the
  W1 set (it is authoritative for executable message contracts), ARC-020 supplying
  classification/routing/minimum-data.
- The three capacity commands use `com.evplatform.command` exchange (ARC-020 §3 defines no
  inter-service command exchange) — flagged for owner awareness.
- `commands/start-charging-command.json` (pre-existing) lacks ARC-020 §7 `issuedBy`/`expiresAt`
  metadata; left unchanged — minimal enrichment is an owner decision.
- traceability `apis` carry REQ-001's logical interfaces (not REST paths); `dbTables` empty
  (table mapping lands with ARC-022 DATA work packages).

## Verification

`npm run contracts:verify` → exit 0 (all eight validators); `contracts:self-test` 21/21;
`contracts:asyncapi` exit 0; `git diff --check` clean. See `delivery/evidence/I0-CON-002/`.
