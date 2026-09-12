---
role: orchestrator
taskId: I1-MSG-001
previousState: HUMAN_REVIEW
resultingState: FIX_REQUIRED
baselineCommit: fd9559833d89c8a7b02c7332c67f2255eb8d0cea
candidateCommit: d870e449800b6785908829df12b123b4c13d7c1b
mergeCommit: f9de0f4c (PR #24, owner merge, verified via git fetch + GitHub API check-runs)
impactLevel: L3
date: 2026-09-12T12:00:00Z
---

# I1-MSG-001 — Orchestrator closeout handoff

## Merge and CI verification (repository truth, not chat claims)

- Owner merged **PR #24** → merge commit `f9de0f4c` on `origin/main`; candidate
  `d870e449` is contained in the merge; tree diff candidate→merge is status
  bookkeeping only. Candidate diff: 13 files, 974 insertions — all within the
  packet's allowedFiles (the `status.yaml` change came from the separate
  orchestrator claim commit `0020dfcb`, control-plane, consistent with
  I1-DAT-001/I1-STA-001 precedent; packet-vs-workflow conflict recorded as a
  packet-authoring finding, not a scope violation).
- **CI evidence verified via GitHub API at the merge commit** (gh CLI absent
  locally): "Station Operations Service (S1-01 seed): success"; "Flyway
  Migrations and Role Separation (PostgreSQL 18): success". Both required
  checks for AC-05 are green.

## Independent post-merge specialist reviews (packet-required; authoring session was coder+tester only)

Four read-only reviews (general, contract, data, security) executed against
`fd955983..d870e449`. Orchestrator revalidated all load-bearing claims
against the code before accepting them. Findings were deduplicated:

### MAJOR (must fix — task cannot close VERIFIED with these open)

- **M1 (silent event loss)** — `OutboxDispatcher` awaits
  `CorrelationData.getFuture()` and checks only `isAck()`; no
  `ReturnsCallback`/returned-message handling exists. With mandatory flagging
  + publisher returns enabled, RabbitMQ acks an unroutable mandatory message
  after `basic.return`. A `StationPublished` with no bound queue is therefore
  marked PUBLISHED and silently lost — defeating the outbox no-loss guarantee
  for the exact window this task creates (no consumer queue until
  I1-DSC-001). Confirmed by orchestrator inspection. Violates doc 14 §4 and
  FR-PLT-03 quarantine intent.
- **M2 (vacuous dedup test / evidence integrity)** — the consume loop
  `basicGet`s until the queue is empty; every message is delivered exactly
  once, so `assertTrue(first)` can never observe a duplicate. The "forced
  duplicate consumption deduplicated … single effect" claim in
  `04-tester.md` and `messaging-foundation.md` is not exercised. AC-03's
  required negative case is untested. Confirmed by orchestrator inspection.
- **M3 (dispatcher claim protocol)** — `pendingBatch()` has no
  `FOR UPDATE SKIP LOCKED`/claim CAS; `markPublished` has no state guard. The
  packet-required concurrency case ("two dispatcher instances do not
  double-publish") has no test and was not reported NOT_RUN. Confirmed by
  orchestrator inspection.
- **M4 (AC-05 contract validation)** — no code or test validates the emitted
  payload/envelope against `station-published-event.json` /
  `cloud-event.json`; `contracts:verify` validates the contracts repo, not
  the emitted payload. The contract reviewer supplied supplementary manual
  ajv evidence (example valid; seed-shape valid) — accepted as interim
  evidence, not as the AC-05 test.

### MINOR (bounded, fold into the fix round)

- m1: unnamed multi-column unique constraints in V3 (repo rule: explicit
  stable names; forward `RENAME CONSTRAINT` migration required — V3 itself is
  immutable).
- m2: `ON CONFLICT DO NOTHING` without conflict target in `OutboxWriter`
  (masks PK collisions with different facts).
- m3: routing-key derivation is a string heuristic; diverges from registry
  keys for command families (latent; no current defect — only
  StationPublished is dispatched).
- m4: `outbox.*` config keys nested under `spring:` in application.yml while
  `@Value` reads top-level `outbox.*` — all `OUTBOX_*` env overrides are
  inert (defaults coincide; behavior unchanged). Confirmed by orchestrator
  inspection.
- m5: missing ARC-022 §10 `idempotency expiry` index (deferred to W3
  retention work; additive V4 item).
- m6: `seed-reset` profile cannot run with its documented invocation (runtime
  role hits the audit REVOKE and lacks ALTER TABLE); document the migrator
  override or gate the profile.
- m7: seed atomicity lacks a failure-injection proof (success path only).
- m8: evidence lacks the required run references (`requiredRunReference: true`)
  — orchestrator verified via API for this record; the evidence file should
  cite them.

### NOTE-level (recorded, no action required now)

Envelope omits `dataschema`/`correlationid`/`aggregateversion` extensions
(needed before I1-DSC-001 consumer work — flag in its planning); markPublished
lacks PENDING guard; constant backoff + synchronous per-message confirms
(disclosed W1-S1 decision); inbox/idempotency state lists are implementation
choices pending spec traceability; topology declares four exchanges beyond the
packet line (benign, registry-anchored); pre-existing AsyncAPI exchange-name
drift unchanged, not worsened; audit append-only control verified non-vacuous
by the data reviewer (migrator-run REVOKE is load-bearing; runtime-role
negative test is real); no secrets or personal data in messages or logs;
secret scan PASS.

## Verdicts

- Contract review: **PASS_WITH_FINDINGS** (contract conformance PASS across
  envelope/payload/topology/registry; `contracts.affected=false` claim holds).
- Security review: **PASS_WITH_FINDINGS** (no blockers; data-in-message and
  secret-scan audits PASS; fail-closed prod credential guard noted).
- General review: **FAIL** (4 MAJOR).
- Data review: **FAIL** (3 MAJOR).
- Aggregate: **FIX_REQUIRED** per AGENTS.md §15 (task cannot proceed with
  unresolved MAJOR findings).

## State transition

`I1-MSG-001: HUMAN_REVIEW → FIX_REQUIRED` (fix round tracked as
**I1-MSG-002**, L3, packet drafted in this closeout). Merge and CI remain
valid; this is not a revert. The owner's merge of PR #24 stands as the human
gate for the foundation as-implemented; the fix round closes the review
findings before Discovery consumption begins.

## Human decisions required

1. Approve the I1-MSG-002 fix packet (L3 authorization, per I1-MSG-001
   precedent — planning-PR merge = approval).
2. Disposition on M3: implement the claim protocol, or accept a deviation
   that double-publish is tolerated by design under at-least-once + inbox
   dedup (owner call; recommendation: implement `FOR UPDATE SKIP LOCKED`
   claim — small, removes the ambiguity, satisfies the packet as written).
3. Owner merge of the I1-MSG-002 fix PR after CI green.

## Recommended next step

Owner reviews this closeout + the I1-MSG-002 packet; on approval, coder
implements the fix round on a dedicated branch, tester re-runs the focused
suite plus the new negative tests, and I1-DSC-001 planning proceeds in
parallel (its packet must note the envelope-extensions NOTE and the
AsyncAPI drift).
