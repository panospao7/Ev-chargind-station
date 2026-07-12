# Security Implementation Proof Plan v1.0

## Document metadata

- **Document ID:** SEC-001
- **Version:** 1.0
- **Status:** APPROVED
- **Owner:** Security Architect
- **Authoritative for:** W1 security proofs, Keycloak configuration profile, BFF session security, service identity, membership revocation, MFA, secret handling, abuse controls and audit protection
- **Refines:** ARC-003, ARC-007, ARC-009, ARC-019
- **Depends on:** REQ-002, ENG-001, ARC-022
- **Does not supersede:** the threat model, privacy design, service boundaries or domain authorization rules
- **Release applicability:** W1 cross-cutting
- **Approval rule:** No proof is complete without executable test evidence

---

# 1. Purpose

This document defines the security implementation proofs required before W1 business-feature implementation is considered secure enough to proceed.

It proves:

1. Browser tokens remain inside the BFF.
2. Session cookies and CSRF controls operate correctly.
3. Keycloak issues audience-restricted tokens.
4. Every service has an independent machine identity.
5. Dynamic organization membership is enforced after revocation.
6. MFA and recent authentication protect privileged operations.
7. Secrets and keys can be rotated without entering source control.
8. Abuse controls reject excessive requests safely.
9. Business and privileged audit evidence is append-only and tamper-evident.

This document is a testable implementation plan. It does not itself prove that the controls work.

---

# 2. Current baseline corrections

Before SEC-001 is approved, patch the following inconsistencies.

## 2.1 Keycloak status

ARC-007 currently describes Keycloak as preferred pending selection, while ARC-009 selects Keycloak conditionally on a proof of concept.

Canonical wording:

> Keycloak 26.6.x is the approved W1 Identity Provider, conditional on successful completion of SEC-P02 through SEC-P05.

## 2.2 Session-cookie name

Replace the generic OpenAPI cookie named `session` with:

`__Host-evsession`

The selected browser contract is:

- opaque session cookie;
- no OAuth token in browser JavaScript;
- session-bound CSRF token;
- same-origin browser/BFF deployment.

## 2.3 Authorization authority

Token roles provide coarse context only. They are not authoritative for:

- current organization membership;
- resource ownership;
- account eligibility;
- Booking state;
- support-case scope;
- break-glass grants.

Every service performs final authorization using authoritative data or a versioned, freshness-bounded projection.

## 2.4 Required executable registries

Add:

- `contracts/registries/authorization-v1.yaml`
- `contracts/registries/rate-limits-v1.yaml`
- `contracts/registries/security-events-v1.yaml`
- `contracts/schemas/security/delegated-action-assertion-v1.json`
- `contracts/schemas/security/audit-seal-v1.json`

---

# 3. Proof environment

The proof environment must contain:

- Angular or a minimal browser test client;
- BFF;
- Keycloak;
- BFF session PostgreSQL database;
- Account Service;
- Station Operations Service;
- Booking and Session Service;
- RabbitMQ where revocation events are tested;
- HTTPS for the browser, BFF and Keycloak;
- synthetic identities only;
- Playwright and backend integration tests.

Required synthetic users:

| User | Initial state |
|---|---|
| Driver | Verified, active, no mandatory MFA |
| Operator Owner | Active membership, MFA |
| Operator Technician | Restricted role, MFA |
| Removed Operator | Membership revoked during test |
| Platform Administrator | MFA and privileged assurance |
| Suspended Account | Valid identity but application-ineligible |
| Security Reviewer | Audit read permissions only |

The proof must run from a deterministic Keycloak realm import and automated bootstrap. No manual console-only configuration is accepted as final evidence.

---

# 4. Keycloak realm and client topology

## 4.1 Realm separation

Use one realm per environment:

- `ev-local`
- `ev-test`
- `ev-staging`
- `ev-production`

Rules:

- no shared signing keys;
- no development users outside non-production;
- no wildcard production redirect URIs;
- administration endpoints are not publicly exposed;
- realm configuration is version-controlled without private keys or secrets.

