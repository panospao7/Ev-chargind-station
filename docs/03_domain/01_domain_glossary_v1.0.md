Document ID: DOM-001
Title: Domain Glossary v1.0
Version: 1.0
Status: APPROVED
Owner: PO/DA/BA
Last reviewed: 2026-07-12
Supersedes: None
Depends on: None
Authoritative for: Canonical Terminology and Vocabulary Definition

---

This glossary establishes canonical terminology and resolves ambiguous terms such as charger, slot, status, reservation, availability, and session.

# Domain Glossary v1.0

## 1. Purpose

This glossary defines the canonical language used across:

- Requirements
- Architecture
- APIs and events
- Database models
- User interfaces
- Tests
- Documentation
- AI-agent instructions
- Academic evaluation

Where earlier documents use conflicting terminology, this glossary takes precedence after approval.

---

## 2. Terminology rules

1. Every important concept has one canonical name.
2. API, event and code names should use canonical English terms.
3. Greek UI translations must preserve the same meaning.
4. Similar concepts must not share an overloaded status field.
5. User-facing wording may be simpler, but must not alter domain meaning.
6. Internal IDs must not be exposed as public references.
7. Historical facts and current projections must be clearly distinguished.
8. “Charger,” “slot,” “available,” “active” and “failed” must not be used without qualification.
9. State names use uppercase `SNAKE_CASE`.
10. Event names describe completed facts in past tense.

---

# 3. Infrastructure

## Operator Organization

A business entity responsible for one or more charging stations.

It owns infrastructure configuration, tariffs, booking policies and operator staff memberships.

Canonical short form: **Operator**

Do not use “operator” ambiguously for an individual employee. Use **Operator Staff Member** for a person.

## Charging Station

A physical geographical location containing one or more EVSEs.

A station includes:

- Address and coordinates
- Opening hours
- Access instructions
- Amenities
- Tariffs and booking policies
- EVSE inventory

Canonical short form: **Station**

A station is not an individual charging point.

## EVSE

**Electric Vehicle Supply Equipment** representing one independently usable and reservable charging point.

In v1:

- One EVSE can serve one vehicle at a time.
- A booking allocates the entire EVSE.
- An EVSE may expose multiple connector options.
- Only one connector can be used for energy transfer at a time.

Canonical term in code and technical documentation: **EVSE**

Acceptable user-facing term: **Charging point**

Avoid using “charger” where EVSE is intended.

## Connector

A physical charging interface offered by an EVSE.

Examples:

- Type 2
- CCS Combo 2
- CHAdeMO

A connector describes compatibility and electrical capability. It is not independently reservable in v1.

## Connector Type

The standardized physical interface required by a vehicle.

A booking records the required connector type even though it reserves the EVSE.

## Compatible Connector

A connector whose type and supported electrical characteristics satisfy the driver’s selected requirements.

Compatibility assists booking but does not guarantee successful physical charging.

## Charging Station Controller

The simulated device-level controller representing one charging station and its EVSEs.

It owns one machine identity and one device connection.

Do not confuse it with the Station domain entity.

## Infrastructure

The collective term for:

- Operator organizations
- Stations
- EVSEs
- Connectors
- Tariffs
- Booking policies

## Public EVSE Identifier

A stable, non-secret identifier displayed on an EVSE and encoded in its QR code.

It can identify an EVSE but grants no authority to check in or start charging.

## Internal ID

A datastore-specific identifier used internally.

Internal IDs must not be exposed through public URLs, QR codes or ordinary user interfaces.

## Public Reference

A safe external identifier assigned to a booking, support case, fault report or another user-visible resource.

A public reference is not an authorization secret.

---

# 4. Administrative and operational state

## Administrative State

A state controlled by an authorized platform or operator user.

For an EVSE:

- `ACTIVE`
- `DISABLED`
- `DEACTIVATED`

Administrative state determines whether infrastructure is allowed to participate in platform operations.

It is separate from device-reported state and derived availability.

## Device-Reported State

The latest operational observation reported by the simulator.

Examples:

- `AVAILABLE`
- `RESERVED`
- `OCCUPIED`
- `CHARGING`
- `SUSPENDED`
- `FINISHING`
- `FAULTED`
- `UNAVAILABLE`
- `UNKNOWN`

A device-reported state does not independently authorize a booking.

## Derived Availability

The platform’s calculated result for whether an EVSE may be allocated for a particular interval and compatibility requirement.

