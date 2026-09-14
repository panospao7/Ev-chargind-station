# I1-IAM-002 — SEC-P03 token-exchange evidence (AC-01, AC-02, AC-03, AC-06)

- **Task ID:** I1-IAM-002 (L3; packet approved via owner merge of planning PR #48)
- **Baseline commit:** b67f517c (phase 2 commit; branch task/i1-iam-002-ci, uncommitted phase-3 round on top)
- **Date:** 2026-09-14
- **Environment:** REAL Keycloak 26.6 via Testcontainers (digest-pinned, realm imported with generated test JWKS — see harness summary below) + real PostgreSQL 18 session store (same provisioning path as the other BFF test clusters); full Spring context; Flyway under the MIGRATOR role
- **Suite:** `apps/bff/src/test/java/com/evplatform/bff/exchange/TokenExchangeIntegrationTests` — **12/12 PASS**

## AC-01 — positive exchange (raw RFC 8693 + cache path)

Raw token-endpoint exchange in the exact form `TokenExchangeClient` sends
(subject token = real ROPC access token of `driver-local` carrying the
`ev-bff` audience; `client_assertion` = ev-bff private_key_jwt), then the
same resolution through `ExchangedTokenCache.getOrExchange`:

| Assertion | Result |
|---|---|
| HTTP status of the raw exchange | PASS — 200 |
| `aud` exactly `[svc-station-operations]` | PASS — `containsExactly(AUD_STATION)` |
| `sub` preserved from the subject token | PASS |
| `azp` = `ev-bff` (the requester) | PASS |
| `acr` preserved | PASS — equals the ROPC token's acr |
| `auth_time` preserved | PASS — equals the ROPC token's auth_time |
| Lifetime ≤ 300s (`exp − iat`) | PASS — the realm sets no `accessTokenLifespan` override, so Keycloak's 5-minute default applies |
| Token type | PASS — `typ` **claim** = `Bearer`. Probed empirically: real Keycloak 26.6 emits JOSE header `typ=JWT` with the Bearer designation in the `typ` claim (observed for both ROPC and exchanged tokens) |
| No refresh material | PASS — response body has no `refresh_token` |
| `issued_token_type` | PASS — `urn:ietf:params:oauth:token-type:access_token` |
| Cache path returns a usable token | PASS — `getOrExchange` → `Cached`, aud exactly `[AUD_STATION]` |

## SEC-001 §7.3 negative matrix (AC-02) — 9 items

| §7.3 item | Test | Result |
|---|---|---|
| Cross-service audience isolation (Account↔Booking-equivalent, realized as Discovery↔STA) | N1a/N1b — resource-server suites: Discovery rejects a token minted for `svc-station-operations`; STA rejects a token minted for `svc-discovery-insights` | PASS — 11/11 both services (`wrongAudienceIsRejectedWithTokenAudienceInvalid` → 401 `TOKEN_AUDIENCE_INVALID`) |
| Unsupported audience exchange fails | N2 — `getOrExchange(sessionRef, "unknown-client")` | PASS — `ExchangeFailure` with non-blank error |
| Scope escalation fails / no over-scoped token | N3 — structural: `TokenExchangeClient` sends **no `scope` parameter** (exact field set: grant_type, subject_token, subject_token_type, requested_token_type, audience, client_id, client_assertion_type, client_assertion); exchanged-token scopes are governed by the `downscope-assertion-grant-enforcer` executor bound via the `client-attributes` condition | PASS — verified in source + realm (DA-2 round-trip proves the policy imported) |
| Exchange by an unauthorized client fails | N4 — `rogue-exchange-client` (service-account enabled, `client_secret` auth, NO standard-token-exchange attribute) attempts the same grant | PASS — 400 `invalid_request`, observed description "Standard token exchange is not enabled for the requested client" (test asserts status 400 + non-blank OAuth error) |
| Expired/invalid subject token fails | N5 — self-crafted expired JWT (signed with the shared svc test key, `exp` 1h in the past) as subject_token | PASS — 400 + non-blank OAuth error |
| Revoked BFF session cannot exchange | N6 — `cache_d_revokedSessionYieldsSessionInvalid` (cache primed, then `lifecycle.revoke`) | PASS — `ExchangeFailure` with error `session_invalid`; no exchange occurs |
| No broad multi-audience token | N7 — exchange for BOTH configured targets | PASS — each exchanged token carries EXACTLY its own audience (`containsExactly`) for `svc-station-operations` and `svc-discovery-insights` |
| ID Token rejected as API token | N8 — ROPC `id_token` as subject_token | PASS — 400 + non-blank OAuth error; ID-typ rejection at the resource servers additionally proven in the phase-2 suites |
| User token without service audience rejected | N9 — resource-server suites: user-profile token (no service aud, no azp) | PASS — 401 `TOKEN_AUDIENCE_INVALID` (the suite's assertion admits the generic `TOKEN_INVALID` fallback; the minted token has no `aud`, so the audience validator deterministically flags the audience-failure path) |

## Cache lifecycle (AC-03) — a–e, all PASS

| # | Scenario | Result |
|---|---|---|
| a | Encrypted entry in the session row | PASS — `security_event_metadata` jsonb carries `exchangedTokens → <aud> → {ciphertext, keyId=v1, expiresAt}`; the raw DB row does NOT contain the plaintext access-token string |
| b | Cache hit without a second exchange | PASS — counting decorator proves no second HTTP exchange; same decrypted token returned |
| c | Expiry-skew re-exchange | PASS — shared `MutableClock` advanced +6min (unambiguously inside the 30s skew window of `expiresAt = mutableNow + 300s`): counter increments, new token differs. **Test-side clock substitution note:** the `ClientAssertionFactory` inside the counting client uses `Clock.systemUTC()` because Keycloak validates the assertion `iat`/`exp` against ITS real clock (an assertion built from the advanced mutable clock would be rejected as "issued in the future"); production BFF clock IS real time, so the substitution changes nothing about the code under test. Keycloak-side token validity is unaffected by the mutable clock throughout |
| d | Revoked session's cache unusable | PASS — `session_invalid` failure, no exchange |
| e | Cross-session AAD binding | PASS — session A's `exchangedTokens` fragment copied into session B's row: AES-GCM authentication fails (AAD = `sessionRef:aud`) → degrades to a cache miss → fresh exchange yields a genuine audience-limited token |

## DA-1 / DA-2 — empirically CLOSED (AC-06)

Admin REST round-trip against the live container realm
(`da1_da2_adminRoundTrip`, Order 12):

- **DA-1** — the `ev-bff` client attributes were read back via
  `GET /admin/realms/ev-local/clients?clientId=ev-bff`; the attribute key
  `standard.token.exchange.enabled` was **observed present = true** (every
  attribute key dumped for the evidence record). The realm import did not
  silently ignore the switch.
- **DA-2** — `GET /admin/realms/ev-local/client-policies/profiles` contains
  profile **`ev-token-exchange-downscope`** and
  `.../client-policies/policies` contains policy
  **`ev-bff-token-exchange-policy`** — both round-trip confirmed.

Both doc-absence items from the deviation file are closed; the STOP rule
did not trigger.

## Realm amendments recorded (addendum_1 + addendum_2)

1. **ev-bff: 7 svc-\* `oidc-audience-mapper` entries** (addendum_1) — V2
   builds the exchanged token under the requester's client session, so the
   target audiences must be reachable on **ev-bff** (`restrictRequestedAudience`
   only filters; `checkRequestedAudiences` fails otherwise).
2. **security-test-client: `aud-ev-bff` mapper** — ROPC subject tokens carry
   the `ev-bff` audience (the V2 rule that the subject token must carry the
   requester as audience).
3. **`downscope-assertion-grant-enforcer` executor carries
   `"configuration": {}`** (A2-1) — Keycloak 26.6.4 refuses realm import
   without a configuration node ("configNode must be not null"); the official
   docs specify none, the empty object satisfies the loader.
4. **`firstName`/`lastName` on all 7 imported users** (A2-2) — ROPC on an
   imported user without them fails VERIFY_PROFILE resolution ("Account is
   not fully set up"); after the fix ROPC for `driver-local` succeeds
   (HTTP 200, aud=ev-bff via the mapper, acr=1).
5. **`jwks.string` keys carry `use: sig` + `alg: RS256`** (A2-3) —
   `ClientPublicKeyLoader` filters client JWKS keys through
   `JWKSUtils.getKeyWrappersForUse(SIG)` and silently drops use-less keys
   ("Available kids: '[]'"); after the fix the client_assertion authenticates.
6. **ev-bff `defaultClientScopes` + `acr`** — `acr` is a separate realm
   default scope, not carried by `basic`; required so exchanged/ROPC tokens
   carry the acr claim the AC-01 preservation assertion checks.

## Harness design summary (`KeycloakTestHarness`)

- **Pinned digest** — same image as `infra/local/compose.yaml`:
  `keycloak/keycloak@sha256:0aae0de7fca85525f727d3354df17896092de8bb26ae4c12d89c77e5df8cbce4`,
  started `start-dev --import-realm --hostname-strict=false` with bootstrap
  admin env.
- **Temp realm with test JWKS** — the committed realm template's
  `<PLACEHOLDER_JWKS_*>` markers are replaced with generated (JSON-escaped)
  JWKS strings into a temp file copied into the container's import directory;
  the committed file is never modified.
- **Key handling** — two RSA pairs generated at start; the ev-bff pair is
  built EXACTLY like `ClientKeyConfig` (kid = RFC 7638 SHA-256 thumbprint) so
  the client_assertion kid matches the imported JWKS; its private key is
  written as a **PKCS#8 PEM temp file** and surfaced via the
  `bff.oauth.client-private-key-path` property. One shared svc-\* test key is
  reused for all seven svc clients (test-only, documented: the svc clients
  never sign anything in these tests).
- **Readiness poll** — after the port listens, the realm JWKS endpoint is
  polled until HTTP 200 (up to ~120s; realm import completes asynchronously).
- **ROPC subject-token source** — password grant for `driver-local` against
  `security-test-client` (`directAccessGrantsEnabled=true`); tokens are
  parsed in memory, never logged.
- **Admin helpers** — master-realm `admin-cli` ROPC → Bearer-authenticated
  `GET /admin/realms/ev-local/*` readers for the DA-1/DA-2 round-trip.
- Dev-convention credentials only (`evplatform_dev_only`,
  `evplatform_dev_only_rogue`); no token material in logs or evidence.

## Counts

| Suite | Tests | Result |
|---|---|---|
| TokenExchangeIntegrationTests (real Keycloak 26.6 + real PG18) | 12 | PASS |
| Full BFF suite (`-pl apps/bff -am test`) | 78 | PASS — BUILD SUCCESS |
| test-support | 16 | PASS |

No secrets or personal data in this evidence.
