Document ID: PLT-001
Title: Background Processes and Distributed Consistency v1.0
Version: 1.0
Status: APPROVED
Owner: BA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: DOM-004, DOM-005
Authoritative for: Transactional Outbox Pattern, Lockings, and Heartbeat Check rules

---

# Background Processes and Distributed Consistency v1.0

## 1. Purpose

Define how the platform executes scheduled work, publishes and consumes asynchronous events, coordinates changes across independently owned data, and recovers from partial or uncertain failures.

This specification covers:

- Booking hold expiration
- No-show processing
- Maintenance activation and completion
- Charger-status freshness
- Device-command reconciliation
- Transactional outbox delivery
- Idempotent event consumption
- Retries and dead-letter handling
- Notification dispatch
- Analytics projections
- Privacy retention and anonymization
- Cross-capability workflow consistency

The terms **Booking capability**, **Infrastructure capability**, and similar names describe logical ownership. Final microservice boundaries remain undecided.

---

## 2. Core consistency principles

1. Booking allocation and conflict prevention require strong consistency.
2. Each business entity has exactly one authoritative owner.
3. A capability writes only to data it owns.
4. Cross-capability database transactions are prohibited.
5. Services never directly update another capability’s database.
6. Cross-capability propagation uses versioned events or explicit APIs.
7. Transactional outbox records are committed with business changes.
8. Event delivery is at least once; consumers must be idempotent.
9. Exactly-once delivery is not assumed.
10. Events may be duplicated, delayed or received out of order.
11. Search, analytics, notifications and dashboards are eventually consistent.
12. Uncertain external/device outcomes remain visible until reconciled.
13. Scheduled jobs must be safe when executed more than once.
14. Failed secondary processing must not reverse an already committed booking.
15. All deadlines use authoritative database time in UTC.
16. Authoritative audit evidence remains local to each service performing the action. A central audit view (Governance Service) is an eventually consistent, searchable projection.

---

## 3. Consistency classification

### 3.1 Strongly consistent operations

These operations must complete against one authoritative transactional boundary:

- Claiming EVSE capacity
- Creating and confirming a hold
- Booking conflict detection
- Atomic booking rescheduling
- Booking assignment and reassignment
- Booking state transitions
- Check-in and start-authorization issuance
- Start-authorization consumption
- Charging-session state transitions owned by one capability
- Idempotency-record creation
- Audit/outbox creation associated with an authoritative change

### 3.2 Eventually consistent operations

These may update after the authoritative transaction:

- Search availability projections
- Station bookable-EVSE counts
- Notifications
- Analytics and reports
- Operator dashboards
- Platform-wide operational views
- Audit aggregation
- Support-case projections
- Charger-state summaries outside the device authority
- Privacy workflow progress views

### 3.3 External consistency

The following depend on systems outside a local database transaction:

- Email delivery
- Charger/simulator commands
- Identity-provider account or session operations
- Object-storage exports
- Cloud monitoring and alert delivery

Their outcomes must be tracked explicitly as pending, successful, rejected, failed or uncertain.

---

## 4. Authoritative data ownership rules

Provisional logical ownership:

| Information | Authoritative owner |
|---|---|
| User identity, credentials, MFA and identity sessions | Identity provider |
| Application profile, vehicles and preferences | Account/profile capability |
| Operator, station, EVSE, connector, tariff and policy definitions | Infrastructure capability |
| Device heartbeat, reported state and command outcome | Device/simulator capability |
| Booking, hold, allocation and check-in authorization | Booking capability |
| Charging-session lifecycle and meter sequence processing | Charging capability |
| Maintenance and fault workflow | Operations capability |
| Notification delivery state | Notification capability |
| Support cases | Support capability |
| Audit records | Audit capability or append-only audit store |
| Analytics projections | Analytics capability |
| Privacy-request coordination | Privacy/account capability |

Copies stored elsewhere are projections and cannot override the authoritative owner.

---

## 5. Transactional outbox

Every authoritative business transaction that requires asynchronous propagation must write an outbox record in the same local transaction.

Example:

1. Booking becomes `CONFIRMED`.
2. Tariff and policy snapshots are persisted.
3. Audit metadata is persisted.
4. `BookingConfirmed` is inserted into the local outbox.
5. The database transaction commits.
6. An outbox publisher later sends the event to the broker.

A committed booking remains valid even if the broker or publisher is unavailable.

### Outbox record

