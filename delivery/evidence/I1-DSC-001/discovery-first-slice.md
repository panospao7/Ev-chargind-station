# I1-DSC-001 — Discovery first slice evidence

- **Task ID:** I1-DSC-001 (L2; packet approved via owner merge of PR #28;
  scope extensions owner-approved: RESOURCE_NOT_FOUND code, details endpoint,
  gap-strategy deviation)
- **Baseline commit:** 5a2ff4c5 (origin/main, PR #30 merge)
- **Candidate commits:** f008b71c (phase 1) + e4b64e46 (phase 2) + ee5f8f26 (fix round)
- **Branch:** task/i1-dsc-001-discovery
- **Environment:** Windows, Docker 27.3.1, JDK 21 diagnostic (`-Dmaven.compiler.release=21`), PG 18 + RabbitMQ 4.3 Testcontainers
- **Date:** 2026-09-12

## What was delivered — the first user-visible slice of the S1 journey

STA seed → `StationPublished` on `ev.domain.v1`/`station.published` →
Discovery consumer (inbox-deduplicated, per-aggregate version-validated,
fail-closed) → `station_search_projection` → **public API**:
`GET /api/v1/stations` (geo list) and `GET /api/v1/stations/{stationRef}`
(details). The seed's two fixture stations are discoverable end-to-end.

## Findings closed (from the three independent reviews)

| Finding | Disposition | Proof |
|---|---|---|
| Data F-1 (MAJOR): gap-strategy deviation unrecorded | Owner approved deviation DEV-I1-DSC-001-01 | delivery/deviations/I1-DSC-001/ |
| Data F-2: unnamed inbox CHECK | Named `ck_inbox_processing_outcome` + Order 1 assertion | V2 + test |
| Data F-3: gap evidence erased on completion | `VERSION_GAP` preserved on completed rows + Order 4 assertion | consumer + test |
| Data F-6: aggregateid vs stationRef unvalidated | Coherence guard + Order 9 DLQ test | consumer + test |
| Security 1: fail-open numeric coercion | `isNumber()` + required-field guards → `PAYLOAD_INVALID` DLQ + Order 10 test | consumer + test |
| Security 2: misleading transient classification | Permanent payload defects classified `PAYLOAD_INVALID`; DB failures `TRANSIENT_DB_FAILURE` | consumer |
| General F-1: false javadoc claim | Corrected (URI anchoring booked in follow-up) | controller |
| General F-4: test-comment honesty | Comment fixed + explicit absence assertions | test |
| General F-6: dead config keys | Removed (both) | yml + consumer |
| General F-7: OpenAPI example honesty | Aligned to SEEDSTA0001 fixture | OpenAPI |
| General F-5 (partial): rebuild procedure | Deviation + evidence file document it; operator-docs pass is a follow-up (docs/** prohibited this task) | this file |

## AC status (local evidence; CI is authoritative)

- AC-01 (dedup): PASS — Order 2/3 (single effect, no duplicate audit row)
- AC-02 (version validation): PASS — Order 4 (gap recorded, §7.1 guard,
  older skipped); strategy per owner-approved deviation
- AC-03 (projection correctness + privacy): PASS — Order 2/8 (schema scan
  + wire assertions)
- AC-04 (API): PASS — Order 6 (list, geo filter, details, 404 problem+json)
- AC-05 (rebuild): PASS — Order 7 (atomic clear + re-consume → identical state)
- AC-06 (failure handling): PASS — Order 5 (poison→DLQ, stream continues),
  Order 9/10 (permanent-failure classes)
- AC-07 (contracts/traceability): PASS locally — G3 exit 0; traceability
  status flips PLANNED→IMPLEMENTED only at closeout with CI evidence

## Gates

- Full module suite: **27/27** (discovery 11 + test-support 16), BUILD SUCCESS
- `npm run contracts:verify` exit 0; `node scripts/delivery/validate.mjs` ALL CHECKS PASSED
- Independent reviews: data PASS_WITH_FINDINGS (gate APPROVED), security
  PASS_WITH_FINDINGS (no blockers), general PASS_WITH_FINDINGS

## Authoritative CI status

**GREEN at merge commit 2c7e5f86 (PR #31, owner merge 2026-09-12), verified
via GitHub API (check-runs + workflow-runs):**

- Database Migrations (PG18): **success** (V2 fresh-install + role separation,
  real PostgreSQL 18)
- G3 Contract Validation: **success** (all 9 sub-checks incl. OpenAPI with the
  details operation, registry 31 codes, privacy scan)
- Service Tests (JDK 25): **NOT_RUN — workflow path filter does not cover
  services/discovery-insights-service/** (pre-existing gap, exposed by this
  task; owner-approved close with follow-up I1-ENG-003 to extend the
  workflow). Local 27/27 (release 21 diagnostic) stands as the test evidence;
  the gap is disclosed, not hidden.

## Residual tracked items (non-blocking)

1. Boot 4.1.1 listener retry chain inert with MANUAL acks (transient DB
   failures dead-letter immediately) — restore bounded retry; check STA's
   listener config for the same pattern.
2. Full JSON-schema payload validation (networknt) — hardening follow-up.
3. Remaining ARC-003 §14 general problem codes — small contract task before
   their first API use.
4. Problem-code type-URI anchoring in the registry — fold into (3).
5. Operator-facing rebuild/runbook documentation — docs pass.
6. G3 AsyncAPI↔registry binding cross-check automation (from CON-004).
7. Multi-instance consumption (checkpoint hot row) — future scaling task.

No secrets or personal data in this evidence.