## 4.2 Client catalogue

| Client | Purpose | Browser flow | Service account | Client authentication |
|---|---|---|---:|---:|---|
| `ev-bff` | Browser login and target token exchange | Enabled | Disabled | `private_key_jwt` |
| `svc-account` | Account resource server/identity | Disabled | Enabled | `private_key_jwt` |
| `svc-station-operations` | Station authority | Disabled | Enabled | `private_key_jwt` |
| `svc-booking-session` | Booking/session authority | Disabled | Enabled | `private_key_jwt` |
| `svc-device-integration` | Device boundary | Disabled | Enabled | `private_key_jwt` |
| `svc-discovery-insights` | Public projections | Disabled | Enabled | `private_key_jwt` |
| `svc-notification` | Notification delivery | Disabled | Enabled | `private_key_jwt` |
| `svc-governance-support` | Governance/support | Disabled | Enabled | `private_key_jwt` |
| `security-test-client` | Automated negative tests | Restricted | Optional | Test-only credential |

For all clients:

- implicit flow disabled;
- password/direct-access grant disabled;
- exact redirect and logout URIs;
- no shared client secret;
- least-privilege scopes;
- separate keys per environment;
- explicit allowed signing algorithms.

Keycloak supports standard token exchange, signed-JWT client authentication, service accounts, session controls, TOTP and WebAuthn flows; these capabilities still require project-specific proof. ([keycloak.org](https://www.keycloak.org/securing-apps/token-exchange?utm_source=openai))

---

# 5. SEC-P01 — BFF cookie-session proof

## 5.1 Authentication flow

Prove:

1. BFF creates high-entropy state, nonce and PKCE verifier.
2. Authorization request uses Code Flow and PKCE S256.
3. Callback validates state, nonce, issuer and exact redirect binding.
4. Code exchange uses BFF `private_key_jwt`.
5. Tokens are stored only in the server-side session store.
6. Session ID rotates after authentication.
7. Browser receives no access or refresh token.
8. Session ID rotates after privilege elevation or step-up.
9. Logout removes the local BFF session.
10. Keycloak-initiated logout removes mapped BFF sessions.

Spring Security supports servlet OAuth clients, token exchange, session fixation protection and explicit CSRF handling. ([docs.spring.io](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html?utm_source=openai))

## 5.2 Cookie contract

Production-like proof cookie:

```text
Name: __Host-evsession
Secure: true
HttpOnly: true
Path: /
Domain: absent
SameSite: Lax
```

Initial lifetimes:

| Property | W1 value |
|---|---:|
| Idle timeout | 30 minutes |
| Absolute lifetime | 8 hours |
| Privileged idle timeout | 15 minutes |
| Recent-authentication window | 5 minutes |
| User access token | 5 minutes |
| Service access token | 5 minutes |

`SameSite=Strict` remains optional pending successful login/logout usability tests.

A local HTTP convenience profile may use a different explicitly labelled cookie, but it does not satisfy SEC-P01.

## 5.3 Session persistence

The BFF session store records:

- opaque session reference;
- Keycloak subject;
- Keycloak session ID;
- encrypted OAuth token material;
- authentication context;
- `acr`;
- authentication time;
- creation time;
- last activity;
- idle expiry;
- absolute expiry;
- revocation state;
- token encryption key ID.

Arbitrary Java serialization is prohibited.

OAuth material is encrypted using authenticated encryption and a versioned key ring.

## 5.4 Required tests

- browser storage contains no OAuth token;
- cookie attributes are exact;
- session ID changes after login;
- session ID changes after step-up;
- idle expiry works;
- absolute expiry works;
- logout invalidates the BFF session;
- refresh after logout fails;
- stolen old session ID fails after rotation;
- Keycloak back-channel logout invalidates the local session;
- malformed logout token is rejected;
- session lookup by another subject fails;
- authenticated responses use `Cache-Control: no-store`.

---

# 6. SEC-P02 — CSRF proof

## 6.1 Selected model

Use a server-side synchronizer token:

- token is generated by Spring Security;
- token is bound to the BFF session;
- browser obtains it through `GET /api/v1/session/csrf`;
- Angular stores it in memory;
- mutations send `X-CSRF-TOKEN`;
- token is never used as authentication;
- token rotates after login and session rotation.

## 6.2 Mutation validation

Every browser-originated mutation requires:

- valid BFF session;
- valid CSRF token;
- exact allowed `Origin`;
- valid `Referer` where present;
- approved content type;
- non-simple JSON request where appropriate.

Safe methods may be exempt:

- `GET`
- `HEAD`
- `OPTIONS`

Login callback security is provided by state, nonce and PKCE rather than the application CSRF header.

## 6.3 OpenAPI changes

For every browser mutation, add:

- session-cookie security;
- required `X-CSRF-TOKEN` parameter;
- `403 CSRF_VALIDATION_FAILED`;
- `403 ORIGIN_NOT_ALLOWED`;
- `415 UNSUPPORTED_CONTENT_TYPE`.

Add:

- `GET /api/v1/session`
- `GET /api/v1/session/csrf`
- `POST /api/v1/session/logout`
- `GET /api/v1/session/step-up`

## 6.4 Required tests

- valid token succeeds;
- missing token fails;
- token from another session fails;
- stale token after session rotation fails;
- hostile `Origin` fails;
- wildcard CORS with credentials is impossible;
- form-encoded mutation fails;
- forged `Referer` does not bypass `Origin`;
- CSRF failure does not reveal sensitive state.

---

# 7. SEC-P03 — Target-audience token-exchange proof

## 7.1 Selected flow

The BFF receives a user token for itself, then uses Keycloak Standard Token Exchange to obtain a token for exactly one target service.

Example target audiences:

- `svc-account`
- `svc-station-operations`
- `svc-booking-session`

Standard Token Exchange V2 is the supported Keycloak path for same-realm audience changes. ([keycloak.org](https://www.keycloak.org/securing-apps/token-exchange?utm_source=openai))

## 7.2 Target token rules

An exchanged token must contain:

- expected issuer;
- human subject;
- exactly intended target audience;
- BFF authorized-party/client identity;
- approved scopes only;
- `acr`;
- authentication time;
- short expiry;
- token type.

It must not contain:

- all organization memberships;
- unrestricted platform roles;
- another service audience;
- secrets;
- refresh token material.

The BFF may cache exchanged tokens only within the originating session and only until shortly before expiry.

## 7.3 Negative proofs

Prove:

- Account token is rejected by Booking;
- Booking token is rejected by Station Operations;
- unsupported audience exchange fails;
- scope escalation fails;
- exchange by an unauthorized client fails;
- expired subject token fails;
- revoked BFF session cannot refresh or exchange;
- a broad multi-audience token is not issued;
- ID Token is rejected as an API token.

---

# 8. SEC-P04 — Service-identity proof

## 8.1 Initial profile

Each service uses:

- unique Keycloak client;
- unique asymmetric key pair;
- Client Credentials Grant;
- `private_key_jwt`;
- target-audience access token;
- maximum 60-second client assertion;
- unique assertion `jti`;
- five-minute service access token;
- TLS.

Shared service credentials are prohibited.

## 8.2 Client assertion validation

Keycloak must reject assertions with:

- wrong audience;
- wrong client;
- expired assertion;
- excessive lifetime;
- duplicate `jti`;
- unapproved algorithm;
- unknown key ID;
- invalid signature.

Keycloak supports signed client assertions without shared client secrets and permits algorithm/lifetime restrictions. ([keycloak.org](https://www.keycloak.org/docs/latest/server_admin/?utm_source=openai))

## 8.3 Resource-server validation

Each service validates:

- signature;
- allowlisted algorithm;
- issuer;
- audience;
- expiry;
- not-before;
- token type;
- calling client;
- required service scope.

Network location, forwarded actor headers and correlation IDs are never authorization evidence.

## 8.4 Service-scope examples

| Scope | Permitted caller/use |
|---|---|
| `station.booking-context.read` | Booking reads repair context outside locks |
| `booking.capacity-impact.read` | Station Operations previews impact |
| `booking.restriction.command` | Maintenance restriction workflow |
| `device.command.write` | Booking requests device action |
| `notification.request.write` | Approved services request delivery |
| `audit.projection.write` | Services publish audit projection |
| `membership.decision.read` | Sensitive authorization validation |

The final list belongs in `authorization-v1.yaml`.

---

# 9. SEC-P05 — Delegated human-action proof

## 9.1 Selected model

For a chained human action, the target receives:

1. source service access token;
2. compact signed Delegated Action JWS.

The JWS is not a generic bearer token.

## 9.2 Required JWS claims

- `iss`: source service;
- `sub`: human subject;
- `aud`: one target service;
- `iat`;
- `nbf`;
- `exp`: no more than 60 seconds;
- `jti`;
- exact operation ID;
- target resource type/reference;
- organization reference where applicable;
- membership reference and version;
- authentication context;
- authentication time;
- authorization-decision reference;
- correlation ID.

Header:

- explicit `typ`;
- allowlisted asymmetric `alg`;
- valid `kid`.

## 9.3 Recipient rules

The recipient must:

- verify the source service token;
- verify the JWS signature;
- require JWS issuer to match the calling service;
- verify audience;
- verify operation and resource binding;
- enforce one-use `jti`;
- check expiry;
- independently validate target lifecycle/invariants;
- reject unknown membership versions;
- record decision reference in audit evidence.

## 9.4 Negative tests

- altered organization fails;
- altered resource fails;
- assertion for another operation fails;
- assertion replay fails;
- assertion from wrong service fails;
- expired assertion fails;
- valid assertion cannot bypass Booking state rules;
- unsigned actor/organization headers are ignored.

---

# 10. SEC-P06 — Membership-revocation proof

## 10.1 Authority

Station Operations Service owns:

- organization membership;
- operator role;
- membership status;
- membership version;
- organization status.

Keycloak roles are coarse identity context only.

## 10.2 W1 authorization path

For operator writes:

1. Browser calls operator BFF.
2. BFF obtains a Station Operations audience token.
3. Station Operations checks membership authoritatively.
4. If another service must act, Station Operations issues a delegated assertion.
5. The target validates the assertion and its own business invariant.

This keeps normal membership authorization at its authoritative owner.

## 10.3 Membership event

Add canonical event:

`OrganizationMembershipChanged`

Payload:

- membership reference;
- account subject reference;
- organization reference;
- new state;
- approved role set;
- source version;
- effective time.

It contains no email or profile fields.

## 10.4 Projection behavior

Where another service requires a membership projection:

- apply only higher versions;
- duplicate version is idempotent;
- version gap marks projection `UNKNOWN`;
- `UNKNOWN` fails closed;
- repair uses Station Operations snapshot/API;
- revoked membership cannot be restored by an older event.

## 10.5 Targets

- membership projection propagation: 99% within 10 seconds;
- high-impact operations: authoritative decision required;
- normal assertion lifetime: at most 60 seconds;
- token lifetime is only a residual bound.

## 10.6 Required tests

- role removal immediately denies new Station Operations writes;
- revocation event invalidates projections;
- delayed old event cannot restore access;
- version gap fails closed;
- broker outage makes projection-dependent sensitive operations fail closed;
- pre-revocation token alone cannot authorize;
- active session may remain logged in but cannot perform removed actions;
- organization suspension blocks all organization writes.

---

# 11. SEC-P07 — MFA and step-up proof

## 11.1 Assurance levels

Use:

- `ev-loa1`: ordinary verified authentication;
- `ev-loa2`: password plus TOTP or WebAuthn/passkey.

Tokens used for protected operations include `acr` and authentication time.

## 11.2 Role requirements

| Actor | Baseline |
|---|---|
| Driver | `ev-loa1`; MFA optional |
| Operator Owner | `ev-loa2` |
| Operator Manager | `ev-loa2` |
| Operator Technician | `ev-loa2` |
| Operator Support | `ev-loa2` |
| Platform Administrator | `ev-loa2` |
| Platform Support | `ev-loa2` |
| Security Reviewer | `ev-loa2` |

Initial mandatory factor:

- TOTP.

Supported alternative:

- WebAuthn/passkey.

Recovery codes must be enabled for privileged users. SMS MFA remains excluded.

## 11.3 Step-up operations

Require `ev-loa2` and authentication within five minutes for:

- ownership transfer;
- role elevation;
- email change;
- privacy export download;
- account deletion confirmation;
- break-glass activation;
- emergency intervention;
- sensitive field reveal;
- machine credential issue/revocation.

The protected service returns an RFC 9470-compatible authentication challenge using `insufficient_user_authentication`, `acr_values`, and/or `max_age`. ([rfc-editor.org](https://www.rfc-editor.org/rfc/rfc9470.html))

## 11.4 Required tests

- operator without MFA cannot enter privileged routes;
- driver can use ordinary W1 flow without mandatory MFA;
- LoA1 token fails privileged action;
- step-up produces LoA2;
- authentication older than five minutes triggers reauthentication;
- session ID rotates after step-up;
- recovery code is one-use;
- removing MFA from a privileged user requires current MFA;
- revoked authenticator cannot be reused.

---

# 12. SEC-P08 — Session and account revocation proof

The BFF session table must be indexed by:

- application session reference;
- Keycloak subject;
- Keycloak `sid`.

Support:

- logout current session;
- logout all application sessions for subject;
- Keycloak back-channel logout;
- account-suspension session revocation;
- administrator emergency revocation;
- BFF token-encryption-key compromise response.

Rules:

- role removal does not depend on session revocation;
- account suspension revokes BFF sessions and application eligibility;
- outstanding access tokens expire within five minutes;
- business services still check current eligibility where required.

---

# 13. SEC-P09 — Secret and key-management proof

## 13.1 Secret classes

- service private keys;
- BFF client private key;
- BFF session-encryption keys;
- database credentials;
- RabbitMQ credentials;
- SMTP/provider credentials;
- object-storage credentials;
- webhook verification secrets;
- simulator enrollment credentials;
- certificate-authority keys.

## 13.2 Storage profiles

### Local

- generated through an initialization script;
- stored in ignored, permission-restricted files;
- mounted read-only;
- synthetic values only;
- no production keys;
- no committed realm client secrets.

### CI

- ephemeral test keys where possible;
- protected CI secret store for unavoidable credentials;
- no secrets exposed to untrusted pull requests;
- redaction tests.

### Staging/production-like

- selected external secret manager;
- workload-specific access;
- mounted file or short-lived credential delivery;
- audited access;
- no secret baked into an image.

OWASP ASVS requires managed secret storage, least privilege, and exclusion of secrets from source and build artifacts. ([cornucopia.owasp.org](https://cornucopia.owasp.org/taxonomy/asvs-5.0/13-configuration/03-secret-management?utm_source=openai))

## 13.3 Cryptographic baseline

W1 baseline:

- asymmetric JWT signatures only;
- explicit algorithm allowlists;
- unique `kid`;
- no `none`;
- no shared HMAC signing across services;
- authenticated encryption for BFF token storage;
- SHA-256 or stronger content hashes;
- TLS for every credential-bearing connection.

## 13.4 Rotation sequence

1. Generate new key.
2. Publish new public key.
3. Retain old verification key temporarily.
4. Begin signing/encrypting with new key.
5. Verify both versions operate.
6. terminate old signing use.
7. revoke old key.
8. verify old assertions fail after overlap.
9. record audit evidence.

## 13.5 Required proofs

- service-key rotation without outage;
- BFF token-encryption-key rotation;
- Keycloak realm signing-key/JWKS refresh;
- database credential rotation;
- RabbitMQ credential rotation;
- secret scanner rejects seeded test secret;
- logs, traces and broker payloads contain no secrets.

---

# 14. SEC-P10 — Rate-limit and abuse proof

All initial values are `PROVISIONAL_W1` and require load/abuse validation.

| Policy | Keys | Initial limit |
|---|---|---:|
| Public station search | IP | 120/minute |
| Registration | IP | 5/hour |
| Verification resend | account + IP | 3/hour account; 10/hour IP |
| Recovery request | identifier + IP | 3/hour identifier; 10/hour IP |
| Booking hold | account + IP | 10/minute; 30/hour account |
| Booking mutation | account | 20/minute |
| Check-in | booking + account | 6/10 minutes |
| Charging start | booking + account | 3/5 minutes booking |
| Operator write | actor + organization | 60/minute |
| Privileged action | actor | 10/minute |
| Emergency action | actor + organization | 3/hour |
| Simulator connection | machine + source | 10/minute |
| Simulator messages | machine | 180/minute |

## 14.1 Enforcement layers

- Keycloak: password spraying/brute-force controls;
- ingress/BFF: source/IP and session limits;
- authoritative service: account, booking, EVSE, organization and operation limits;
- Device Integration: machine-specific connection/message limits.

## 14.2 Rules

- return HTTP 429 and `Retry-After`;
- use generic responses for registration/recovery;
- rate limiting never replaces idempotency;
- rate limiting never protects allocation correctness;
- equipment failure is never classified as driver abuse;
- trusted proxy addresses are explicit;
- untrusted `X-Forwarded-For` is ignored;
- IPv4/IPv6 keys are normalized;
- public and authenticated budgets are separate.

A single-instance in-memory limiter is acceptable only for the local demonstrator. Multi-replica deployment requires a distributed or provider-edge limiter and separate validation.

## 14.3 Required tests

- limit boundary succeeds/fails correctly;
- `Retry-After` is present;
- identifier enumeration is prevented;
- spoofed forwarding headers do not bypass limits;
- account and IP limits combine correctly;
- different IP does not bypass account limit;
- service restart behavior is documented;
- concurrency cannot exceed the intended critical-operation budget materially;
- high request volume does not alter Booking state incorrectly.

---

# 15. SEC-P11 — Audit-protection proof

## 15.1 Ownership

Each service owns authoritative audit evidence for its actions.

Governance owns only:

- searchable projection;
- seal verification;
- alerting;
- investigation views.

Projection failure cannot destroy source audit evidence.

## 15.2 `audit_event`

Required fields:

- event reference;
- monotonic service sequence;
- occurred time;
- actor type/reference;
- authenticated calling service;
- action;
- target type/reference;
- organization/case scope;
- reason code;
- outcome;
- authorization-decision reference;
- correlation/workflow references;
- safe before/after summaries;
- classification;
- canonical payload hash.

## 15.3 Database permissions

Application runtime role:

- may insert;
- may read only where required;
- may not update;
- may not delete;
- may not alter audit tables.

Migration role:

- may alter schema;
- cannot be used by application runtime.

Retention role:

- may delete only under approved retention jobs;
- cannot rewrite history.

## 15.4 Tamper-evident seals

Use periodic signed audit seals.

An `audit_seal` contains:

- service;
- first and last sequence;
- previous seal hash;
- ordered batch root/hash;
- creation time;
- sealing key ID;
- signature.

Rules:

- seal at least every five minutes or 1,000 events;
- sealing key unavailable to business runtime;
- seals form a continuity chain;
- Governance verifies signatures, ranges and continuity;
- missing rows, changed hashes or broken seals create security alerts;
- sealing failure does not block the business transaction but creates an urgent operational alert.

## 15.5 Required audit events

At minimum:

- authentication success/failure;
- logout and session revocation;
- MFA registration/removal;
- step-up failure;
- token-exchange denial;
- service-token validation denial;
- membership/role changes;
- cross-tenant denial;
- booking hold/confirm/cancel;
- check-in/start/stop;
- authorization reuse;
- maintenance restrictions;
- support reveals;
- break-glass activation/use/expiry;
- secret/key rotation;
- rate-limit enforcement for sensitive operations;
- audit verification failure.

Do not store:

- token values;
- cookies;
- passwords;
- authorization secrets;
- private keys;
- unnecessary request bodies;
- full email addresses.

Privileged audit records retain the existing provisional 24-month policy. ([github.com](https://github.com/panospao7/Ev-chargind-station/blob/main/docs/06_security_and_privacy/01_privacy_retention_export_deletion_v1.0.md))

## 15.6 Required tests

- runtime update fails;
- runtime deletion fails;
- business action and audit insert commit atomically;
- rolled-back action leaves no false success audit;
- altered audit row breaks seal verification;
- deleted row creates sequence/seal gap;
- wrong signing key fails;
- Governance outage does not affect local evidence;
- replayed audit projection remains idempotent;
- retention job cannot delete records under hold.

---

# 16. Authorization registry

Create `authorization-v1.yaml`.

Every protected operation must declare:

- `operationId`;
- surface: browser/BFF/internal/message;
- actor type;
- required coarse role;
- required OAuth scope;
- ownership check;
- organization-membership check;
- authoritative owner;
- minimum `acr`;
- maximum authentication age;
- whether delegated assertion is allowed;
- required resource state;
- audit category;
- rate-limit policy;
- existence-masking rule;
- release wave.

OpenAPI operations reference registry policies through:

- `x-authorization-policy`;
- `x-csrf-required`;
- `x-assurance-level`;
- `x-rate-limit-policy`;
- `x-audit-category`.

Default behavior is deny.

---

# 17. Security problem codes

Add or normalize:

| Code | HTTP |
|---|---:|
| `AUTHENTICATION_REQUIRED` | 401 |
| `SESSION_EXPIRED` | 401 |
| `AUTHENTICATION_ASSURANCE_REQUIRED` | 401 |
| `TOKEN_INVALID` | 401 |
| `TOKEN_AUDIENCE_INVALID` | 401 |
| `CSRF_VALIDATION_FAILED` | 403 |
| `ORIGIN_NOT_ALLOWED` | 403 |
| `INSUFFICIENT_SCOPE` | 403 |
| `ACCESS_DENIED` | 403 |
| `CURRENT_MEMBERSHIP_REQUIRED` | 403 |
| `MEMBERSHIP_STATE_UNKNOWN` | 503 |
| `ACCOUNT_SUSPENDED` | 403 |
| `RATE_LIMIT_EXCEEDED` | 429 |

For step-up challenges, include the appropriate `WWW-Authenticate` challenge without revealing whether the requester would otherwise be authorized.

---

# 18. Evidence package

Each proof produces:

- test source;
- configuration snapshot;
- sanitized request/response evidence;
- negative-test evidence;
- security log evidence;
- trace/correlation evidence;
- exact commit SHA;
- CI run reference;
- residual limitations;
- reviewer sign-off.

Required evidence files:

```text
security/evidence/SEC-P01-bff-session.md
security/evidence/SEC-P02-csrf.md
security/evidence/SEC-P03-token-exchange.md
security/evidence/SEC-P04-service-identity.md
security/evidence/SEC-P05-delegated-action.md
security/evidence/SEC-P06-membership-revocation.md
security/evidence/SEC-P07-mfa-step-up.md
security/evidence/SEC-P08-session-revocation.md
security/evidence/SEC-P09-secret-rotation.md
security/evidence/SEC-P10-rate-limits.md
security/evidence/SEC-P11-audit-protection.md
```

Screenshots alone are insufficient.

---

# 19. Work packages

| ID | Deliverable |
|---|---|
| SEC-IMP-01 | Keycloak realm/client configuration |
| SEC-IMP-02 | BFF login, session and logout proof |
| SEC-IMP-03 | CSRF and browser-header proof |
| SEC-IMP-04 | Token-exchange proof |
| SEC-IMP-05 | Service `private_key_jwt` proof |
| SEC-IMP-06 | Delegated Action JWS profile |
| SEC-IMP-07 | Authorization registry |
| SEC-IMP-08 | Membership-revocation proof |
| SEC-IMP-09 | MFA and step-up proof |
| SEC-IMP-10 | Secret/key rotation proof |
| SEC-IMP-11 | Rate-limit registry and tests |
| SEC-IMP-12 | Append-only audit and sealing proof |
| SEC-IMP-13 | Security CI gates |
| SEC-IMP-14 | Evidence review and approval |

Dependencies:

```text
SEC-IMP-01
  → SEC-IMP-02
  → SEC-IMP-03
  → SEC-IMP-04
  → SEC-IMP-05
  → SEC-IMP-06/07
  → SEC-IMP-08/09
  → SEC-IMP-10/11/12
  → SEC-IMP-13
  → SEC-IMP-14
```

---

# 20. Required documentation patches

## ARC-007

- state that Keycloak is selected conditionally on SEC-001;
- replace proposed/pending wording where already decided;
- reference exact proof IDs;
- use `__Host-evsession`;
- reference authorization and rate-limit registries;
- define signed audit sealing as the W1 tamper-evidence mechanism.

## ARC-009

Replace POC-01 with SEC-P01 through SEC-P09 references.

## ARC-003 and OpenAPI

- add session/CSRF/step-up operations;
- add CSRF headers;
- add authorization-policy extensions;
- add rate-limit policies;
- normalize security problem codes.

## ARC-019

- reference the executable delegated-action schema;
- define service scopes;
- require membership version/decision reference.

## ARC-005/ARC-022

Add:

- BFF token/session storage;
- membership projections;
- delegated assertion replay records;
- security rate-limit counters where authoritative enforcement is required;
- `audit_event`;
- `audit_seal`.

## GOV-001

Add:

- `DEC-SEC-26` — SEC-001 is the W1 proof baseline;
- `DEC-SEC-27` — server-side synchronizer CSRF token;
- `DEC-SEC-28` — Keycloak Standard Token Exchange;
- `DEC-SEC-29` — unique `private_key_jwt` service identities;
- `DEC-SEC-30` — `ev-loa1`/`ev-loa2`;
- `DEC-SEC-31` — versioned membership revocation;
- `DEC-SEC-32` — provisional W1 rate limits;
- `DEC-SEC-33` — signed periodic audit seals.

---

# 21. Definition of done

SEC-001 is complete only when:

1. Browser JavaScript never receives OAuth tokens.
2. Session-cookie attributes pass browser tests.
3. Session fixation tests pass.
4. Every mutation enforces CSRF.
5. Target tokens contain one intended audience.
6. Cross-audience token use fails.
7. Every service authenticates independently.
8. Shared service credentials are absent.
9. Delegated assertion replay and mutation fail.
10. Revoked operator membership no longer authorizes.
11. Projection gaps fail closed.
12. Mandatory MFA works for privileged roles.
13. Step-up and recent-authentication tests pass.
14. Session and account revocation work.
15. Two key-rotation exercises pass without outage.
16. Rate limits return safe 429 responses.
17. Runtime roles cannot alter audit evidence.
18. Audit tampering is detected through seal verification.
19. Secret/log/message scans pass.
20. Authorization registry covers every W1 protected operation.
21. CI is green from a clean checkout.
22. Evidence contains immutable commit and CI references.
23. Residual `HIGH` risks have explicit acceptance.
24. ARC-007 may then move from `IN_REVIEW` to `APPROVED`.

---

# 22. Recommended commit sequence

1. `docs: add SEC-001 security implementation proof plan`
2. `security: add deterministic Keycloak realm and client profile`
3. `security: prove BFF session cookie CSRF and logout`
4. `security: prove token exchange and private-key service identity`
5. `security: add delegated-action and authorization registries`
6. `security: prove membership revocation MFA and step-up`
7. `security: add secret rotation rate limits and audit sealing`
8. `test: add security integration and browser proof suite`
9. `governance: approve SEC-001 after green evidence`

Business-feature implementation may use authentication scaffolding while these proofs are developed, but no protected W1 vertical slice should be declared complete before SEC-P01 through SEC-P09 pass.
