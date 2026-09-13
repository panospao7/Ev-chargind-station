# I1-IAM-001 — SEC-P02 CSRF evidence (AC-04)

- **Task ID:** I1-IAM-001 (L3; packet approved via owner merge of planning PR #44)
- **Baseline commit:** 78a8ae9c; **Branch:** task/i1-iam-001-ci
- **Implementation commits:** a6bdd313 (phase 2 part 1 — CSRF token repository + Origin/Referer filter), 3da3d71e (phase 2 part 2), 0d1e9e02 (phase 2 part 3 — D3+D4 clusters and defect fixes), 4c368a7c (D2 suite)
- **Date:** 2026-09-13
- **Environment:** real PostgreSQL 18 via Testcontainers; MockMvc security chain

## SEC-P02 §6.4 required-test mapping (10 items)

| § | Item | Result | Evidence (one line) |
|---|---|---|---|
| 6.4 | 1. Token issuance — GET /api/v1/session/csrf issues a session-bound synchronizer token | PASS | `SecP02CsrfTests` item 1 — token bound to the authenticated session, served from `security_event_metadata` jsonb |
| 6.4 | 2. Valid token on mutation succeeds | PASS | item 2 — mutation with header `X-CSRF-TOKEN` matching the session token passes the filter |
| 6.4 | 3. Mutation without X-CSRF-TOKEN fails 403 CSRF_VALIDATION_FAILED | PASS | item 3 — missing header → 403 CSRF_VALIDATION_FAILED |
| 6.4 | 4. Token from another session fails | PASS | item 4 — foreign-session token rejected |
| 6.4 | 5. Stale token after session rotation fails | PASS | item 5 — token issued pre-rotation rejected post-rotation (CSRF token rotates with the session, SEC-P02 §6.1) |
| 6.4 | 6. Hostile Origin fails 403 ORIGIN_NOT_ALLOWED | PASS | item 6 — disallowed Origin → 403 ORIGIN_NOT_ALLOWED |
| 6.4 | 7. Forged Referer does not bypass Origin validation | PASS | item 7 — hostile Origin wins over a same-site Referer (hostile-Origin-wins-over-Referer) |
| 6.4 | 8. Form-encoded mutation fails | PASS | item 8 — `application/x-www-form-urlencoded` mutation → 403 CSRF_VALIDATION_FAILED (content-type restricted to the BCL path only; see defect 5) |
| 6.4 | 9. CSRF failure body reveals no sensitive state | PASS | item 9 — generic problem body only |
| 6.4 | 10. Wildcard CORS with credentials is impossible | PASS | item 10 — configuration-level assertion with documented adjustment (see deviation SCOPE-001 item c): mvcHandlerMappingIntrospector framework bean + null-CORS-config check + preflight status in {401, 403} |

## Defects found and fixed by the D3/D4 tests (phase 2 part 3, 0d1e9e02)

| # | Defect | Fix |
|---|---|---|
| 4 | `BffAccessDeniedHandler` mapped `CsrfException` → ACCESS_DENIED; `CsrfFilter` handles failures inline via its own `AccessDeniedHandler`, so CSRF failures emitted the wrong code | handler now maps `CsrfException` → CSRF_VALIDATION_FAILED |
| 5 | `application/x-www-form-urlencoded` was accepted on ALL mutations (parser-typing bypass surface) | form-urlencoded content-type restricted to the back-channel logout path only |
| 6 | `rotateRef` copied `security_event_metadata` verbatim into the new row — the CSRF token survived rotation, violating SEC-P02 §6.1 | new row receives `'{}'`; CSRF token rotates with the session (verified by item 5) |

All six defects (1)–(6) were found by the D1–D4 tests; no test was
weakened — existing tests were realigned to the corrected behavior.

## D4 back-channel logout matrix (BackChannelLogoutTests 8/8)

| Case | Result |
|---|---|
| Valid token (sub+sid match existing session) → session revoked | PASS |
| Garbage string → 400 | PASS |
| Unsigned JWT → 400 | PASS |
| Wrong `iss` → 400 | PASS |
| `nonce` present → 400, row NOT revoked | PASS |
| Missing `events` → 400 | PASS |
| Valid token, unknown subject → 200, 0 rows changed | PASS |
| sid-only token (no sub) → 200, 0 rows changed (documented current receiver behavior — store matches subject+sid) | PASS |

RS256 verification via injectable `JWKSource`; permissive `typ` verifier
(defect 3 in sec-p01-session.md); TOKEN_INVALID applicableOperations []
(BCL endpoint is internal).

## Test command and counts

```
.\mvnw.cmd -pl apps/bff -am test "-Dmaven.compiler.release=21"
```

SecP02CsrfTests 10/10 · BackChannelLogoutTests 8/8 · full BFF suite 57/57
+ test-support 16/16 — BUILD SUCCESS.

No secrets or personal data in this evidence.
