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
**Release applicability:** W1

1. Optionally resolve advisory preflight reservation context from Station Operations Service (outside transaction locks). (Release applicability: W1)
2. Optionally obtain fresh Device Integration Service status for near-term requests (outside transaction locks). (Release applicability: W1)
3. Select compatible EVSE candidates using Booking-local projections. (Release applicability: W1)
4. In one Booking transaction (holding no remote synchronous locks):
   - Check capacity restrictions using local projections (fail closed if stale/incomplete).
   - Create allocation and exclusive hold (`BOOKING_HOLD` claim).
   - Store tariff/policy/configuration snapshots.
   - Store idempotency result and outbox event. (Release applicability: W1)
5. Confirm within the 5-minute hold deadline. (Release applicability: W1)
6. Emit `BookingConfirmed` event (triggers mandatory confirmation email). (Release applicability: W1)
7. Optionally mirror the reservation to the charger (optional W2 feature; mirror failure does not rollback the platform booking). (Release applicability: W2)

The allocation table uses range/exclusion protection so overlapping EVSE allocations cannot both commit. PostgreSQL supports non-overlap enforcement using range types and exclusion constraints. ([postgresql.org](https://www.postgresql.org/docs/current/ddl-constraints.html))

Lower-level validation checks are performed locally inside the Booking db.

A device-side reservation rejection does not silently undo a confirmed platform booking. It creates an operational-risk flag and starts reassignment/reconciliation.

## 3. Rescheduling and reassignment

**Owner:** Booking module (Booking and Session Service)
**Release applicability:** W1

### Driver rescheduling
**Release applicability:** W1

- Optionally resolve updated Station Operations Service context (advisory preflight check only).
- Lock booking/version.
- Atomically check compatibility and capacity using Booking-local projections.
- Change interval/EVSE and snapshots.
- Release old allocation within the same transaction.
- On conflict, roll back completely and preserve the original booking.

### Fault-driven reassignment
**Release applicability:** W1

- Freeze the affected EVSE for new bookings.
- Find compatible candidates using Booking-local projections.
- Automatically reassign only under approved equivalence rules.
- Otherwise request driver approval.
- Update assignment atomically.
- Send cancellation/reservation commands to the relevant simulated chargers via Device Integration Service commands (asynchronous outbox).
- Command failures create reconciliation tasks, not duplicate assignments.

## 4. Maintenance and closure workflow

**Owner:** Station Operations Service owns the maintenance planning record lifecycle. Booking module (Booking and Session Service) owns the capacity-restriction lifecycle.
**Release applicability:** W1

This introduces a necessary **capacity-freeze phase**.

1. Station Operations Service creates a maintenance planning record in `DRAFT` or `PROPOSED` (Release applicability: W1).
2. Station Operations Service asks the Booking module to create a `FREEZE` capacity restriction (Release applicability: W1).
3. The Booking module commits the `FREEZE` restriction (Release applicability: W1) to:
   - Block new holds, confirmations, and rescheduling in the interval.
   - Return existing affected bookings/sessions.
4. The Booking module coordinates reassignment or cancellation of affected bookings (Release applicability: W1).
5. Stop or resolve active sessions when required (Release applicability: W1).
6. Once no conflicts remain, Booking module commits the restriction to `BLOCKED` (hard block) (Release applicability: W1).
7. Station Operations Service changes the maintenance planning record to `SCHEDULED` (using the Booking restriction commitment acknowledgement as transition evidence; Station Operations Service retains overall ownership of the maintenance plan) (Release applicability: W1).
8. At start of the maintenance window:
   - Maintenance becomes `ACTIVE` (Station Operations Service). (Release applicability: W1)
   - Device availability command is sent (asynchronous command via Device Integration Service). (Release applicability: W1)
   - Query projections in Discovery and Insights Service update. (Release applicability: W1)
9. At completion:
   - Maintenance becomes `COMPLETED` (Station Operations Service). (Release applicability: W1)
   - Capacity block is released in the Booking module (`RELEASED` state). (Release applicability: W1)
   - EVSE becomes `UNKNOWN` in local projections until a fresh device report is received. (Release applicability: W1)

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
**Release applicability:** W1

- Crash before authorization consumption: persisted session worker retries.
- Crash after consumption: the persisted `AUTHORIZING` session resumes.
- Uncertain result: no retry while unresolved. Remains in `AUTHORIZING` / `startPending` and requests a device snapshot to reconcile. (Release applicability: W1)
- Definitive rejection: the original start attempt is terminal. Booking clears `startPending` and the session shell transitions to failed. (Release applicability: W1)
- Retry: permitted only within the booking deadline after a definitive rejection or retryable failure is resolved. A retry requires a new attempt number, a newly issued start authorization bound to the same booking, and a new session shell. Every attempt remains fully auditable. (Release applicability: W1)

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

If the charger continues past the reserved end, the operational-occupation claim tracks physical overrun, an overrun alert is raised, and the next affected booking enters reassignment handling.

## 8. Device fault workflow

**Owners:** Station Operations Service for fault; Charging module (Booking and Session Service) for active session; Booking module (Booking and Session Service) for reservations.
**Release applicability:** W1

1. Device Integration Service emits a fault/status event. (Release applicability: W1)
2. Station Operations Service opens or updates the fault incident. (Release applicability: W1)
3. Critical faults block new near-term bookings based on the 60-minute horizon. (Release applicability: W1)
4. Booking module identifies affected reservations. (Release applicability: W1)
5. Charging module interrupts or attempts to stop active sessions. (Release applicability: W1)
6. Booking module reassigns future/check-in bookings where possible. (Release applicability: W1)
7. Unresolved bookings become `FULFILMENT_FAILED` or operator-cancelled. (Release applicability: W1)
8. Repair completion does not imply availability; a fresh device status is required. (Release applicability: W1)

This is choreography with an incident tracker, not one distributed transaction.

## 9. Organization suspension or station closure

**Owner:** Platform Governance and Support Service or Station Operations Service, depending on cause.
**Release applicability:** W1

1. Record suspension/closure with reason. (Release applicability: W1)
2. Create a Booking capacity freeze at organization/station scope. (Release applicability: W1)
3. Block new reservations immediately. (Release applicability: W1)
4. Resolve existing bookings according to effective date. (Release applicability: W1)
5. Send device availability commands via Device Integration Service. (Release applicability: W1)
6. Update public projections in Discovery and Insights Service. (Release applicability: W1)
7. Restrict operator actions based on organization status. (Release applicability: W1)

Operator users are not globally disabled if they belong to another active organization.

## 10. Operator invitation and role synchronization

**Owner:** Station Operations Service
**Release applicability:** W1

1. Owner/manager creates an expiring invitation. (Release applicability: W1)
2. Notification Service sends the invite. (Release applicability: W1)
3. Invitee signs in or registers through Keycloak. (Release applicability: W1)
4. Station Operations Service validates and consumes the invitation. (Release applicability: W1)
5. Membership is created. (Release applicability: W1)
6. Identity adapter grants the broad operator role when required. (Release applicability: W1)
7. If identity synchronization fails, membership remains `PENDING_IDENTITY_SYNC` and cannot be used. (Release applicability: W1)
8. A reconciliation worker completes or reverses the pending membership. (Release applicability: W1)

Removing the final operator membership removes the broad operator role after reconciliation.

## 11. Privacy export and deletion

**Owner:** Account Service coordinates subject-facing requests and orchestrates the deletion/export sagas. Each service owns its local data deletion/export action. Platform Governance and Support Service owns oversight, escalation, and audit review but does not execute the deletion workflow.
**Release applicability:** W3

### Export
**Release applicability:** W3

1. Verify identity and recent authentication.
2. Create privacy request record in Account Service.
3. Send `CollectSubjectData` to every relevant service.
4. Services produce encrypted export fragments/manifests.
5. Account Service assembles the package in protected object storage.
6. User receives a short-lived download notification via Notification Service.
7. Temporary artifacts expire automatically.

### Deletion
**Release applicability:** W3

1. Reject or defer while an active booking/session exists.
2. Revoke authentication sessions.
3. Restrict the account during processing.
4. Send idempotent anonymization/deletion commands.
5. Each service reports completion (no service directly modifies another service's data; each service acts as local tombstone owner).
6. Legally retained records are restricted and pseudonymized.
7. Delete or disable the Keycloak identity after operational dependencies are resolved.
8. Mark the privacy request completed in Account Service.

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
