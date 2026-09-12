---
role: coder
taskId: I1-DSC-002
previousState: CLAIMED
resultingState: SELF_VERIFIED
baselineCommit: 753c31ab (origin/main, PR #35 merge)
impactLevel: L2
date: 2026-09-13T00:30:00Z
---

# I1-DSC-002 — Coder handoff

## Implemented (candidate commits ea359e47 + 79ead124 + 50f25bed + de89ecc8)

**Phase 1 — STA producer (ea359e47):**
- Seed emits 15 facts atomically: 2 StationPublished + 4 EVSEConfigurationChanged
  + 8 ConnectorConfigurationChanged + 1 TariffPublished (deterministic ids
  ...0301–...0315, subjects station/<publicRef>, evse/<uid>,
  connector/<ref>, tariff/<versionRef>).
- `connectorRef(uid, type)` helper shared between persistence and emission —
  persisted rows and emitted facts cannot drift.
- Dataschema map extended to 4 families; unmapped types now fail-fast
  (markAttempt DATASCHEMA_UNMAPPED → QUARANTINED) — closes the I1-MSG-003
  tracked finding.
- Tests updated honestly: per-type counts, 15-row wire proof with per-family
  dataschema + routing key, ContractSchemaValidationTest validates every row
  against its family schema (inventory 2/4/8/1).

**Phase 2 — Discovery (79ead124):**
- V3 migration: evse_search_projection, connector_search_projection,
  tariff_public_projection (owner-approved naming) — all named constraints,
  §7.1 fields, §10 indexes, NO FKs (orphan handling in consumer).
- Queue renamed discovery.sta.domain with 4 routing-key bindings; consumer
  renamed StaDomainConsumer with a per-family pipeline: fail-closed payload
  validation, aggregateid coherence per family, per-family version gates
  (family's own row source_version + §7.1 SQL guard), single stream
  checkpoint (GREATEST), per-family audit actions.
- EVSE orphan handling: missing station row → ORPHAN_FACT → bounded requeue
  (3) → DLQ; retryable-inbox semantics (FAILED rows are retryable, only
  COMPLETED/SKIPPED are duplicates — exactly-once preserved, data-reviewer
  traced and APPROVED).
- API: connectorType/minPowerW filters (EXISTS via the projections),
  enriched StationDetails (evses with connectors, tariff with components,
  totalEvses); openingHours omitted per owner decision.

**Fix round 1 (50f25bed):** totalEvses on LIST (owner-approved),
int-range guards, routing-key binding proof, STA fail-fast test, V3
pre-application index amendment, runtime DDL-denial assertion.

**Fix round 2 (de89ecc8):** phantom-connector fix (LEFT JOIN miss → empty
connectors list, never ConnectorView(null,0); DEPTHSTA-B/C1 test),
per-family duplicate coverage, geo+type combined filter, minPowerW boundary
proof both sides, attempt_count single-increment, explicit hasNonNull guards.

## Verification

- Discovery **16/16** + STA **25/25** green (release 21 diagnostic).
- G3 exit 0 (74 warnings unchanged — nullable idiom kept per owner).
- Independent reviews: data PASS_WITH_FINDINGS (orphan/retry exactly-once
  APPROVED; shared checkpoint APPROVED), contract PASS_WITH_FINDINGS (wire
  facts PASS per family; registry byte-for-byte; NON_BREAKING), general
  findings fixed in two rounds (phantom connector MAJOR closed).

## Disclosed behaviors

1. Connector-family leniency (no orphan check) — invisible to API joins.
2. DATASCHEMA_UNMAPPED uses the full 3-attempt budget before QUARANTINED.
3. Tariff selection organization-scoped (highest ACTIVE version).
4. totalEvses counts projected (configured) EVSEs — not availability.
5. V3 amended pre-application (index addition; never applied to any shared
   environment).

Full disclosure list with follow-ups: delivery/evidence/I1-DSC-002/discovery-depth.md.
