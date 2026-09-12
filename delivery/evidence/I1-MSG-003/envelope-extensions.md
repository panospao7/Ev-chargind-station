# I1-MSG-003 — Envelope extensions evidence

- **Task ID:** I1-MSG-003 (L2; packet approved via owner merge of PR #28)
- **Baseline commit:** c79d5b4d (origin/main)
- **Candidate commits:** 96d8608a (implementation) + 993f1c60 (review micro-fixes)
- **Branch:** task/i1-msg-003-envelope
- **Environment:** Windows, Docker 27.3.1, JDK 21 diagnostic (`-Dmaven.compiler.release=21`), Maven wrapper
- **Date:** 2026-09-12

## What changed

StationPublished wire envelopes now carry the full ARC-020 §2 / ARC-004 §4
extension set, derived from the outbox §8.1 columns at send time (single
source of truth; persisted payload unchanged; pre-existing rows enriched
uniformly):

| Attribute | Source | Notes |
|---|---|---|
| `dataschema` | static type→$id map | executable schema $id; unmapped types omit (disclosed) |
| `correlationid` | `correlation_id` column | always emitted (NOT NULL) |
| `aggregateid` | `aggregate_ref` column | |
| `aggregateversion` | `aggregate_version` column | JSON number (ARC-004 §4.1 example) |
| `causationid` | `causation_id` column | only when non-null; omitted otherwise |
| `classification` | `classification` column | owner-approved addition (ARC-004 §4 six-attribute conformance) |
| `traceparent` | — | never emitted; not fabricated |

## Verification

- Local focused suite: **24/24 STA + 16/16 test-support, BUILD SUCCESS**
  (diagnostic JDK 21).
- Independent contract review: **PASS_WITH_FINDINGS** — attribute-by-attribute
  conformance PASS vs ARC-020 §2 with ARC-004 §4 cross-check; contracts
  untouched verified; G3 (`npm run contracts:verify`) exit 0 executed by the
  reviewer; no vacuous assertions; NON_BREAKING (additive optional
  attributes, no consumer exists yet).
- Review findings disposition: F1 classification emission — owner chose
  **emit now** (implemented in 993f1c60); F2 authority label ARC-014→ARC-020 —
  fixed; F3 causationid non-null test — **owner chose follow-up deferral**
  (tracked below); F4 unmapped-dataschema fail-fast — flagged for the second
  message family; NOTEs (contentType drift = I1-CON-004 scope; javadoc
  exception-type precision; test $id duplication; seed self-correlation)
  recorded.

## Authoritative CI status

`NOT_RUN at evidence time — CI_PENDING on push.` Required check: Service
Tests (JDK 25 temurin). Run reference to be appended at closeout:

- Service Tests: PENDING

## Residual tracked items

1. causationid non-null branch test (owner-deferred follow-up).
2. Unmapped message_type → silent dataschema omission — must become
   fail-fast or registry-derived before a second message family is emitted.
3. contentType application/json vs AsyncAPI application/cloudevents+json —
   I1-CON-004 scope.
4. isAck() deprecation — dependency-bump decision.
5. Seed correlationid == id fixture characteristic — documented; Discovery
   must not assume correlationid always references a prior workflow.

No secrets or personal data in this evidence.