- Event ID
- Event type and schema version
- Aggregate type and ID
- Aggregate version
- Occurred-at timestamp
- Correlation and causation IDs
- Tenant/organization scope where applicable
- Payload
- Publication status
- Attempt count
- Next-attempt time
- Creation and publication timestamps

Sensitive credentials, reusable authorization tokens and unnecessary personal information must not appear in event payloads.

### Publishing rules

- Multiple publishers may run concurrently.
- Records are claimed using database locking, leases or equivalent safe concurrency control.
- Publisher crashes must not lose records.
- A crash after broker publication but before marking the record published may produce a duplicate.
- Successfully published records are retained for a configurable period before archival or deletion.
- Outbox backlog age and size must be monitored.

---

## 6. Event envelope and contracts

Every integration event must include:

- Globally unique `eventId`
- Stable `eventType`
- Explicit `schemaVersion`
- `occurredAt` in UTC
- Producer identity
- Aggregate identifier
- Aggregate version or sequence
- Correlation ID
- Causation ID
- Trace context where supported
- Data-classification indicator where justified
- Versioned payload

Event names describe completed facts, such as:

- `BookingConfirmed`
- `BookingCancelled`
- `DriverCheckedIn`
- `ChargingSessionStarted`
- `MaintenanceScheduled`

Commands request actions and must not be represented as completed facts.

Consumers must tolerate additive backward-compatible fields. Breaking changes require a new schema version and migration strategy.

---

## 7. Idempotent event consumption

Every consumer must maintain an inbox or equivalent deduplication record containing:

- Consumer name
- Event ID
- Processing status
- First-seen timestamp
- Completion timestamp
- Attempt count
- Failure information where appropriate

Business updates and inbox completion should commit in the same local transaction.

If the same event is delivered again:

- A completed event returns success without repeating effects.
- An in-progress event is retried or deferred safely.
- A previously failed event follows the retry policy.
- No duplicate notification, state transition or analytics increment may occur.

Deduplication records require a retention period longer than the maximum expected broker replay window.

---

## 8. Ordering and aggregate versions

Global event ordering is not assumed.

Where order matters:

- Events include aggregate versions or device sequence numbers.
- Consumers track the latest applied version.
- Duplicate versions are ignored.
- Older versions do not overwrite newer state.
- A gap may trigger temporary deferral, source lookup or reconciliation.
- Independent aggregates may be processed in parallel.

Charging meter events use session-scoped sequence numbers. Energy totals must be derived without counting duplicate events twice.

Events that cannot safely be applied because predecessors are missing are quarantined or reconciled rather than silently discarded.

---

## 9. Retry policy

Retries apply only to failures likely to be temporary.

### Retriable examples

- Broker unavailable
- Database connection interruption
- Email provider timeout
- Temporary dependent-service failure
- Simulator disconnection
- Transient network error

### Non-retriable examples

- Invalid event schema
- Unauthorized command
- Invalid state transition
- Unknown mandatory reference
- Connector incompatibility
- Expired authorization
- Permanent email-address rejection

### Recommended retry behaviour

- Exponential backoff with jitter
- Maximum delay cap
- Configurable maximum attempts
- Separate policies by operation
- Circuit breaking for repeatedly failing dependencies
- No unbounded immediate retries
- Retry metrics and alerts

Commands with business side effects require an idempotency key before automatic retries are allowed.

---

## 10. Dead-letter handling

A message moves to a dead-letter queue or quarantine state after exhausting retries or encountering an unrecoverable processing problem.

Each dead-letter record includes:

- Original event and metadata
- Consumer
- Failure classification
- Attempt history
- First and latest failure timestamps
- Correlation ID
- Safe diagnostic details

Dead-letter messages must not disappear automatically.

Authorized operators may:

- Inspect sanitized failure details
- Correct configuration or reference data
- Replay safely
- Mark an item resolved
- Escalate a software defect

Replay uses the original event ID so consumer idempotency remains effective. Dead-letter access and replay are audited.

---

## 11. Booking-hold expiration

### Trigger

A recurring worker searches for `HELD` bookings whose `expiresAt` is at or before database time.

### Processing

For each candidate:

1. Claim the booking using a lock, lease or conditional update.
2. Re-read its current state and deadline.
3. If still `HELD` and expired, transition it to `EXPIRED`.
4. Release its capacity.
5. Revoke related temporary authorization.
6. Record audit and outbox entries.
7. Commit atomically.