It combines administrative, operational, scheduling and capacity information.

## Status Freshness

The age and reliability of the latest accepted device information.

Canonical classifications:

- **Live** — sufficiently recent
- **Stale** — older than the freshness threshold
- **Unknown** — no reliable status is available

Freshness is based on server-received time, not an untrusted device clock.

## Status Override

A temporary, audited operational restriction created by an authorized operator.

Ordinary overrides may make infrastructure less available. They cannot bypass booking, maintenance, fault or safety rules.

Overrides must expire automatically.

## Current Operational Summary

A present-time overview of device and infrastructure condition when no booking interval was requested.

It must not be labelled reservation availability.

---

# 5. Availability and allocation

## Availability Evaluation

The process of deciding whether an EVSE is eligible for a requested interval and compatibility requirement.

It evaluates:

- Administrative eligibility
- Connector compatibility
- Required power
- Opening hours
- Booking policy
- Maintenance
- Faults
- Status overrides
- Existing allocations
- Active or uncertain sessions
- Device status freshness

## Advisory Availability

Availability displayed by search or station-detail projections.

It may be eventually consistent and does not reserve capacity.

## Authoritative Availability Decision

The final availability evaluation made by the Booking authority during a transactional allocation operation.

Only this decision may claim EVSE capacity.

## Availability Result

One of:

### `AVAILABLE`

The EVSE is currently eligible for authoritative allocation with sufficient operational confidence.

### `PLANNED_AVAILABLE`

A future interval is eligible based on known schedules and capacity, but current device condition is not considered predictive of its future state.

### `UNAVAILABLE`

A definite business or operational rule prevents allocation.

### `UNKNOWN`

The system lacks sufficient reliable information to make a safe positive decision.

`UNKNOWN` must never be silently treated as `AVAILABLE`.

### `INCOMPATIBLE`

The EVSE does not satisfy the required connector or power characteristics.

## Allocation

The authoritative claim on one EVSE for an effective time interval.

An allocation is not a separate user-facing product. It is the capacity-control mechanism supporting a booking.

## Charging Interval

The period requested for the driver’s planned use:

`bookingStart` to `bookingEnd`.

## Allocation Interval

The interval used for conflict prevention.

In v1, it consists of:

- Charging interval
- Post-booking turnaround buffer

## Turnaround Buffer

A policy-defined period after planned or actual use during which the EVSE remains unavailable for another allocation.

It allows safe transition, departure and operational recovery.

## Half-Open Interval

A time interval including its start but excluding its end:

`[start, end)`

Two intervals touching exactly at their unbuffered boundaries do not overlap. Their effective allocation intervals may still conflict because of a buffer.

## Allocation Conflict

An overlap between two effective allocation intervals for the same EVSE.

## Double Booking

The invalid condition where more than one conflicting allocation is committed for the same EVSE.

Preventing double booking is a release-critical correctness invariant.

## Auto-Assignment

The platform’s selection of any compatible and available EVSE at a chosen station.

## Exact EVSE Selection

A driver’s explicit choice of a particular EVSE.

It does not bypass authoritative availability evaluation.

## Near-Term Horizon

The configured period before a requested start during which fresh operational state is required for booking.

Initial proposed value: 60 minutes.

---

# 6. Booking

## Booking

A driver’s planned right to use one assigned EVSE during a defined charging interval under snapshotted policy and tariff terms.

Canonical technical term: **Booking**

Acceptable user-facing synonym: **Reservation**

Use “reservation” only as presentation wording or when discussing device reservation mirrors.

## Booking Aggregate

The authoritative consistency boundary containing:

- Driver ownership
- Assigned EVSE
- Requested connector
- Scheduled interval
- Allocation
- Lifecycle state
- Tariff snapshot
- Policy snapshot
- Check-in information
- Version and transition history

## Booking Owner

The driver account that created and controls the booking.

## Hold

A temporary allocation created while the driver reviews and confirms a booking.

A hold expires automatically and has no validity after `expiresAt`, even if cleanup is delayed.

## Hold Expiry

The deadline after which an unconfirmed hold becomes invalid and stops blocking capacity.

## Confirmed Booking

A booking durably committed in `CONFIRMED` state with an EVSE assignment and immutable snapshots.

Email delivery is not required for confirmation.

## Booking State

The current lifecycle state of a booking:

- `HELD`
- `CONFIRMED`
- `CHECKED_IN`
- `ACTIVE`
- `COMPLETED`
- `CANCELLED`
- `EXPIRED`
- `NO_SHOW`
- `FULFILMENT_FAILED`

Avoid generic `FAILED`.

## Terminal Booking State

A booking state from which normal lifecycle processing cannot resume:

- `COMPLETED`
- `CANCELLED`
- `EXPIRED`
- `NO_SHOW`
- `FULFILMENT_FAILED`

## Cancellation

An explicit termination of an eligible booking by a driver, operator or administrator.

Cancellation metadata records actor and reason rather than using separate cancellation states.

## Expiration

Automatic termination of an unconfirmed hold after its deadline.

Expiration is not cancellation.

## No-Show

The outcome where the driver failed to complete required arrival/start behaviour within the allowed window without equipment or platform failure preventing fulfilment.

Equipment failure must never produce `NO_SHOW`.

## Fulfilment Failure

An outcome where the platform could not provide the booked charging opportunity because of equipment, infrastructure or platform failure before charging began.

Canonical state: `FULFILMENT_FAILED`

## Rescheduling

Changing a confirmed booking’s interval or compatible EVSE through one atomic operation.

Failure to claim the replacement must preserve the original allocation.

## Reassignment

Moving a booking to another compatible EVSE, normally at the same station and interval, because of an operational need.

Reassignment differs from rescheduling because the intended time normally remains unchanged.

## Tariff Snapshot

An immutable copy of the applicable tariff components, currency, tax information and calculation version stored for a confirmed booking.

## Policy Snapshot

An immutable copy of the effective booking rules used for a booking.

It includes timing, check-in, grace and buffer rules.

## Public Booking Reference

A safe identifier shown to the driver and support staff.

It is not the booking’s internal database ID.

---

# 7. Check-in and authorization

## Check-In

The process by which the booking owner proves arrival at the assigned EVSE during the permitted window.

Check-in changes the booking to `CHECKED_IN`. It does not start charging.

## Check-In Window

The period during which normal check-in is permitted.

Initial defaults:

- Opens 15 minutes before scheduled start
- Closes at the grace deadline

## Grace Period

The policy-defined period after the scheduled start during which late arrival or session start may remain permitted.

Initial default: 15 minutes.

## Grace Deadline

The scheduled booking start plus the snapshotted grace period.

## Check-In Method

The mechanism used to identify the assigned EVSE:

- `QR`
- `MANUAL_IDENTIFIER`
- `SUPPORT_OVERRIDE`

## Start Authorization

A short-lived, single-use authorization created after successful check-in and consumed when starting a session.

It is bound to:

- Booking
- Driver
- EVSE
- Intended session
- Expiry

## Authorization Consumption

The atomic action that permanently marks a start authorization as used before or while issuing the corresponding device command.

A consumed authorization cannot be reused even when the command outcome becomes uncertain.

## Abandon Check-In

The driver’s withdrawal from `CHECKED_IN` before a start attempt begins.

It revokes the start authorization and may return the booking to `CONFIRMED` while the check-in window remains valid.

## EVSE QR Code

A QR code containing a public URL and public EVSE identifier.

It contains no credential, booking data or start authorization.

---

# 8. Charging session

## Charging Session

The platform record of actual or attempted simulated charging associated with one booking and EVSE.

A session is distinct from a booking:

- Booking represents planned access.
- Session represents attempted or actual usage.

## Session Aggregate

The authoritative consistency boundary for:

- Session state
- Booking and EVSE association
- Device command status
- Transaction events
- Meter sequence
- Final outcome
- Summary

## Session State

One of:

- `STARTING`
- `CHARGING`
- `SUSPENDED`
- `STOPPING`
- `COMPLETED`
- `INTERRUPTED`
- `START_REJECTED`

## `STARTING`

A start request has been created and the physical simulated outcome is pending or uncertain.

## `CHARGING`

Simulated energy transfer has been confirmed through a transaction event.

Command acceptance alone does not establish `CHARGING`.

## `SUSPENDED`

An existing session remains open but energy transfer is temporarily paused.

A suspension must include a reason.

## `STOPPING`

A stop has been requested, but definitive termination has not yet been confirmed.

## `COMPLETED`

The simulated session ended normally.

## `INTERRUPTED`

Energy transfer began but ended because of fault, disconnection, emergency or another abnormal reason.

