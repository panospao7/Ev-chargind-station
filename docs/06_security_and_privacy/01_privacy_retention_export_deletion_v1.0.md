Document ID: PRV-001
Title: Privacy, Retention, Export, Deletion and Anonymization v1.0
Version: 1.0
Status: APPROVED
Owner: PA/SA/BA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: DOM-001
Authoritative for: GDPR Compliance, Deletion purge bounds, and location masking

---

# Privacy, Retention, Export, Deletion and Anonymization Workflows v1.0 — Draft

## 1. Purpose

Define how the platform minimizes, classifies, retains, exports, restricts, deletes and anonymizes personal data across independently owned capabilities.

This is an engineering specification, not a declaration of legal compliance. Before any real public deployment, the controller/processor roles, legal bases, notices, contracts, retention periods and transfer arrangements require qualified review.

## 2. Regulatory design baseline

The platform uses GDPR principles including purpose limitation, data minimization, storage limitation, integrity/confidentiality and accountability. GDPR rights include access, rectification, erasure, restriction and portability, but those rights have different scopes and exceptions. citeturn2view0turn2view2turn2view3turn1view3

Requests under GDPR Articles 15–22 generally require action without undue delay and within one month; a justified extension of up to two further months is possible. The platform will use an internal 20-day target to preserve review time. citeturn2view1

Mobility and location-related data deserve particular minimization because they can expose a person’s movements. The project will avoid continuous driver tracking and retain only the station/session information required for the requested service. citeturn1view1

A DPIA checkpoint is required before implementation readiness. Whether a formal DPIA is legally mandatory depends on the final processing risk, but the HDPA identifies systematic processing descriptions, necessity/proportionality, risks, and safeguards as core DPIA content. citeturn1view4

## 3. Privacy principles

1. Collect only data necessary for an approved purpose.
2. Assign every data field an owner, purpose, classification, lawful-basis candidate and retention rule.
3. Do not reuse data for incompatible purposes silently.
4. Privacy defaults must be the least intrusive.
5. Do not collect VIN, registration plate, home address or continuous location history in v1.
6. User location is processed transiently for search unless the user explicitly saves a preference.
7. Simulator/device messages contain no unnecessary driver information.
8. Search, analytics and logs use pseudonymous or aggregated data where practical.
9. Deletion from one capability must propagate to all derived projections.
10. Pseudonymized data remains personal data when re-identification remains possible.
11. Data is called anonymous only when re-identification is not reasonably possible.
12. No service may retain data indefinitely merely because storage is inexpensive.

## 4. Roles and governance

Before deployment, document:

- Platform controller identity and contact details
- Data Protection Officer/contact, if applicable
- Operator organization roles where operator staff access booking data
- Cloud, identity, email, map, monitoring and storage providers
- Processor/subprocessor relationships and agreements
- Data locations and international transfers
- Processing purposes and legal-basis assessment
- Records of processing activities
- DPIA outcome and residual risks

GDPR Article 30 provides for records of processing activities, and Article 32 requires security measures appropriate to risk, including measures such as encryption, resilience, restoration and regular testing. citeturn2view6turn2view7

## 5. Data classification

### P0 — Public

- Published station information
- Public EVSE identifiers
- Connector and tariff information
- Aggregated non-personal statistics

### P1 — Internal operational

- Simulator configuration
- Non-sensitive infrastructure diagnostics
- Deployment metadata

### P2 — Personal

- Name and email
- Language and notification preferences
- Saved vehicle details
- Booking and session history
- Support-case information
- Account and policy-acceptance history

### P3 — Sensitive security/linked mobility

- Authentication/session metadata
- IP/security events
- Precise booking/session location linked to a driver
- Privacy-request evidence
- Privileged-access records

### P4 — Secrets

- Passwords handled by the identity provider
- Access/refresh tokens
- Enrollment credentials
- Private keys
- Start-authorization secrets

P4 data is never exported through ordinary privacy exports, logged, placed in events or exposed to operators.

## 6. Data inventory

Every personal-data field must be recorded in a data inventory containing:

- Field and data category
- Authoritative owner
- Processing purpose
- Candidate legal basis
- Source
- Recipients
- Classification
- Retention trigger and period
- Export/rectification/deletion treatment
- Security controls
- Projection and backup locations

Schema reviews must reject new personal fields without inventory entries.

## 7. Purpose boundaries

### Service delivery

Account operation, compatibility, bookings, check-in, simulated charging, support and essential emails.

### Security and abuse prevention

Authentication events, rate-limit evidence, suspicious booking activity and privileged actions.

### Operations