### Race with confirmation

- If confirmation commits before expiration, expiration does nothing.
- If expiration commits first, confirmation fails.
- Worker query time alone is not authoritative; the state and deadline must be checked inside the transaction.
- Expired holds are non-blocking during authoritative allocation even if cleanup has not run yet.

Delayed expiration jobs cannot extend hold validity.

---

## 12. Check-in deadline and no-show processing

A recurring worker evaluates `CONFIRMED` and eligible `CHECKED_IN` bookings after their snapshotted grace deadline.

### `CONFIRMED` after deadline

The worker must check:

- No check-in committed
- No charging session exists
- No equipment failure prevented fulfilment
- No active reassignment or operational-resolution workflow exists
- No valid support override exists

If all conditions hold, transition the booking to `NO_SHOW` and release capacity.

### `CHECKED_IN` after deadline

If no start command/session exists:

- Revoke the start authorization.
- Determine whether the driver abandoned, the device failed, or the result is uncertain.
- Equipment/platform failure routes to operational resolution or `FULFILMENT_FAILED`.
- Driver inaction may become `NO_SHOW` according to policy.

### Race rule

Check-in, session start and no-show processing must use the same authoritative booking state/version:

- Check-in first: no-show cannot commit.
- No-show first: ordinary check-in fails.
- Session start first: no-show cannot commit.
- Recorded equipment failure first: no-show is prohibited.

No-show notifications are emitted only after commit.

---

## 13. Maintenance activation and completion

### Activation worker

At the scheduled start time:

1. Claim the maintenance record.
2. Confirm it remains `SCHEDULED`.
3. Re-evaluate affected bookings and sessions.
4. Confirm all required reassignment/cancellation actions are resolved.
5. If safe, transition maintenance to `ACTIVE`.
6. Emit events that cause derived availability to become unavailable.

Normal maintenance must not activate over unresolved bookings or active sessions.

If unresolved conflicts remain:

- Maintenance stays pending.
- Operators are alerted.
- The worker retries according to policy.
- The conflict is visible operationally.

Emergency activation requires a privileged, justified action and may initiate session interruption.

### Completion worker

At completion or operator confirmation:

1. Transition maintenance to `COMPLETED`.
2. Remove the maintenance-derived block.
3. Set operational confidence to `UNKNOWN`.
4. Require a fresh heartbeat/status before near-term booking or charging.
5. Emit completion and availability-recalculation events.

Maintenance completion never assumes the EVSE is healthy.

---

## 14. Charger heartbeat and stale-status detection

The device capability records each accepted heartbeat and status update using server-received time.

A recurring detector identifies EVSEs whose last reliable status exceeds the configured freshness threshold.

### Behaviour

- Fresh to stale transition emits `EvseStatusBecameStale`.
- Missing initial status produces `UNKNOWN`.
- A stale EVSE becomes unavailable for near-term booking and check-in.
- Future booking may remain `PLANNED_AVAILABLE` according to the availability model.
- The first fresh accepted event clears stale status and emits a recovery event.
- Repeated detector runs must not repeatedly emit the same transition.

Device timestamps may be stored for diagnostics, but untrusted device clocks do not determine freshness.

---

## 15. Device-command processing

Commands include:

- Reserve or validate assignment
- Start charging
- Stop charging
- Apply simulator operation
- Request current state

Every command includes:

- Globally unique command ID
- Idempotency key
- Target device/EVSE
- Aggregate/booking/session reference
- Expected state or version
- Creation and expiry timestamps
- Correlation and trace identifiers

Command states:

`PENDING → DISPATCHED → ACCEPTED/REJECTED/TIMED_OUT`

`TIMED_OUT` means the outcome is uncertain, not necessarily that the device rejected the command.

The simulator/device must return the prior result for a duplicate command ID rather than repeating the physical action.

---

## 16. Uncertain command reconciliation

Network failure may occur after a device acts but before the platform receives acknowledgement.

### Start-command uncertainty

- Session remains `STARTING`.
- Booking must not revert to `CHECKED_IN` automatically.
- Start authorization cannot be reused.
- EVSE capacity remains blocked.
- Reconciliation requests device/session state.
- Meter or transaction events proving charging began transition the session to `CHARGING`.
- Definite rejection transitions the session to `START_REJECTED`.
- If non-start is proven, the booking follows fulfilment-failure or retry policy.
- If uncertainty cannot be resolved automatically, operator intervention is required.

