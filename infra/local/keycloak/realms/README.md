# Keycloak realm imports — `ev-local` (I1-IAM-001 / I1-IAM-002)

This directory is mounted read-only into the Keycloak container at
`/opt/keycloak/data/import` (see `infra/local/compose.yaml`). With
`--import-realm`, Keycloak imports realm files placed here **on startup,
skipping any realm that already exists** (`skip-if-exists` semantics).

## Contents

`ev-local-realm.json` defines the local development realm (SEC-001 §4.1
realm separation — one realm per environment; this is the non-production
`ev-local` instance):

| Client | Flow | Service account | Client authentication |
|---|---|---|---|
| `ev-bff` | Authorization Code + PKCE S256 (standard flow) | no | `private_key_jwt` (`client-jwt`) |
| `svc-account`, `svc-station-operations`, `svc-booking-session`, `svc-device-integration`, `svc-discovery-insights`, `svc-notification`, `svc-governance-support` | none (service identity only; SEC-P04) | yes | `private_key_jwt` (`client-jwt`) |
| `security-test-client` | direct-access grants only (dev-only negative tests) | no | `client-secret` |
| `rogue-exchange-client` | service account only (dev-only token-exchange negative test) | yes | `client-secret` |

All browser flows (implicit, ROPC/password grant) are disabled on every
client except the ROPC-only `security-test-client`, which exists solely for
automated negative tests (SEC-001 §4.2).

## Standard Token Exchange mechanism (I1-IAM-002, SEC-P03)

The BFF exchanges a user session access token for an audience-limited
downstream token via **Standard Token Exchange V2** (decision record:
`delivery/deviations/I1-IAM-002/PLAN-001-v2-exchange-mechanism.yaml`;
official docs: https://www.keycloak.org/securing-apps/token-exchange,
accessed 2026-09-14 — "FGAP not needed for standard token exchange"). The
realm configures the V2 mechanism as follows; **no FGAP admin-permission
objects are created**:

1. **Per-client switch** — the `ev-bff` client carries the attribute
   `standard.token.exchange.enabled: "true"` (Standard token exchange
   switch). Only clients with this attribute may use the token-exchange
   grant.
2. **Client policy** — top-level realm keys `clientProfiles` and
   `clientPolicies` define profile `ev-token-exchange-downscope` with the
   `downscope-assertion-grant-enforcer` executor (no configuration object —
   the docs specify none), bound by policy `ev-bff-token-exchange-policy`
   through a `client-attributes` condition matching
   `standard.token.exchange.enabled: "true"`. This enforces that the
   `audience` parameter can only **downscope**: it cannot grant an audience
   the subject token does not already carry.
3. **Audience mappers** — each of the 7 `svc-*` clients carries an
   `oidc-audience-mapper` protocol mapper (`self-audience`) that adds its
   own client id to the access-token `aud` claim, so an exchanged token for
   a target service carries that service's audience.
4. **Rogue client** — `rogue-exchange-client` (confidential, service
   account enabled, client secret `evplatform_dev_only_rogue`, all flows
   off except the service account, **no** standard-token-exchange attribute)
   exists solely for the unauthorized-client negative proof: the same
   token-exchange request from this client must be rejected by Keycloak.

**Empirical-confirmation note (doc-absence items DA-1/DA-2 of the decision
record):** the exact attribute key string
(`standard.token.exchange.enabled`) and the exact realm-import
representation keys for `clientProfiles`/`clientPolicies` are not stated
verbatim in the official documentation. Both are to be confirmed
empirically via an Admin REST round-trip in the Testcontainers harness
(import → read back → exchange succeeds for `ev-bff`, fails for
`rogue-exchange-client`). If either item proves inexpressible or
ineffective, implementation STOPS and reports.

Test users (SEC-001 §3 — synthetic identities only, `@evplatform.local`):

| User | Initial state |
|---|---|
| `driver-local` | verified, active, no mandatory MFA |
| `operator-owner-local` | active membership, MFA-capable (`CONFIGURE_TOTP` required action) |
| `operator-tech-local` | restricted role, MFA-capable (`CONFIGURE_TOTP` required action) |
| `removed-operator-local` | membership revocation is exercised later by the Account domain (I1-IAM-003) |
| `platform-admin-local` | MFA-capable (`CONFIGURE_TOTP` required action) |
| `suspended-account-local` | valid identity, application-ineligible |
| `security-reviewer-local` | audit-read persona |

No roles are assigned in this slice; realm roles arrive with
I1-IAM-002/I1-IAM-003.

## Dev-only credentials

**LOCAL DEV ONLY — NEVER PRODUCTION.** These values are documented
development defaults for a loopback-only stack (ENG-001 doc §3). They must
never be replaced with, or reused as, real credentials.