Fault resolution, reconciliation, reliability and auditability.

### Analytics

Utilization and reliability analysis using aggregated or pseudonymized records.

### Marketing

Excluded from v1. Transactional email cannot be treated as marketing consent.

Legal bases are candidates until formal review. Consent must not be used merely because it is convenient when processing is actually required to provide the requested service.

## 8. Collection rules

- Browsing does not require an account.
- Browser geolocation requires an explicit user action and permission.
- Exact search coordinates are not retained by default.
- IP addresses are not stored in ordinary business records.
- Logs avoid email, display name, authorization headers and full request bodies.
- Vehicle make/model/year and battery capacity remain optional.
- No special-category personal data is intentionally collected.
- Free-text fields display warnings and apply access restrictions because users may enter unexpected personal data.
- Public analytics use thresholds to prevent singling out individuals.

## 9. Rights-request lifecycle

States:

`SUBMITTED → IDENTITY_VERIFICATION → TRIAGE → IN_PROGRESS → REVIEW → COMPLETED`

Alternative states:

- `AWAITING_CLARIFICATION`
- `PARTIALLY_FULFILLED`
- `REJECTED_WITH_REASON`
- `CANCELLED_BY_REQUESTER`

Every request records:

- Request ID and type
- Submission channel/time
- Identity-verification status
- Scope
- Deadline and extension
- Assigned reviewer
- Capability responses
- Decision and reason
- Completion evidence

The requester receives status information without exposure of internal security controls.

## 10. Identity verification

- Authenticated self-service requests require recent reauthentication.
- Requests made outside an authenticated session require proportionate verification.
- Do not request identity documents by default.
- Request only additional information necessary to resolve reasonable identity doubt.
- Uploaded verification evidence, if exceptionally required, receives restricted access and short retention.
- Agents cannot use support knowledge questions containing easily discoverable information.

## 11. Access export — DR-24

An access package explains and provides:

- Account and profile data
- Saved vehicles and compatibility preferences
- Booking records and lifecycle metadata
- Charging-session summaries and accepted meter information
- Notification preferences and delivery history
- Support cases involving the requester
- Policy acceptances
- Relevant audit/security information that can safely be disclosed
- Purposes, categories, recipients, retention information and source where required

It excludes or redacts:

- Other individuals’ data
- Secrets and credentials
- Security details whose disclosure would harm platform protection
- Legally privileged or otherwise exempt material
- Internal information that is not personal data about the requester

### Workflow

1. Verify identity and scope.
2. Create a stable workflow ID.
3. Request contributions from authoritative owners.
4. Track missing, failed and completed contributions.
5. Assemble JSON as the canonical machine-readable format.
6. Optionally provide readable CSV/PDF summaries.
7. Encrypt the package in storage.
8. Issue a short-lived authenticated download.
9. Notify the user without attaching the export to email.
10. Delete the package after expiry.

Proposed download expiry: seven days. Export generation and download are audited.

## 12. Portability

Portability is not identical to a complete access export. The portability dataset is limited to applicable data provided by the user and relevant observed data processed by automated means under the applicable legal basis. GDPR Article 20 specifies a structured, commonly used, machine-readable format and limits the right to processing based on consent or contract. citeturn2view2turn2view4

The project supports JSON and CSV. Direct controller-to-controller transfer is deferred, but the architecture must not prevent it.

## 13. Rectification

Users can directly edit ordinary profile, vehicle and preference data.

Immutable historical facts are not overwritten. Corrections use:

- Corrective metadata
- Superseding records
- Audit-linked amendments
- Recalculated projections where necessary

A user cannot rewrite actual booking/session events, but may dispute their accuracy through a support/privacy case.

## 14. Restriction of processing

A restriction marks specified data as unavailable for ordinary processing while preserving it where required.

Effects may include:

- Preventing analytics use
- Preventing ordinary support access
- Suspending disputed-data updates
- Allowing storage and authorized legal/security review only

Restrictions must propagate to projections and must not be represented as deletion.

## 15. Account deletion — DR-25

Deletion states:

`REQUESTED → VALIDATING → COOLING_OFF → APPROVED → PROCESSING → COMPLETED`

Alternative states:

- `BLOCKED`
- `PARTIALLY_COMPLETED`
- `REQUIRES_REVIEW`
- `CANCELLED`

### Validation blockers

- Active or upcoming booking
- Active, starting, stopping or uncertain session
- Unresolved equipment-fulfilment workflow
- Open dispute, fraud or security investigation
- Applicable legal/contractual retention requirement
- Active operator ownership that must first be transferred