### Stop-command uncertainty

- Session remains `STOPPING`.
- EVSE capacity remains blocked.
- Reconciliation checks transaction state and later events.
- Confirmed termination finalizes the session.
- Evidence of continuing charging restores or retains the active state and may trigger another idempotent stop command.
- The system must not report successful completion merely because a timeout elapsed.

Reconciliation attempts and final decisions are audited.

---

## 17. Charging-event processing

Charging events may be delayed, duplicated or out of order.

For every session:

- Sequence numbers are validated.
- Duplicate events do not repeat transitions or energy increments.
- Older state events cannot overwrite newer states.
- Missing sequence ranges are recorded.
- Final summaries remain provisional while required events are missing.
- Reconciliation requests missing state or reconstructs from accepted events.
- Implausible meter regressions or jumps are flagged.
- Final cost is calculated from authoritative accepted meter/session data and the immutable tariff snapshot.

An event received after finalization may trigger reconciliation, but cannot silently rewrite the final summary.

---

## 18. Booking reassignment workflow

Reassignment is a coordinated workflow, but allocation correctness remains local to the Booking capability.

1. Operations requests reassignment with a stable workflow ID.
2. Booking evaluates compatible replacement EVSEs.
3. Booking atomically claims the replacement, updates assignment and releases the original.
4. Booking emits `BookingReassigned`.
5. Infrastructure/search projections update.
6. Notification is dispatched.
7. Operations records the workflow as completed.

If allocation fails, the original assignment remains unchanged.

Repeated workflow requests return the existing outcome. Notification or analytics failure does not reverse reassignment.

---

## 19. Notification dispatch

Business capabilities emit notification-request facts only after authoritative changes commit.

The notification capability:

1. Consumes the event idempotently.
2. Applies notification rules and preferences.
3. Renders a versioned template.
4. Creates one delivery record per recipient/channel.
5. Sends through the provider.
6. Records provider acceptance, rejection or failure.
7. Retries transient failures.
8. Dead-letters permanent or exhausted failures.

A booking is confirmed even if email cannot be delivered.

Security-sensitive email, such as verification and recovery, may be initiated through the identity provider but must follow equivalent expiration, rate-limit and audit rules.

Notification events must minimize personal data. Consumers retrieve current contact data through an authorized boundary where practical.

---

## 20. Analytics projections

Analytics consumes domain events into purpose-built read models.

Rules:

- Analytics never authorizes bookings or operational actions.
- Duplicate events do not double-count metrics.
- Aggregate versions and event time are retained.
- Late events update affected reporting periods.
- Projection rebuild from retained authoritative events or source data must be possible.
- Reports expose their data freshness.
- Personal information is excluded unless essential and authorized.
- Deletion/anonymization events update affected projections.

Analytics failure cannot block bookings, sessions or station operations.

---

## 21. Search and availability projections

Search projections combine infrastructure, maintenance, allocation and device events.

- Projection updates are asynchronous.
- Each record stores source versions and last-updated time.
- Older events cannot overwrite newer projections.
- Missing source data results in `UNKNOWN`, not confident availability.
- A projection rebuild must be possible.
- Authoritative booking operations never trust the projection as their final allocation check.

Projection lag and rebuild status must be observable.

---

## 22. Privacy export workflow

Privacy export is a long-running workflow:

1. Validate the requester and recent authentication.
2. Create an export request with an expiry.
3. Request data from every relevant authoritative owner.
4. Track each contribution independently.
5. Retry temporary failures.
6. Assemble the export in secure object storage.
7. Notify the requester.
8. Provide a short-lived authenticated download.
9. Delete the export after expiry.

The export status may be:

`REQUESTED → COLLECTING → READY → DOWNLOADED/EXPIRED`

Alternative state: `FAILED_REQUIRES_REVIEW`.

Exports must not include secrets, internal security data or another person’s information.

---

## 23. Account deletion and anonymization workflow

Deletion is coordinated because no distributed transaction can erase all data atomically.

Proposed states:

`REQUESTED → VALIDATING → BLOCKED/APPROVED → PROCESSING → COMPLETED`

### Validation

Deletion is blocked while the driver has:

- Active or upcoming bookings
- Active or uncertain charging sessions
- Unresolved support/security cases requiring retention
- Another legal or operational retention requirement

### Processing

After approval:

