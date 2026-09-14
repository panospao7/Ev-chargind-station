# I1-IAM-001 — SEC-P01 session evidence (AC-02, AC-03, AC-06)

- **Task ID:** I1-IAM-001 (L3; packet approved via owner merge of planning PR #44)
- **Baseline commit:** 78a8ae9c; **Branch:** task/i1-iam-001-ci
- **Implementation commits:** 0dc20d4e (phase 1), a6bdd313 + 3da3d71e (phase 2 parts 1–2), 0d1e9e02 (phase 2 part 3), 4c368a7c (D2 suite)
- **Date:** 2026-09-13
- **Environment:** real PostgreSQL 18 via Testcontainers; real provisioning (V1+V2 migrations applied by Flyway); MockMvc security chain

## SEC-P01 §5.4 required-test mapping (13 items)

| § | Item | Result | Evidence (one line) |
|---|---|---|---|
| 5.4 | 1. Browser storage contains no OAuth token | PASS | `SecP01SessionTests` item 1 — no token material in any response body/cookie; browser receives only the `__Host-evsession` opaque cookie |
| 5.4 | 2. Cookie contract attribute-exact | PASS | item 2 — `__Host-evsession`; Secure, HttpOnly, Path=/, no Domain, SameSite=Lax asserted on the emitted Set-Cookie |
| 5.4 | 3. Session ID changes after login | PASS | item 3 — pre-auth and post-auth session references differ (rotation on authentication) |
| 5.4 | 4. Step-up rotation | NOT_RUN | SEC-P07 (MFA/step-up) deferred per packet nonGoals; op deferred with documented rationale |
| 5.4 | 5. Idle expiry (30 min boundary) | PASS | item 5 — `MutableClock` crosses the idle boundary; expired-vs-absent distinction yields SESSION_EXPIRED |
| 5.4 | 6. Absolute expiry (8 h) is not extended by activity | PASS | item 6 — activity refreshes idle timer only; absolute cap enforced via clock control |
| 5.4 | 7. Logout invalidates the BFF session | PASS | item 7 — same cookie no longer authenticates after logout |
| 5.4 | 8. Refresh after logout fails | PASS | item 8 — second request with the same cookie also fails |
| 5.4 | 9. Stolen pre-rotation session reference fails | PASS | item 9 — old reference rejected after rotation |
| 5.4 | 10. Valid back-channel logout token revokes the session | PASS | item 10 — RS256-verified BCL token with matching sub+sid revokes the local session |
| 5.4 | 11. Malformed logout tokens rejected without revoking | PASS | item 11 — garbage/unsigned/wrong-iss/nonce-bearing/missing-events tokens rejected (see also `BackChannelLogoutTests`) |
| 5.4 | 12. Another subject's session untouched by a BCL revoke | PASS | item 12 — cross-subject isolation |
| 5.4 | 13. Authenticated responses use Cache-Control: no-store | PASS | item 13 — no-store writer asserted on authenticated responses |

## Cookie contract assertion details (AC-02)

The `__Host-evsession` contract is proven via emitted-attribute assertions
(MockMvc `Set-Cookie` inspection) per the OQ-IAM-1 packet default:
cookie-contract proven via emitted-attribute assertions; local HTTPS
termination deferred to the UI slice. Assertions cover the cookie name,
Secure, HttpOnly, Path=/, absence of Domain, and SameSite=Lax.

## Defects found and fixed by the D1/D2 tests (phase 2)

| # | Defect | Fix | Commit |
|---|---|---|---|
| 1 | `rotateRef` copied the old `session_ref` into the new row — PK violation; rotation could never succeed | rotation generates a new reference for the new row | 3da3d71e |
| 2 | `@EnableWebSecurity` is no longer meta-annotated `@Configuration` in Security 7.1.1 — the `JWKSource` bean was never registered | explicit `@Configuration` on `SecurityConfig` | 3da3d71e |
| 3 | Nimbus 10.9.1 `DefaultJWTProcessor.setJWSTypeVerifier(null)` throws — every BCL token would have been rejected | permissive `typ` verifier added to the BCL processor | 3da3d71e |

(SEC-P02-specific defects (4)–(6) are recorded in
`sec-p02-csrf.md`; all six were found by the D1–D4 tests, none weakened.)

## Migration evidence (AC-06)

- `V1__baseline.sql` + `V2__bff_session.sql` (bff_session_db, service-owned
  migration path; grants to `bff_session_runtime`).
- `BffSessionMigrationTests` 4/4 on real PostgreSQL 18 (Testcontainers):
  V1+V2 fresh apply, repeatable, runtime role cannot DDL (negative),
  runtime DML grant works.
- Encrypted token material (`bytea`) with versioned key id; AES-256 key
  ring supplied via environment (`SessionKeyRing` fail-fast on missing
  material); no key material committed.

## Test command and counts

```
.\mvnw.cmd -pl apps/bff -am test "-Dmaven.compiler.release=21"
```

| Suite | Tests | Result |
|---|---|---|
| TokenEncryptionServiceTests | 8 | PASS |
| BffSessionMigrationTests | 4 | PASS |
| JdbcSessionStoreTests | 7 | PASS |
| SecP01SessionTests | 11 | PASS (item 4 NOT_RUN inside the suite as documented) |
| SecP02CsrfTests | 10 | PASS |
| BackChannelLogoutTests | 8 | PASS |
| **BFF total** | **57** | **PASS — BUILD SUCCESS** |
| test-support | 16 | PASS |

No secrets or personal data in this evidence.
