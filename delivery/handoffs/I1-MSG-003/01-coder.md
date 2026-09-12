---
role: coder
taskId: I1-MSG-003
previousState: CLAIMED
resultingState: SELF_VERIFIED
baselineCommit: c79d5b4d (origin/main, PR #28 merge)
impactLevel: L2
date: 2026-09-12T17:00:00Z
---

# I1-MSG-003 — Coder handoff

## Implemented (candidate commits 96d8608a + 993f1c60)

Send-time envelope enrichment in `OutboxDispatcher` (the planner-decided
seam — wire extensions always derived from the authoritative §8.1 columns,
never duplicated at write time):

- `claimBatch()` RETURNING extended with `correlation_id`, `causation_id`,
  `aggregate_ref`, `aggregate_version`, `classification`; `OutboxRow` record
  extended accordingly. Claim protocol, ordering, LIMIT, SKIP LOCKED, lease
  untouched.
- `enrichedPayload(OutboxRow)`: parses the persisted envelope, adds
  `dataschema` (static message_type→$id map; unmapped → omitted, disclosed),
  `correlationid`, `aggregateid`, `aggregateversion` (JSON number),
  `causationid` only when non-null, `classification` (owner-approved
  addition closing the ARC-004 §4 six-attribute conformance gap), never
  `traceparent` (no ambient trace context — not fabricated).
- `dispatchOnce()` sends the enriched envelope; all confirm/UNROUTABLE/CAS
  logic byte-for-byte unchanged. Javadoc authority label corrected to
  ARC-020 §2 / ARC-004 §4.

## Tests

- Order 4 (wire): per consumed envelope asserts dataschema == schema $id and
  correlationid/aggregateid/aggregateversion/classification == the outbox
  columns (wire==columns is the AC property); causationid and traceparent
  asserted ABSENT for seed facts (honest disclosure).
- ContractSchemaValidationTest: enriched envelope validates against
  cloud-event.json (extensions as additional properties); enriched data
  validates against station-published-event.json; column-equality
  assertions; raw-payload validation and remove-`id` negative control
  retained unchanged.
- Suite: **24/24 STA + 16/16 test-support, BUILD SUCCESS** (release 21
  diagnostic; CI temurin 25 authoritative on the PR).

## Disclosed behaviors and characteristics

1. Malformed payload in the dispatcher now lands in the catch path →
   retry/quarantine instead of sending garbage (planner-flagged improvement).
2. Unmapped message_type → no `dataschema` (single-family map; fail-fast
   deliberately avoided per AC-03; flagged for the second message family).
3. Seed facts self-correlate: `correlation_id == message_id` (fixture
   characteristic; tests assert against columns, not the coincidence).
4. `causationid` non-null branch untested (owner chose follow-up deferral —
   recorded as a tracked item, not silently dropped).
5. Jackson was already on the main classpath (BOM-managed) — no pom change.
6. Pre-existing `isAck()` deprecation warning unchanged.

## Out of scope (untouched)

OutboxWriter, Seeder, contracts/**, docs/**, other services, migrations,
application.yml, AsyncAPI contentType alignment (I1-CON-004).