1. Revoke identity sessions.
2. Disable authentication.
3. Prevent new bookings.
4. Delete or anonymize profile and vehicle data.
5. Anonymize booking/session personal references where retention is required.
6. Update notifications, support and analytics projections.
7. Record privacy-safe completion evidence.
8. Delete the identity account when required steps succeed.

Each participant handles the same deletion workflow ID idempotently.

Partial completion remains visible and retries continue. The platform must not claim completion until all mandatory participants acknowledge completion or an authorized exception is recorded.

---

## 24. Retention jobs

Scheduled retention jobs remove or anonymize eligible data based on its classification and approved retention policy.

Rules:

- Retention deadlines are computed from authoritative timestamps.
- Active legal/security holds override ordinary deletion.
- Holds require scope, reason, approver and expiry/review date.
- Jobs process bounded batches.
- Repeated execution is safe.
- Deletion evidence contains identifiers and outcome, not deleted personal content.
- Backup expiry follows a documented schedule; individual records may remain in protected backups until backup expiration.
- Restored backups must reapply completed deletion/anonymization records.

Exact retention periods remain provisional until the privacy specification is approved.

---

## 25. Scheduler and worker safety

Background workers must support horizontal scaling.

Required controls:

- Database row claims, leases or distributed locks
- Short, renewable lease durations
- Recovery after worker crash
- Bounded batch sizes
- Per-item transaction isolation
- Backpressure
- Graceful shutdown
- Idempotent handlers
- Explicit job run identifiers
- Metrics for scanned, claimed, succeeded, skipped and failed items

A global singleton scheduler should not be required for correctness.

Jobs use database/server time rather than local machine time. Clock synchronization remains operationally required.

---

## 26. Cross-capability workflow pattern

Use event-driven choreography for simple propagation, such as:

- Booking confirmed → notification and analytics update
- Status changed → search projection update

Use an explicit workflow coordinator where:

- Several mandatory participants must complete
- Progress must be queried
- Compensating action is required
- Timeouts and manual intervention must be managed

Likely coordinated workflows:

- Privacy deletion/export
- Maintenance conflict resolution
- Booking fulfilment failure
- Complex emergency intervention

Compensation is a new business action, not a database rollback. Historical facts remain recorded.

---

## 27. Dependency failure rules

| Failure | Required behaviour |
|---|---|
| Broker unavailable | Commit local authoritative change and retain outbox |
| Search unavailable | Booking management remains usable |
| Analytics unavailable | Core operations continue |
| Email unavailable | Booking remains committed; delivery retries |
| Simulator unavailable | New starts may be blocked; uncertain sessions reconcile |
| Identity provider unavailable | Existing token behaviour follows security policy; no insecure bypass |
| Infrastructure projection stale | Booking retrieves/revalidates authoritative required data |
| Booking capability unavailable | No other capability may allocate EVSE capacity |
| Audit aggregation unavailable | Local required audit/outbox evidence remains durable |
| Privacy participant unavailable | Workflow remains incomplete and retries |

---

## 28. Observability

Required metrics include:

- Oldest unpublished outbox record
- Outbox publication failures
- Consumer lag
- Inbox duplicate count
- Retry attempts
- Dead-letter count and age
- Hold-expiration delay
- No-show-processing delay
- Maintenance activation conflicts
- Stale EVSE count
- Commands pending or uncertain
- Reconciliation age and failures
- Notification backlog
- Search/analytics projection lag
- Privacy workflow age
- Retention-job failures

Logs use correlation, causation, event, command and workflow IDs. Logs exclude tokens, passwords and unnecessary personal information.

Alerts are required for:

- Confirmed-booking event backlog
- Unresolved command uncertainty
- Repeated allocation workflow failure
- Dead-letter growth
- Privacy workflows exceeding target time
- Maintenance activating unsafely
- Projection lag exceeding defined thresholds

---

## 29. Recovery and replay

After service, broker or database recovery:

1. Unpublished outbox records resume publication.
2. Consumers safely replay duplicate events.
3. Expired holds are recognized immediately during allocation.
4. Overdue scheduled transitions are processed.
5. Device state becomes `UNKNOWN` until fresh evidence arrives.
6. Uncertain commands enter reconciliation.
7. Projections catch up or rebuild.
8. Privacy and notification workflows resume from persisted state.

A replay must not:

- Create duplicate bookings
- Send uncontrolled duplicate notifications
- Inflate meter values
- Repeat privileged interventions
- Reopen terminal entities
- Reverse newer aggregate versions

