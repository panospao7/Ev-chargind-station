Document ID: ARC-020
Title: Domain Events and Asynchronous Contracts v1.0
Version: 1.0
Status: IN_REVIEW
Owner: Architecture Lead
Last reviewed: 2026-07-12
Depends on: ARC-001–017
Authoritative for: Domain Events And Async Contracts
Refines: ARC-004
Does not supersede: Service topology and data ownership in ARC-001
Release applicability: W1 | W2 | W3 | Cross-cutting

---



# Domain Events and Asynchronous Contracts v1.0

**Status:** Draft (In Review)

## 1. Message classifications

### Domain event

A past-tense, immutable fact produced after a committed domain change.

Examples:

- `BookingConfirmed`
- `ChargingSessionStarted`
- `MaintenanceActivated`

It may have multiple consumers and cannot be rejected by consumers.

### Command

An imperative request intended for exactly one logical consumer.

Examples:

- `StartChargingAtEVSE`
- `CollectPrivacyData`

Commands may be accepted, rejected, expire, or produce an uncertain result.

### Telemetry

High-volume device observations such as meter samples. Telemetry uses a separate exchange and retention policy from normal domain events.

## 2. Event envelope

Adopt CloudEvents JSON structured format with:

- `specversion`
- `id`
- `source`
- `type`
- `subject`
- `time`
- `datacontenttype`
- `dataschema`
- `data`

Extensions:

- `correlationid`
- `causationid`
- `traceparent`
- `aggregateid`
- `aggregateversion`

