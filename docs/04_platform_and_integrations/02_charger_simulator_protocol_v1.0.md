Document ID: SIM-001
Title: Charger Simulator Protocol v1.0
Version: 1.0
Status: APPROVED
Owner: BA/QA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: DOM-001
Authoritative for: Simulator Telemetry Sequence and Committing Command Rules

---

# Charger Simulator Protocol, Machine Identity, Commands and Events v1.0

## 1. Purpose

Define the charging-station simulator’s:

- Logical architecture
- Machine identity and enrollment
- Connection lifecycle
- Protocol envelope
- Platform commands
- Charger events
- Session and meter reporting
- Offline behaviour
- Failure simulation
- Idempotency, ordering and reconciliation
- Security and observability requirements

The simulator provides realistic device behaviour for development, testing, demonstrations and architectural evaluation.

It does not control real charging hardware.

---

## 2. Protocol status and reference

The simulator protocol is a **custom, versioned, OCPP 2.1 Edition 2-inspired subset**.

It adopts selected concepts such as:

- Charging Station → EVSE → Connector hierarchy
- Boot registration
- Heartbeats
- Device and EVSE status reporting
- Remote commands
- Transaction events
- Meter values
- Offline event queuing
- Command correlation
- Sequence-based reconstruction
- Secure machine identity

