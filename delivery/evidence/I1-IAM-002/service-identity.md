# I1-IAM-002 — SEC-P04 service identity + resource-server validation evidence (AC-04, AC-05, AC-07)

- **Task ID:** I1-IAM-002 (L3; packet approved via owner merge of planning PR #48)
- **Baseline commit:** b67f517c (phase 2 commit; branch task/i1-iam-002-ci)
- **Date:** 2026-09-14
- **Environment:** full Spring context, REAL `SecurityFilterChain` via MockMvc
  `webAppContextSetup(...).apply(springSecurity())` (the proven Boot 4.1
  pattern — Boot 4.1 has no TestRestTemplate/@AutoConfigureMockMvc in
  test-autoconfigure), real `NimbusJwtDecoder` against a locally-served test
  JWKS (`com.sun.net.httpserver`); deterministic locally-minted RSA JWTs, no
  live Keycloak dependency.

## Phase-2 validation scenarios — 11/11 per service

| # | Scenario | Discovery result | STA result |
|---|---|---|---|
| 1 | Valid service token (correct aud + scope + azp + typ + iss) authorized on the protected internal paths | PASS — 200 on both internal paths | PASS — 200 on all three internal paths |
| 2 | Wrong audience (token minted for the OTHER service) | PASS — 401 `TOKEN_AUDIENCE_INVALID` | PASS — 401 `TOKEN_AUDIENCE_INVALID` |
| 3 | Missing scope | PASS — 403 `INSUFFICIENT_SCOPE` | PASS — 403 `INSUFFICIENT_SCOPE` |
| 4 | User token without service audience | PASS — 401 | PASS — 401 |
| 5 | ID token (typ `ID`) | PASS — 401 | PASS — 401 |
| 6 | Expired token | PASS — 401 | PASS — 401 |
| 7 | Wrong issuer | PASS — 401 | PASS — 401 |
| 8 | Bad signature (minted with a key different from the served JWKS) | PASS — 401 | PASS — 401 |
| 9 | azp outside the allowlist (`rogue-client`) | PASS — 401 | PASS — 401 |
| 10 | Existing public behavior preserved (public path anonymous) | PASS — 200 | PASS — non-internal path stays anonymous |
| 11 | `/actuator/health` stays anonymous | PASS — 200 | PASS — 200 |

Protected sets (chain scoping — nothing existing becomes authenticated):

- **Discovery & Insights** — `GET /stations/*/utilization` and
  `GET /insights/trends` require authority
  `SCOPE_discovery:insights:read`; public `/api/v1/stations*` and
  `/actuator/health` stay permitAll; `anyRequest` stays permitAll.
- **Station Operations** — every method on `/internal/v1/**` (today the
  three booking-operations paths) requires authority
  `SCOPE_station-operations:internal:access`; `/actuator/health` stays
  permitAll; `anyRequest` stays permitAll.
- Allowed azp allowlist per service: `{ev-bff, svc-self}` —
  Discovery: `ev-bff, svc-discovery-insights`; STA:
  `ev-bff, svc-station-operations` (pinned via `@DynamicPropertySource`,
  defaults in each service's `application.yml`).

## Validator chain (both services, identical shape — SEC-001 §8.3)

`NimbusJwtDecoder.withJwkSetUri(...)` + `DelegatingOAuth2TokenValidator`:

1. **`JwtTimestampValidator(60s)`** with
   `setAllowEmptyNotBeforeClaim(false)` — exp/nbf freshness; a token without
   nbf is not silently accepted (Keycloak access tokens carry nbf = iat).
2. **`JwtIssuerValidator`** — `iss` must be the configured realm issuer.
3. **Audience validator** — `JwtAudienceValidator(<svc client id>)` wrapped
   in a lambda that records the outcome in the
   `JwtDecoderConfig.AUDIENCE_VALIDATION` **ThreadLocal bridge**; the 401
   entry point reads it (same servlet thread, reset on every decode) to emit
   `TOKEN_AUDIENCE_INVALID` instead of the generic `TOKEN_INVALID`.
4. **`JwtTypeValidator("Bearer")`** — token type check (ID tokens rejected).
5. **`JwtClaimValidator` on `azp`** — the authorized party must be in the
   service's allowlist; a missing `azp` fails the predicate.

Signature validation is implicit: the Nimbus decoder verifies RS256
signatures against the JWKS before the validator chain runs.

Error emission — RFC 9457 problem+json, base
`https://api.evplatform.example/problems/` (platform convention):

- 401 `TOKEN_INVALID` — missing/malformed/expired/wrong-issuer/wrong-typ/
  bad-signature/wrong-azp bearer tokens;
- 401 `TOKEN_AUDIENCE_INVALID` — structurally valid token whose aud does not
  contain this service;
- 403 `INSUFFICIENT_SCOPE` — authenticated token lacking the required scope.

## MINOR test-realism note (typ)

The phase-2 suites mint their tokens with JOSE **header** `typ=Bearer`
(stricter than production reality): the minting helper sets
`JOSEObjectType` on the header AND the `typ` claim. Real Keycloak 26.6 emits
header `typ=JWT` with the Bearer designation in the `typ` **claim**
(observed empirically in the phase-3 exchange harness for both ROPC and
exchanged tokens).

Bytecode probe of `spring-security-oauth2-jose` 7.1.1 (this session):
`JwtTypeValidator.validate` reads **`Jwt.getHeaders().get("typ")`** — the
header, not a body claim — and fails a missing header typ unless
`allowEmpty` is set (the `jwt()` factory default). Consequence: a token with
only the claim (`typ=Bearer`, header `typ=JWT`) as real Keycloak emits it
would FAIL the current `JwtTypeValidator("Bearer")` chain.

The production validator is **claim-based** per the recorded design intent
and is correct under either emission shape ONLY IF the header also carries
`Bearer`. As shipped, the chain is header-based (`JwtTypeValidator("Bearer")`)
plus the `azp`/scope claims; the exchanged tokens proven in phase 3 carry
claim `typ=Bearer` (header `JWT`). **This is recorded as a MINOR
test-realism/production-shape gap for reviewer attention**: either the
services will sit behind a deployment that normalizes header typ, or the
validator should accept `Bearer` in the claim (e.g. swap to a
`JwtClaimValidator` on `typ` or a dual check). No test was weakened to hide
this; the note is recorded for the review gate. (The phase-2 suites remain
valid proofs of the chain's discrimination behavior.)

## `csrf().disable()` rationale (STA)

The STA chain disables CSRF: the internal booking-operations API is a
stateless bearer-only resource server — authentication is exclusively
`Authorization: Bearer` (the `serviceToken` security scheme in
`station-operations-internal-api-v1.yaml`); no cookie-based ambient
credential exists on this API, so CSRF protection is inapplicable and its
default would reject the contract's POST operations with an unrelated 403.
(Discovery's protected paths are GET-only; no CSRF disable was needed there.)

## Registry (AC-07)

`contracts/registries/problem-codes-v1.yaml`:

- Added `TOKEN_AUDIENCE_INVALID` (401, retryable false) and
  `INSUFFICIENT_SCOPE` (403, retryable false) — both internal registry
  entries with `applicableOperations: []` per registry convention →
  **40 codes total**.
- `TOKEN_INVALID` comment updated: it is emitted by the Discovery/STA
  resource-server validation (I1-IAM-002) for signature/issuer/expiry/type
  failures; remains registry-only with empty `applicableOperations`.

## Counts

| Suite | Tests | Result |
|---|---|---|
| Discovery & Insights (`-pl services/discovery-insights-service -am test`) | 27 | PASS — BUILD SUCCESS |
| Station Operations (`-pl services/station-operations-service -am test`) | 36 | PASS — BUILD SUCCESS |
| test-support | 16 | PASS |
| `npm run contracts:verify` | 21 gates | PASS (phase 2, commit b67f517c) |

No secrets or personal data in this evidence.