---

## 30. Durability definition

For the NFR “No acknowledged booking may be lost”:

- A `HELD` booking is acknowledged as a temporary hold only after its transaction commits.
- A booking is acknowledged as confirmed only after the `CONFIRMED` state, snapshots and outbox record commit.
- Email or broker delivery is not required before confirmation is returned.
- If the transaction outcome is unknown to the client, retrying with the same idempotency key returns the committed result.
- No success response may be returned before durable commit.

---

## 31. Security requirements

- Machine-to-machine calls require authenticated service identity.
- Authorization applies to internal APIs, not only public endpoints.
- Broker permissions restrict producers and consumers by topic/queue.
- Events and dead-letter records follow least-privilege access.
- Sensitive commands have short expiry periods.
- Replay and manual reconciliation require scoped privileged roles.
- Break-glass actions require MFA, reason, expiry, alert and review.
- Event payloads and logs must not contain credentials or reusable start authorizations.
- Transport encryption is required.
- Event schema validation occurs before processing.

---

## 32. Acceptance criteria

1. Broker failure cannot lose a committed booking event.
2. Duplicate event delivery produces one business effect.
3. Out-of-order events cannot overwrite newer state.
4. Expired holds stop blocking capacity even if cleanup is delayed.
5. Concurrent confirmation and hold expiration produce one valid outcome.
6. Concurrent check-in/session start and no-show processing produce one valid outcome.
7. Maintenance cannot normally activate over unresolved bookings or sessions.
8. Maintenance completion does not falsely mark an EVSE available.
9. Stale detection emits one transition per freshness change.
10. Duplicate device commands do not repeat physical actions.
11. Timed-out start/stop commands remain uncertain until reconciled.
12. Meter-event replay does not inflate energy or estimated cost.
13. Notification failure does not reverse a committed booking.
14. Analytics and search can rebuild without becoming authoritative.
15. Reassignment failure preserves the original allocation.
16. Privacy workflows survive participant and coordinator restarts.
17. Dead-letter replay remains idempotent.
18. Worker crashes do not permanently claim scheduled work.
19. Restoring a backup causes completed privacy actions to be reapplied.
20. Every workflow can be traced using correlation and causation IDs.

---

## 33. Required test categories

- Transactional outbox failure tests
- Broker outage and recovery tests
- Duplicate and out-of-order event tests
- Consumer crash-before/after-commit tests
- Hold expiration/confirmation race tests
- Check-in/no-show/session-start race tests
- Maintenance activation conflict tests
- Stale-heartbeat transition tests
- Device-command timeout and reconciliation tests
- Meter sequence-gap and replay tests
- Dead-letter and controlled-replay tests
- Projection rebuild tests
- Notification-provider outage tests
- Privacy workflow partial-failure tests
- Retention and restored-backup tests
- Multi-worker lease/crash tests
- Dependency-isolation tests
- Security and broker-permission tests

---

## 34. Proposed decisions for approval

1. Use transactional outbox and idempotent inbox patterns.
2. Guarantee at-least-once delivery rather than claim exactly-once delivery.
3. Prohibit cross-capability database transactions and direct database writes.
4. Use database time for business deadlines.
5. Treat device-command timeout as an uncertain outcome.
6. Keep uncertain EVSE/session capacity blocked until reconciliation.
7. Use choreography for simple projections and explicit coordination for privacy and complex operational workflows.
8. Make expired holds non-blocking independently of cleanup-job timing.
9. Prevent normal maintenance activation while affected bookings or sessions remain unresolved.
10. Set post-maintenance device status to `UNKNOWN` until fresh evidence arrives.
11. Make notification, search and analytics failure non-blocking to committed core operations.
12. Require event aggregate versions or device sequence numbers wherever ordering matters.
13. Retain inbox deduplication records beyond the supported replay window.
14. Define confirmed-booking acknowledgement as successful durable database commit, not email or broker delivery.
15. Require safe replay and rebuild procedures for every asynchronous projection.

---

## 35. Traceability

Primarily implements:

- `FR-PLT-01`
- `FR-BKG-02`
- `FR-BKG-04`
- `FR-BKG-05`
- `FR-AVL-01`
- `FR-CHG-01`
- `FR-CHG-02`
- `FR-OPS-02`
- `FR-NOT-01`
- `FR-PRV-01`
- `FR-AUD-01`

Supports the reliability, recovery, durability, observability, maintainability and security NFRs.
