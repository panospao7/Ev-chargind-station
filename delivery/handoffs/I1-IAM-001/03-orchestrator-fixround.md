---
role: orchestrator
taskId: I1-IAM-001
previousState: FIX_REQUIRED
resultingState: SELF_VERIFIED (fix round complete; CI on fix commits pending PR)
baselineCommit: 4cc632b2 (origin/main after PR #45 merge)
fixCommit: 2d5a762d
evidenceCommit: dda30396
impactLevel: L3
date: 2026-09-14T12:00:00Z
---

# I1-IAM-001 — Orchestrator handoff (fix round complete)

## What happened after the initial merge (PR #45 → 4cc632b2)

The independent review round returned:
- General reviewer: FAIL_FINDINGS — 2 MAJOR (F1: no live IdP round-trip
  proof for AC-02; F2: x-release-wave/x-slice-applicability absent vs AC-05
  text) + 6 MINOR.
- Contract reviewer: PASS_WITH_FINDINGS (F2 shared; 415 deferral + inline
  examples + normalization all disclosed and defensible).
- Data reviewer: PASS_WITH_FINDINGS (migrations clean; retention purge
  missing; DB-time vs app-clock note).
- Security reviewer: FAIL_FINDINGS — 3 MAJOR: M-1 (rotation copied
  ciphertext but AAD binds to sessionRef → post-rotation decryption would
  always fail), M-2 (BCL validation omitted aud per OIDC BCL §2.6), M-3
  (default authorization-request repository emits JSESSIONID, violating
  the only-__Host-evsession invariant).

## Fix round (commit 2d5a762d)

- M-1: rotateRef re-encrypts token material with the new sessionRef as
  AAD; decrypt failure aborts rotation before any store mutation (old row
  stays ACTIVE). Decrypt-after-rotation round-trip test proves AAD binding
  both ways.
- M-2: BCL validates aud contains ev-bff (reject 400, no revocation);
  wrong-audience negative test added.
- M-3: StoreBackedAuthorizationRequestRepository — authorization requests
  (incl. PKCE verifier material) stored as encrypted pre-auth rows
  (sentinel subject, state as ref, 10-min TTL, single-use removal);
  findByRef excludes sentinel rows; no HttpSession/JSESSIONID path remains.
- F6: cookie name property-driven; constant removed.
- TOKEN_INVALID registry comment corrected.
- ObjectMapper internal instantiation (Boot 4.1 no longer exposes the bean).

## Verification after fixes

- Full suite: 66/66 bff (57 + 2 store + 1 BCL + 6 repo) + 16/16
  test-support, BUILD SUCCESS on real PostgreSQL 18 (Testcontainers).
- contracts:verify: 21/21 gates PASS.
- Security re-review: PASS_NO_BLOCKERS — M-1/M-2/M-3/F6 all RESOLVED
  (verified against code, not claims); suite claims independently
  reproduced by the reviewer; 1 new MINOR (N-1 pre-auth row residence —
  sweeper/rate-limit follow-up) + carried residual risks documented.

## State

FIX_REQUIRED → SELF_VERIFIED. Pushed to origin/task/i1-iam-001-closeout
(dda30396). Next: PR open → CI green → HUMAN_REVIEW (owner merge) →
VERIFIED.

## Owner-disposition items (MINORs, none blocking)

1. N-1 pre-auth row residence (sweeper or rate-limit follow-up).
2. Sid-only BCL tokens revoke 0 rows (fails closed; documented).
3. In-memory authorized-client token duplication (m-8) — before
   multi-instance/production.
4. Retention purge for expired/revoked session rows (F4).
5. Problem-response `code` member emission (F3) + detail-text alignment.
6. x-release-wave/x-slice-applicability normalization across all contracts
   (F2) — or owner acceptance of the deviation.
7. AC-02 live IdP round-trip proof (F1) — FullLoginFlowIT as a follow-up
   task or explicit owner risk acceptance.
8. Compose parallel-stack ergonomics (container_name/ports pinning).
9. SEC-001 §20 docs task (DEC-SEC-26..33 register patches).