| Principal | Credential |
|---|---|
| All 7 test users (password) | `evplatform_dev_only` |
| `security-test-client` (client secret) | `evplatform_dev_only` |
| `rogue-exchange-client` (client secret) | `evplatform_dev_only_rogue` |

Passwords are hashed by Keycloak at import time; the JSON file contains no
private key material by design (SEC-001 §4.1: realm configuration is
version-controlled without private keys or secrets).

## Client signing keys (`jwks.string` placeholders)

Every `client-jwt` client carries a placeholder
`"<PLACEHOLDER_JWKS_...>"` in its `use.jwks.string` / `jwks.string`
attributes. **These placeholders must be replaced with a
developer-generated public JWKS for the `private_key_jwt` client
assertion flow to function locally.** Keycloak validates the client's
signed JWT assertion against this embedded JWKS.

### 1. Generate a private key per client (outside the repository)

One 2048-bit RSA key per client, stored **outside the repo** (never
committed):

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.evplatform\dev-keys" | Out-Null
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 `
  -out "$env:USERPROFILE\.evplatform\dev-keys\ev-bff.pem"
# repeat for each svc-* client: svc-account.pem, svc-station-operations.pem, ...
```

### 2. Derive the public key

```powershell
openssl rsa -in "$env:USERPROFILE\.evplatform\dev-keys\ev-bff.pem" -pubout `
  -out "$env:USERPROFILE\.evplatform\dev-keys\ev-bff.pub.pem"
```

### 3. Build the JWKS string for `jwks.string`

Convert the public PEM key into a single-line JWKS JSON document
(`{"keys":[{"kty":"RSA","use":"sig","alg":"RS256","kid":"...","n":"...","e":"AQAB"}]}`),
with Base64URL-encoded `n`/`e` moduli. Two documented options:

**Option A — node (no extra dependencies):**

```powershell
node -e "const c=require('crypto');const pem=require('fs').readFileSync(process.argv[1]);const k=c.createPublicKey(pem);const j=k.export({format:'jwk'});const jwks={keys:[{kty:j.kty,use:'sig',alg:'RS256',kid:'ev-bff-1',n:j.n,e:j.e}]};console.log(JSON.stringify(jwks))" `
  "$env:USERPROFILE\.evplatform\dev-keys\ev-bff.pub.pem"
```

**Option B — pure openssl + manual Base64URL:** extract modulus and
exponent with `openssl rsa -in <pem> -noout -modulus` and
`openssl rsa -pubin -in <pub.pem> -text -noout`, hex-decode, Base64URL
encode (`[Convert]::ToBase64String(...).TrimEnd('=').Replace('+','-').Replace('/','_')`
in PowerShell), then assemble the JWKS by hand. Option A is strongly
preferred.

### 4. Paste into the realm JSON

Replace the placeholder string value of `jwks.string` for each client with
the generated one-line JWKS JSON (JSON-escaped as an ordinary string
value). Use a distinct key per client and per environment (SEC-001 §4.1:
no shared signing keys). The matching **private** key PEM is what the BFF /
services use to sign their client assertions at runtime — supply it via
environment (e.g. `BFF_OAUTH_CLIENT_PRIVATE_KEY_PATH`), never commit it.

## Import behavior

- Import runs **only on Keycloak startup** and only for realms that do not
  already exist (`skip-if-exists`). Editing this file after a first import
  has **no effect** on an existing realm.
- For a clean bootstrap from the edited file, remove the Keycloak
  container volume first:

  ```powershell
  docker compose -f infra/local/compose.yaml down
  docker volume rm evplatform-local_keycloak-data 2>$null
  # dev-file H2 storage lives inside the container/volume; recreating it
  # re-imports from this directory
  docker compose -f infra/local/compose.yaml up -d --wait keycloak
  ```

  (If no named volume holds state for the dev-file DB, simply recreating
  the container is sufficient.)
- Verify import in the logs: `Realm "ev-local" imported`, or check
  `http://127.0.0.1:8180/realms/ev-local/.well-known/openid-configuration`.

## Scope note

This realm supports the I1-IAM-001 slice (realm/client bootstrap + BFF
session proof) and the I1-IAM-002 slice (Standard Token Exchange V2
mechanism: per-client switch, downscope client policy, svc-* audience
mappers, rogue-exchange-client negative-test identity). Realm roles, group
structure and MFA policy enforcement arrive with I1-IAM-003.

## Dev-key procedure reminder

The `private_key_jwt` flow (both the ev-bff login and every svc-* service
identity) still requires the developer-generated JWKS from the section
above: generate one RSA key pair per client **outside the repository**,
derive the public JWKS, and paste it into each client's `jwks.string`
placeholder before starting the stack. The BFF exchange client signs its
own client assertions with the ev-bff key via
`BFF_OAUTH_CLIENT_PRIVATE_KEY_PATH` — the same key the login flow uses.
