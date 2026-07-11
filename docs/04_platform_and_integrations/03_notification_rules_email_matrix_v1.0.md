Document ID: NOT-001
Title: Notification Rules and Essential Email Matrix v1.0
Version: 1.0
Status: APPROVED
Owner: PO/BA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: DOM-001
Authoritative for: Transactional Email Triggers and Retrying Constraints

---

# Notification Rules and Essential Email Matrix v1.0 — Draft

## 1. Purpose

Define:

- Which platform events require email
- Which messages users may disable
- Notification ownership and delivery
- Security-link handling
- Retry, deduplication and failure behaviour
- Template and localization governance
- Email-provider integration
- Privacy, observability and testing requirements

Version 1 supports **transactional email only**. In-app, SMS, push and marketing communications remain deferred.

## 2. Core principles

1. Email is never the authoritative source of business state.
2. A booking or account operation succeeds only according to its authoritative transaction—not email delivery.
3. Notifications are created only after the triggering business transaction commits.
4. Notification processing is asynchronous and eventually consistent.
5. Delivery uses at-least-once event processing with idempotent notification creation.
6. Mandatory service/security messages cannot be disabled.
7. Optional reminders and summaries respect user preferences.
8. Email failure never silently reverses a committed booking.
9. Messages contain the minimum personal and operational data needed.
10. Passwords, access tokens, session tokens and start authorizations must never appear in email.
11. Sensitive action links are single-use, expiring and server-validated.
12. Every template and delivery attempt is versioned and auditable.
13. Email must not reveal another user’s identity or booking.
14. Greek and English templates are required.
15. The application remains usable when the email provider is unavailable, except where mailbox verification is itself required.

## 3. Notification categories

### `SECURITY`

Mandatory messages protecting account access:

- Email verification
- Password reset
- Password changed
- Email address changed
- MFA changed
- Account/session security intervention

### `ACCOUNT`

Mandatory account lifecycle messages:

- Account suspension/reactivation
- Operator invitation
- Role or organization-access change
- Account deletion workflow

### `BOOKING`

Mandatory messages for significant reservation changes:

- Confirmation
- Rescheduling
- Cancellation
- Reassignment
- No-show
- Fulfilment failure

### `CHARGING`

Mandatory only for significant unexpected outcomes:

- Session interrupted
- Start permanently rejected
- Session outcome requires review

Routine start and completion summaries may be preference-controlled.

### `OPERATIONS`

Operationally necessary messages:

- Maintenance impact
- Station closure affecting a booking
- Operator approval/suspension
- Privileged emergency action

### `PRIVACY`

Mandatory privacy-workflow messages:

- Export ready
- Deletion confirmation
- Request blocked or completed

### `SERVICE_REMINDER`

Optional:

- Upcoming booking reminder
- Check-in window reminder
- Session-completion summary
- Support-case status update without required action

### `MARKETING`

Excluded from v1.

## 4. Preference rules

Users may configure:

- Upcoming booking reminders
- Session-completion summaries
- Non-critical support updates
- Future optional product communications

Users may not disable:

- Authentication and security messages
- Booking confirmations and material changes
- Operator-initiated cancellation
- Reassignment
- Fulfilment failure
- Account suspension
- Privacy-request completion
- Messages requiring user action

Disabling an optional category prevents future notification creation. It does not delete already queued or legally required records.

Transactional messages must not be reclassified as marketing.

## 5. Essential email matrix