## `START_REJECTED`

The simulator definitively rejected the start and charging never began.

## Session Outcome

The final classification explaining how a session ended.

State and outcome must not conceal interruption or uncertainty.

## Start Attempt

One request to begin a charging session.

The exact relationship between retryable start attempts and the session aggregate remains an open architecture/domain decision.

## Transaction

The simulator-side representation of actual simulated energy-transfer activity.

Canonical usage:

- Use **Charging Session** for the platform domain concept.
- Use **Device Transaction** only for the simulator protocol concept.

## Transaction Started

The device event proving simulated energy transfer began.

## Transaction Ended

The device event providing definitive simulated termination evidence.

## Meter Value

A measured or simulated electrical value reported for a session.

Initial values include cumulative energy and instantaneous power.

## Meter Sequence

A monotonically increasing session-scoped number used to identify duplicate, missing or out-of-order meter events.

## Cumulative Energy

The total energy reported for the session, initially represented in watt-hours.

Duplicate values must never inflate the accepted total.

## Estimated Cost

A platform-calculated monetary estimate based on accepted session data and the booking’s tariff snapshot.

It is not a payment or settled charge.

## Session Summary

The reproducible final record containing:

- Start and end times
- Duration
- Energy
- Estimated cost
- Tariff snapshot
- Stop reason
- Completion or interruption outcome

## Session Overrun

The condition where actual EVSE occupation continues beyond the booking interval.

The EVSE remains unavailable, and affected later bookings become at risk.

## Uncertain Outcome

A condition where the platform cannot prove whether a device command or physical action succeeded.

Uncertainty remains explicit until reconciliation.

---

# 9. Device and simulator

## Charger Simulator

Software emulating charging-station and EVSE behaviour for testing and demonstration.

Canonical term: **Charger Simulator**

It must not be described as real charger hardware.

## Simulator Protocol

The project’s custom, versioned, OCPP-inspired communication protocol.

Canonical description:

**OCPP 2.1-inspired simulator protocol**

It is not OCPP-compliant or wire-compatible.

## Machine Identity

A unique non-human identity assigned to one simulated charging-station controller.

It determines which station and EVSEs the simulator may report or control.

## Enrollment

The process of securely assigning credentials to a pending simulator identity.

## Heartbeat

A periodic simulator message demonstrating connection liveness.

Heartbeat receipt does not prove every EVSE is healthy or available.

## Command

A platform request asking the simulator to perform an action.

Examples:

- Start charging
- Stop charging
- Synchronize reservation
- Report status

A command is not a completed fact.

## Command Result

The simulator’s acceptance or rejection of a command.

Acceptance does not necessarily prove physical completion.

## Transport Receipt

Confirmation that a protocol message was authenticated, structurally valid and durably accepted for processing.

It does not mean that the requested action succeeded.

## Device Event

A fact reported by the simulator after or during simulated physical activity.

## Station Event Sequence

A monotonically increasing sequence for durable station-originated events.

## Reservation Mirror

The simulator’s operational copy of a platform booking allocation.

It is not authoritative and cannot create or cancel the platform booking.

## Offline Queue

Durable simulator storage containing events created while disconnected.

Queued events retain their original IDs, occurrence times and sequences.

## Inventory Reconciliation

Comparison between simulator-reported EVSE/connector inventory and authoritative platform infrastructure.

Simulator inventory cannot create infrastructure automatically.

## Failure Injection

Controlled simulation of faults, delays, duplicates, disconnections, timeouts or event reordering.

## Simulation Seed

A value making a failure scenario deterministic and reproducible.

---

# 10. Maintenance and faults

## Maintenance

Planned or emergency work that may restrict station or EVSE operation.

Lifecycle:

- `SCHEDULED`
- `ACTIVE`
- `COMPLETED`
- `CANCELLED`

## Blocking Maintenance

Maintenance that prevents allocation, check-in or charging during its affected interval.

## Emergency Maintenance

Maintenance activated through exceptional authority because delaying action creates material risk.

It requires justification and audit.

## Fault

A tracked operational problem affecting a station, EVSE, connector or transaction.

Lifecycle:

- `OPEN`
- `ACKNOWLEDGED`
- `IN_PROGRESS`
- `RESOLVED`

A resolved fault may be reopened to `OPEN`.

## Fault Report

A report submitted by a driver, operator, simulator or monitoring process.