The platform explains blockers without exposing security-sensitive details.

### Proposed cooling-off period

Seven days after confirmation, unless immediate action is required or legally appropriate. During this period:

- New bookings are disabled.
- Existing obligations are resolved.
- The requester may cancel the deletion request.

This is a product safeguard, not a claimed legal requirement.

### Processing

1. Reauthenticate and confirm deletion.
2. Disable new business activity.
3. Revoke application and identity sessions.
4. Delete saved vehicles and optional profile fields.
5. Remove notification preferences and pending non-essential deliveries.
6. Delete or tokenize direct identifiers.
7. Anonymize or pseudonymize retained booking/session records.
8. Redact support-case personal content where permissible.
9. Propagate deletion facts to search, analytics, notification and audit projections.
10. Delete the identity-provider account when required steps complete.
11. Preserve only minimal completion evidence.

Erasure is not absolute where an applicable exception or continuing necessity exists; retained data must be limited, protected and tied to a documented reason. citeturn2view3

## 16. Anonymization and pseudonymization

### Pseudonymization

Replace the driver ID with a protected surrogate while retaining a separately controlled re-identification mapping. Use when temporary linkage remains necessary for disputes, security or workflow completion.

### Anonymization

For permanent analytics retention:

- Remove direct identifiers.
- Remove stable user/account identifiers.
- Generalize dates where exact time is unnecessary.
- Aggregate station/session statistics.
- Suppress rare combinations and small groups.
- Remove free text.
- Test linkage and singling-out risk.
- Destroy the re-identification mapping.

Hashing an email address alone is not sufficient anonymization.

## 17. Provisional retention schedule

These are engineering defaults, not assertions of mandatory legal periods.

| Data | Proposed retention trigger/period |
|---|---|
| Unverified account | Delete after 7 days |
| Expired verification/recovery tokens | Delete payload immediately; retain safe outcome metadata 90 days |
| Active profile and vehicles | Account lifetime |
| Deleted profile direct identifiers | Remove within 30 days after approved deletion |
| Expired booking holds | 90 days, then delete or aggregate |
| Booking/session user-visible history | Account lifetime, with a user option to hide/archive |
| Linked completed booking/session records | 24 months, then anonymize unless retention is justified |
| Raw simulator meter telemetry | 90 days |
| Final session summary | 24 months linked, then anonymized aggregate |
| Transactional email content | 90 days after final delivery outcome |
| Email delivery metadata | 12 months |
| Ordinary application logs | 30 days |
| Security logs | 12 months |
| Privileged audit records | 24 months |
| Support cases | 24 months after closure, with earlier redaction where possible |
| Privacy-request case | 3 years after closure, containing minimal evidence |
| Generated export archive | 7 days |
| Inbox/outbox payloads | Minimize personal data; purge after replay/audit window, proposed 90 days |
| Database backups | 35-day rolling expiry |

Before implementation, every period needs an owner, rationale and approved deletion test.

## 18. Retention engine

Retention rules are configuration-controlled and versioned.

Each rule defines:

- Data category
- Authoritative owner
- Trigger event
- Retention period
- Action: delete, redact, aggregate, anonymize or archive
- Exceptions
- Batch size
- Evidence produced
- Rule version

Jobs must be idempotent, use database time, process bounded batches and expose failures. Changing a retention rule requires approval and impact review.

## 19. Legal/security holds

A hold pauses an otherwise due deletion only for defined records.

A hold requires:

- Scope
- Reason category
- Authorized requester and approver
- Start date
- Review/expiry date
- Access restrictions
- Audit record

Indefinite or organization-wide holds are prohibited by default. When a hold ends, overdue retention work resumes.

## 20. Backups and restoration

- Backups are encrypted and access-controlled.
- Individual records are not expected to be surgically removed from immutable backups.
- Deleted data may remain unavailable in protected backups until scheduled backup expiry.
- A deletion ledger/tombstone is stored separately from deleted personal content.
- After restoration, completed deletions, restrictions and anonymizations are replayed before ordinary service resumes.
- Restoration testing must verify privacy-action replay.

## 21. Logs, traces, events and caches

- Use internal IDs rather than email addresses.
- Redact authorization headers, cookies, tokens and QR/start secrets.
- Do not log request/response bodies by default.
- Events carry the minimum data needed by consumers.
- Consumers must not build undeclared shadow profiles.
- Cache entries receive explicit TTLs and deletion propagation.
- Trace attributes must avoid personal and free-text fields.
- Dead-letter access is restricted and retained payloads follow source-data rules.

## 22. Analytics