| ID | Trigger | Recipient | Mandatory | Target timing |
|---|---|---|---|---|
| IAM-EM-01 | Registration accepted | Driver | Yes | Immediate |
| IAM-EM-02 | Verification link resent | Driver | Yes | Immediate |
| IAM-EM-03 | Password-reset request for existing account | Account owner | Yes | Immediate |
| IAM-EM-04 | Password changed | Account owner | Yes | Immediate |
| IAM-EM-05 | Primary email change requested | Old and new addresses as appropriate | Yes | Immediate |
| IAM-EM-06 | Primary email changed | Old and new addresses | Yes | Immediate |
| IAM-EM-07 | MFA enrolled, removed or recovered | Privileged account owner | Yes | Immediate |
| IAM-EM-08 | All sessions revoked | Account owner | Yes | Immediate |
| IAM-EM-09 | Account suspended/reactivated | Account owner | Yes | Immediate |
| OP-EM-01 | Operator staff invitation | Invitee | Yes | Immediate |
| OP-EM-02 | Operator application approved/rejected | Applicant | Yes | Immediate |
| OP-EM-03 | Organization role changed or access removed | Staff member | Yes | Immediate |
| OP-EM-04 | Organization suspended/closed | Owner and affected managers | Yes | Immediate |
| BKG-EM-01 | Booking confirmed | Driver | Yes | After commit |
| BKG-EM-02 | Booking rescheduled | Driver | Yes | After commit |
| BKG-EM-03 | Booking cancelled | Driver | Yes | After commit |
| BKG-EM-04 | Booking reassigned | Driver | Yes | After commit |
| BKG-EM-05 | Booking classified as no-show | Driver | Yes | After commit |
| BKG-EM-06 | Booking fulfilment at risk requiring action | Driver/operator | Yes | Promptly |
| BKG-EM-07 | Booking fulfilment failed | Driver | Yes | After commit |
| BKG-EM-08 | Emergency maintenance affects booking | Driver/operator | Yes | Promptly |
| CHG-EM-01 | Start permanently rejected | Driver | Yes | After resolution |
| CHG-EM-02 | Charging session interrupted | Driver | Yes | After confirmed outcome |
| CHG-EM-03 | Session outcome remains unresolved | Driver/operator | Yes | After uncertainty threshold |
| CHG-EM-04 | Session completed normally | Driver | Optional | After finalization |
| SUP-EM-01 | Support case created | Requester | Yes | After commit |
| SUP-EM-02 | Support case requires user action | Requester | Yes | After commit |
| SUP-EM-03 | Support case resolved/closed | Requester | Optional unless action/material decision exists | After commit |
| PRV-EM-01 | Privacy request received | Requester | Yes | After verification |
| PRV-EM-02 | Privacy export ready | Requester | Yes | After secure assembly |
| PRV-EM-03 | Privacy request delayed or clarification required | Requester | Yes | When determined |
| PRV-EM-04 | Deletion request confirmed/cooling-off begins | Requester | Yes | After commit |
| PRV-EM-05 | Deletion blocked | Requester | Yes | After validation |
| PRV-EM-06 | Deletion cancelled | Requester | Yes | After commit |
| PRV-EM-07 | Deletion completed | Former account address where permitted | Yes | After completion |
| SEC-EM-01 | Break-glass access activated | Security reviewers | Yes | Immediate |
| SEC-EM-02 | Privileged emergency action affects user | User and reviewers where safe | Yes | After commit |

“Immediate” means queued without intentional delay. Provider acceptance and final mailbox delivery are measured separately.

## 6. Messages not sent by email in v1

No email is required for:

- Booking hold creation
- Hold expiration before confirmation
- Successful check-in
- Routine charging start
- Individual meter updates
- Ordinary EVSE status changes
- Search or availability changes
- Every simulator heartbeat
- Routine analytics generation
- Repeated copies of unchanged faults

These states remain visible in the application.

## 7. Recommended reminder policy

Optional reminders:

- 24 hours before booking start
- 60 minutes before booking start
- When the check-in window opens

Rules:

- Do not create reminders for cancelled, expired, no-show or completed bookings.
- Recalculate reminders after rescheduling.
- Cancel obsolete scheduled reminders.
- Use the booking’s current version.
- Reminders cannot extend booking or check-in deadlines.
- The exact scheduled time and `Europe/Athens` timezone must be shown.

## 8. Notification architecture

Logical components:

### Event producer

The authoritative capability commits:

- Business state
- Audit metadata
- Transactional outbox event

### Notification capability

It:

1. Consumes the event idempotently.
2. Determines whether a notification is required.
3. Evaluates recipient and preferences.
4. Selects locale and template version.
5. Creates a delivery record.
6. Dispatches through the provider.
7. Tracks delivery outcome.
8. Retries transient failures.
9. Quarantines permanent failures.

### Email provider

The provider transports email but never determines business state.

Final microservice placement remains open.

## 9. Identity-provider messages

Email verification, password reset and identity-action links remain owned by the identity provider.

