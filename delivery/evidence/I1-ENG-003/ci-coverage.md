# I1-ENG-003 — CI coverage evidence

- **Task ID:** I1-ENG-003 (L2; packet approval via PR merge)
- **Baseline commit:** 9eb14eec6ff5c676f7771ed09d5621898922a63d (PR #32 merge)
- **Candidate commit:** b0f5ef11 (branch task/i1-eng-003-ci-coverage)
- **Date:** 2026-09-12

## What changed

1. **`.github/workflows/service-tests.yml`** — path filters extended to
   `services/discovery-insights-service/**` (both `push` and `pull_request`
   triggers) and a new `discovery-insights` job added (same JDK 25 temurin
   setup, same pinned action SHAs, same `-am test` invocation as the STA
   job). No permission, secret, runner, or step changes (AC-04).
2. **`StationPublishedConsumer`** — the transient DB-failure path now
   rethrows the ORIGINAL `DataAccessException` instead of wrapping it in
   `AmqpRejectAndDontRequeueException`. Root cause (bytecode-verified during
   the I1-DSC-001 fix round): `AmqpRejectAndDontRequeueException` signals
   the container to reject immediately, bypassing the retry interceptor —
   so the yml-configured bounded retry (3 attempts, 500 ms) never ran and
   transient failures dead-lettered on first occurrence. With the rethrow,
   the yml-configured stateless retry retries, and the default
   `RejectAndDontRequeueRecoverer` dead-letters after exhaustion — matching
   the disclosed design. Javadoc updated (AC-03).
3. **STA verification** (AC-03): station-operations has NO `@RabbitListener`
   in production code — its dispatcher is `@Scheduled` polling with its own
   outbox-level retry arithmetic (`outbox.max-attempts`/`backoff-ms`, custom
   `@Value` keys, unaffected by the Boot 4.1.1 listener-retry rename). Its
   POC consumers live in tests only. No STA config change needed; verified
   clean.

## Local verification

- Discovery suite: **11/11 green** (release 21 diagnostic)
- STA suite: **24/24 green** (release 21 diagnostic)
- Delivery validator: ALL CHECKS PASSED (status/task bookkeeping)

## Authoritative CI status

**GREEN — verified via GitHub API:**

- PR run (head 2f6a4315): both jobs **success** — first CI execution of
  the Discovery suite (AC-01 proof)
- Merge commit run (ded6a06d, main): both jobs **success** — Station
  Operations Service (S1-01 seed): success; Discovery Insights Service
  (first slice): success (AC-02 no-regression proof)

AC-01/AC-02 satisfied by CI evidence; AC-03 by the consumer correction + STA
verification note; AC-04 by diff review (path filters + jobs only).

## Residual notes

- The `discovery.consumer.max-delivery-retries`/`gap-retry-attempts` keys
  were already removed in the I1-DSC-001 fix round; no dead config remains.
- Future services (Booking/Session, Device Integration) will need the same
  path-filter extension when their test suites land — the pattern is now
  established in this workflow.
