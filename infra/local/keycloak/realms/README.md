# Keycloak realm imports — `ev-local` (I1-IAM-001)

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

All browser flows (implicit, ROPC/password grant) are disabled on every
client except the ROPC-only `security-test-client`, which exists solely for
automated negative tests (SEC-001 §4.2).

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
session proof). Realm roles, group structure, MFA policy enforcement and
token-exchange scopes arrive with I1-IAM-002/I1-IAM-003.
