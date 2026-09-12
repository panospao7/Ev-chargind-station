---
role: coder
taskId: I1-DSC-001
previousState: CLAIMED
resultingState: SELF_VERIFIED
baselineCommit: 5a2ff4c5 (origin/main, PR #30 merge)
impactLevel: L2
date: 2026-09-12T20:30:00Z
---

# I1-DSC-001 — Coder handoff

## Implemented (candidate commits f008b71c + e4b64e46 + ee5f8f26)

**Phase 1 — contracts + migration (f008b71c):**
- `RESOURCE_NOT_FOUND` (404, non-retryable) added to problem-codes-v1.yaml
  (owner-approved scope extension, ARC-003 §14 authority; 30→31 codes).
- `getStationDetails` OpenAPI operation + `StationDetails` schema
  (owner-approved L2 extension); example aligned to the SEEDSTA0001 fixture.
- Discovery V2 migration: `station_search_projection` (public reference data
  only — no subject identifiers; §7.1 fields; named PK/UQ/CHECK constraints;
  §10 source-version + location indexes), `projection_checkpoint` (stream
  high-water mark + gap fields), Discovery's `inbox_message` (§8.2) and
  append-only `audit_event` (§8.4 + REVOKE to runtime role). Outbox and
  idempotency deferred (consumer-only slice, disclosed).

**Phase 2 — Java (e4b64e46):**
- `StationPublishedConsumer`: `@RabbitListener` (manual ack, concurrency 1)
  on `discovery.station.published` (quorum + DLQ, lazily declared, bound to
  `ev.domain.v1`/`station.published`). Pipeline: envelope validation →
  type filter → **payload-shape validation (fail-closed)** → aggregateid ==
  stationRef coherence guard → ONE transaction (inbox dedup insert →
  **per-aggregate version gate** → projection upsert with §7.1 SQL guard →
  monotonic checkpoint → audit) → ack. Poison → DLQ; gap → apply-immediately
  with gap recorded (owner-approved deviation DEV-I1-DSC-001-01).
- `StationSearchProjectionReader` + `PublicStationController`: listStations
  (bounding-box + haversine, limit clamp 1..100) and getStationDetails
  (404 → problem+json with RESOURCE_NOT_FOUND semantics).
- Discovery test suite (10 ordered tests on real PG18 + RabbitMQ 4.3).

**Fix round (ee5f8f26, from three independent reviews):**
- Fail-closed payload validation (`isNumber()` guards + required-field
  checks → `PAYLOAD_INVALID` → DLQ) — closes the security reviewer's
  fail-open coercion finding: Jackson 2.x `decimalValue()` silently
  coerces malformed numerics to zero; a malformed fact would have been
  projected at (0,0). Order 10 proves the DLQ path.
- All review MINORs: dead config keys removed, controller javadoc
  corrected, OpenAPI example aligned to fixture data, inbox CHECK named
  (`ck_inbox_processing_outcome`), aggregateid==stationRef guard +
  Order 9 test, gap evidence preserved on completed inbox rows,
  station_ref-leak absence assertions, honest test comments.
- Owner-approved deviation recorded: `delivery/deviations/I1-DSC-001/
  01-gap-strategy-deviation.md`.

## Verification

- Full module suite: **27/27 green** (discovery 11 + test-support 16),
  BUILD SUCCESS (release 21 diagnostic; CI temurin 25 authoritative).
- G3 `npm run contracts:verify` exit 0 (31 problem codes, 51 messages,
  55 schemas); delivery validator ALL CHECKS PASSED.
- Independent reviews: data PASS_WITH_FINDINGS (per-aggregate version gate
  APPROVED; transaction/ack discipline CORRECT), security
  PASS_WITH_FINDINGS (no blockers), general PASS_WITH_FINDINGS.

## Disclosed behaviors

1. Gap strategy: apply-immediately-from-full-snapshot (owner-approved
   deviation); checkpoint is a stream high-water mark, per-aggregate
   ordering via the projection row's source_version + SQL guard.
2. Unknown message types on the station.published queue → DLQ (misrouted,
   operator-visible).
3. Transient DB failures: `AmqpRejectAndDontRequeueException` — the Boot
   4.1.1 retry chain wiring was found inert with MANUAL acks during the fix
   round; the message dead-letters immediately instead of after bounded
   retries. Honest behavior; retry-chain restoration is booked as a
   follow-up (also applies to the STA stack — check its listener config).
4. Full JSON-schema payload validation (networknt) is the booked hardening
   follow-up; current validation is explicit field/type checks.
5. Audit PK named pre-application (V2 never applied to any shared
   environment — branch-local amendment, noted in the migration header).

## Out of scope (untouched)

STA service, docs/**, apps/**, simulator/**, infra/**, AsyncAPI, registry
messages, traceability statuses (flip at closeout with CI evidence).
