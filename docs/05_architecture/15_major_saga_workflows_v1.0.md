Document ID: ARC-021
Title: Major Saga Workflows v1.0
Version: 1.0
Status: IN_REVIEW
Owner: Architecture Lead
Last reviewed: 2026-07-12
Depends on: ARC-001–017
Authoritative for: Major Saga Workflows
Refines: ARC-001, ARC-002, DOM-002, DOM-006
Does not supersede: Service topology and data ownership in ARC-001
Release applicability: W1 | W2 | W3 | Cross-cutting

---



# Major Saga Workflows v1.0

**Status:** Draft (In Review)

## 1. Saga policy

- Use orchestration when a workflow has a clear business owner and compensations.
- Use choreography for independent reactions such as notifications, analytics, and fault projections.
- Do not introduce a generic Saga Service initially.
- The orchestrating domain stores saga state, deadline, current step, attempts, and last failure.
- Saga states: `RUNNING`, `WAITING`, `COMPENSATING`, `COMPLETED`, `FAILED_REQUIRES_ACTION`, `CANCELLED`.
- Compensation records a new business action; it does not erase historical facts.

## 2. Reservation hold and confirmation

**Owner:** Booking module (Booking and Session Service)  
**Type:** Primarily one local transaction, not a distributed saga.

1. Resolve reservation context from Network.
2. For near-term requests, obtain fresh Device Integration Service status.
3. Select compatible EVSE candidates.
4. In one Booking transaction:
   - Check capacity restrictions.
   - Create allocation and hold.
   - Store tariff/policy/configuration snapshots.
   - Store idempotency result and outbox event.
5. Confirm within the hold deadline.
6. Emit `BookingConfirmed`.
7. Optionally mirror the reservation to the charger.