CloudEvents defines common event context and identifies duplicates using the event source and ID. Sensitive information should not be placed in context attributes because infrastructure may inspect or log them. ([github.com](https://github.com/cloudevents/spec/blob/main/cloudevents/spec.md))

Example type convention:

`com.evplatform.booking.confirmed.v1`

Breaking payload changes create `v2`. Additive optional fields remain within `v1`.

## 3. RabbitMQ topology

Recommended durable exchanges:

| Exchange | Purpose |
|---|---|
| `ev.domain.v1` | Business domain events |
| `ev.device.command.v1` | Commands targeting chargers |
| `ev.device.result.v1` | Device command and transaction results |
| `ev.device.telemetry.v1` | Meter and high-volume status data |
| `ev.audit.v1` | Restricted privileged/security audit |
| `ev.identity.private.v1` | Restricted contact and identity changes |
| `ev.privacy.command.v1` | Privacy export/anonymization jobs |

Each consumer owns its queue. Critical queues should use quorum queues.

## 4. Delivery guarantees

- Delivery is at least once.
- Producers use transactional outbox records and publisher confirms.
- Consumers use manual acknowledgements.
- A consumer acknowledges only after its database transaction and inbox record commit.
- Duplicate event IDs return success without repeating the effect.
- Messages are published as persistent to durable exchanges/queues.
- Mandatory routing detects events with no valid queue binding.
- Transient failures use bounded retries with backoff and jitter.
- Invalid or exhausted messages enter a quarantine queue and alert operators.

RabbitMQ publisher confirms and consumer acknowledgements solve different delivery stages; redelivery and duplication remain possible, so consumers must be idempotent. ([rabbitmq.com](https://www.rabbitmq.com/docs/next/confirms))

Dead-letter forwarding can itself fail under some configurations. Critical retry/dead-letter queues should therefore use quorum queues or controlled application-level republishing. ([rabbitmq.com](https://www.rabbitmq.com/docs/next/dlx))

## 5. Ordering clarification

The system will **not assume global or aggregate ordering from RabbitMQ**.

Consumers use:

- Event ID for deduplication
- Aggregate version for ordering detection
- Last-applied version
- Gap detection and reconciliation
- Idempotent projection rebuilds

A consumer receiving version 12 before version 11 may temporarily hold it, retry later, or request an authoritative snapshot. Critical business correctness never depends solely on event arrival order.

## 6. Core event catalogue

### Account

| Event | Minimum data | Main consumers |
|---|---|---|
| `AccountProfileCreated` | User ID, status, locale | Discovery and Insights, Platform Governance | (Release applicability: W1) |
| `AccountProfileUpdated` | User ID, changed fields, version | Discovery and Insights, Platform Governance | (Release applicability: W1) |
| `VehicleUpserted` | User ID, vehicle compatibility snapshot | Discovery and Insights | (Release applicability: W1) |
| `VehicleRemoved` | User ID, vehicle ID | Discovery and Insights | (Release applicability: W1) |
| `ConsentRecorded` | User ID, policy type/version/time | Platform Governance | (Release applicability: W1) |
| `AccountStatusChanged` | User ID, old/new status, reason code | Booking module, Platform Governance | (Release applicability: W1) |
| `AccountAnonymized` | Subject ID, completion time | Platform Governance | (Release applicability: W3) |

Restricted identity stream:

- `NotificationContactChanged` (Release applicability: W1)
- `EmailVerificationChanged` (Release applicability: W1)
- `IdentitySessionsRevoked` (Release applicability: W1)

Email addresses do not appear on the general domain exchange.

### Station Operations Service

- `OperatorApplicationSubmitted` (Release applicability: W1)
- `OperatorApplicationUnderReview` (Release applicability: W1)
- `OperatorApplicationClarificationRequested` (Release applicability: W1)
- `OperatorApplicationWithdrawn` (Release applicability: W1)
- `OperatorApplicationApproved` (Release applicability: W1)
- `OperatorApplicationRejected` (Release applicability: W1)
- `OperatorOrganizationCreated` (Release applicability: W1)
- `OperatorOrganizationStatusChanged` (Release applicability: W1)
- `StaffMembershipGranted` (Release applicability: W1)
- `StaffMembershipChanged` (Release applicability: W1)
- `StaffMembershipRevoked` (Release applicability: W1)
- `StationPublished` (Release applicability: W1)
- `StationUpdated` (Release applicability: W1)
- `StationStatusChanged` (Release applicability: W1)
- `EVSEConfigurationChanged` (Release applicability: W1)
- `EVSEAdministrativeStateChanged` (Release applicability: W1)
- `ConnectorConfigurationChanged` (Release applicability: W1)
- `TariffPublished` (Release applicability: W1)
- `TariffRetired` (Release applicability: W1)
- `BookingPolicyChanged` (Release applicability: W1)
- `MaintenanceScheduled` (Release applicability: W1)
- `MaintenanceActivated` (Release applicability: W1)
- `MaintenanceCompleted` (Release applicability: W1)
- `MaintenanceCancelled` (Release applicability: W1)
- `MaintenanceFailed` (Release applicability: W1)
- `FaultOpened` (Release applicability: W1)
- `FaultStateChanged` (Release applicability: W1)
- `FaultResolved` (Release applicability: W1)
- `OperationalOverrideApplied` (Release applicability: W1)
- `OperationalOverrideExpired` (Release applicability: W1)

Configuration events contain the public projection data required by Discovery and Insights. They do not contain device secrets or internal diagnostics.

### Booking module (Booking and Session Service)
*Note on Driver Privacy (CON-080): BookingHeld and other booking events published to the message broker use pseudonymous driver/subject IDs only. Discovery and Insights Service subscribes to capacity updates/pseudonymous records and does not receive driver personal identity.*

| Event | Important data |
|---|---|
| `BookingHeld` | Booking, pseudonymous driver ID, EVSE, interval, hold expiry | (Release applicability: W1) |
| `BookingConfirmed` | Booking reference, assignment and snapshots | (Release applicability: W1) |
| `BookingHoldExpired` | Booking and release time | (Release applicability: W1) |
| `BookingRescheduled` | Old/new interval and assignment version | (Release applicability: W1) |
| `BookingCancelled` | Actor type and structured reason | (Release applicability: W1) |
| `BookingCheckedIn` | Booking, EVSE and check-in time | (Release applicability: W1) |
| `BookingNoShow` | Deadline and responsibility classification | (Release applicability: W1) |
| `BookingReassigned` | Old/new EVSE and approval mode | (Release applicability: W1) |
| `BookingFulfilmentFailed` | Failure category and responsible party | (Release applicability: W1) |
| `BookingActivated` | Booking and charging-session ID | (Release applicability: W1) |
| `BookingCompleted` | Session outcome and release time | (Release applicability: W1) |
| `CapacityRestrictionCreated` | Scope, interval, type and phase | (Release applicability: W1) |
| `CapacityRestrictionFinalized` | Restriction ID and block interval | (Release applicability: W1) |
| `CapacityRestrictionReleased` | Restriction ID and reason | (Release applicability: W1) |
| `StartAuthorizationConsumed` | Booking/session IDs only; never token | (Release applicability: W1) |

### Charging module (Booking and Session Service)

- `ChargingSessionCreated` (Release applicability: W1)
- `ChargingStartRequested` (Release applicability: W1)
- `ChargingSessionStarted` (Release applicability: W1)
- `ChargingStartRejected` (Release applicability: W1)
- `ChargingSessionSuspended` (Release applicability: W1)
- `ChargingSessionResumed` (Release applicability: W1)
- `ChargingStopRequested` (Release applicability: W1)
- `ChargingSessionProgressed` (Release applicability: W1)
- `ChargingSessionCompleted` (Release applicability: W1)
- `ChargingSessionInterrupted` (Release applicability: W1)
- `ChargingReconciliationRequired` (Release applicability: W1)
- `ChargingReconciliationResolved` (Release applicability: W1)

`ChargingSessionProgressed` is throttled for UI/analytics. Raw meter readings remain in Charging storage and the telemetry stream.

### Device Integration Service

- `DeviceProvisioned` (Release applicability: W1)
- `DeviceOnline` (Release applicability: W1)
- `DeviceOffline` (Release applicability: W1)
- `DeviceHeartbeatStale` (Release applicability: W1)
- `EVSEStatusChanged` (Release applicability: W1)
- `DeviceCommandResult` (Release applicability: W1)
- `DeviceTransactionStarted` (Release applicability: W1)
- `DeviceTransactionUpdated` (Release applicability: W1)
- `DeviceTransactionEnded` (Release applicability: W1)
- `DeviceSnapshotReceived` (Release applicability: W1)
- `DeviceFaultRaised` (Release applicability: W1)
- `DeviceFaultCleared` (Release applicability: W1)
- `DeviceTelemetryAnomalyDetected` (Release applicability: W1)

Raw protocol-specific messages are normalized before being published.

### Notification and Platform Governance and Support Services

- `NotificationQueued` (Release applicability: W1)
- `NotificationDelivered` (Release applicability: W1)
- `NotificationPermanentlyFailed` (Release applicability: W1)
- `SupportCaseCreated` (Release applicability: W2)
- `SupportCaseStateChanged` (Release applicability: W2)
- `PrivacyRequestCreated` (Release applicability: W3)
- `PrivacyServiceStepCompleted` (Release applicability: W3)
- `PrivacyRequestCompleted` (Release applicability: W3)
- `IncidentStateChanged` (Release applicability: W2)
- `PrivilegedActionRecorded` (Release applicability: W1)

Audit events use a restricted exchange and contain no passwords, tokens, or unnecessary user data.

## 7. Asynchronous commands

### Device commands

- `ReserveEVSE` (Release applicability: W2)
- `CancelEVSEReservation` (Release applicability: W2)
- `StartChargingAtEVSE` (Release applicability: W1)
- `StopChargingAtEVSE` (Release applicability: W1)
- `RequestDeviceSnapshot` (Release applicability: W1)
- `ChangeDeviceAvailability` (Release applicability: W1)
- `ResetDevice` (Release applicability: W1)
- `ApplySimulatorScenario` (Release applicability: W1)

Required command metadata:

- Command ID
- Command type
- Target device/EVSE
- Issued and expiry times
- Correlation/causation IDs
- Issuing service
- Domain reference and version

Commands are idempotent by command ID. Expired commands must not execute.

### Privacy commands

- `CollectSubjectData` (Release applicability: W3)
- `AnonymizeSubjectData` (Release applicability: W3)
- `RestrictSubjectData` (Release applicability: W3)
- `DeleteServiceSpecificData` (Release applicability: W3)

Each service emits a success or failure event for the request and service step.

### Emergency charging command (Release applicability: W1)

`EmergencyStopChargingSession` is sent only after Station Operations Service or Platform Governance and Support Service authorizes and audits the action.

## 8. Subscription matrix
*Same-service Booking and Session Service modules (Booking and Charging) coordinate via local domain events or direct interfaces rather than RabbitMQ broker integrations. Broker subscriptions are for inter-service integration events.*

| Consumer | Main subscriptions |
|---|---|
| Discovery and Insights Service | Public account, station-operations, booking-session, and device-integration events (Release applicability: W1) |
| Notification Service | booking-session and account-security events (Release applicability: W1) |
| Platform Governance and Support Service | Audit, privacy, incident and delivery-failure events (Release applicability: W2) |
| Booking module (Booking & Session) | Device Integration Service telemetry/status events, and Station Operations Service configuration events (Release applicability: W1) |
| Charging module (Booking & Session) | Device Integration Service transaction/meter events (Release applicability: W1) |
| Station Operations Service | Device Integration Service telemetry/fault events, and Booking capacity restriction outcomes (Release applicability: W1) |
| Device Integration Service | Device command queues (Release applicability: W1) |
| Account Service | Identity provider lifecycle events (Release applicability: W1) |

## 9. Payload rules

- Events contain stable identifiers and the snapshot needed by consumers.
- No access tokens, passwords, QR secrets, start tokens, or device credentials.
- No large files or binary payloads; use a protected object reference.
- Money includes amount, currency, tax treatment, and calculation version.
- Times use UTC RFC 3339 format.
- Deleted personal identities are represented by pseudonymous subject IDs where retention is required.