- Analytics is never authoritative for user rights or business actions.
- Prefer station-, EVSE-, day- or month-level aggregation.
- Maintain no unnecessary user-level analytics profile.
- Apply deletion/anonymization events idempotently.
- Rebuilt projections must reapply privacy tombstones.
- Reports display freshness and aggregation limits.
- Small-cell suppression thresholds will be finalized during analytics design.

## 23. Operator and support access

- Operators see only data necessary for owned-station operations.
- Technicians do not see driver identity.
- Support access requires an assigned case.
- Contact fields are masked by default.
- Reveal actions require reason and audit.
- Bulk user export is not an operator capability.
- Administrators cannot silently impersonate drivers.
- Privacy-case access is separated from ordinary support access.

## 24. Notifications

- Privacy exports are never attached to email.
- Deletion and rights-request emails contain minimal detail.
- Email provider payloads exclude booking history beyond what the message requires.
- Unsubscribe applies to optional communications, not mandatory security/service messages.
- Suppression lists retain the minimum address evidence necessary to prevent prohibited resending, subject to legal review.

## 25. Data incidents

The security plan must define:

- Detection and containment
- Affected-data and subject assessment
- Evidence preservation
- Processor escalation
- Notification decision workflow
- Corrective action and lessons learned

GDPR Article 33 sets a supervisory-authority notification rule of without undue delay and, where feasible, within 72 hours after awareness when the applicable risk threshold is met. citeturn2view0

No automated job independently decides that an incident is legally reportable; authorized review is required.

## 26. Privacy acceptance criteria

1. Public browsing works without account creation.
2. Exact browser location is not stored by default.
3. No ordinary logs contain tokens, passwords or start authorizations.
4. Every personal field maps to a purpose, owner and retention rule.
5. Access export includes all mandatory authoritative participants.
6. Export retries do not create inconsistent packages.
7. Export links are authenticated, single-purpose and expiring.
8. Portability and full access exports are distinguished.
9. Deletion cannot silently remove an active booking/session obligation.
10. Completed deletion propagates to all projections.
11. Retained records contain only justified minimum data.
12. Pseudonymized records are not falsely labelled anonymous.
13. Analytics cannot re-identify deleted users through stable identifiers.
14. Retention jobs are idempotent and observable.
15. Holds are scoped, expiring and audited.
16. Restored backups reapply privacy actions.
17. Support cannot browse privacy data without case scope.
18. Another person’s data is excluded or redacted from exports.
19. Privacy requests survive worker/service restarts.
20. Completion is not claimed while mandatory participants remain incomplete.

## 27. Required tests

- Data-inventory/schema-governance tests
- Geolocation minimization tests
- Log/event secret-scanning tests
- Export completeness and redaction tests
- Cross-capability partial-failure tests
- Expired download tests
- Rectification projection tests
- Restriction-enforcement tests
- Deletion-blocker tests
- Deletion idempotency tests
- Identity-provider failure tests
- Anonymization/linkage-risk review
- Retention boundary tests
- Hold-expiry tests
- Cache and projection deletion tests
- Backup restoration/privacy replay tests
- Operator/support authorization tests
- Dead-letter personal-data tests

## 28. Proposed decisions for approval

1. Do not retain precise search-location history by default.
2. Exclude VIN, registration plate and home address from v1.
3. Separate access export, portability, rectification, restriction and deletion workflows.
4. Use JSON as the canonical export format, with optional CSV/PDF views.
5. Make generated exports available for seven days.
6. Introduce a seven-day deletion cooling-off period as a product safeguard.
7. Block deletion while active or uncertain business obligations exist.
8. Preserve immutable historical facts through correction metadata rather than destructive rewriting.
9. Use pseudonymization only where justified and anonymization for long-term analytics.
10. Use a versioned retention engine rather than scattered hard-coded cleanup periods.
11. Adopt the provisional retention table pending formal review.
12. Use 35-day rolling backup retention initially.
13. Reapply privacy tombstones after backup restoration.
14. Require scoped, expiring legal/security holds.
15. Prohibit user-level analytics profiles unless later justified and approved.
16. Perform a DPIA-style assessment before implementation readiness.
17. Maintain a data inventory and record of processing activities.
18. Treat controller, processor, legal-basis and international-transfer decisions as open until cloud/provider selection.

## 29. Traceability

Primarily implements:

- `FR-PRV-01`
- `FR-IAM-01`
- `FR-IAM-04`
- `FR-AUD-01`
- `FR-PLT-01`
- `DR-24`
- `DR-25`
- `AD-08`

Supports the privacy, security, durability, maintainability and operability NFRs.