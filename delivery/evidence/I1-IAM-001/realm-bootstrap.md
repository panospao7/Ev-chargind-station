# I1-IAM-001 — Realm bootstrap evidence (AC-01)

- **Task ID:** I1-IAM-001 (L3; packet approved via owner merge of planning PR #44)
- **Baseline commit:** 78a8ae9c (origin/main after PR #44)
- **Branch:** task/i1-iam-001-ci
- **Implementation commit:** 0dc20d4e (phase 1)
- **Date:** 2026-09-13
- **AC-01 status:** PARTIALLY_VERIFIED — file + parse validation done; fresh-volume compose import demo NOT_RUN (assigned to tester, see Limitations)

## Realm import file

`infra/local/keycloak/realms/ev-local-realm.json` — 284 lines, hand-authored,
version-controlled, no secret material (see JWKS-placeholder note below).

Contents per SEC-001 §3/§4.1/§4.2 and SEC-IMP-01:

- Realm `ev-local`.
- Client `ev-bff`: confidential, Authorization Code + PKCE S256
  (`pkce.code.challenge.method` = S256), `clientAuthenticatorType:
  "client-jwt"` (private_key_jwt per SEC-001 §4.2), exact redirect URIs for
  the local BFF callback, back-channel logout attributes
  (`backchannel.logout.url` / `backchannel.logout.revoke.offline.tokens`),
  implicit and ROPC disabled.
- Seven `svc-*` service clients declared per SEC-001 §4.2 (no shared
  secrets; identity proofs are I1-IAM-002 scope).
- `security-test-client` — dev-only test client for the local profile
  (documented dev convention; the dev password convention is
  `evplatform_dev_only`; no production credential material).
- Seven users per SEC-001 §3, including driver verified-active, suspended
  account, and a privileged user marked MFA-capable
  (`requiredActions: ["CONFIGURE_TOTP"]`).

## Verified Keycloak 26.6 import facts

Verified against the keycloak 26.6.0 source and official Keycloak server
admin documentation (accessed 2026-09-13):

| Fact | Verified behavior |
|---|---|
| `clientAuthenticatorType: "client-jwt"` | = private_key_jwt client authentication |
| `use.jwks.string` + `jwks.string` | public-key embedding in the import (placeholder JWKS documented, see below) |
| `backchannel.logout.url` / `backchannel.logout.revoke.offline.tokens` | back-channel logout attributes honored on import |
| `requiredActions: ["CONFIGURE_TOTP"]` | marks the user MFA-capable at import |
| Realm import default | skip-if-exists — re-running compose does not clobber an existing realm |

## Key-generation procedure

`infra/local/keycloak/realms/README.md` documents the procedure for
generating the local signing keypair and filling the JWKS placeholders
(keys stay outside the repository; the committed file contains
placeholders only — no private key material is committed).

## Limitations / NOT_RUN

- **Fresh-volume compose import demo NOT_RUN.** The `docker compose up
  keycloak` (fresh volume) + realm-import verification step was not
  executed within the coder phase (step budget). AC-01 is therefore
  PARTIALLY_VERIFIED. This demonstration is assigned to the tester phase.
- **JWKS placeholders:** the realm JSON intentionally contains JWKS
  placeholder entries; the BFF-side `ClientKeyConfig` loads its private
  key from an environment-supplied path (fail-fast when absent) and never
  commits key material. Placeholder resolution is part of the local
  key-generation procedure above and is exercised by the fresh-volume
  demo (tester).

No secrets or personal data in this evidence.