A report is evidence used during triage; it is not automatically a fault incident.

## Fault Incident

The authoritative operational record representing one identified problem.

Several reports may be linked to one incident.

## Fault Severity

One of:

- `WARNING`
- `DEGRADED`
- `CRITICAL`
- `EMERGENCY`

## Blocking Fault

A fault whose severity and operational effect prevent new allocation, check-in or session start.

## Fulfilment Risk

A condition indicating that an existing booking may not be serviceable.

It triggers reassignment, cancellation or operational review.

## Operational Resolution

A workflow resolving maintenance, fault, device or platform conditions affecting a booking or session.

---

# 11. Identity, actors and authorization

## Driver

A registered or public user discovering stations and, when verified and active, managing bookings and sessions.

## Operator Staff Member

A human user acting within one operator organization.

Roles:

- Operator Owner
- Operator Manager
- Operator Technician
- Operator Support Agent

## Operator Owner

The staff member with full organization authority, including ownership transfer and closure requests.

## Operator Manager

A staff member authorized to manage infrastructure, policies, tariffs and operational staff.

## Operator Technician

A restricted staff member managing faults, maintenance and simulator operations without ordinary access to tariffs or driver identity.

## Operator Support Agent

A staff member assisting with bookings belonging to their organization.

Canonical role name prevents confusion with platform support.

## Platform Administrator

A privileged platform role managing governance, moderation and exceptional interventions.

It does not automatically grant cloud, database or identity-provider administration.

## Platform Support Agent

A case-scoped platform role assisting users and operators.

Canonical role name: **Platform Support**

## Auditor / Security Reviewer

A read-only role reviewing audit, security and privileged-access evidence.

## Device Actor

A non-human simulator identity communicating through the device protocol.

## Authentication

Verification of who or what is making a request.

## Authorization

The decision determining whether an authenticated actor may perform a specific action on a specific resource.

## Resource Ownership

The relationship restricting access to:

- A driver’s own records
- An operator’s own infrastructure
- An assigned support case
- A simulator’s assigned EVSEs

## MFA

Multi-factor authentication required for privileged human roles.

## Recent Reauthentication

A fresh authentication check required before a sensitive action.

## Break-Glass Access

Exceptional, scoped, time-limited privileged access used when ordinary permissions are insufficient and delay creates material risk.

It requires MFA, justification, alerting, expiry and review.

## Impersonation

Acting as another user.

Silent administrator impersonation is prohibited.

---

# 12. Distributed consistency

## Authoritative Owner

The only capability permitted to make a final business decision and update the authoritative record for a concept.

## Logical Capability

A cohesive business responsibility used during domain planning.

A logical capability does not automatically equal one microservice.

## Projection

A non-authoritative representation derived from authoritative facts.

Examples:

- Search read model
- Analytics dashboard
- Notification view
- Operational summary

## Strong Consistency

A guarantee that a correctness-critical decision observes and updates authoritative state atomically.

Used for allocation and lifecycle transitions.

## Eventual Consistency

A model where derived views become consistent after asynchronous propagation.

Used for search, analytics, notifications and dashboards.

## Integration Event

A versioned message describing a completed domain fact for other capabilities.

Example: `BookingConfirmed`

## Command

A request for another component to attempt an action.

Example: `StartChargingAtEVSE`

Commands use imperative naming; events use completed-fact naming.

## Transactional Outbox

A local table or record set written in the same transaction as a business change, then published asynchronously.

It prevents a committed change from losing its integration event.

## Inbox

A consumer-side record of processed events used to prevent duplicate business effects.

## At-Least-Once Delivery

A delivery model in which a message may arrive more than once but must not be lost within the supported reliability boundary.

Consumers must be idempotent.

## Exactly-Once Business Effect

The intended outcome that a duplicated message changes business state only once.

This is achieved through idempotency, not through a claim of exactly-once transport.

## Idempotency

The property that repeating the same logical request produces no additional business effect.

## Idempotency Key

A client- or workflow-supplied stable key identifying one logical operation.

## Aggregate Version

A monotonically increasing version used to detect concurrent updates and out-of-order events.

## Correlation ID

An identifier linking all operations related to one request or workflow.

## Causation ID

An identifier showing which prior message or operation caused another message.

## Retry

A controlled repeat of a transiently failed operation.

Retries require bounded attempts, backoff, jitter and idempotency.