If Keycloak is selected, it supports verification and reset-credentials email workflows and customizable messages. ([keycloak.org](https://www.keycloak.org/docs/latest/server_admin/?utm_source=openai))

Rules:

- The identity provider generates and validates action tokens.
- Raw verification/reset tokens must not pass through the message broker.
- Application services must not create password-reset credentials.
- Identity and application emails should use consistent branding and provider configuration.
- Identity delivery outcomes should be observable without exposing token values.

## 10. Security-link requirements

Verification, recovery, invitation, email-change and privacy-download links must:

- Use HTTPS.
- Use an allowlisted, configured application origin.
- Never derive their origin directly from an untrusted `Host` header.
- Be single-purpose.
- Be single-use where applicable.
- Expire after a defined period.
- Be invalidated after successful use or superseding action.
- Be protected by rate limits.
- Avoid token leakage through logs and referrer headers.

OWASP recommends generic recovery responses, cryptographically strong single-use tokens, expiry, HTTPS, trusted reset origins and protection against referrer leakage. ([cheatsheetseries.owasp.org](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html?utm_source=openai))

Proposed lifetimes:

| Action | Lifetime |
|---|---:|
| Email verification | 24 hours |
| Password reset | 30 minutes |
| Email-address change | 30 minutes |
| Operator invitation | 48 hours |
| Privacy export download | 7 days |
| Deletion confirmation | 24 hours |

Resending creates a new link and invalidates the previous one where supported.

## 11. Account-enumeration protection

Registration, recovery and verification-resend endpoints return safe, generic responses regardless of whether an address exists.

Notification records may reveal account existence only to authorized internal services.

Requests are rate-limited by:

- Source address
- Normalized email hash
- Account, where known
- Device/browser indicators
- Global abuse thresholds

Generic responses and consistent processing reduce account-enumeration risk. ([cheatsheetseries.owasp.org](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html?utm_source=openai))

## 12. Notification record

Each notification records:

- Notification ID
- Category
- Template key and version
- Locale
- Trigger event ID
- Aggregate ID and version
- Recipient reference
- Masked destination
- Mandatory/optional classification
- Creation time
- Scheduled time
- Delivery state
- Attempt count
- Provider message reference
- Failure classification
- Correlation and causation IDs
- Expiry or obsolescence time

Full email content follows the approved retention policy.

## 13. Delivery lifecycle

`REQUESTED → QUEUED → DISPATCHING → PROVIDER_ACCEPTED`

Possible later states:

- `DELIVERED`
- `TEMPORARILY_FAILED`
- `PERMANENTLY_FAILED`
- `BOUNCED`
- `COMPLAINT`
- `SUPPRESSED`
- `OBSOLETE`
- `CANCELLED_BEFORE_SEND`

`PROVIDER_ACCEPTED` does not prove inbox delivery.

A terminal business operation does not wait for `DELIVERED`.

## 14. Idempotency

Notification uniqueness uses a stable key based on:

- Notification type
- Recipient
- Aggregate ID
- Aggregate version or workflow milestone
- Channel

Duplicate domain events return the existing notification record.

Retries reuse the same notification identity and must not create multiple provider submissions unless the previous provider outcome is genuinely unknown.

Provider webhook events are also deduplicated.

## 15. Ordering and obsolete messages

Events may be delayed or out of order.

Before sending a state-sensitive notification, the notification capability checks:

- Aggregate version
- Current relevant state
- Whether the message has become obsolete
- Whether a newer mandatory message supersedes it

Examples:

- A pending booking reminder is cancelled after cancellation.
- An unsent reschedule email may become obsolete after a later cancellation.
- A cancellation email is never suppressed merely because another later informational event exists.
- Security-change notifications remain historical facts and are not discarded.

## 16. Retry and failure rules

Transient failures use exponential backoff with jitter.

Retriable:

- Provider timeout
- Temporary provider rejection
- Network failure
- Rate-limit response
- Temporary DNS/provider problem

Permanent:

- Invalid destination syntax
- Confirmed hard bounce
- Rejected sender configuration
- Invalid template
- Provider suppression
- Unauthorized provider request

After retries are exhausted:

- Mark the delivery permanently failed.
- Move required diagnostic work to dead-letter/quarantine.
- Alert when mandatory-message failure thresholds are exceeded.
- Do not roll back the triggering business action.
- Display an application warning where user action depends on email.

## 17. Bounce, complaint and suppression handling

- Hard bounces suppress repeated sends to the address.
- Soft bounces follow bounded retries.
- Complaint webhooks suppress optional communications.
- Provider suppression lists must not be bypassed silently.
- A verified address that becomes undeliverable is marked `DELIVERY_PROBLEM`.
- The user is prompted in-app to provide and verify a new address.
- Mandatory business actions remain visible in the authenticated application.
- Booking validity is not cancelled solely because email bounced.

## 18. Template governance

Every template has:

- Stable template key
- Semantic version
- Greek and English variants
- HTML and plain-text bodies
- Subject
- Required variables
- Data classification
- Owning capability
- Mandatory/optional classification
- Snapshot tests
- Accessibility review
- Security/privacy review

Rules:

- Variables are escaped by default.
- Untrusted HTML is prohibited.
- Free text is omitted or strictly sanitized.
- Templates cannot directly query databases.
- Breaking variable changes require a new template version.
- The rendered template version is recorded.
- Template previews use synthetic data only.

## 19. Required booking-email content

Booking confirmation, reschedule and reassignment messages include:

- Public booking reference
- Station name and address
- Assigned EVSE public label
- Connector type
- Booking date/start/end in `Europe/Athens`
- Estimated tariff/cost label
- Check-in opening and grace deadline
- Cancellation rules
- Secure link to booking details
- Support route

They do not include:

- Internal database IDs
- Device credentials
- Another driver’s information
- Full operational diagnostics
- Reusable charging authorization
- Claims that estimated cost was paid

## 20. Privacy-email content

Privacy emails:

- Never attach export archives.
- Use an authenticated, expiring download flow.
- Avoid listing all data categories in the subject.
- Do not state sensitive rejection reasons in unsecured detail.
- Direct the requester to the authenticated privacy-request page.
- Contain no identity-document copies.

## 21. Email-domain protection

The deployment must configure:

- SPF authorization for legitimate sending systems
- DKIM signing
- DMARC policy and reporting
- Separate controlled sender addresses
- Bounce/return-path configuration

SPF authorizes sending hosts for a domain, DKIM associates messages with a cryptographic domain signature, and DMARC applies domain-alignment policy and reporting. ([datatracker.ietf.org](https://datatracker.ietf.org/doc/html/rfc6376?utm_source=openai))

Proposed senders:

- `account@…` for identity/security
- `bookings@…` for booking/service messages
- `privacy@…` for privacy workflows
- `no-reply@…` only where replies are genuinely unsupported

A monitored support address should be provided where the email asks users to report an unauthorized action.

## 22. Environment safety

### Local development

- Use a local mail catcher.
- Do not send to public addresses.
- Use clearly synthetic recipients.

### Test/staging

- Use a provider sandbox or destination allowlist.
- Prefix subjects with the environment.
- Prevent production mailing-list imports.
- Use synthetic data.
- Disable accidental external delivery by default.

### Production-like deployment

- Use verified sender domains.
- Store provider credentials in secrets management.
- Enable provider webhook authentication.
- Apply sending-rate and cost controls.

## 23. Security and privacy controls

- Provider credentials never appear in source control.
- Email addresses are excluded from ordinary logs.
- Logs use notification IDs and masked destinations.
- Tokens and action URLs are never logged.
- Provider webhooks require signature/authentication verification.
- Webhook payloads are validated and processed idempotently.
- Operators cannot send arbitrary custom email to drivers.
- Support agents use approved templates.
- Privileged manual resend requires authorization and audit.
- Email content avoids unnecessary booking/location history.
- Notification events contain references rather than broad profile data.

OWASP recommends excluding access tokens, passwords and sensitive personal information from logs. ([cheatsheetseries.owasp.org](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html?utm_source=openai))

## 24. Observability

Required metrics:

- Notification queue depth and age
- Creation-to-dispatch latency
- Provider-acceptance latency
- Delivery, bounce and complaint rates
- Retry counts
- Permanent failures
- Suppression count
- Dead-letter count and age
- Duplicate events prevented
- Obsolete messages suppressed
- Template-rendering failures
- Failures by category and locale

Alerts:

- Verification/reset dispatch failure
- Booking-cancellation delivery backlog
- Privacy-export delivery failure
- Break-glass alert failure
- Sudden bounce/complaint increase
- Provider authentication failure
- Dead-letter growth
- Queue age above threshold

## 25. Performance targets

Under normal platform conditions:

- p95 verification/reset messages queued to the provider within 60 seconds.
- p95 other essential messages queued within 2 minutes.
- p99 essential messages queued within 10 minutes.
- Provider/network latency is measured separately.
- Booking requests never synchronously wait for email transport.
- Notification processing applies backpressure under provider throttling.

## 26. Retention

Aligned with the privacy specification:

- Rendered transactional email content: proposed 90 days.
- Delivery metadata: proposed 12 months.
- Security-message metadata: according to security/audit retention.
- Provider webhook payloads: minimize and purge after processing/audit need.
- Expired token payloads: removed immediately after use/expiry.
- Suppression evidence: retain only what is required to prevent repeated sending.
- Templates and versions: retain while referenced by delivery records.

## 27. Acceptance criteria

1. Booking confirmation commits even when email is unavailable.
2. Every mandatory event produces at most one logical notification per recipient.
3. Duplicate events do not create duplicate logical email.
4. Optional reminders respect current preferences.
5. Mandatory security and booking messages cannot be disabled.
6. Cancelled bookings receive no later scheduled reminders.
7. Out-of-order events cannot send misleading stale notifications.
8. Verification and recovery links are expiring and single-use.
9. Reset requests do not reveal whether an account exists.
10. Email contains no password, start authorization or access token.
11. Provider acceptance is not reported as inbox delivery.
12. Hard bounces stop uncontrolled retries.
13. Booking state remains visible when email delivery fails.
14. Privacy exports are never attached to email.
15. Greek and English templates render correctly.
16. HTML and plain-text versions contain equivalent essential information.
17. Cross-organization users cannot trigger or inspect another organization’s notifications.
18. Provider webhook replay produces one state update.
19. Manual resend is authorized and audited.
20. Local/staging environments cannot accidentally email real users.

## 28. Required tests

- Event-consumer idempotency tests
- Out-of-order lifecycle tests
- Template snapshot tests
- Greek/English rendering tests
- HTML escaping and injection tests
- Secret/token leakage tests
- Generic recovery-response tests
- Expired/single-use link tests
- Reminder cancellation tests
- Preference-enforcement tests
- Provider timeout and retry tests
- Hard/soft bounce tests
- Complaint/suppression tests
- Webhook authentication/replay tests
- Dead-letter replay tests
- Cross-tenant authorization tests
- Environment destination-safety tests
- Accessibility tests
- Provider outage/load tests

## 29. Proposed decisions for approval

1. Support transactional email only in v1.
2. Keep SMS, push and in-app notifications deferred.
3. Make security, material booking, account and privacy messages mandatory.
4. Allow users to disable reminders and routine session summaries.
5. Use asynchronous event-driven delivery after authoritative commit.
6. Keep identity action-link ownership in the identity provider.
7. Never place raw identity-action tokens on the broker.
8. Adopt the proposed action-link lifetimes.
9. Send optional reminders 24 hours and 60 minutes before start and when check-in opens.
10. Use one notification identity per type, recipient, aggregate and version.
11. Suppress obsolete unsent messages after newer lifecycle changes.
12. Treat provider acceptance and final delivery as separate outcomes.
13. Never cancel a booking solely because email delivery failed.
14. Require HTML and plain-text templates in Greek and English.
15. Configure SPF, DKIM and DMARC for deployed email.
16. Use a local mail catcher and staging destination allowlist.
17. Adopt the proposed notification performance targets.
18. Apply the privacy specification’s provisional retention periods.
19. Prohibit arbitrary operator-authored driver email.
20. Require application-visible recovery when mandatory email is undeliverable.

## 30. Traceability

Primarily implements:

- `FR-NOT-01`
- `FR-IAM-01`
- `FR-IAM-02`
- `FR-BKG-04`
- `FR-BKG-05`
- `FR-OPS-02`
- `FR-PRV-01`
- `FR-AUD-01`
- `FR-PLT-01`
- `DR-23`

Supports security, privacy, reliability, accessibility, operability and maintainability NFRs.