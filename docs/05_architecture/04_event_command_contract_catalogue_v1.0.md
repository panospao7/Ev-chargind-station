Document ID: ARC-004  
Title: Event and Command Contract Catalogue  
Version: 1.0  
Status: IN_REVIEW  
Owner: Backend / Event-Driven Architecture Lead  
Last reviewed: 2026-07-11  
Depends on: ARC-001, ARC-002, ARC-003, PLT-001, SIM-001, DOM-002  
Authoritative for: Integration-event and asynchronous-command semantics, envelopes, routing, schemas, delivery, retries, ordering, replay and evolution  

# Event and Command Contract Catalogue v1.0

## 1. Purpose

This document defines:

- Integration-event and asynchronous-command semantics
- Message envelopes and naming
- Producers, handlers and consumers
- Logical broker topology
- Delivery guarantees
- Publisher and consumer transaction rules
- Idempotency, ordering and sequencing
- Retry, quarantine and replay behaviour
- Schema validation and compatibility
- Workflow commands and outcomes
- Device-normalized messages
- Security, privacy and observability requirements

It does not define raw simulator WebSocket frames, database tables, final RabbitMQ infrastructure or implementation classes.

---

## 2. Standards baseline

Integration events use CloudEvents 1.0 semantics and the stable CloudEvents 1.0.2 JSON/AMQP specifications. CloudEvents defines a vendor-neutral event envelope and requires core attributes such as `id`, `source`, `specversion` and `type`. ([github.com](https://github.com/cloudevents/spec))

Asynchronous interfaces will be documented using AsyncAPI 3.1.0, subject to final Java and TypeScript tooling validation. ([asyncapi.com](https://www.asyncapi.com/docs/reference))

Payload schemas use JSON Schema Draft 2020-12. ([json-schema.org](https://json-schema.org/draft/2020-12?utm_source=openai))

RabbitMQ remains provisional. If selected, publishers use publisher confirms and consumers use manual acknowledgements after durable processing. RabbitMQ distinguishes publisher confirms from consumer acknowledgements; together they support reliable at-least-once processing. ([rabbitmq.com](https://www.rabbitmq.com/docs/next/confirms))

---

## 3. Message categories

### 3.1 Integration Event

An immutable statement that a completed fact occurred.

Examples:

- `BookingConfirmed`
- `MaintenanceScheduled`
- `ChargingSessionStarted`

Properties:

- Published after authoritative commit
- May have multiple consumers
- Cannot be rejected or cancelled by consumers
- Must not be phrased as a request
- May be delivered more than once
- Does not identify a single destination

### 3.2 Asynchronous Command

A directed request for exactly one logical handler to attempt an action.

Examples:

- `StartCharging`
- `InstallCapacityBlock`
- `CollectPrivacyExportContribution`

Properties:

- Uses imperative naming
- Has an expiry where appropriate
- Is idempotent by command ID
- May be accepted, rejected, completed, failed or remain uncertain
- Must not claim the requested action already occurred

### 3.3 Command Outcome Event

A completed fact reporting a command’s business or operational outcome.

Examples:

- `CapacityBlockInstalled`
- `PrivacyExportContributionPrepared`
- `DeviceCommandTimedOut`

The outcome references the original command through `causationid` and its payload.

### 3.4 Projection Event

An integration event consumed to update a non-authoritative read model.

It remains an ordinary integration event; “projection event” describes its use, not a separate transport.

### 3.5 Audit Fact

A minimized event projecting locally authoritative audit evidence into the centralized audit view.

The projection does not replace the source service’s durable audit record.

### 3.6 Device-Normalized Event

A platform event produced by Device Integration after authenticating and validating a simulator message.

Raw simulator messages are not forwarded directly to other business services.

---

## 4. Event envelope

Events use structured CloudEvents JSON.

Required attributes:

| Attribute | Meaning |
|---|---|
| `specversion` | CloudEvents version, fixed to `1.0` |
| `id` | Globally unique event ID |
| `source` | Stable producer identity |
| `type` | Versioned event type |
| `subject` | Aggregate or resource subject |
| `time` | UTC time at which the fact occurred |
| `datacontenttype` | `application/json` |
| `dataschema` | Stable schema identifier |
| `data` | Event-specific payload |

Required platform extension attributes:

| Attribute | Meaning |
|---|---|
| `correlationid` | End-to-end request/workflow correlation |
| `causationid` | Message or operation causing this event |
| `aggregateid` | Aggregate public/internal integration reference |
| `aggregateversion` | Version after the reported transition |
| `classification` | Payload data classification |
| `traceparent` | Distributed trace context when available |

Conditional extension attributes:

- `tenantid`
- `workflowid`
- `partitionkey`
- `actorref`

CloudEvents requires uniqueness from the combination of `source` and `id`; platform event IDs must also be globally unique independently. ([github.com](https://github.com/cloudevents/spec/blob/main/cloudevents/spec.md?utm_source=openai))

### 4.1 Example event

```json
{
  "specversion": "1.0",
  "id": "01J2Y7W4K8W5N6A3ZX2F9T1M0P",
  "source": "urn:ev-platform:booking-session",
  "type": "gr.evbooking.booking.confirmed.v1",
  "subject": "booking/BKG-7K4M2P",
  "time": "2026-07-11T20:14:32.418Z",
  "datacontenttype": "application/json",
  "dataschema": "urn:ev-platform:schema:booking-confirmed:v1",
  "correlationid": "01J2Y7VZKJS4C8V8QHKB6NQK3P",
  "causationid": "01J2Y7VY7TP75HY0R15PTZMW1D",
  "aggregateid": "BKG-7K4M2P",
  "aggregateversion": 4,
  "classification": "PERSONAL_OPERATIONAL",
  "data": {
    "bookingRef": "BKG-7K4M2P",
    "accountRef": "ACC-48K1",
    "stationRef": "STN-ATH-21",
    "evseRef": "EVSE-04",
    "scheduledStart": "2026-07-14T08:00:00Z",
    "scheduledEnd": "2026-07-14T09:00:00Z"
  }
}
```

---

## 5. Command envelope

Commands use a platform envelope compatible with the event metadata model.

Required fields:

| Field | Meaning |
|---|---|
| `specversion` | Platform command envelope version, initially `1.0` |
| `id` | Globally unique command ID and idempotency identity |
| `kind` | `COMMAND` |
| `source` | Sending service |
| `type` | Versioned command type |
| `subject` | Target aggregate/resource |
| `time` | Command creation time |
| `expiresAt` | Deadline after which new execution is prohibited |
| `datacontenttype` | `application/json` |
| `dataschema` | Command payload schema |
| `correlationid` | Workflow/request correlation |
| `causationid` | Triggering event/operation |
| `workflowid` | Required for coordinated workflows |
| `classification` | Payload classification |
| `traceparent` | Trace context where available |
| `data` | Command-specific payload |

### 5.1 Command rules

1. `id` is the command’s stable idempotency key.
2. Retrying a command reuses the same ID and payload.
3. Reusing the ID with different data is invalid.
4. The recipient stores the prior result.
5. An expired command cannot begin new execution.
6. Expiry does not reverse work already performed.
7. Commands contain expected versions where stale execution would be unsafe.
8. Commands cannot contain reusable credentials or start-authorization secrets.
9. Only one logical consumer handles a command type.
10. Broker receipt does not mean command completion.

---

## 6. Naming conventions

### 6.1 Event types

Pattern:

`gr.evbooking.<domain>.<fact>.v<major>`

Examples:

- `gr.evbooking.booking.confirmed.v1`
- `gr.evbooking.session.started.v1`
- `gr.evbooking.maintenance.scheduled.v1`

### 6.2 Command types

Pattern:

`gr.evbooking.<target-domain>.command.<action>.v<major>`

Examples:

- `gr.evbooking.device.command.start-charging.v1`
- `gr.evbooking.booking.command.install-capacity-block.v1`

### 6.3 Routing keys

Event routing key:

`event.<domain>.<fact>.v<major>`

Command routing key:

`command.<target>.<action>.v<major>`

Examples:

- `event.booking.confirmed.v1`
- `command.device.start-charging.v1`

### 6.4 Schema identifiers

Pattern:

`urn:ev-platform:schema:<message-name>:v<major>`

Event and command type names cannot be reused with different semantics.

---

## 7. Logical broker topology

If RabbitMQ is selected, the initial logical topology is:

| Logical exchange | Type | Purpose |
|---|---|---|
| `ev.platform.events.v1` | Topic | Completed integration facts |
| `ev.platform.commands.v1` | Topic/direct | Directed asynchronous commands |
| `ev.platform.retry.v1` | Topic | Delayed retry routing |
| `ev.platform.quarantine.v1` | Topic | Exhausted or permanently invalid messages |
| `ev.platform.unroutable.v1` | Fanout/topic | Mandatory unroutable publications |

Rules:

- Exchanges and critical queues are durable.
- Important messages are persistent.
- Every logical consumer has its own queue.
- Distinct consumers never share one queue merely to reduce queue count.
- Competing instances of the same consumer service may share its queue.
- Production publishers use mandatory routing and publisher confirms.
- Topology is deployed through infrastructure configuration rather than uncontrolled runtime declarations.
- Dead-letter and retry settings are managed through broker policies where possible.

RabbitMQ recommends policies over hardcoded queue arguments for dead-letter configuration because policies can be changed without redeploying applications. ([rabbitmq.com](https://www.rabbitmq.com/docs/dlx))

Queue technology—quorum or classic—will be selected during deployment design. Critical dead-letter paths must not depend on unsafe best-effort republishing; RabbitMQ documents at-least-once dead-lettering support for quorum queues. ([rabbitmq.com](https://www.rabbitmq.com/docs/dlx))

---

## 8. Producer transaction

Every authoritative producer follows:

1. Begin local database transaction.
2. Apply business state change.
3. Record local audit evidence.
4. Insert event/command into the local outbox.
5. Commit.
6. Outbox publisher claims the record.
7. Publish with mandatory routing and publisher confirms.
8. Mark published after broker confirmation.

If confirmation is lost after broker acceptance:

- The outbox record may be published again.
- The same message ID is reused.
- Consumers deduplicate it.

No business transaction waits for broker publication.

An unroutable mandatory message is treated as a configuration incident, not successful publication.

---

## 9. Consumer transaction

Every state-changing consumer follows:

1. Receive message without automatic acknowledgement.
2. Authenticate broker context and validate envelope/schema.
3. Begin local transaction.
4. Insert or locate inbox record by consumer and message ID.
5. If already completed, commit/no-op and acknowledge.
6. Validate expected aggregate/workflow version.
7. Apply business effect.
8. Add resulting outbox messages.
9. Mark inbox record completed.
10. Commit.
11. Acknowledge broker delivery.

A consumer acknowledges only after it has durably completed the required processing. RabbitMQ’s acknowledgement semantics transfer responsibility for the delivery back from the broker to the consumer. ([rabbitmq.com](https://www.rabbitmq.com/docs/reliability))

If the consumer crashes after commit but before acknowledgement, redelivery produces no duplicate business effect.

---

## 10. Delivery guarantees

The platform guarantees:

- Durable local business commit
- Durable outbox intent
- At-least-once publication and delivery
- Idempotent business effects
- Detectable ordering gaps where ordering matters
- Controlled quarantine and replay

The platform does not claim:

- Exactly-once transport
- Global message ordering
- Immediate projection consistency
- Automatic resolution of every uncertain external action

“Exactly once” may describe the intended business effect only.

---

## 11. Ordering

### 11.1 Aggregate ordering

Events concerning a mutable aggregate include `aggregateversion`.

Consumer behaviour:

- Version already processed: ignore as duplicate/obsolete.
- Next expected version: apply.
- Older version: do not overwrite newer state.
- Future version with a gap: defer or reconcile.

### 11.2 Device ordering

Device-normalized events include:

- Station event sequence
- Session sequence where applicable
- Meter sequence for meter batches

### 11.3 No global order

Ordering between unrelated aggregates is not assumed.

Consumers must not infer:

- Account changes occurred before booking changes
- Maintenance and device changes arrived in real-time order
- Notifications reflect broker arrival order

### 11.4 Terminal-state protection

Delayed events cannot:

- Reopen a terminal Booking
- Replace a final Session outcome
- Restore expired authorization
- Undo a privacy tombstone
- Reactivate a newer restriction state

---

## 12. Schema governance

### 12.1 Source of truth

Schemas are stored in the repository alongside:

- AsyncAPI document
- JSON Schema payload files
- Examples
- Compatibility tests
- Producer and consumer ownership metadata

A runtime schema registry is not required initially.

### 12.2 Compatibility rules

Backward-compatible within one major version:

- Add optional fields
- Add optional metadata
- Add enum values where consumers tolerate unknown values
- Relax a field from required to optional
- Broaden safe validation constraints

Breaking:

- Remove or rename a field
- Change field type or unit
- Make an optional field required
- Change field meaning
- Narrow accepted values
- Reuse an enum value with new meaning
- Change aggregate-version semantics

Breaking changes require a new event/command major version.

### 12.3 Producer rules

- Validate before outbox insertion.
- Emit only declared message versions.
- Populate every required field.
- Never emit undocumented payload variants.

### 12.4 Consumer rules

- Validate required fields.
- Ignore unknown optional fields.
- Explicitly declare supported major versions.
- Quarantine unsupported major versions.
- Avoid language-level deserialization that fails on unknown properties.

### 12.5 Schema lifecycle

`DRAFT → IN_REVIEW → APPROVED → DEPRECATED → RETIRED`

A schema cannot be retired while supported consumers still depend on it.

---

# 13. Account events

| Event | Producer | Consumers | Minimum payload |
|---|---|---|---|
| `AccountActivated` | Account | Booking, Notification, Governance | Account reference, eligibility |
| `AccountBookingEligibilityChanged` | Account | Booking | Account reference, eligible flag, reason, version |
| `AccountSuspensionStarted` | Account/Governance | Notification, Governance | Account reference, workflow |
| `AccountSuspended` | Account | Booking, Notification, Governance | Account reference, effective time |
| `AccountReactivated` | Account | Booking, Notification | Account reference |
| `AccountDeletionPending` | Account | Booking, Notification, Governance | Account reference, workflow |
| `AccountDeleted` | Account | Discovery, Notification, Governance | Tombstone reference, completion time |
| `NotificationPreferencesChanged` | Account | Notification | Account reference, preference version |
| `ProfileLocaleChanged` | Account | Notification | Account reference, locale |
| `VehicleProfileChanged` | Account | No mandatory consumer | Account and vehicle references |

`AccountBookingEligibilityChanged` is the principal enforcement event used by Booking.

---

# 14. Organization and infrastructure events

| Event | Producer | Consumers |
|---|---|---|
| `OperatorApplicationSubmitted` | Station Operations | Governance |
| `OperatorApplicationApproved` | Governance/Station Operations | Notification |
| `OperatorApplicationRejected` | Governance/Station Operations | Notification |
| `OperatorOrganizationActivated` | Station Operations | Booking, Discovery, Notification |
| `OperatorOrganizationSuspended` | Station Operations | Booking, Discovery, Notification |
| `OperatorOrganizationReactivated` | Station Operations | Booking, Discovery |
| `OperatorOrganizationClosed` | Station Operations | Booking, Discovery, Notification |
| `OrganizationMembershipChanged` | Station Operations | Governance/security projections |
| `StationCreated` | Station Operations | Discovery |
| `StationPublished` | Station Operations | Booking, Discovery |
| `StationConfigurationChanged` | Station Operations | Booking, Discovery |
| `StationTemporarilyClosed` | Station Operations | Booking, Discovery, Notification |
| `StationReopened` | Station Operations | Booking, Discovery |
| `StationDeactivated` | Station Operations | Booking, Discovery |
| `EvseConfigurationChanged` | Station Operations | Booking, Discovery, Device Integration |
| `EvseAdministrativeStateChanged` | Station Operations | Booking, Discovery, Device Integration |
| `ConnectorConfigurationChanged` | Station Operations | Booking, Discovery, Device Integration |
| `TariffVersionActivated` | Station Operations | Booking, Discovery |
| `BookingPolicyVersionActivated` | Station Operations | Booking, Discovery |
| `SimulatorAssignmentChanged` | Station Operations | Device Integration |

Configuration events include:

- Resource reference
- Source configuration version
- Effective time
- Necessary bookable fields or snapshot lookup reference

They exclude unrestricted operator notes.

---

# 15. Maintenance, fault and override events

| Event | Producer | Consumers |
|---|---|---|
| `MaintenanceProposed` | Station Operations | Governance/operational views |
| `MaintenanceScheduled` | Station Operations | Discovery, Notification |
| `MaintenanceActivated` | Station Operations | Booking, Discovery, Device Integration |
| `MaintenanceCompleted` | Station Operations | Booking, Discovery, Device Integration |
| `MaintenanceCancelled` | Station Operations | Booking, Discovery |
| `FaultReportSubmitted` | Station Operations | Governance/operational projection |
| `FaultIncidentOpened` | Station Operations | Booking, Discovery, Notification |
| `FaultIncidentSeverityChanged` | Station Operations | Booking, Discovery |
| `FaultIncidentResolved` | Station Operations | Booking, Discovery |
| `FaultIncidentReopened` | Station Operations | Booking, Discovery |
| `StatusOverrideActivated` | Station Operations | Booking, Discovery, Device Integration |
| `StatusOverrideExpired` | Station Operations | Booking, Discovery, Device Integration |
| `StatusOverrideRevoked` | Station Operations | Booking, Discovery, Device Integration |

A Maintenance record is not considered transactionally enforceable merely because `MaintenanceScheduled` exists. Booking enforcement depends on the coordinated Capacity Block workflow.

---

# 16. Booking and allocation events

| Event | Producer | Consumers |
|---|---|---|
| `BookingHeld` | Booking | Discovery |
| `BookingConfirmed` | Booking | Discovery, Notification, Station Operations, Governance |
| `BookingRescheduled` | Booking | Discovery, Notification, Station Operations |
| `BookingReassigned` | Booking | Discovery, Notification, Station Operations, Device Integration |
| `BookingCancelled` | Booking | Discovery, Notification, Station Operations, Device Integration |
| `BookingExpired` | Booking | Discovery |
| `DriverCheckedIn` | Booking | Notification where configured, Governance |
| `CheckInAbandoned` | Booking | Device Integration where mirror exists |
| `BookingNoShowRecorded` | Booking | Discovery, Notification, Station Operations, Insights |
| `BookingFulfilmentFailed` | Booking | Notification, Station Operations, Governance, Insights |
| `BookingActivated` | Booking | Station Operations, Discovery |
| `BookingCompleted` | Booking | Discovery, Station Operations, Insights |
| `AllocationClaimed` | Booking | Discovery |
| `AllocationChanged` | Booking | Discovery |
| `AllocationReleased` | Booking | Discovery |
| `CapacityBlockInstalled` | Booking | Station Operations coordinator |
| `CapacityBlockReleased` | Booking | Station Operations coordinator |
| `DriverRestrictionInstalled` | Booking | Account/Governance coordinator |
| `DriverRestrictionReleased` | Booking | Account coordinator |

### 16.1 Booking payload rules

Events may include:

- Booking reference
- Account reference
- Station and EVSE references
- Scheduled interval
- Required connector type
- Lifecycle state/outcome
- Safe structured reason
- Snapshot version references

Events must not include:

- Start Authorization secret
- Full driver profile
- Unnecessary email address
- Internal database row IDs

---

# 17. Charging Session events

| Event | Producer | Consumers |
|---|---|---|
| `ChargingSessionStarting` | Booking | Notification only for failure monitoring; Governance |
| `ChargingSessionStarted` | Booking | Station Operations, Discovery, Insights |
| `ChargingSessionSuspended` | Booking | Station Operations, Notification where material |
| `ChargingSessionResumed` | Booking | Station Operations |
| `ChargingSessionStopping` | Booking | Station Operations |
| `ChargingSessionCompleted` | Booking | Notification, Station Operations, Insights |
| `ChargingSessionInterrupted` | Booking | Notification, Station Operations, Governance, Insights |
| `ChargingSessionStartRejected` | Booking | Notification, Station Operations, Governance |
| `ChargingSessionOutcomeUncertain` | Booking | Station Operations, Governance |
| `ChargingSessionSummaryFinalized` | Booking | Notification, Insights |

High-frequency accepted meter values are not broadcast as general integration events.

Analytics receives the finalized summary rather than every raw sample.

Operational live views may consume a rate-limited `ChargingSessionTelemetryUpdated` projection event containing:

- Session reference
- Latest cumulative energy
- Current power
- State
- Observed time
- Freshness

Telemetry projection events are disposable and cannot finalize a Session.

---

# 18. Device Integration events

| Event | Producer | Consumers |
|---|---|---|
| `SimulatorEnrolled` | Device Integration | Station Operations, Governance |
| `SimulatorIdentitySuspended` | Device Integration | Station Operations |
| `SimulatorIdentityRevoked` | Device Integration | Station Operations |
| `StationDeviceConnected` | Device Integration | Booking, Discovery, Station Operations |
| `StationDeviceDisconnected` | Device Integration | Booking, Discovery, Station Operations |
| `DeviceStatusBecameStale` | Device Integration | Booking, Discovery, Station Operations |
| `DeviceStatusRecovered` | Device Integration | Booking, Discovery, Station Operations |
| `EvseDeviceStateChanged` | Device Integration | Booking, Discovery, Station Operations |
| `DeviceFaultReported` | Device Integration | Station Operations |
| `DeviceFaultCleared` | Device Integration | Station Operations |
| `DeviceInventoryMismatchDetected` | Device Integration | Station Operations, Governance |
| `DeviceCommandDispatchAccepted` | Device Integration | Booking |
| `DeviceCommandRejected` | Device Integration | Booking |
| `DeviceCommandTimedOut` | Device Integration | Booking |
| `DeviceCommandOutcomeReconciled` | Device Integration | Booking |
| `DeviceTransactionStarted` | Device Integration | Booking |
| `DeviceTransactionUpdated` | Device Integration | Booking |
| `DeviceMeterValuesReported` | Device Integration | Booking |
| `DeviceTransactionEnded` | Device Integration | Booking |

### 18.1 Device evidence rules

- Device events identify the authenticated machine identity.
- EVSE assignment is validated before publication.
- Raw driver data is excluded.
- `DeviceTransactionStarted` is the only ordinary evidence that charging began.
- `DeviceTransactionEnded` or reconciled equivalent proves physical termination.
- Command acceptance is not physical completion.
- Meter payloads include session and meter sequence numbers.

### 18.2 Meter batching

`DeviceMeterValuesReported` may contain a bounded batch.

Initial proposed limits:

- Maximum 100 samples
- Maximum encoded message size 256 KiB
- Explicit units per field
- Consecutive or explicitly identified sequence numbers

Large or invalid batches are rejected or quarantined.

---

# 19. Notification and audit events

## 19.1 Notification triggers

Notification consumes domain facts rather than a broad generic `SendEmail` command.

A dedicated `NotificationRequested` command is allowed only for:

- Approved manual resend
- Case-specific message with an approved template
- Workflow message that has no suitable domain fact

It requires:

- Approved template key
- Recipient reference
- Immutable template variables
- Mandatory/optional category
- Reason and requesting actor

Arbitrary message bodies are prohibited.

## 19.2 Notification outcomes

| Event | Consumers |
|---|---|
| `NotificationProviderAccepted` | Source workflow where necessary |
| `NotificationDelivered` | Operational projection |
| `NotificationTemporarilyFailed` | Operational projection |
| `NotificationPermanentlyFailed` | Account/Governance where action is required |
| `NotificationBounced` | Account |
| `NotificationSuppressed` | Account/operational projection |
| `NotificationBecameObsolete` | Operational projection |

## 19.3 Audit projection

`AuditFactRecorded` contains:

- Source service
- Audit event reference
- Actor category/reference
- Action
- Target type/reference
- Safe reason category
- Outcome
- Occurred time
- Correlation ID
- Before/after summary where safe

Passwords, tokens, secrets and unnecessary personal data are excluded.

---

# 20. Asynchronous command catalogue

## 20.1 Device commands

| Command | Sender | Handler | Outcome evidence |
|---|---|---|---|
| `StartCharging` | Booking | Device Integration | Dispatch result plus `DeviceTransactionStarted` |
| `StopCharging` | Booking/Governance through Booking | Device Integration | Dispatch result plus `DeviceTransactionEnded` |
| `RequestDeviceState` | Booking | Device Integration | `DeviceCommandOutcomeReconciled` |
| `SynchronizeReservationMirror` | Booking | Device Integration | Device command outcome |
| `CancelReservationMirror` | Booking | Device Integration | Device command outcome |
| `TriggerDeviceStatusReport` | Station Operations | Device Integration | Updated device-status events |

## 20.2 Booking-enforcement commands

| Command | Sender | Handler | Outcome |
|---|---|---|---|
| `InstallCapacityBlock` | Station Operations | Booking | `CapacityBlockInstalled` or rejected outcome |
| `ReleaseCapacityBlock` | Station Operations | Booking | `CapacityBlockReleased` |
| `InstallDriverRestriction` | Account/Governance | Booking | `DriverRestrictionInstalled` |
| `ReleaseDriverRestriction` | Account | Booking | `DriverRestrictionReleased` |
| `RevalidateNearTermBooking` | Booking scheduler | Booking | Local result events where material |

## 20.3 Privacy commands

| Command | Sender | Handler |
|---|---|---|
| `CollectPrivacyExportContribution` | Account | Each personal-data owner |
| `ApplyAccountDeletion` | Account | Each mandatory participant |
| `ApplyProcessingRestriction` | Account | Applicable participant |
| `RemoveProcessingRestriction` | Account | Applicable participant |
| `ReapplyPrivacyTombstone` | Account/restore coordinator | Projection/data owner |

Outcome events:

- `PrivacyExportContributionPrepared`
- `PrivacyExportContributionFailed`
- `AccountDataDeletionApplied`
- `AccountDataDeletionBlocked`
- `AccountDataDeletionFailed`
- `ProcessingRestrictionApplied`
- `PrivacyTombstoneReapplied`

Large export data is stored in secure object storage; messages carry only an authorized expiring object reference.

## 20.4 Governance commands

Durable commands may be used for:

- `ExecuteEmergencyBookingCancellation`
- `ExecuteEmergencySessionStop`
- `ExecuteEmergencyInfrastructureRestriction`

The authoritative business owner validates:

- Intervention workflow
- Actor authority
- Scope
- Expiry
- Reason
- Current resource state

Governance cannot force a successful result or bypass owner invariants.

---

# 21. Command outcomes

A command handler must produce a durable outcome when the sender requires workflow acknowledgement.

Canonical outcome classifications:

- `ACCEPTED`
- `REJECTED`
- `COMPLETED`
- `FAILED_TEMPORARILY`
- `FAILED_PERMANENTLY`
- `EXPIRED`
- `TIMED_OUT`
- `REQUIRES_RECONCILIATION`

Rules:

1. `ACCEPTED` means responsibility was durably accepted.
2. It does not necessarily mean physical/business completion.
3. `REJECTED` includes a stable safe reason code.
4. `TIMED_OUT` is non-terminal when external action may have occurred.
5. `REQUIRES_RECONCILIATION` keeps the workflow incomplete.
6. Exactly one final outcome is accepted for a command unless reconciliation explicitly supersedes an uncertain intermediate outcome.
7. Outcome events carry the original command ID.

---

# 22. Maintenance capacity-block workflow

1. Station Operations creates a Maintenance workflow.
2. Existing Booking impact is resolved.
3. Station Operations publishes `InstallCapacityBlock`.
4. Booking validates the request and source version.
5. Booking installs the block transactionally.
6. Booking emits `CapacityBlockInstalled`.
7. Station Operations transitions Maintenance to `SCHEDULED`.
8. `MaintenanceScheduled` is published.
9. At completion, Station Operations publishes `ReleaseCapacityBlock`.
10. Booking releases the block only when safe.
11. Booking emits `CapacityBlockReleased`.
12. Device status must become fresh before near-term availability returns.

If the workflow stops after step 5, reconciliation detects the orphaned block by workflow ID.

---

# 23. Device start workflow

1. Booking consumes Start Authorization.
2. Booking creates Session `STARTING`.
3. Booking publishes `StartCharging`.
4. Device Integration records the command idempotently.
5. Device Integration dispatches the simulator command.
6. It emits dispatch rejection, timeout or acceptance evidence.
7. Command acceptance leaves Session `STARTING`.
8. `DeviceTransactionStarted` causes Booking to transition:
   - Session to `CHARGING`
   - Booking to `ACTIVE`
9. Definitive rejection causes:
   - Session `START_REJECTED`
   - Booking `FULFILMENT_FAILED`
10. Timeout leaves Session `STARTING` and starts reconciliation.

A consumed Start Authorization is never restored.

---

# 24. Device stop workflow

1. Booking commits Session `STOPPING`.
2. Booking publishes `StopCharging`.
3. Device Integration dispatches idempotently.
4. Acceptance does not finalize the Session.
5. `DeviceTransactionEnded` determines the final outcome.
6. Timeout produces reconciliation.
7. Capacity remains blocked until termination and release time are established.

---

# 25. Retry policy

## 25.1 Failure classification

Transient:

- Broker or database interruption
- Temporary dependency outage
- Lock timeout
- Temporary storage failure
- Provider throttling

Permanent:

- Invalid schema
- Unsupported major version
- Unauthorized producer
- Impossible state transition
- Missing mandatory immutable reference
- Expired command
- Payload integrity failure

## 25.2 Retry behaviour

- No immediate infinite requeue loop
- Exponential backoff with jitter
- Operation-specific maximum attempts and age
- Original message ID preserved
- Attempt metadata stored outside the business payload
- Consumer processing remains idempotent

Suggested initial retry schedule:

- 5 seconds
- 30 seconds
- 2 minutes
- 10 minutes
- 1 hour

Privacy and reconciliation workflows may use longer persisted schedules.

## 25.3 Quarantine

After exhaustion or permanent failure:

- Route to quarantine.
- Preserve original envelope.
- Record consumer, failure class and attempts.
- Alert according to severity.
- Require authorized replay or resolution.
- Never silently discard a release-critical command/event.

---

# 26. Dead-letter safety

Dead-lettering is itself message publication and can fail. RabbitMQ documents that default dead-letter republishing does not always use publisher confirms, while quorum queues can support at-least-once dead-lettering. ([rabbitmq.com](https://www.rabbitmq.com/docs/dlx))

Therefore:

- Critical retries must use a delivery-safe mechanism.
- Final queue type and DLX policy require deployment validation.
- If broker-level retry safety is insufficient, the consumer stores retry work durably in its database and republishes through its outbox.
- Privacy, restriction and device-reconciliation workflows always retain persisted workflow state independently of the broker.

---

# 27. Replay

Authorized replay requires:

- Original message ID
- Original type and schema version
- Original occurrence time
- Replay actor and reason
- Target consumer/queue
- Correlation reference
- Audit record

Replay must not:

- Generate a new logical event ID
- Repeat physical device actions with a new command ID
- Inflate meter totals
- Duplicate notifications
- Undo terminal states
- Restore deleted personal projections
- bypass current authorization

A corrected replacement message receives a new ID and explicitly references the invalid original.

---

# 28. Retention

Provisional retention:

| Record | Minimum |
|---|---:|
| Unpublished outbox | Until confirmed publication |
| Published outbox metadata | 90 days |
| Inbox deduplication | Longer than maximum replay window |
| Booking/session command results | 7 days minimum |
| Device command results | Supported reconciliation/replay period |
| Quarantine records | Until resolved plus audit window |
| Event schemas | Indefinite while referenced |
| High-frequency telemetry messages | According to privacy/telemetry retention |
| Workflow messages | Workflow lifetime plus audit window |

Exact retention is finalized with database and privacy design.

---

# 29. Security

- Broker connections require authenticated service identities.
- TLS is required outside isolated local development.
- Services receive least-privilege exchange and queue permissions.
- Producers cannot publish as another service.
- Consumers cannot bind arbitrary sensitive routing keys.
- Payload schemas and message size are validated.
- Message bodies are not logged by default.
- Tokens, passwords, private keys and Start Authorization secrets are prohibited.
- Personal-data fields require documented necessity.
- Device messages contain no unnecessary driver data.
- Quarantine and replay access is privileged and audited.
- Large object references are short-lived, scoped and authenticated.
- Environment boundaries use separate broker credentials and preferably separate virtual hosts or brokers.
- Trace and correlation fields are not trusted authorization evidence.

---

# 30. Data classifications

Allowed `classification` values:

- `PUBLIC`
- `INTERNAL`
- `OPERATIONAL`
- `PERSONAL`
- `PERSONAL_OPERATIONAL`
- `SECURITY_RESTRICTED`
- `PRIVACY_RESTRICTED`

Messages marked `SECURITY_RESTRICTED` or `PRIVACY_RESTRICTED` require:

- Explicit consumer allowlist
- Shorter broker retention
- Restricted quarantine access
- Payload-minimization review

P4 secrets are never valid message content.

---

# 31. Observability

Required message metrics:

- Outbox backlog size and age
- Publication attempts and failures
- Publisher-confirm latency
- Unroutable count
- Consumer queue depth and age
- Consumer processing latency
- Duplicate messages detected
- Version gaps
- Retry count
- Quarantine count and age
- Command age by state
- Incomplete workflow age
- Device sequence gaps
- Schema-validation failures
- Privacy workflow delays

Required log identifiers:

- Message ID
- Type
- Producer
- Consumer
- Aggregate reference/version
- Workflow ID
- Correlation ID
- Causation ID
- Safe outcome

Payload logging is prohibited by default.

---

# 32. Contract testing

Every message contract requires:

1. JSON Schema validation test
2. Producer serialization test
3. Consumer deserialization test
4. Unknown optional-field tolerance test
5. Required-field failure test
6. Data-minimization test
7. Version-compatibility test
8. Duplicate-delivery test
9. Out-of-order test where applicable
10. Retry/quarantine test
11. Traceability to requirement and invariant IDs

Critical scenarios:

- Outbox commit followed by broker outage
- Publication confirmation lost
- Consumer crash before commit
- Consumer crash after commit but before acknowledgement
- Duplicate command
- Command ID reused with changed data
- Event version gap
- Old event after terminal state
- Unroutable mandatory message
- Invalid schema
- Quarantine replay
- Device event sequence gap
- Duplicate meter batch
- Privacy command replay
- Capacity block acknowledgement loss
- Notification trigger duplication

---

# 33. AsyncAPI deliverables

Before implementation, create:

- Root AsyncAPI 3.1 document
- Channel definitions
- Operation definitions
- Event and command message definitions
- CloudEvents envelope bindings
- RabbitMQ binding configuration
- JSON Schema payload files
- Valid and invalid examples
- Producer/consumer ownership metadata
- Security schemes
- Compatibility test configuration

Generated code may assist serialization but must not replace domain validation.

---

# 34. Traceability summary

| Message group | Requirements |
|---|---|
| Account eligibility | FR-IAM-01, FR-BKG-01, FR-PLT-01/02 |
| Infrastructure configuration | FR-OPS-01/02, FR-AVL-01 |
| Maintenance/fault restrictions | FR-OPS-03, FR-AVL-01, FR-BKG-02 |
| Booking/allocation | FR-BKG-01–06, FR-AVL-03 |
| Charging/device evidence | FR-CHG-01–04, FR-SIM-02/03 |
| Discovery/analytics | FR-DIS-01/02, FR-OPS-05, FR-PLT-06 |
| Notifications | FR-NOT-01–03 |
| Privacy commands | FR-PRV-01–04, FR-PLT-05 |
| Audit projection | FR-AUD-01/02 |
| Retry/replay/quarantine | FR-PLT-01–05 |

---

# 35. Decisions proposed for approval

| ID | Decision |
|---|---|
| ARC-MSG-01 | Use CloudEvents 1.0 structured JSON for integration events. |
| ARC-MSG-02 | Use a compatible custom envelope for asynchronous commands. |
| ARC-MSG-03 | Document asynchronous contracts using AsyncAPI 3.1.0. |
| ARC-MSG-04 | Use JSON Schema Draft 2020-12 for payload schemas. |
| ARC-MSG-05 | Use at-least-once delivery with idempotent business effects. |
| ARC-MSG-06 | Require transactional outbox publication for authoritative facts and commands. |
| ARC-MSG-07 | Require transactional inbox processing for state-changing consumers. |
| ARC-MSG-08 | Use publisher confirms, mandatory routing and manual consumer acknowledgements if RabbitMQ is selected. |
| ARC-MSG-09 | Use separate logical exchanges for events, commands, retries and quarantine. |
| ARC-MSG-10 | Give every logical consumer its own queue. |
| ARC-MSG-11 | Use aggregate versions and device/session sequences rather than global ordering assumptions. |
| ARC-MSG-12 | Preserve original IDs through retries and replay. |
| ARC-MSG-13 | Use a new major message type for breaking schema changes. |
| ARC-MSG-14 | Publish normalized device evidence rather than raw simulator messages. |
| ARC-MSG-15 | Do not broadcast high-frequency raw meter samples to general consumers. |
| ARC-MSG-16 | Use domain facts—not generic email commands—as ordinary notification triggers. |
| ARC-MSG-17 | Persist coordinated workflow state independently of broker messages. |
| ARC-MSG-18 | Keep command acceptance separate from business or physical completion. |
| ARC-MSG-19 | Use secure object storage references for large privacy contributions. |
| ARC-MSG-20 | Require authorized, audited and idempotent replay. |

---

# 36. Open questions

| ID | Question | Resolution phase |
|---|---|---|
| ARC-MSG-OQ-01 | Confirm RabbitMQ as the broker | Final technology selection |
| ARC-MSG-OQ-02 | Select quorum versus classic queue types by workload | Deployment design |
| ARC-MSG-OQ-03 | Final exchange, queue and routing names | AsyncAPI/infrastructure design |
| ARC-MSG-OQ-04 | Final retry intervals and maximum attempts | Testing/operations |
| ARC-MSG-OQ-05 | Maximum event and meter-batch sizes | Performance testing |
| ARC-MSG-OQ-06 | Final inbox/outbox retention periods | Database/privacy design |
| ARC-MSG-OQ-07 | Whether runtime schema-registry functionality is justified | Technology selection |
| ARC-MSG-OQ-08 | Service-to-broker authentication mechanism | Security architecture |
| ARC-MSG-OQ-09 | Encryption and access model for privacy contribution storage | Cloud/security design |
| ARC-MSG-OQ-10 | Maximum enforcement-projection lag | NFR/testing |
| ARC-MSG-OQ-11 | Final telemetry projection frequency | Performance/frontend design |
| ARC-MSG-OQ-12 | Whether audit projection consumes one shared routing pattern or service-specific queues | Security/deployment design |

---

# 37. Acceptance criteria

This catalogue is approved when:

1. Every asynchronous interaction in ARC-002 maps to a defined message family.
2. Events and commands have distinct semantics.
3. Every message has one producer and declared consumers/handler.
4. No message is required for a local transaction to commit.
5. Duplicate delivery cannot repeat business effects.
6. Ordering-sensitive consumers use versions or sequences.
7. Commands retain stable IDs through retries.
8. Device timeout remains uncertain.
9. Raw simulator messages cannot bypass Device Integration.
10. Meter duplicates cannot inflate energy or cost.
11. Privacy payloads do not travel directly through the broker when large.
12. Retry and quarantine cannot silently discard critical work.
13. Message schemas support controlled evolution.
14. Personal data and secrets are minimized.
15. Broker outage cannot lose committed integration intent.
16. Every coordinated workflow persists state outside the broker.
17. Replay is authorized and audited.
18. Contracts can be represented in AsyncAPI and JSON Schema.
19. All message types map to requirements and tests.
20. Broker-specific decisions remain replaceable until the technology ADR is approved.

---

# 38. Consequences

## Positive

- Standardized event metadata
- Explicit command semantics
- Reliable at-least-once processing
- Strong duplicate protection
- Controlled schema evolution
- Rebuildable projections
- Honest device uncertainty
- Traceable workflow outcomes

## Negative

- Outbox and inbox storage overhead
- More message schemas and contract tests
- Workflow coordination complexity
- Operational burden for retry and quarantine
- Delayed cross-service visibility
- Additional schema-version governance

These costs are accepted to preserve service autonomy without distributed transactions.

---

# 39. Next architecture artifact

The next document is:

**Database Models, Ownership and Migration Strategy v1.0**

It must define:

- Database-per-service logical ownership
- Aggregate tables and relationships
- IDs and public references
- Booking allocation constraints
- Outbox, inbox and idempotency tables
- Projection storage
- Audit and privacy tombstones
- Indexes and partitioning
- Migration ownership
- Backward-compatible migration rules
- Backup and restoration considerations
- Data retention implementation

### Event Contract Implementation Roadmap
To convert this catalogue into machine-readable AsyncAPI schemas:
- **AsyncAPI Schemas:** Event and command channels will be documented using AsyncAPI 2.6/3.0.
- **Event Versioning:** Schema changes use backwards-compatible addition of fields; breaking upgrades require versioned channels.
- **Broker Quality Gates:** Event schema validation checks are integrated into CI/CD pipelines to prevent schema drift.