## Dead-Letter Queue

A controlled quarantine for messages that cannot be processed successfully after retries or because of permanent invalidity.

## Replay

Authorized reprocessing of a previously stored event or message.

Replay must preserve the original event identity.

## Reconciliation

A process that compares authoritative records and external/device evidence to resolve uncertain or conflicting outcomes.

## Compensation

A new business action addressing the consequences of an earlier committed action.

Compensation is not a distributed database rollback.

## Workflow Coordinator

A component tracking a long-running multi-participant process where completion, timeout or compensation must be managed explicitly.

## Database Time

Time obtained from the authoritative datastore and used for business deadlines and race-safe lifecycle decisions.

---

# 13. Notifications

## Transactional Notification

A message required to support security, account, booking, operational or privacy activity.

It is not marketing.

## Essential Notification

A mandatory transactional message that users cannot disable.

## Optional Service Notification

A non-marketing reminder or routine summary that users may disable.

## Notification Preference

A user-controlled setting affecting future optional notification creation.

## Notification Delivery Record

The authoritative record of one logical notification and its provider delivery attempts.

## Provider Acceptance

Confirmation that the email provider accepted the message for transport.

It does not prove mailbox delivery.

## Delivery

Evidence, where available, that the provider delivered the message to the destination mail system.

## Bounce

A provider result indicating temporary or permanent inability to deliver email.

## Suppression

A rule preventing further sends to a destination or category.

## Obsolete Notification

An unsent message whose content is no longer valid because a newer domain state superseded it.

## Action Link

A purpose-bound, expiring URL used for verification, recovery, invitation or privacy download.

---

# 14. Privacy and records

## Personal Data

Information relating to an identified or identifiable person.

## Pseudonymization

Replacement of direct identifiers with a protected surrogate while re-identification remains possible through separately controlled information.

Pseudonymized information remains personal data.

## Anonymization

Transformation preventing reasonable re-identification or singling out.

Deleting direct identifiers alone does not necessarily anonymize data.

## Data Minimization

Collecting and retaining only information necessary for an approved purpose.

## Privacy Request

A tracked request for access, portability, correction, restriction or deletion.

## Access Export

A package representing the personal data and explanatory information applicable to an access request.

## Portability Export

A machine-readable subset prepared for applicable data-portability rights.

It is not identical to the full access export.

## Restriction of Processing

A control preventing ordinary processing of specified data while preserving it for permitted purposes.

## Account Deletion

A coordinated process disabling identity access and deleting, redacting, pseudonymizing or anonymizing data according to obligations and retention rules.

## Deletion Blocker

An active obligation or justified retention condition preventing immediate account deletion.

## Deletion Tombstone

Minimal privacy-safe evidence that a deletion or anonymization action occurred and must be reapplied after projection rebuild or backup restoration.

## Cooling-Off Period

A product-defined delay during which a confirmed deletion request may be cancelled and obligations can be resolved.

## Retention Rule

A versioned rule defining:

- Data category
- Trigger
- Retention period
- Required action
- Exceptions

## Legal/Security Hold

A scoped, justified and expiring pause on ordinary deletion.

## Data Inventory

The register mapping personal fields to owner, purpose, classification, processing basis, recipients and retention treatment.

---

# 15. Audit and support

## Audit Event

Immutable evidence of a security-sensitive or business-critical action.

It records actor, target, action, time, reason, outcome and correlation information.

## Business Audit

Audit evidence concerning lifecycle or operational changes.

## Security Audit

Audit evidence concerning authentication, authorization, privileged access or security events.

## Support Case

A scoped record used to assist a driver or operator and coordinate investigation or resolution.

Lifecycle:

- `OPEN`
- `IN_PROGRESS`
- `WAITING_FOR_USER`
- `WAITING_FOR_OPERATOR`
- `RESOLVED`
- `CLOSED`

## Case Assignment

The relationship granting a support agent temporary access to the data and actions required for a specific case.

## Masking

Hiding part of a sensitive value by default.

## Reveal Action

A justified and audited temporary display of previously masked information.

---

# 16. Pricing and time

## Tariff

A versioned pricing definition applicable to a station or EVSE.

Initial components:

- Energy price
- Time price
- Session fee
- Idle fee
- Tax rate

## Price Estimate

A non-binding cost calculation shown before or during simulated charging.

## Gross EUR Estimate

An estimate including the applicable snapshotted tax component and expressed in euros.