The allocation table uses range/exclusion protection so overlapping EVSE allocations cannot both commit. PostgreSQL supports non-overlap enforcement using range types and exclusion constraints. ([postgresql.org](https://www.postgresql.org/docs/current/ddl-constraints.html?utm_source=openai))

Failures before the local commit create no booking. Hold expiration is the compensation after a successful hold.

A device-side reservation rejection does not silently undo a confirmed platform booking. It creates an operational-risk flag and starts reassignment/reconciliation.

## 3. Rescheduling and reassignment

**Owner:** Booking module (Booking and Session Service)

### Driver rescheduling

- Resolve updated Network context.
- Lock booking/version.
- Atomically change interval/EVSE and snapshots.
- Release old allocation within the same transaction.
- On conflict, roll back completely and preserve the original booking.

### Fault-driven reassignment

- Freeze the affected EVSE for new bookings.
- Find compatible candidates.
- Automatically reassign only under approved equivalence rules.
- Otherwise request driver approval.
- Update assignment atomically.
- Send cancellation/reservation commands to the relevant simulated chargers.
- Command failures create reconciliation tasks, not duplicate assignments.

## 4. Maintenance and closure workflow

**Owner:** Station Operations Service, with Booking owning capacity.

This introduces a necessary **capacity-freeze phase**.

1. Network creates a planning workflow.
2. Network asks Booking to create a `FREEZE` restriction.
3. Booking atomically:
   - Blocks new holds, confirmations, and rescheduling in the interval.
   - Returns existing affected bookings/sessions.
4. Reassign or cancel affected bookings.
5. Stop or resolve active sessions when required.
6. Once no conflicts remain, Booking promotes the freeze to a hard block.
7. Network changes maintenance to `SCHEDULED`.
8. At start:
   - Maintenance becomes `ACTIVE`.
   - Device availability command is sent.
   - Query projections update.
9. At completion:
   - Maintenance becomes `COMPLETED`.
   - Capacity block is released.
   - EVSE becomes `UNKNOWN` until a fresh device report.

If planning is abandoned, the freeze is released. Emergency maintenance may activate before normal resolution only with an emergency reason, active-session handling, notification, and audit.

This closes a race where a new booking could otherwise appear between maintenance impact review and activation.

## 5. Check-in and start authorization

**Owner:** Booking module (Booking and Session Service)

1. Verify driver, booking, time window, EVSE identifier, status freshness, and compatibility.
2. Reassign if the assigned EVSE has failed and an eligible replacement exists.
3. Change booking to `CHECKED_IN`.
4. Create a short-lived, single-use authorization.
5. Store only a hash of the opaque token.
6. Expire unused authorizations automatically.

Check-in itself does not start charging.

## 6. Start charging

**Owner:** Charging module (Booking and Session Service)

Add internal technical state `AUTHORIZING`; public states remain unchanged.

1. Charging creates an idempotent session shell in `AUTHORIZING`.
2. Charging asks Booking to consume the start authorization, bound to that session ID.
3. Booking marks a `startPending` flag and returns the immutable start context.
4. Charging changes the session to `STARTING` and publishes `StartChargingAtEVSE`.
5. Device Integration Service sends the command.
6. Command acceptance means only that the charger accepted the instruction.
7. `DeviceTransactionStarted` proves charging began.
8. Charging changes to `CHARGING` and emits `ChargingSessionStarted`.
9. Booking consumes the event and becomes `ACTIVE`.

### Recovery

- Crash before authorization consumption: persisted session worker retries.
- Crash after consumption: the persisted `AUTHORIZING` session resumes.
- Definitive rejection: `START_REJECTED`; Booking clears `startPending`.
- Timeout: remain uncertain and request a device snapshot.
- A retryable rejection may permit another attempt within the booking deadline.
- No new session or authorization is created while an existing start is uncertain.

## 7. Stop and completion

**Owner:** Charging module (Booking and Session Service)

1. Validate driver/operator authority.
2. Change session to `STOPPING`.
3. Publish `StopChargingAtEVSE`.
4. Await `DeviceTransactionEnded`.
5. Reconcile final meter values.
6. Use internal `FINALIZING` state if meter data or device outcome remains incomplete.
7. Calculate final estimated energy/cost with a quality indicator.
8. Change to `COMPLETED` or `INTERRUPTED`.
9. Emit the final session event.
10. Booking becomes `COMPLETED` and releases capacity after the turnaround buffer.

If the charger continues past the reserved end, the allocation remains blocked, an overrun alert is raised, and the next affected booking enters reassignment handling.

## 8. Device fault workflow

**Owners:** Network for fault; Charging for active session; Booking for reservations.

1. Device Integration Service emits a fault/status event.
2. Network opens or updates the fault incident.
3. Critical faults block new near-term bookings.
4. Booking identifies affected reservations.
5. Charging interrupts or attempts to stop active sessions.
6. Booking reassigns future/check-in bookings where possible.
7. Unresolved bookings become `FULFILMENT_FAILED` or operator-cancelled.
8. Repair completion does not imply availability; a fresh device status is required.

This is choreography with an incident tracker, not one distributed transaction.

## 9. Organization suspension or station closure

**Owner:** Governance or Network, depending on cause.

1. Record suspension/closure with reason.
2. Create a Booking capacity freeze at organization/station scope.
3. Block new reservations immediately.
4. Resolve existing bookings according to effective date.
5. Send device availability commands.
6. Update public projections.
7. Restrict operator actions based on organization status.

Operator users are not globally disabled if they belong to another active organization.

## 10. Operator invitation and role synchronization

**Owner:** Station Operations Service

1. Owner/manager creates an expiring invitation.
2. Notification sends the invite.
3. Invitee signs in or registers through Keycloak.
4. Network validates and consumes the invitation.
5. Membership is created.
6. Identity adapter grants the broad operator role when required.
7. If identity synchronization fails, membership remains `PENDING_IDENTITY_SYNC` and cannot be used.
8. A reconciliation worker completes or reverses the pending membership.

Removing the final operator membership removes the broad operator role after reconciliation.

## 11. Privacy export and deletion

**Owner:** Governance Service

### Export

1. Verify identity and recent authentication.
2. Create privacy request.
3. Send `CollectSubjectData` to every relevant service.
4. Services produce encrypted export fragments/manifests.
5. Governance assembles the package in protected object storage.
6. User receives a short-lived download notification.
7. Temporary artifacts expire automatically.

### Deletion

1. Reject or defer while an active booking/session exists.
2. Revoke authentication sessions.
3. Restrict the account during processing.
4. Send idempotent anonymization/deletion commands.
5. Each service reports completion.
6. Legally retained records are restricted and pseudonymized.
7. Delete or disable the Keycloak identity after operational dependencies are resolved.
8. Mark the privacy request completed.

Partial failure remains visible and retryable; the system never reports full deletion while a service is incomplete.

## 12. Charger-command reconciliation

**Owner:** Service that issued the command.

1. Command reaches its deadline without a definitive result.
2. Domain state becomes `RECONCILIATION_REQUIRED`.
3. Request a current device snapshot.
4. Compare device transaction ID, EVSE state, sequence, and meter state.
5. Apply the observed result idempotently.
6. If still unresolved, retry within policy or create an operator incident.
7. Never infer success from timeout alone.

## 13. Non-saga asynchronous workflows

The following are simple event reactions:

- Transactional email
- Search projection updates
- Analytics
- Central audit indexing
- Status dashboards
- Reminder scheduling

Their failure never compensates or rolls back the original business action.
