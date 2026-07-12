Document ID: ARC-007  
Title: Security Architecture and Threat Model  
Version: 1.0  
Status: IN_REVIEW  
Owner: Security Architect  
Last reviewed: 2026-07-12  
Depends on: ARC-001–006, REQ-001, REQ-002, DOM-002, SIM-001, PRV-001  
Authoritative for: Trust boundaries, authentication, authorization, service identity, simulator identity, security controls, threat treatment and security-verification requirements  

# Security Architecture and Threat Model v1.0

## 1. Purpose

This document defines:

- Security objectives and trust boundaries
- Browser and Identity Provider integration
- Session and token handling
- Human and machine authentication
- Service-to-service identity
- Actor-context propagation
- Authorization and tenant isolation
- Platform Support and break-glass access
- Simulator certificate identity
- API, broker and database protections
- Secret and cryptographic-key management
- Abuse prevention and audit requirements
- Principal threats and mitigations
- Security verification and release criteria

This document is an engineering security baseline. It does not constitute certification or a claim of legal compliance.

---

## 2. Security standards baseline

The release baseline is **OWASP ASVS 5.0.0 Level 2**, with selected Level 3 controls for privileged access, privacy exports, machine identity, cryptographic keys and emergency intervention. ASVS 5.0.0 is the current stable ASVS release; exact versioned requirement IDs must be recorded in the future security verification matrix. ([github.com](https://github.com/OWASP/ASVS/releases?utm_source=openai))

Authentication and federation design also uses:

- OAuth 2.0 Security Best Current Practice, RFC 9700
- JWT Best Current Practices, RFC 8725
- NIST SP 800-63B-4 for authentication and authenticator management
- NIST SP 800-63C-4 for federation and assertions
- NIST SP 800-218 SSDF for secure development practices

RFC 9700 requires modern protections such as PKCE for authorization-code flows and rejects unsafe patterns including open redirectors. ([datatracker.ietf.org](https://datatracker.ietf.org/doc/html/rfc9700?utm_source=openai))

JWT processing must follow explicit algorithm, issuer, audience, type and claim validation rather than accepting tokens based only on a valid signature. ([datatracker.ietf.org](https://datatracker.ietf.org/doc/html/rfc8725?utm_source=openai))

NIST published SP 800-63B-4 and SP 800-63C-4 in July 2025 as the current authentication and federation guidance. ([csrc.nist.gov](https://csrc.nist.gov/pubs/sp/800/63/b/4/final?utm_source=openai))

Secure-development governance follows NIST SSDF practices for preparing the organization, protecting software, producing secure software and responding to vulnerabilities. ([csrc.nist.gov](https://csrc.nist.gov/pubs/sp/800/218/final?utm_source=openai))

---

## 3. Security objectives

The platform must preserve:

### Confidentiality

- Credentials and tokens
- Personal profile data
- Booking and linked location history
- Support and privacy-case data
- Machine credentials
- Provider and database secrets

### Integrity

- EVSE allocations
- Booking and session lifecycles
- Tariff and policy snapshots
- Meter sequences and estimated cost
- Authorization decisions
- Maintenance and fault workflows
- Audit evidence
- Privacy workflow outcomes

### Availability

- Booking management
- Cancellation
- Device reconciliation
- Identity and account recovery
- Operational restriction enforcement
- Audit and incident visibility

### Accountability

Every privileged or business-critical action must be attributable to:

- Human or machine actor
- Authenticated service
- Resource and organization scope
- Reason
- Time
- Outcome
- Correlation and workflow identifiers

---

## 4. Principal assets

| Asset | Classification | Principal risk |
|---|---|---|
| Passwords and MFA factors | Secret | Account takeover |
| Access and refresh tokens | Secret | Session hijacking |
| BFF session records | Security-restricted | User impersonation |
| Start Authorization | Secret | Unauthorized charging |
| Machine private keys | Secret | Device impersonation |
| Booking/location history | Personal operational | Movement profiling |
| Privacy exports | Privacy-restricted | Bulk disclosure |
| Support cases | Personal/restricted | Unauthorized browsing |
| EVSE allocations | Operational critical | Double booking |
| Device commands | Operational critical | False start/stop actions |
| Meter evidence | Operational | Cost/energy manipulation |
| Audit records | Security-restricted | Evidence tampering |
| Service credentials | Secret | Lateral movement |
| Broker messages | Mixed | Forgery, replay or disclosure |
| Database backups | Mixed/restricted | Bulk compromise |

---

## 5. Trust boundaries

### TB-01 — Public browser boundary

Untrusted:

- Browser JavaScript
- Browser extensions
- Client storage
- User-supplied content
- Network input

The browser cannot hold service credentials or make authoritative decisions.

### TB-02 — Edge/BFF boundary

The API Gateway/BFF is the only public application API entry point.

It performs:

- Browser-session handling
- OAuth redirection
- Coarse request authentication
- CSRF protection
- Rate limiting
- Target-service routing
- Correlation propagation

It does not make final business authorization decisions.

### TB-03 — Identity boundary

The Identity Provider owns:

- Credentials
- Verification
- Recovery
- MFA
- Authentication sessions
- OAuth/OIDC tokens

Business services never receive passwords or MFA secrets.

### TB-04 — Internal service boundary

Every service is independently authenticated and authorized.

Internal networking is not considered trusted merely because it is private.

### TB-05 — Message-broker boundary

Messages may be duplicated, delayed, reordered or replayed.

Broker access does not authorize a business transition by itself.

### TB-06 — Device boundary

The Charger Simulator is an external, potentially compromised machine actor.

Every connection and reported EVSE is validated against assignment.

### TB-07 — External-provider boundary

Email, map, object-storage and monitoring providers are outside the platform’s transactional boundary.

### TB-08 — Operations boundary

Cloud consoles, database administration, secret stores and deployment systems are separate from application roles.

`PLATFORM_ADMINISTRATOR` does not imply cloud or database administration.

---

# 6. Browser security architecture

## 6.1 Selected profile: confidential BFF

The Angular application uses a server-side Backend for Frontend security model.

The browser receives:

- An opaque application-session cookie
- A non-secret CSRF token
- User-safe account and authorization state

The browser never receives or stores:

- OAuth access tokens
- OAuth refresh tokens
- Client credentials
- Service tokens
- Start Authorization secrets beyond a one-time browser operation reference

This reduces the impact of token theft through browser storage or JavaScript compromise.

The IETF browser-application specification is still completing publication, but its BFF profile recommends server-held tokens and strongly protected cookies. It is used here as informative guidance rather than a finalized normative reference. ([datatracker.ietf.org](https://datatracker.ietf.org/doc/draft-ietf-oauth-browser-based-apps/26/?utm_source=openai))

## 6.2 Authentication flow

1. Browser requests login through the BFF.
2. BFF creates a transaction-bound state, nonce and PKCE verifier.
3. Browser is redirected to the Identity Provider.
4. Identity Provider authenticates the user.
5. Authorization code returns to an exact allowlisted BFF callback.
6. BFF validates state, issuer and transaction binding.
7. BFF exchanges the code using PKCE.
8. Tokens are encrypted in the server-side session store.
9. Browser receives only the opaque session cookie.
10. BFF rotates the session identifier after authentication.

The implicit flow and Resource Owner Password Credentials flow are prohibited.

## 6.3 Session cookie

Proposed cookie name:

`__Host-evsession`

Required attributes:

- `Secure`
- `HttpOnly`
- `Path=/`
- No `Domain`
- `SameSite=Lax` initially
- Random high-entropy value
- Rotation after authentication and privilege change

`SameSite=Strict` may be adopted if OIDC redirect and usability testing confirms compatibility.

## 6.4 Server-side session

The session store contains:

- Session reference
- Account/identity subject
- Encrypted Identity Provider tokens
- Authentication assurance
- Creation, idle and absolute expiry
- Revocation state
- Security-event metadata

Provisional lifetimes:

| Item | Initial value |
|---|---:|
| Browser session idle timeout | 30 minutes |
| Browser session absolute lifetime | 8 hours |
| Privileged idle timeout | 15 minutes |
| Recent-authentication window | 5 minutes |
| User access token | 5 minutes |
| Service access token | 5 minutes |

Final values require security and UX testing.

## 6.5 CSRF protection

Every browser-originated state-changing request requires:

- Same-origin session cookie
- Session-bound CSRF token
- Custom request header
- Valid `Origin`
- `Referer` validation where appropriate
- Allowed content type

`SameSite` cookies are defense in depth and do not replace CSRF tokens.

## 6.6 CORS

Preferred deployment is same-origin Angular and BFF.

If cross-origin development is required:

- Exact origin allowlist
- No wildcard with credentials
- Restricted methods and headers
- Preflight validation
- Environment-specific configuration

---

# 7. Identity Provider architecture

Keycloak remains the preferred Identity Provider pending its ADR.

Keycloak currently supports:

- Service accounts
- Asymmetric signed-JWT client authentication
- X.509 client authentication
- Standard token exchange
- DPoP
- MFA and passkey capabilities

These features make it technically suitable for the proposed architecture, but final selection still requires proof-of-concept and operational review. ([keycloak.org](https://www.keycloak.org/securing-apps/token-exchange?utm_source=openai))

## 7.1 Realm separation

Recommended:

- One application realm per deployed environment
- Separate administration realm or restricted master-realm access
- No development users or clients in production
- No shared signing keys across environments

## 7.2 Client categories

- Confidential BFF client
- One confidential client per service
- Device enrollment client or certificate authority integration
- Administrative automation clients
- Test clients restricted to non-production

## 7.3 Human roles in tokens

Tokens may carry coarse platform roles:

- `DRIVER`
- `OPERATOR_OWNER`
- `OPERATOR_MANAGER`
- `OPERATOR_TECHNICIAN`
- `OPERATOR_SUPPORT`
- `PLATFORM_ADMINISTRATOR`
- `PLATFORM_SUPPORT`
- `AUDITOR_SECURITY_REVIEWER`

Tokens must not be the sole authority for:

- Current organization membership
- Resource ownership
- Active support-case scope
- Break-glass scope
- Account eligibility
- Booking state

## 7.4 MFA

Mandatory for:

- All operator roles
- Platform administrators
- Platform Support
- Audit/security reviewers
- Cloud and deployment administrators

Supported initial factors:

- TOTP
- WebAuthn/passkeys where selected
- Recovery codes

SMS MFA is excluded.

NIST SP 800-63B-4 is the reference for authenticator management and authentication assurance. ([csrc.nist.gov](https://csrc.nist.gov/pubs/sp/800/63/b/4/final?utm_source=openai))

## 7.5 Step-up authentication

Recent MFA-backed authentication is required for:

- Ownership transfer
- Account deletion
- Privacy-export download
- Email change
- Role elevation
- Data reveal
- Emergency intervention
- Break-glass activation
- Machine credential issuance or revocation

The API may return an authentication-requirements challenge aligned with the OAuth step-up model. ([rfc-editor.org](https://www.rfc-editor.org/info/rfc9470/?utm_source=openai))

---

# 8. Token security

## 8.1 Access-token requirements

Resource servers validate:

- Signature
- Explicit allowlisted algorithm
- `iss`
- `aud`
- `exp`
- `nbf`
- Token type
- Authorized client
- Required scopes
- Subject where applicable

Spring Security supports issuer, signature, expiry and not-before validation and automatic JWK rotation; audience validation must also be configured explicitly. ([docs.spring.io](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html?utm_source=openai))

## 8.2 Token rules

- Access tokens are short lived.
- Refresh tokens remain only in the BFF session store.
- Refresh-token rotation and reuse detection are enabled.
- Tokens never appear in URLs.
- Tokens are never logged.
- ID Tokens are not accepted as API access tokens.
- Tokens issued for one audience are rejected by another.
- Symmetric JWT signing between independently deployed services is prohibited.
- Algorithm selection is configured, not taken blindly from token input.

## 8.3 Audience-limited edge tokens

Before calling a business service, the BFF obtains or exchanges for a token limited to that target audience.

If Keycloak is selected, Standard Token Exchange V2 is the preferred mechanism. Keycloak supports target-audience filtering, while legacy token exchange is deprecated. ([keycloak.org](https://www.keycloak.org/securing-apps/token-exchange?utm_source=openai))

The BFF must not forward one broad multi-service bearer token throughout the platform.

## 8.4 DPoP

DPoP can bind tokens to a client key and reduce stolen-token replay. Keycloak supports DPoP, and RFC 9449 defines the protocol. ([keycloak.org](https://www.keycloak.org/securing-apps/dpop?utm_source=openai))

Decision for v1:

- DPoP is not required for the browser because OAuth tokens remain server-side.
- DPoP may be evaluated for internal service tokens.
- mTLS or workload-bound identity may be preferable for internal workloads.
- DPoP adoption requires compatibility and operational testing.

---

# 9. Service-to-service authentication

## 9.1 Service identity

Every service has:

- Unique client identity
- Unique asymmetric key or workload identity
- Dedicated scopes
- Explicit target audiences
- Independent revocation
- Separate credentials per environment

Shared fleet-wide client secrets are prohibited.

## 9.2 Portable initial profile

Until cloud workload identity is selected:

- OAuth client-credentials grant
- `private_key_jwt` client authentication
- Short-lived audience-specific access tokens
- TLS for every connection
- Keys stored in secret/key management

Keycloak supports private-key signed client assertions and service accounts without requiring shared client secrets. ([keycloak.org](https://www.keycloak.org/docs/latest/server_admin/?utm_source=openai))

## 9.3 Target deployment profile

Prefer, in order of feasibility:

1. Cloud/Kubernetes workload identity federated to the Identity Provider
2. SPIFFE-compatible workload identity
3. mTLS service identity
4. `private_key_jwt`
5. Rotating client secret only as a constrained fallback

## 9.4 Internal token validation

Each recipient validates:

- Service subject/client
- Audience
- Allowed scope
- Issuer
- Expiry
- Token binding where enabled
- Calling-service permission for the operation

Network location is never accepted as service identity.

---

# 10. Originating actor context

## 10.1 Direct edge request

The target service receives an audience-specific user access token and performs final authorization.

## 10.2 Delegated internal action

When one business service requests another service to act following a human operation, it supplies:

1. Its own authenticated service token
2. A short-lived Delegated Action Assertion

The assertion contains:

- Original human subject
- Authentication assurance and MFA time
- Coarse platform role
- Source service
- Target service
- Exact operation
- Target resource reference
- Organization or case scope
- Authorization-decision reference
- Correlation ID
- Issued and expiry times
- Unique assertion ID

The assertion is signed by the source service.

## 10.3 Delegation rules

- Assertion lifetime must not exceed two minutes.
- Assertion is bound to one recipient and operation.
- Recipient validates source-service authority.
- Recipient independently validates current resource state and invariant.
- Assertion cannot grant a permission the source service does not own.
- Sensitive commands require idempotency.
- Assertion is not stored in logs.
- Asynchronous messages contain actor and decision references, not reusable bearer assertions.

This limits confused-deputy risk while preserving accountability.

---

# 11. Authorization model

Authorization is the conjunction of:

```text
authenticated actor
AND allowed role/permission
AND current resource scope
AND organization/case ownership
AND current lifecycle state
AND required authentication assurance
AND applicable restriction status
```

## 11.1 Default deny

Every endpoint, command and message handler is denied unless explicitly authorized.

## 11.2 Driver authorization

Drivers may access only resources whose authoritative `account_ref` matches their identity subject mapping.

A public reference does not prove ownership.

## 11.3 Operator tenant isolation

Station Operations is authoritative for:

- Organization memberships
- Operator roles
- Organization-owned infrastructure

Operator-facing booking actions pass through Station Operations or a verified organization-scope projection.

No operator token contains an unrestricted list of all organization resources.

## 11.4 Platform Support

Platform Support requires:

- Assigned case
- Active temporary access grant
- Permitted resource set
- Permitted fields/actions
- Expiry
- Reason
- Audit

Case access never implies unrestricted account browsing.

## 11.5 Platform administrators

Administrators may request authoritative actions but cannot directly write another service’s database.

## 11.6 Separation of duties

Where practical, separate:

- Operator applicant and approver
- Break-glass actor and reviewer
- Privacy requester and export reviewer
- Audit reviewer and audit-storage administrator
- Developer and production migration executor

---

# 12. Support access grants

A support grant contains:

- Grant reference
- Case reference
- Agent reference
- Resource allowlist
- Field allowlist
- Action allowlist
- Issued and expiry times
- Issuing authority
- Revocation status

Preferred representation:

- Short-lived signed grant for local verification
- Durable authoritative record in Governance
- Immediate revocation event
- Authoritative lookup when local state is uncertain

Sensitive field reveal requires a separate audited action even when a case grant exists.

---

# 13. Break-glass access

Break-glass is not a permanent role.

## 13.1 Preconditions

- Ordinary permissions are insufficient
- Delay creates material operational or security risk
- Recent MFA-backed reauthentication
- Explicit scope
- Maximum duration
- Structured justification

## 13.2 Controls

- Default maximum duration: 15 minutes
- Automatic expiry
- Immediate reviewer alert
- Every action separately audited
- No ability to disable audit
- No access to raw credentials or private keys
- No authority to fabricate device success
- No destructive history editing

## 13.3 Approval

Preferred production model:

- Independent approval before activation

Emergency model:

- Immediate activation where delay is unsafe
- Mandatory retrospective review

An individual-project demonstration may use owner self-activation only in a clearly labelled non-production environment.

---

# 14. Simulator machine identity

## 14.1 Deployed target

Every simulated station authenticates through mutual TLS using a unique certificate.

OAuth mTLS standards define certificate-based client authentication and certificate-bound tokens. ([datatracker.ietf.org](https://datatracker.ietf.org/doc/html/rfc8705?utm_source=openai))

## 14.2 Certificate binding

Certificate identity maps to exactly one Machine Identity.

Authorization additionally checks:

- Machine state is `ACTIVE`
- Station assignment
- EVSE assignment
- Allowed protocol version
- Credential validity
- Revocation status

## 14.3 Enrollment

Enrollment credentials are:

- Single-use
- Short-lived
- Assignment-bound
- Hashed or encrypted at rest
- Invalidated after use
- Excluded from logs

## 14.4 Rotation

- New and old certificates may overlap briefly.
- Rotation does not change public station identity.
- Private keys never leave the simulator’s secure credential store.
- Revocation affects only the compromised machine.

## 14.5 Proxy handling

When TLS terminates before Device Integration:

- Public requests cannot set trusted certificate headers.
- Proxy strips incoming identity headers.
- Proxy injects verified identity metadata.
- Device Integration trusts only requests from the authenticated proxy.
- End-to-end mTLS is preferred where practical.

## 14.6 Development profile

Local development may use a unique short-lived machine token.

Development credentials must be rejected in staging and production-like environments.

---

# 15. API security controls

- Strict OpenAPI request schemas
- Unknown mutation fields rejected
- Explicit response allowlists
- Object-level authorization
- Parameterized queries
- Bounded body and collection sizes
- Request timeouts
- Rate limiting
- Idempotency for commands
- `If-Match` for mutable resources
- No stack traces or SQL in responses
- No unrestricted field expansion
- No file uploads in v1
- Safe redirect allowlists
- SSRF-resistant outbound HTTP clients
- No user-controlled destination URLs
- Egress restrictions for sensitive services

---

# 16. Browser application controls

Required response headers include:

- Content Security Policy
- `frame-ancestors 'none'`
- `X-Content-Type-Options: nosniff`
- Strict transport security
- Restrictive Referrer Policy
- Restrictive Permissions Policy
- No caching of authenticated personal responses

## 16.1 XSS

- Angular templates use escaped bindings.
- Dynamic unsafe HTML is prohibited by default.
- Sanitization is centralized.
- Inline scripts require CSP nonces if unavoidable.
- Third-party scripts are minimized.
- No OAuth tokens exist in JavaScript storage.
- Synthetic data is used for template previews.

## 16.2 Geolocation

- Browser permission requires explicit user action.
- Location is not retained by default.
- Geolocation permission is limited through Permissions Policy.
- Failure does not prevent manual search.

---

# 17. Broker security

- TLS for broker connections
- Unique identity per service
- Separate environment virtual hosts or brokers
- Least-privilege exchange and queue permissions
- Producer restrictions by routing key
- Consumer restrictions by queue
- No anonymous access
- No management interface exposure to the public internet
- Restricted quarantine and replay permissions
- Message-size limits
- Payload-schema validation
- No secrets or reusable tokens in messages

Correlation and actor references are not authorization credentials.

High-impact handlers validate:

- Calling service
- Command type
- Workflow reference
- Source version
- Current target state

Message-level encryption or signing is not required in v1 when broker, transport and storage controls meet the threat model. Privacy export content uses secure object storage rather than broker payloads.

---

# 18. Database and storage security

- Unique credentials per service
- No cross-service grants
- Runtime roles cannot perform DDL
- Migration credentials are separate
- TLS database connections
- Encrypted backups
- Restricted operational read roles
- Parameterized SQL
- Secret columns excluded from diagnostics
- Start Authorization stored only as a strong hash
- Append-only local audit permissions
- Database administration outside application roles

Encryption at rest provided by the deployment platform does not remove the need for application-level minimization and access control.

---

# 19. Secret and key management

Secrets include:

- Service private keys
- Machine enrollment credentials
- Certificate-authority keys
- Database credentials
- Broker credentials
- Email-provider credentials
- Object-storage credentials
- Session encryption keys
- Webhook verification secrets

Rules:

1. Never store secrets in source control.
2. Never place secrets in container images.
3. Load secrets through the selected secret manager.
4. Restrict each secret to one workload.
5. Support rotation without a full platform outage.
6. Maintain key identifiers and versions.
7. Permit controlled dual-key verification during rotation.
8. Audit secret access and changes.
9. Separate signing, encryption and transport keys.
10. Back up only keys required for recovery under protected procedures.

Production private keys must not be copied into local developer environments.

---

# 20. External providers and webhooks

Provider webhooks require:

- Cryptographic signature or equivalent authentication
- Timestamp validation
- Replay detection
- Payload size/schema validation
- Idempotent event processing
- Provider-reference validation
- Restricted source network as optional defense in depth

An IP allowlist alone is insufficient authentication.

Privacy export download uses:

- Authenticated application session
- Recent reauthentication
- Short-lived authorization
- Object-specific scope
- Download audit
- No email attachment

---

# 21. Logging and audit security

Logs must not contain:

- Passwords
- Access/refresh tokens
- Cookies
- Start Authorization secrets
- Enrollment credentials
- Private keys
- Raw privacy-export content
- Full email addresses unless explicitly required
- Unnecessary request/response bodies

Security logs record:

- Authentication success/failure
- MFA and authenticator changes
- Token validation failures
- Authorization denials
- Rate-limit actions
- Cross-tenant attempts
- Support reveals
- Break-glass activity
- Machine enrollment/revocation
- Invalid device messages
- Broker replay
- Privacy export/download activity
- Migration and secret-management actions

Audit projection failure cannot destroy local audit evidence.

---

# 22. Abuse prevention

Layered controls apply by:

- Source address
- Account
- Session
- Organization
- Public reference
- EVSE
- Machine identity
- Client identity
- Operation category

Particularly protected:

- Registration
- Verification resend
- Recovery
- Booking holds
- Check-in/start
- Fault reporting
- Invitations
- Privacy exports
- Data reveal
- Emergency intervention
- Simulator enrollment

Responses must not reveal whether an account exists during recovery or verification resend.

Automated abuse controls must not silently classify equipment failures as driver misconduct.

---

# 23. Threat-model method

Threats are categorized using:

- Spoofing
- Tampering
- Repudiation
- Information disclosure
- Denial of service
- Elevation of privilege

Risk ratings:

- `CRITICAL`
- `HIGH`
- `MEDIUM`
- `LOW`

Risk considers impact and realistic exploitability before controls.

Residual risk must be accepted explicitly when it remains `HIGH`.

---

# 24. Threat register

| ID | Threat | Initial risk | Principal controls | Residual |
|---|---|---:|---|---:|
| THR-01 | Credential stuffing or password spraying | High | IdP protection, rate limits, breached-password controls, MFA, alerts | Medium |
| THR-02 | Password recovery enumeration | High | Generic responses, rate limits, one-time tokens, audit | Low |
| THR-03 | OAuth authorization-code interception/injection | High | Exact redirects, PKCE, state, nonce, BFF, RFC 9700 profile | Low |
| THR-04 | Browser token theft | Critical | Tokens server-side only, HttpOnly session, CSP, no browser storage | Low |
| THR-05 | Session fixation or hijacking | High | Session rotation, Secure cookie, idle/absolute expiry, revocation | Medium |
| THR-06 | CSRF against booking or privileged actions | High | Session-bound token, Origin validation, SameSite, reauthentication | Low |
| THR-07 | XSS in Angular or rendered content | Critical | CSP, output encoding, no arbitrary HTML, no browser tokens | Medium |
| THR-08 | Driver accesses another driver’s Booking | Critical | Owner check at Booking authority, opaque references, existence masking | Low |
| THR-09 | Cross-organization operator access | Critical | Authoritative membership, organization scope, façade validation, tests | Low |
| THR-10 | Stale token retains removed operator role | High | Short tokens, authoritative membership checks, revocation/restriction events | Medium |
| THR-11 | Platform Support browses unrelated records | Critical | Case grants, field scope, expiry, reveal audit | Low |
| THR-12 | Confused-deputy internal service call | High | Audience token, service scope, delegated assertion, recipient validation | Medium |
| THR-13 | Compromised service moves laterally | Critical | Unique identity, least scopes, network/egress controls, rotation | Medium |
| THR-14 | Forged or replayed broker command | High | Broker ACL, command ID, inbox, workflow/source validation | Low |
| THR-15 | Malicious simulator impersonates station | Critical | Per-station mTLS certificate, assignment checks, revocation | Low |
| THR-16 | Simulator reports another station’s EVSE | High | Machine-to-resource allowlist, quarantine, security event | Low |
| THR-17 | Duplicate Start command repeats physical action | Critical | Stable command ID and simulator result history | Low |
| THR-18 | Command timeout falsely treated as success | Critical | Explicit uncertainty and reconciliation | Low |
| THR-19 | Device events inflate energy or cost | High | Event IDs, meter sequence, bounds, accepted-data calculation | Low |
| THR-20 | Allocation bypass or double booking | Critical | Booking-local transaction, guards, exclusion constraints, authorization | Low |
| THR-21 | SQL or query injection | High | Parameter binding, schemas, code review, SAST/DAST | Low |
| THR-22 | SSRF through provider or URL parameters | High | No arbitrary URLs, egress allowlists, address validation | Low |
| THR-23 | Privacy export theft | Critical | Reauthentication, encrypted storage, scoped download, expiry, audit | Low |
| THR-24 | Personal data exposed in logs/events | High | Field minimization, secret scanning, schema review, restricted logs | Medium |
| THR-25 | Admin abuses emergency access | Critical | No permanent break-glass role, reason, scope, expiry, alert, review | Medium |
| THR-26 | Audit records altered or deleted | High | Local append-only permissions, separate retention role, projection checks | Low |
| THR-27 | Email webhook spoofing | Medium | Signature, timestamp and replay validation | Low |
| THR-28 | Broker/database denial of service | High | Backpressure, quotas, timeouts, bounded messages, recovery | Medium |
| THR-29 | Dependency or build compromise | Critical | Lockfiles, SBOM, signed artifacts, dependency scanning, provenance | Medium |
| THR-30 | Secret committed to repository | Critical | Pre-commit/CI scanning, immediate revocation process, no static secrets | Medium |
| THR-31 | Malicious operator places script in public text | High | Plain text, validation, escaping, CSP | Low |
| THR-32 | Backup restoration reintroduces deleted data | High | Privacy tombstone replay before service restoration | Low |
| THR-33 | BFF compromise exposes all sessions | Critical | Hardened runtime, encrypted token store, least scope, key rotation, monitoring | Medium |
| THR-34 | Stolen service token is replayed | High | Short expiry, audience, asymmetric client auth, optional sender constraint | Medium |
| THR-35 | Availability or status data manipulated to permit unsafe booking | Critical | Signed service identity, versions, fail-closed projections, local authority | Low |

---

# 25. High-risk trust-boundary requirements

## 25.1 BFF compromise

Because the BFF stores user tokens, it is a high-value component.

Required:

- Minimal dependencies
- No business database access
- No general outbound internet access
- Encrypted session/token store
- Restricted Identity Provider client
- Strict administrative access
- Security monitoring
- Rapid session revocation
- Key rotation procedures

## 25.2 Booking authority compromise

Required:

- No cloud-management credentials
- No machine private keys
- Restricted database role
- Strict internal API scopes
- Allocation-integrity monitoring
- Immutable audit/outbox evidence
- Quarantine after integrity corruption

## 25.3 Device Integration compromise

Required:

- Cannot directly alter Booking tables
- Cannot create infrastructure
- Cannot calculate authoritative cost
- Machine credentials isolated
- Normalized event validation
- Rate limits per machine
- Broker publishing restricted to device event types

---

# 26. Secure software development

The delivery process must include:

- Threat-model review for material architecture changes
- Peer review of security-sensitive code
- SAST
- Dependency and license scanning
- Secret scanning
- Container scanning
- IaC scanning
- DAST against deployed test environments
- SBOM generation
- Dependency pinning
- Signed release artifacts where supported
- Vulnerability triage and remediation targets
- Security regression tests
- Protected production deployment credentials

These practices align with the secure-development activities described by NIST SSDF. ([csrc.nist.gov](https://csrc.nist.gov/pubs/sp/800/218/final?utm_source=openai))

---

# 27. Security verification requirements

## 27.1 Authentication

- Authorization-code and PKCE tests
- Redirect allowlist tests
- State/nonce replay tests
- Session fixation tests
- Session revocation tests
- MFA enforcement tests
- Recent-authentication tests
- Recovery enumeration tests

## 27.2 Authorization

- Driver object-level access tests
- Cross-organization operator tests
- Removed-membership tests
- Case-grant expiry tests
- Masked-field reveal tests
- Break-glass scope tests
- Internal caller-scope tests
- Delegated-action assertion tests

## 27.3 Browser

- CSP validation
- XSS payload tests
- CSRF tests
- Cookie-attribute tests
- CORS tests
- Clickjacking tests
- Cache-control tests

## 27.4 Services and messages

- Invalid issuer/audience tests
- Expired token tests
- Wrong service identity tests
- Broker permission tests
- Message replay tests
- Command-ID reuse tests
- Forged actor-context tests
- Quarantine authorization tests

## 27.5 Device

- Invalid/revoked certificate tests
- Wrong-station event tests
- Certificate rotation tests
- Connection-flood tests
- Oversized/invalid message tests
- Duplicate command tests
- Sequence manipulation tests

## 27.6 Data and privacy

- SQL injection tests
- Log/token leakage scans
- Export authorization tests
- Tombstone replay tests
- Backup access tests
- Cross-service database-access tests

---

# 28. Security incident controls

Security incident handling must support:

1. Detection
2. Classification
3. Containment
4. Credential/session revocation
5. Evidence preservation
6. Scope assessment
7. Privacy-impact assessment
8. Recovery
9. Corrective action
10. Post-incident review

Emergency revocation must support:

- User sessions
- Service identity
- Machine certificate
- Provider credentials
- Signing key
- Support or break-glass grant

No automated alert independently declares a legally reportable personal-data breach.

---

# 29. Proposed security decisions

| ID | Decision |
|---|---|
| ARC-SEC-01 | Use a confidential BFF so OAuth tokens never enter Angular storage or JavaScript. |
| ARC-SEC-02 | Use Authorization Code with PKCE, state and nonce. |
| ARC-SEC-03 | Store browser sessions and Identity Provider tokens server-side. |
| ARC-SEC-04 | Use Secure, HttpOnly, host-only session cookies and explicit CSRF protection. |
| ARC-SEC-05 | Use Keycloak as the preferred Identity Provider pending proof-of-concept ADR. |
| ARC-SEC-06 | Use short-lived, audience-limited JWT access tokens. |
| ARC-SEC-07 | Validate issuer, audience, expiry, not-before, type and allowlisted algorithms. |
| ARC-SEC-08 | Use token exchange or equivalent downscoping for target-service edge calls. |
| ARC-SEC-09 | Authenticate services with unique asymmetric credentials or workload identity. |
| ARC-SEC-10 | Use `private_key_jwt` as the portable service-authentication profile. |
| ARC-SEC-11 | Use service identity plus operation-bound delegated assertions for chained human actions. |
| ARC-SEC-12 | Keep final authorization in the authoritative service. |
| ARC-SEC-13 | Treat organization membership, case access and resource ownership as dynamic authoritative data. |
| ARC-SEC-14 | Require MFA for all privileged human roles. |
| ARC-SEC-15 | Require step-up authentication for high-impact actions. |
| ARC-SEC-16 | Use case-scoped, expiring Platform Support grants. |
| ARC-SEC-17 | Implement break-glass as temporary workflow access, not a permanent role. |
| ARC-SEC-18 | Use per-station mTLS certificates for deployed simulator identities. |
| ARC-SEC-19 | Prohibit shared simulator or service credentials. |
| ARC-SEC-20 | Use least-privilege broker, database and secret-store identities. |
| ARC-SEC-21 | Keep credentials, tokens and Start Authorization secrets out of logs and messages. |
| ARC-SEC-22 | Adopt OWASP ASVS 5.0.0 Level 2 as the release verification baseline. |
| ARC-SEC-23 | Apply selected Level 3 controls to privileged, privacy and machine-identity functions. |
| ARC-SEC-24 | Require security scans, SBOMs and threat-model review in the delivery process. |
| ARC-SEC-25 | Require explicit risk acceptance for residual `HIGH` risks. |

---

# 30. Open security questions

| ID | Question | Resolution phase |
|---|---|---|
| ARC-SEC-OQ-01 | Final Keycloak version and deployment profile | Technology selection |
| ARC-SEC-OQ-02 | Final BFF session-store technology | Technology/deployment |
| ARC-SEC-OQ-03 | Final access, refresh and browser-session lifetimes | Security/UX testing |
| ARC-SEC-OQ-04 | Exact token-exchange configuration and caching | Technology proof of concept |
| ARC-SEC-OQ-05 | Final delegated-action assertion format | Detailed security contracts |
| ARC-SEC-OQ-06 | Whether internal access tokens use DPoP or mTLS binding | Technology/deployment |
| ARC-SEC-OQ-07 | Cloud workload-identity mechanism | Cloud architecture |
| ARC-SEC-OQ-08 | Internal certificate-authority technology | Cloud/deployment |
| ARC-SEC-OQ-09 | Final secret-management system | Cloud selection |
| ARC-SEC-OQ-10 | Final support-grant signing and revocation design | Detailed security contracts |
| ARC-SEC-OQ-11 | Final break-glass approval model for deployed demonstration | Operations |
| ARC-SEC-OQ-12 | Final CSP and trusted third-party origins | Frontend architecture |
| ARC-SEC-OQ-13 | Exact rate-limit and anomaly thresholds | Testing/operations |
| ARC-SEC-OQ-14 | Local audit tamper-evidence mechanism | Observability/operations |
| ARC-SEC-OQ-15 | Exact ASVS v5.0.0 requirement mapping | Testing strategy |
| ARC-SEC-OQ-16 | Vulnerability remediation deadlines by severity | CI/CD and operations |

---

# 31. Acceptance criteria

This security architecture is approved when:

1. OAuth access and refresh tokens do not enter browser JavaScript storage.
2. Browser mutations have explicit CSRF protection.
3. Every service validates audience and issuer.
4. Every service has an independent revocable identity.
5. No shared production service or simulator credential exists.
6. Final authorization occurs in the authoritative service.
7. Driver ownership and operator organization scope are enforced.
8. Platform Support cannot access data without a valid case grant.
9. Break-glass access expires and triggers review.
10. Simulator identity is certificate-bound to assigned infrastructure.
11. A simulator cannot report another station’s EVSE.
12. Internal delegated actions are bound to actor, operation and resource.
13. Broker access cannot bypass business authorization.
14. Database and migration roles are separated.
15. Secrets are absent from source control, logs and broker payloads.
16. Privacy exports require recent authentication and scoped download.
17. Critical security actions create durable local audit evidence.
18. Threats rated `CRITICAL` have documented preventive and detective controls.
19. Residual `HIGH` risks require explicit acceptance.
20. Security testing maps to ASVS 5.0.0 and stable requirement IDs.

---

# 32. Consequences

## Positive

- Browser token exposure is substantially reduced.
- Authorization remains resource- and tenant-aware.
- Internal service calls are attributable.
- Device identities are independently revocable.
- Support and emergency access are bounded.
- Compromise of one service does not automatically grant platform-wide authority.
- Security verification has a stable baseline.

## Negative

- The BFF requires secure shared session storage.
- Token exchange and delegated assertions add complexity.
- Machine certificates require issuance and rotation procedures.
- MFA and step-up flows increase UX and testing effort.
- Broker and database permissions require detailed deployment automation.
- ASVS Level 2 verification creates significant test and documentation work.

These costs are accepted because identity, booking integrity, linked location data and device commands are high-value security assets.

---

# 33. Next architecture artifact

The next document is:

**Frontend Architecture, Screen Catalogue and UX Flow Specification v1.0**

It must define:

- Angular application structure
- BFF integration
- Route and role protection
- Screen catalogue
- Driver, operator, administrator and support navigation
- State management
- API-client generation
- Error and workflow status handling
- Availability freshness presentation
- Accessibility
- Responsive map/list behaviour
- Greek/English localization
- Security-sensitive UX
- End-to-end journey mapping

### Service-to-Service Security Design
To secure inter-service communications, the platform enforces the following rules:
- **Service Identity:** Each microservice has an asymmetric credential key pair, requesting access tokens via OAuth 2.0 Client Credentials flow.
- **Audience Validation:** Every target service validates that the incoming token's `aud` claim matches its specific service identity.
- **Broker Permissions:** Access to the RabbitMQ broker is restricted by service-specific virtual hosts and read/write permission scopes.
- **Propagation of Scope:** The actor context (e.g. driver ID, organization ID, and support case scope) is propagated inside signed JWT claims.
- **Membership Caching:** Stale organization memberships are mitigated by setting service tokens' validity lifetime to a maximum of 15 minutes.