## Idle Fee

A tariff component based on time spent occupying an EVSE after charging has ended.

## Station Timezone

The timezone used for opening hours and user-facing booking times.

Initial default: `Europe/Athens`

## UTC Timestamp

The canonical persisted timestamp representation.

## Local Time

A user-facing or station-schedule representation interpreted using the station timezone.

## DST Ambiguity

A local time occurring more than once during a daylight-saving transition.

It must be explicitly disambiguated.

## Non-Existent Local Time

A local time skipped during a daylight-saving transition.

It must be rejected or moved only through an explicit user decision.

---

# 17. Canonical wording corrections

| Avoid | Use instead |
|---|---|
| Charger, when referring to reservable equipment | EVSE or charging point |
| Charger, when referring to software | Charger Simulator |
| Slot | Booking interval or allocation interval |
| Reservation, in technical models | Booking |
| Connector booking | EVSE booking with required connector type |
| Live availability without interval | Current operational summary |
| Real-time availability | Availability plus status freshness |
| Current status | Administrative state, device-reported state or derived availability |
| Failed booking | Fulfilment-failed, expired, cancelled or no-show booking |
| Successful interrupted session | Completed booking with interrupted session outcome |
| Payment | Estimated cost |
| OCPP implementation | OCPP-inspired simulator protocol |
| Device reservation | Reservation mirror |
| Operator, for an employee | Operator Staff Member |
| Support, without scope | Operator Support or Platform Support |
| Admin override | Status override or emergency intervention |
| Delete station | Deactivate station |
| Exactly-once delivery | At-least-once delivery with idempotent business effects |
| Event command | Command or event, according to semantics |
| User ID in public UI | Public reference |
| Available charger count | Bookable compatible EVSE count |

---

# 18. Naming conventions for future contracts

## Resources and code

Use singular canonical nouns:

- `station`
- `evse`
- `connector`
- `booking`
- `allocation`
- `chargingSession`
- `maintenance`
- `faultIncident`
- `supportCase`
- `notification`
- `privacyRequest`

## Commands

Use imperative names:

- `CreateBookingHold`
- `ConfirmBooking`
- `CheckInDriver`
- `StartChargingAtEVSE`
- `StopCharging`
- `ScheduleMaintenance`

## Events

Use completed-fact names:

- `BookingHeld`
- `BookingConfirmed`
- `DriverCheckedIn`
- `ChargingSessionStarted`
- `MaintenanceActivated`
- `FaultRaised`

## Reason codes

Use stable uppercase `SNAKE_CASE` values.

Human-readable wording must be localized separately and must not be used as program logic.

---

# 19. Precedence and governance

1. This glossary becomes authoritative after approval.
2. Existing documents must be updated where terminology conflicts.
3. New terms require glossary review before contract approval.
4. A term cannot silently change meaning.
5. Deprecated terms may remain in historical documents but must be marked.
6. API and event reviews must verify glossary compliance.
7. AI agents must use canonical terminology in generated plans and code.
8. Greek translations must be recorded in a future controlled translation glossary.
9. Acronyms must be expanded at first use in user-facing documentation.
10. Ambiguous terms must be rejected during readiness review.

---

# 20. Proposed glossary decisions

1. Use **Booking** as the canonical technical term and “reservation” only as a user-facing synonym.
2. Use **EVSE** technically and “charging point” in simplified UI wording.
3. Reserve “Charger Simulator” for the software/device actor.
4. Keep Booking, Allocation and Charging Session as distinct concepts.
5. Keep Administrative State, Device-Reported State and Derived Availability separate.
6. Treat a Fault Report as evidence and a Fault Incident as the authoritative operational problem.
7. Use **Device Transaction** only for simulator-level activity.
8. Treat provider acceptance and mailbox delivery as distinct notification facts.
9. Use **Operator Support** and **Platform Support** as separate role names.
10. Prohibit unqualified use of “slot,” “status,” “failed,” “active,” “available” and “charger” in technical specifications.
11. Require canonical terminology in APIs, events, tests, code and AI-agent outputs.
12. Create a controlled Greek/English UI translation glossary during frontend planning.

## Status

**Domain Glossary v1.0 is ready for approval.**

The next foundation artifact is the **Lifecycle and Invariant Catalogue v1.0**, which will consolidate all state machines, transitions, guards, terminal states and cross-lifecycle rules.
