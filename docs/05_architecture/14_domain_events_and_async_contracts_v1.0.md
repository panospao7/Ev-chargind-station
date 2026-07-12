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

CloudEvents defines common event context and identifies duplicates using the event source and ID. Sensitive information should not be placed in context attributes because infrastructure may inspect or log them. ([github.com](https://github.com/cloudevents/spec/blob/main/cloudevents/spec.md?utm_source=openai))

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

RabbitMQ publisher confirms and consumer acknowledgements solve different delivery stages; redelivery and duplication remain possible, so consumers must be idempotent. ([rabbitmq.com](https://www.rabbitmq.com/docs/next/confirms?utm_source=openai))

Dead-letter forwarding can itself fail under some configurations. Critical retry/dead-letter queues should therefore use quorum queues or controlled application-level republishing. ([rabbitmq.com](https://www.rabbitmq.com/docs/next/dlx?utm_source=openai))

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
| `AccountProfileCreated` | User ID, status, locale | Query, Governance |
| `AccountProfileUpdated` | User ID, changed fields, version | Query, Governance |
| `VehicleUpserted` | User ID, vehicle compatibility snapshot | Query if required |
| `VehicleRemoved` | User ID, vehicle ID | Query |
| `ConsentRecorded` | User ID, policy type/version/time | Governance |
| `AccountStatusChanged` | User ID, old/new status, reason code | Booking, Governance |
| `AccountAnonymized` | Subject ID, completion time | Governance |

Restricted identity stream:

- `NotificationContactChanged`
- `EmailVerificationChanged`
- `IdentitySessionsRevoked`

Email addresses do not appear on the general domain exchange.

### Network

- `OperatorApplicationSubmitted`
- `OperatorOrganizationStatusChanged`
- `StaffMembershipGranted`
- `StaffMembershipChanged`
- `StaffMembershipRevoked`
- `StationPublished`
- `StationUpdated`
- `StationStatusChanged`
- `EVSEConfigurationChanged`
- `EVSEAdministrativeStateChanged`
- `ConnectorConfigurationChanged`
- `TariffPublished`
- `TariffRetired`
- `BookingPolicyChanged`
- `MaintenanceScheduled`
- `MaintenanceActivated`
- `MaintenanceCompleted`
- `MaintenanceCancelled`
- `FaultOpened`
- `FaultStateChanged`
- `FaultResolved`
- `OperationalOverrideApplied`
- `OperationalOverrideExpired`

Configuration events contain the public projection data required by Query. They do not contain device secrets or internal diagnostics.

### Booking

| Event | Important data |
|---|---|
| `BookingHeld` | Booking, driver, EVSE, interval, hold expiry |
| `BookingConfirmed` | Booking reference, assignment and snapshots |
| `BookingHoldExpired` | Booking and release time |
| `BookingRescheduled` | Old/new interval and assignment version |
| `BookingCancelled` | Actor type and structured reason |
| `BookingCheckedIn` | Booking, EVSE and check-in time |
| `BookingNoShow` | Deadline and responsibility classification |
| `BookingReassigned` | Old/new EVSE and approval mode |
| `BookingFulfilmentFailed` | Failure category and responsible party |
| `BookingActivated` | Booking and charging-session ID |
| `BookingCompleted` | Session outcome and release time |
| `CapacityRestrictionCreated` | Scope, interval, type and phase |
| `CapacityRestrictionFinalized` | Restriction ID and block interval |
| `CapacityRestrictionReleased` | Restriction ID and reason |
| `StartAuthorizationConsumed` | Booking/session IDs only; never token |

### Charging

- `ChargingSessionCreated`
- `ChargingStartRequested`
- `ChargingSessionStarted`
- `ChargingStartRejected`
- `ChargingSessionSuspended`
- `ChargingSessionResumed`
- `ChargingStopRequested`
- `ChargingSessionProgressed`
- `ChargingSessionCompleted`
- `ChargingSessionInterrupted`
- `ChargingReconciliationRequired`
- `ChargingReconciliationResolved`

`ChargingSessionProgressed` is throttled for UI/analytics. Raw meter readings remain in Charging storage and the telemetry stream.

### Device Gateway

- `DeviceProvisioned`
- `DeviceOnline`
- `DeviceOffline`
- `DeviceHeartbeatStale`
- `EVSEStatusChanged`
- `DeviceCommandResult`
- `DeviceTransactionStarted`
- `DeviceTransactionUpdated`
- `DeviceTransactionEnded`
- `DeviceSnapshotReceived`
- `DeviceFaultRaised`
- `DeviceFaultCleared`
- `DeviceTelemetryAnomalyDetected`

Raw protocol-specific messages are normalized before being published.

### Notification and Governance

- `NotificationQueued`
- `NotificationDelivered`
- `NotificationPermanentlyFailed`
- `SupportCaseCreated`
- `SupportCaseStateChanged`
- `PrivacyRequestCreated`
- `PrivacyServiceStepCompleted`
- `PrivacyRequestCompleted`
- `IncidentStateChanged`
- `PrivilegedActionRecorded`

Audit events use a restricted exchange and contain no passwords, tokens, or unnecessary user data.

## 7. Asynchronous commands

### Device commands

- `ReserveEVSE`
- `CancelEVSEReservation`
- `StartChargingAtEVSE`
- `StopChargingAtEVSE`
- `RequestDeviceSnapshot`
- `ChangeDeviceAvailability`
- `ResetDevice`
- `ApplySimulatorScenario`

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

- `CollectSubjectData`
- `AnonymizeSubjectData`
- `RestrictSubjectData`
- `DeleteServiceSpecificData`

Each service emits a success or failure event for the request and service step.

### Emergency charging command

`EmergencyStopChargingSession` is sent only after Network or Governance authorizes and audits the action.

## 8. Subscription matrix

| Consumer | Main subscriptions |
|---|---|
| Discovery and Insights Service | Public account, network, booking, charging and device events |
| Notification | Booking/session/account-security events |
| Governance | Audit, privacy, incident and delivery-failure events |
| Booking | Charging outcomes, critical network/device changes |
| Charging | Device command/transaction events and relevant booking cancellations |
| Network | Device status/fault events and maintenance-impact outcomes |
| Device Gateway | Device command queues |
| Account | Identity lifecycle events |

## 9. Payload rules

- Events contain stable identifiers and the snapshot needed by consumers.
- No access tokens, passwords, QR secrets, start tokens, or device credentials.
- No large files or binary payloads; use a protected object reference.
- Money includes amount, currency, tax treatment, and calculation version.
- Times use UTC RFC 3339 format.
- Deleted personal identities are represented by pseudonymous subject IDs where retention is required.
