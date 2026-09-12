# I1-DSC-002 — Discovery depth evidence

- **Task ID:** I1-DSC-002 (L2; packet approved via owner merge of PR #35;
  owner decisions recorded in packet + DEV-I1-DSC-002-01)
- **Baseline commit:** 753c31ab (origin/main, PR #35 merge)
- **Candidate commits:** ea359e47 (phase 1: STA producer) + 79ead124 (phase 2:
  Discovery) + 50f25bed (fix round 1) + de89ecc8 (fix round 2)
- **Branch:** task/i1-dsc-002-depth
- **Date:** 2026-09-12

## What was delivered

The seed now emits **15 facts** (2 StationPublished + 4 EVSEConfigurationChanged
+ 8 ConnectorConfigurationChanged + 1 TariffPublished) in one atomic
transaction; Discovery consumes all four families into
`evse_search_projection` (4 rows), `connector_search_projection` (8 rows)
and `tariff_public_projection` (1 row: EUR, €0.48/kWh + €0.10/min); the
public API gains connectorType/minPowerW filters and enriched details
(EVSEs with connectors, tariff components, totalEvses). The W1-S1 MUST
depth of FR-DIS-02 is complete: prices and connector types are visible at
every station.

## Findings closed (three independent review rounds)

| Finding | Disposition | Proof |
|---|---|---|
| Data MAJOR-1: totalEvses missing on LIST | Owner chose implement; correlated COUNT subquery on all paths | Order 12/13 assertions (0 for FIXSTA flow, 2/3 for depth stations) |
| General MAJOR: phantom ConnectorView(null,0) for connector-less EVSEs | LEFT JOIN miss renders empty connectors list; EVSE still listed | Order 13: DEPTHSTA-B shows GR*SEED*C1 with `connectors: []`, no null/0 entries |
| Data MINOR: int-range wrap risk | maxPowerW/versionNumber int-range guards → PAYLOAD_INVALID | consumer validators |
| Data MINOR: routing-key bindings unproven | depth facts published via their REAL routing keys | Orders 6/13 (bindings positively exercised) |
| Data MINOR: fail-fast path untested | STA Order 13: unmapped type → 3 attempts → QUARANTINED, never PUBLISHED | MessagingFoundationTest |
| Data MINOR: tariff source_version index | V3 pre-application amendment (ix_tariff_public_source_version) | Order 1: 8 index names asserted |
| Data MINOR: DDL denial unasserted | Order 1 runtime CREATE TABLE denial (mirrors STA) | test |
| Contract F1: nullable idiom warning | Owner chose keep nullable+allOf (project pattern); 74 warnings unchanged | G3 output |
| General F2: per-family coverage to the packet's letter | connector+tariff duplicate cases, geo+type combined, boundary both sides | Orders 7/13 |
| General F3: attempt_count double-increment on gap completion | single increment per delivery | consumer SQL |
| General F4: NPE-route numeric validation | explicit hasNonNull guards, structured PAYLOAD_INVALID | consumer validators |

## Owner decisions applied (all recorded)

- tariff_public_projection naming (DEV-I1-DSC-002-01 §1) — ARC-022 §9 gap.
- openingHours deferred (DEV-I1-DSC-002-01 §2) — contract-true omission.
- totalEvses on LIST implemented; nullable+allOf kept.
- Dataschema fail-fast (closes the I1-MSG-003 tracked finding).

## Evidence-note disclosures (tracked, no code change)

1. Connector-family leniency: no orphan check (invisible to API joins;
   inert rows possible; reconciliation sweep is a follow-up).
2. DATASCHEMA_UNMAPPED runs the full 3-attempt budget before QUARANTINED
   (wording tightened vs the recorded decision; behavior bounded).
3. Tariff selection is organization-scoped (highest ACTIVE version);
   multi-tariff emission needs a follow-up decision.
4. Geo listing applies SQL LIMIT before the exact haversine filter
   (pre-existing from I1-DSC-001; can under-return near the radius edge).
5. Queue rename (discovery.sta.domain) orphans the old queue/binding on
   persistent brokers (local-dev hygiene; one-time cleanup note).
6. Depth enrichment issues three independent queries (non-atomic; acceptable
   under FR-PLT-06 advisory invariant).
7. totalEvses counts CONFIGURED (projected) EVSEs, not available ones —
   advisory availability is S1-03 (Booking-owned), correctly absent.

## Gates

- Discovery suite: **16/16 green**; STA suite: **25/25 green** (release 21
  diagnostic, disclosed)
- `npm run contracts:verify` exit 0 (74 warnings unchanged — nullable idiom
  kept per owner)
- Independent reviews: data PASS_WITH_FINDINGS (orphan/retry exactly-once
  APPROVED; shared checkpoint APPROVED), contract PASS_WITH_FINDINGS (wire
  facts per-family PASS; registry consistency byte-for-byte; NON_BREAKING),
  general PASS_WITH_FINDINGS→findings fixed (phantom connector MAJOR closed)

## Authoritative CI status

**ALL 11 CHECKS GREEN at PR head ae0490fb (verified via GitHub API
check-runs), owner merged as PR #36 (merge commit fe582619, 2026-09-13):**

- Discovery Insights Service (first slice): **success** (16 tests, JDK 25)
- Station Operations Service (S1-01 seed): **success** (25 tests, JDK 25)
- Flyway Migrations and Role Separation (PostgreSQL 18): **success**
  (Discovery V3 fresh-install + role separation)
- Required Aggregation: success; all 7 G3 sub-checks: success (OpenAPI with
  the extended shapes, registries, schemas, privacy, secrets, docs, self-test)

## Residual tracked items (non-blocking)

1. Reconciliation sweep for inert connector rows (data review).
2. ARC-022 §9 governance-hygiene: add tariff_public_projection to the list.
3. Opening hours: future schema/registry task (fix path documented in the
   deviation).
4. Multi-tariff selection semantics when a second tariff is emitted.
5. Geo LIMIT-before-haversine under-return (pre-existing; future task).
6. AC-02 evidence now covers all four families (per-family duplicate cases
   added); the station family's full gap/rebuild suite remains from
   I1-DSC-001.

No secrets or personal data in this evidence.