OCPP defines communication between charging stations and charging-management systems and includes device management, transactions, remote control and security capabilities. ([openchargealliance.org](https://openchargealliance.org/certificationocpp/certification-ocpp-2-0-1/))

The project MUST NOT claim:

- OCPP compliance
- Wire compatibility
- OCA certification
- Complete implementation of an OCPP functional block
- Compatibility with commercial charging stations

Documentation and user interfaces must use:

> “OCPP 2.1-inspired simulator protocol”

not:

> “OCPP implementation” or “OCPP-compliant charger.”

---

## 3. Goals

The protocol must support:

1. Authenticated simulated charging stations.
2. Multiple EVSEs per simulated station.
3. Status and heartbeat reporting.
4. Reservation synchronization.
5. Authorized session start and stop.
6. Transaction and meter events.
7. Fault and maintenance simulation.
8. Duplicate, delayed and out-of-order events.
9. Temporary disconnection and offline queuing.
10. Command rejection and uncertain outcomes.
11. Idempotent command and event processing.
12. Automatic and operator-assisted reconciliation.
13. Deterministic scenarios for repeatable tests.

---

## 4. Non-goals

Version 1 excludes:

- Real charger connectivity
- Full OCPP message compatibility
- Smart charging and load balancing
- Vehicle-to-grid functionality
- ISO 15118 communication
- Plug and Charge
- Real firmware updates
- Local payment terminals
- Real electrical safety controls
- Real remote-reset behaviour
- Real certificate-provisioning infrastructure
- Hardware-vendor interoperability

Interfaces should remain extensible without pretending these capabilities already exist.

---

## 5. Logical components

### 5.1 Simulator control plane

Used by authorized operators and test automation to:

- Create simulator assignments
- Start and stop simulator instances
- Configure normal behaviour
- Select deterministic scenarios
- Inject faults and failures
- Inspect connections and queued events
- Reset simulator state

### 5.2 Simulated charging station

Represents one physical charging station controller.

It owns:

- One machine identity
- One connection
- One station-level event sequence
- One or more EVSE simulations
- Local command-result history
- A durable offline event queue
- Configured failure behaviour

### 5.3 Simulated EVSE

Represents one independently usable charging point capable of serving one vehicle at a time.

It owns:

- Reported device state
- Connector inventory
- Active reservation mirror
- At most one active simulated transaction
- Meter-generation state
- Active fault state

### 5.4 Platform device boundary

The logical platform endpoint that:

- Authenticates machines
- Validates protocol messages
- Dispatches commands
- Accepts events
- Deduplicates messages
- Records device state
- Routes accepted facts to owning capabilities

Its final microservice placement remains undecided.

---

## 6. Source-of-truth boundaries

The simulator is authoritative only for simulated physical observations:

- Connection state
- Device-reported EVSE state
- Command acceptance or rejection
- Whether simulated energy transfer began
- Meter readings
- Device faults
- Simulated transaction termination

The simulator is not authoritative for:

- User identity
- Booking validity
- EVSE allocation
- Tariffs
- Booking policy
- Maintenance authorization
- Driver permissions
- Final estimated cost
- Derived public availability

The Booking capability remains authoritative for allocations and start authorization.

The Infrastructure capability remains authoritative for station, EVSE and connector configuration.

A device report cannot create or modify platform infrastructure automatically.

---

## 7. Transport

### 7.1 Primary transport

Use secure, persistent WebSocket communication with versioned JSON messages.

Proposed subprotocol:

`evsim.v1`

Reasons:

- Bidirectional commands and events
- Realistic charger connectivity
- Connection-loss simulation
- Immediate command delivery
- Offline/reconnect testing

### 7.2 Required transport controls

- TLS in all deployed environments
- Explicit protocol-version negotiation
- Maximum message size
- Idle and heartbeat timeout
- Connection rate limiting
- Schema validation
- Controlled reconnect backoff
- One active connection per station identity by default

REST may be used for simulator administration, but not as the primary device-event channel.

---

## 8. Machine identity

Every simulated charging station receives a unique machine identity independent of operator user accounts.

Identity is assigned to the charging-station controller, not to each EVSE.

### Identity record

- Internal machine identity ID
- Public charging-station identifier
- Assigned operator organization
- Assigned station and allowed EVSEs
- Credential or certificate reference
- Status
- Created, rotated, revoked and last-used timestamps
- Last authenticated network metadata
- Permitted protocol versions

### Identity states

`PENDING_ENROLLMENT → ACTIVE → SUSPENDED → REVOKED`

A revoked identity cannot be restored; a new credential must be issued.

### Rules

- One identity may report only its assigned station and EVSEs.
- Operator users cannot authenticate as devices.
- Devices cannot call driver/operator business APIs.
- Credentials must not appear in repository files, images, events or logs.
- Credential rotation must not require changing the public station identifier.
- Suspending an operator or station prevents new device operations according to platform policy.

---

## 9. Authentication profiles

### Target deployed profile

Use mutual TLS or an equivalently strong certificate-based machine identity.

OCA security material distinguishes TLS server authentication with device credentials from TLS with client-certificate authentication; advanced OCPP security supports TLS client authentication. ([openchargealliance.org](https://openchargealliance.org/certificationocpp/certification-ocpp-2-0-1/))

### Development profile

Local development may use:

- TLS where practical
- A unique rotating simulator credential
- Short-lived machine access tokens

Development credentials MUST NOT be accepted in staging or production-like deployment.

### Credential controls

- Unique credential per simulated station
- Encrypted storage
- Rotation and revocation
- Short validity for issued access tokens
- Audience and scope validation
- No shared fleet-wide password
- Failed-authentication throttling
- Security-event generation for suspicious attempts

---

## 10. Enrollment

1. Operator creates or selects an eligible station.
2. Platform creates a pending simulator assignment.
3. A single-use enrollment credential is issued.
4. Simulator authenticates and submits its station identity.
5. Platform validates assignment and enrollment status.
6. Permanent machine credentials are established.
7. Enrollment credential is invalidated.
8. Machine identity becomes `ACTIVE`.
9. Enrollment is audited.

Enrollment credentials must be:

- Single-use
- Short-lived
- Stored securely
- Bound to one assignment
- Redacted from logs
- Invalid after successful enrollment

---

## 11. Connection lifecycle

Connection states:

`DISCONNECTED → CONNECTING → AUTHENTICATED → REGISTERING → ONLINE`

Alternative states:

- `PENDING`
- `REJECTED`
- `STALE`
- `SUSPENDED`

### Connection flow

1. Simulator opens a secure WebSocket connection.
2. Machine authentication is validated.
3. Protocol version is negotiated.
4. Simulator sends `StationBooted`.
5. Platform responds with registration disposition:
   - `ACCEPTED`
   - `PENDING`
   - `REJECTED`
6. Accepted stations enter `ONLINE`.
7. Simulator sends periodic heartbeats.
8. On disconnection, simulator queues eligible events.
9. On reconnect, it registers again and replays queued events.

`PENDING` permits only registration, heartbeat and required diagnostic communication.

`REJECTED` closes the connection after returning a safe reason.

---

## 12. Protocol envelope

Every message contains:

- `messageId`
- `messageKind`
- `action`
- `schemaVersion`
- `protocolVersion`
- `stationId`
- `occurredAt`
- `sentAt`
- `correlationId`
- Optional `causationId`
- Optional `commandId`
- Optional `evseId`
- Optional `connectorId`
- Optional `sessionId`
- Optional `bookingReference`
- Optional sequence information
- Payload

### Message kinds

- `COMMAND`
- `COMMAND_RESULT`
- `EVENT`
- `RECEIPT`
- `PROTOCOL_ERROR`

### Rules

- Every message ID is globally unique.
- Commands have a globally unique command ID.
- Event timestamps use UTC.
- Unknown optional fields are tolerated.
- Unknown mandatory schema versions are rejected.
- Credentials and personal data are forbidden in payloads.
- Protocol errors must not expose internal stack traces.

---

## 13. Acknowledgement semantics

A transport receipt means only:

> The message was authenticated, structurally valid and durably accepted for processing.

It does not mean:

- The command succeeded
- Charging started
- A booking was confirmed
- A business transition completed

Business outcomes are represented by `COMMAND_RESULT` or domain events.

This distinction prevents transport acknowledgement from being mistaken for physical success.

---

## 14. Command lifecycle

Command states:

`CREATED → PENDING → DISPATCHED → ACCEPTED/REJECTED/TIMED_OUT`

Optional terminal state:

`CANCELLED_BEFORE_DISPATCH`

A timeout means the result is uncertain.

It does not mean the command failed physically.

### Required command fields

- Command ID
- Command type
- Target station/EVSE
- Creation time
- Expiry time
- Idempotency key
- Expected device/session state
- Correlation ID
- Business reference
- Command-specific payload

### Duplicate commands

The simulator stores recent command results.

When receiving the same command ID again, it must:

- Return the original result
- Not repeat the physical simulation action
- Not create another session
- Not reset meter state
- Not produce duplicate transaction beginnings

---

## 15. Platform-to-simulator commands

### `SynchronizeReservation`

Mirrors a committed platform reservation to the assigned EVSE.

Possible results:

- `ACCEPTED`
- `REJECTED_EVSE_UNAVAILABLE`
- `REJECTED_CONFLICT`
- `REJECTED_UNKNOWN_EVSE`
- `REJECTED_INVALID_INTERVAL`
- `REJECTED_UNSUPPORTED`
- `EXPIRED`

The platform booking remains authoritative. Rejection does not silently cancel a committed booking; it marks fulfilment risk and triggers reconciliation.

### `CancelReservationMirror`

Removes a mirrored reservation.

It cannot stop an active charging transaction.

### `StartCharging`

Requests one authorized simulated charging session.

Required data:

- Booking reference
- Session ID
- EVSE ID
- Required connector
- Authorization reference
- Command expiry
- Requested start constraints

The command contains no driver identity or reusable browser token.

Possible results:

- `ACCEPTED`
- `REJECTED_NOT_RESERVED`
- `REJECTED_WRONG_EVSE`
- `REJECTED_INCOMPATIBLE_CONNECTOR`
- `REJECTED_OCCUPIED`
- `REJECTED_FAULTED`
- `REJECTED_UNAVAILABLE`
- `REJECTED_EXPIRED`
- `REJECTED_DUPLICATE_SESSION`
- `REJECTED_INTERNAL_ERROR`

Acceptance means the simulator accepted responsibility for starting. Actual energy transfer is confirmed through a transaction event.

### `StopCharging`

Requests termination of an identified session.

Possible results:

- `ACCEPTED`
- `REJECTED_UNKNOWN_SESSION`
- `REJECTED_ALREADY_ENDED`
- `REJECTED_NOT_ALLOWED`
- `REJECTED_INTERNAL_ERROR`

A successful command result does not finalize the session. Finalization requires a transaction-ended event.

### `RequestCurrentState`

Requests station, EVSE and active-session state for reconciliation.

### `TriggerStatusReport`

Requests fresh status events without changing device state.

### `ApplyAvailabilityOverride`

Applies an authorized temporary simulator-side operational restriction.

It cannot override the platform’s administrative or maintenance authority.

### `ClearAvailabilityOverride`

Removes the applicable simulator-side restriction.

### `ResetSimulator`

Resets the logical station.

Permitted modes:

- `SOFT`
- `HARD_SIMULATED`

Resetting must not erase durable queued events or command-result history required for idempotency.

### `SetSimulationProfile`

Changes permitted behaviour parameters, such as:

- Charging power
- Meter interval
- Session-duration behaviour
- Rejection probability
- Event delay
- Disconnect schedule

### `InjectFault`

Creates a simulated fault.

### `ClearFault`

Clears a simulated fault and emits recovery status.

---

## 16. Simulator-to-platform events

### `StationBooted`

Contains:

- Simulator software version
- Protocol versions
- Station model
- Inventory summary/hash
- Supported capabilities
- Last acknowledged station sequence
- Restart reason

### `HeartbeatReceived`

Contains station time and basic health metadata.

Server-received time determines freshness.

### `EvseStatusChanged`

Contains:

- EVSE
- Reported state
- Reason
- Connector context where applicable
- Event sequence
- Device timestamp

### `FaultRaised`

Contains:

- Fault code
- Severity
- EVSE or station scope
- Description category
- Whether operation can continue

### `FaultCleared`

References the fault being cleared.

### `ReservationMirrorChanged`

Reports acceptance, expiry, cancellation or loss of the device-side reservation mirror.

### `TransactionStarted`

Confirms that the simulated transaction began.

### `TransactionUpdated`

Reports material transaction-state changes.

### `MeterValuesReported`

Reports sequence-controlled meter samples.

### `TransactionEnded`

Reports the definitive simulated physical termination.

### `CommandExecutionReported`

Reports asynchronous execution detail when the immediate command result was insufficient.

### `SecurityEventReported`

Reports suspicious or invalid device-side conditions without including secrets.

---

## 17. Device-reported states

### Station connectivity

- `ONLINE`
- `OFFLINE`
- `STALE`
- `UNKNOWN`

### EVSE reported state

- `AVAILABLE`
- `RESERVED`
- `OCCUPIED`
- `CHARGING`
- `SUSPENDED`
- `FINISHING`
- `FAULTED`
- `UNAVAILABLE`
- `UNKNOWN`

These are device observations, not the final public availability decision.

Administrative state, reported state and derived booking availability remain separate.

---

## 18. Transaction model

Simulator transaction states:

- `START_PENDING`
- `CHARGING`
- `SUSPENDED`
- `STOP_PENDING`
- `ENDED`
- `INTERRUPTED`

Every transaction has:

- Stable session/transaction ID
- EVSE ID
- Connector used
- Start and end timestamps
- Transaction sequence
- Meter sequence
- Latest cumulative energy
- Current simulated power
- Stop reason
- Interruption reason where applicable

Only one non-terminal transaction may exist per EVSE.

The simulator rejects attempts to start a second transaction on the same EVSE.

---

## 19. Meter-value model

Initial measurements:

- Cumulative energy in Wh
- Instantaneous power in W
- Optional current in A
- Optional voltage in V
- Simulator state-of-charge estimate, if enabled

### Rules

- Cumulative energy normally never decreases.
- Every sample has a session-scoped sequence number.
- Duplicate samples retain the same event and sequence identifiers.
- Replayed samples must not increase platform totals twice.
- Missing sequence ranges are detectable.
- Units are explicit.
- Values outside configured limits are rejected or flagged.
- Final session energy is reproducible from accepted data.

Estimated cost is calculated by the platform from accepted session data and the tariff snapshot—not by the simulator.

---

## 20. Event sequencing

Use two levels of sequencing:

### Station event sequence

Monotonically increases for durable station-originated events.

Used for:

- Detecting missed events
- Offline replay
- General station-state reconstruction

### Session sequence

Monotonically increases within a charging session.

Used for:

- Transaction state
- Meter samples
- Finalization

Sequence numbers supplement event IDs; they do not replace them.

On a sequence gap, the platform may:

- Temporarily defer application
- Request current state
- Wait for queued events
- Reconcile from a snapshot
- Escalate unresolved inconsistency

Older events cannot overwrite newer accepted state.

---

## 21. Offline behaviour

When disconnected, the simulator:

- Continues an already active configured simulation
- Queues durable events
- Preserves event IDs and sequence numbers
- Records original occurrence times
- Does not accept new remote commands
- Applies only explicitly supported local behaviour
- Reconnects using exponential backoff with jitter

### Reconnection

1. Reauthenticate.
2. Send `StationBooted`.
3. Report last acknowledged sequence.
4. Receive platform replay instructions if needed.
5. Resend queued events in original sequence.
6. Request state reconciliation.
7. Remove queued events only after durable platform receipt.

Events may still arrive duplicated because acknowledgment can be lost.

---

## 22. Offline session rules

An active simulated session may continue during disconnection.

The simulator must:

- Continue cumulative metering
- Queue meter and transaction events
- Preserve start/end evidence
- Prevent another local session on the EVSE
- Report the final physical outcome after reconnect

Version 1 does not permit an entirely new driver-initiated offline session because platform authorization cannot be validated safely.

---

## 23. Reservation synchronization

Platform reservation and simulator reservation are distinct:

- Platform allocation is authoritative.
- Simulator reservation is an operational mirror.
- Missing mirror acknowledgement does not erase the booking.
- A conflicting device report creates fulfilment risk.
- Reconciliation may resend the same synchronization command.
- Duplicate synchronization commands return their previous result.

The simulator must not expose another booking’s identifying information.

---

## 24. Start authorization boundary

The driver never authenticates directly to the simulator.

Flow:

1. Driver completes DR-16 check-in.
2. Platform creates a single-use start authorization.
3. DR-17 consumes that authorization transactionally.
4. Platform issues `StartCharging`.
5. Simulator validates trusted command identity and command constraints.
6. Simulator reports acceptance or rejection.
7. `TransactionStarted` confirms actual charging.

The simulator receives only the minimum business reference required.

---

## 25. Failure-injection profiles

The simulator must support deterministic and probabilistic scenarios.

### Connectivity

- Immediate disconnect
- Scheduled disconnect
- Repeated connection flapping
- Slow reconnect
- Missed heartbeats
- Stale connection
- Authentication rejection

### Commands

- Accept
- Reject with selected reason
- Delay result
- Drop result after executing
- Return duplicate result
- Execute after platform timeout
- Receive duplicate command

### Events

- Duplicate event
- Delay event
- Reorder events
- Drop selected event
- Replay old event
- Create sequence gap
- Deliver queued events after reconnect

### Transactions

- Start normally
- Reject before charging
- Suspend temporarily
- End normally
- Interrupt through fault
- Continue beyond booking time
- Stop command timeout
- Disconnect while charging

### Metering

- Normal progression
- Irregular intervals
- Duplicate samples
- Delayed samples
- Sequence gaps
- Implausible jump
- Meter regression
- Clock skew

### Infrastructure

- EVSE fault
- Connector fault
- Station-wide fault
- Temporary unavailable state
- Simulated restart
- Inventory mismatch

Every deterministic scenario accepts a seed so tests can reproduce it.

---

## 26. Fault model

Fault severity:

- `WARNING`
- `DEGRADED`
- `CRITICAL`
- `EMERGENCY`

Fault scope:

- Station
- EVSE
- Connector
- Active transaction

A fault records:

- Stable fault occurrence ID
- Code
- Severity
- Scope
- Raised time
- Cleared time
- Operational effect
- Simulation scenario
- Human-readable safe description

Duplicate reports for the same occurrence must not create duplicate incidents.

---

## 27. Inventory reconciliation

At boot, the simulator reports its configured inventory summary.

The platform compares it with authoritative infrastructure configuration.

Possible outcomes:

- `MATCHED`
- `MISSING_EVSE`
- `UNEXPECTED_EVSE`
- `CONNECTOR_MISMATCH`
- `CONFIGURATION_VERSION_MISMATCH`

Inventory mismatch rules:

- The simulator cannot create infrastructure records automatically.
- Unexpected EVSE events are quarantined.
- Missing or incompatible inventory generates an operator alert.
- A materially mismatched EVSE cannot start a new session.
- Existing uncertain sessions require reconciliation rather than deletion.

---

## 28. Time handling

- Platform timestamps are UTC.
- Simulator event times are UTC.
- Server-received time determines connection freshness.
- Device event time represents claimed occurrence time.
- Excessive clock skew is recorded.
- Device time cannot extend booking, authorization or command deadlines.
- Business deadlines use authoritative database time.
- Replayed offline events retain their original occurrence time and later received time.

---

## 29. Idempotency and duplicate handling

### Commands

Deduplicate by `commandId`.

Command-result retention must cover the maximum supported command replay period.

### Events

Deduplicate by `eventId`.

Sequence numbers detect missing or reordered events.

### Transaction start

Deduplicate by stable session ID and command ID.

### Transaction end

Only one final physical outcome may be accepted. Conflicting final events trigger reconciliation.

### Meter values

Deduplicate by session ID plus meter sequence/event ID.

---

## 30. Reconciliation

Reconciliation begins when:

- A command times out
- A sequence gap occurs
- Platform and simulator states conflict
- Transaction events are missing
- A reconnect follows an active session
- Reservation synchronization fails
- Inventory does not match
- Duplicate final outcomes conflict

Reconciliation may:

1. Request current state.
2. Request active-transaction details.
3. Replay a command with the same ID.
4. Wait for queued events.
5. Compare event and aggregate versions.
6. Mark the outcome confirmed.
7. Keep capacity blocked.
8. Escalate for operator review.

The system must never invent a successful session completion merely to clear uncertainty.

---

## 31. Operator controls

Authorized operator technicians may:

- View assigned simulator state
- Connect or disconnect simulation
- Select approved scenarios
- Inject or clear faults
- Adjust permitted simulation parameters
- Trigger fresh status
- Inspect sanitized queued-event metadata
- Reset a simulator
- Review command and event history

They cannot:

- Forge driver identity
- Create bookings
- Bypass start authorization
- Change tariff snapshots
- Access another organization’s simulator
- Delete immutable event/audit history
- Mark an uncertain real outcome successful without an authorized workflow

High-impact actions require a reason and audit entry.

---

## 32. Security controls

- Authenticated machine identity on every connection
- TLS for all deployed communication
- Authorization by station and EVSE assignment
- Default-deny command permissions
- Protocol and payload schema validation
- Message-size and frequency limits
- Command expiration enforcement
- Credential rotation and revocation
- No credentials in events or logs
- No driver personal data in device messages
- Restricted dead-letter access
- Audited simulator control actions
- Protection against replay outside supported idempotency windows
- Isolation between operator organizations
- Safe error responses

A compromised simulator identity must be revocable without affecting unrelated stations.

---

## 33. Data retention

The platform retains:

- Command metadata and outcome
- Accepted event metadata
- Device-state history required for operations
- Transaction and meter records
- Fault occurrences
- Security events
- Reconciliation outcomes

Raw high-frequency telemetry may use shorter retention than final session summaries.

Retention periods will be finalized in the privacy specification.

Device messages must avoid personal data so operational retention does not unnecessarily retain driver information.

---

## 34. Observability

Required metrics:

- Connected simulators
- Authentication failures
- Heartbeat age
- Stale stations
- Commands by outcome
- Command timeout age
- Event throughput
- Duplicate events
- Sequence gaps
- Offline queue size
- Reconnect attempts
- Active simulated sessions
- Faults by severity
- Reconciliation backlog
- Inventory mismatches
- Invalid protocol messages

Logs include:

- Station identity
- EVSE ID
- Message/event/command ID
- Correlation and trace ID
- Action and safe outcome
- Schema version

Logs exclude credentials, raw access tokens and unnecessary payload data.

---

## 35. Performance requirements

Under the approved reference load:

- Support 2,000 simulated EVSEs.
- Process at least 100 charger events per second.
- Reflect 99% of accepted events in relevant operational views within 10 seconds.
- Support at least twice the reference event load during capacity testing.
- Apply backpressure rather than exhaust memory.
- Bound per-connection and per-station queues.
- Persist events required for recovery before acknowledging durable receipt.

---

## 36. Acceptance criteria

1. Only enrolled active machine identities can connect.
2. A simulator can report only assigned station/EVSE resources.
3. Duplicate commands do not repeat simulated physical actions.
4. Duplicate events do not repeat platform effects.
5. A command timeout remains uncertain until reconciled.
6. Start-command acceptance alone does not report charging as started.
7. Only `TransactionStarted` proves simulated charging began.
8. Stop-command acceptance alone does not finalize a session.
9. Offline transactions retain correct meter and termination evidence.
10. Queued events preserve IDs, sequence and occurrence timestamps.
11. Out-of-order events cannot overwrite newer state.
12. Meter duplicates cannot inflate energy or cost.
13. Missing sequences are detected.
14. The driver never sends credentials directly to the simulator.
15. One EVSE cannot run two active transactions.
16. Inventory mismatches cannot create infrastructure automatically.
17. Fault injection is deterministic when a seed is provided.
18. Revoking one machine identity does not affect other stations.
19. Post-reconnect reconciliation resolves or visibly retains uncertainty.
20. Simulator controls enforce organization ownership and audit requirements.
21. The system never claims OCPP compliance.
22. Protocol schemas support backward-compatible evolution.

---

## 37. Required test categories

- Machine enrollment tests
- Credential rotation and revocation tests
- Cross-organization authorization tests
- Connection and boot tests
- Protocol-version negotiation tests
- Heartbeat and stale-status tests
- Duplicate-command tests
- Command-timeout uncertainty tests
- Start/stop lifecycle tests
- Offline transaction tests
- Queued-event replay tests
- Duplicate/out-of-order event tests
- Sequence-gap reconciliation tests
- Meter anomaly tests
- Inventory mismatch tests
- Fault-injection reproducibility tests
- Invalid schema and oversized-message tests
- Rate-limit and connection-flood tests
- Credential/log leakage tests
- Capacity and backpressure tests
- Service/broker restart recovery tests

---

## 38. Proposed decisions for approval

1. Reference OCPP 2.1 Edition 2 concepts while implementing a custom protocol.
2. Never claim OCPP compatibility, compliance or certification.
3. Use secure JSON WebSocket communication as the primary device channel.
4. Assign one machine identity per simulated charging station.
5. Use certificate-based authentication as the deployment target.
6. Permit a separate restricted authentication profile for local development only.
7. Keep platform booking allocation authoritative; device reservation is a mirror.
8. Treat command acknowledgement, command acceptance and physical completion as separate facts.
9. Require stable command IDs and event IDs.
10. Use station-level and session-level sequence numbers.
11. Allow active sessions to continue and queue events while disconnected.
12. Prohibit initiation of new driver sessions while offline.
13. Keep uncertain command/session outcomes capacity-blocking until reconciled.
14. Require deterministic failure scenarios with configurable seeds.
15. Prevent simulator inventory reports from creating platform infrastructure.
16. Calculate costs in the platform, never in the simulator.
17. Keep device messages free of unnecessary personal information.
18. Preserve durable offline queues and idempotency history across simulated restarts.

---

## 39. Traceability

Primarily implements:

- `FR-SIM-01`
- `FR-CHG-01`
- `FR-CHG-02`
- `FR-CHG-03`
- `FR-BKG-05`
- `FR-AVL-02`
- `FR-OPS-02`
- `FR-PLT-01`
- `FR-AUD-01`

Supports the security, performance, reliability, observability, recovery and maintainability NFRs.
