Document ID: UC-DR-007
Title: DR-16 - Check-In Authorization v1.0
Version: 1.0
Status: APPROVED
Owner: DA/BA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: UC-DR-004
Authoritative for: Late Grace Limits and Start Authorization Token

---

# DR-16 — Check-In and Arrival Authorization v1.0 — Draft

## 1. Purpose

Allow a driver with a confirmed booking to prove arrival at the assigned EVSE and obtain authorization to start one simulated charging session.

Check-in does **not** start charging. It changes the booking to `CHECKED_IN` and creates a short-lived, single-use start authorization.

## 2. Actors

- Primary: Driver
- Supporting: Booking capability, station/EVSE capability, charger simulator, audit and notification capabilities
- Exceptional: Operator support or platform support

## 3. Preconditions

- Driver account is authenticated, verified, active and not suspended.
- Booking belongs to the driver and is `CONFIRMED`.
- Current time is inside the check-in window.
- Booking has an assigned EVSE.
- Driver is physically presented with the EVSE identifier, normally through its QR code.
- No charging session already exists for the booking.
- Driver has no other active session.

Default window:

- Opens 15 minutes before the scheduled start.
- Closes 15 minutes after the scheduled start.
- A station policy snapshot stored with the booking determines the actual window.

## 4. QR and identifier design

The EVSE QR code contains only a public platform URL and public EVSE identifier. It MUST NOT contain credentials, internal database IDs, reusable authorization tokens or personal information.

Manual identifier entry is available when scanning is unavailable. QR and manual entry follow the same server-side validation.

Possession of an EVSE identifier alone never authorizes charging.

## 5. Primary flow

1. Driver opens the confirmed booking and selects **Check in**.
2. Driver scans the EVSE QR code or enters its public identifier.
3. Client submits the booking reference and EVSE identifier with an idempotency key.
4. Server authenticates the driver and confirms booking ownership.
5. Server locks or version-checks the booking and validates:
   - booking state and check-in window;
   - assigned EVSE identity;
   - connector compatibility;
   - station accessibility;
   - absence of maintenance or blocking faults;
   - fresh EVSE connectivity and operational status;
   - absence of another session or consumed authorization.
6. In one transaction, the system:
   - changes the booking from `CONFIRMED` to `CHECKED_IN`;
   - records the check-in timestamp and method;
   - creates a single-use start authorization;
   - records an audit entry;
   - writes required outbox events.
7. Driver receives confirmation and the earliest permitted charging-start time.
8. The start authorization is later consumed by DR-17 when the simulator accepts the start command.

## 6. Start-authorization rules

The authorization is:

- Opaque and cryptographically random.
- Stored hashed if represented by a token.
- Bound to one booking, driver, EVSE and intended session.
- Single-use and non-transferable.
- Invalid on a different EVSE or by a different driver.
- Revoked by cancellation, account suspension, reassignment, abandonment or expiry.
- Valid no later than the booking’s grace-period deadline.

The browser should not need to send the authorization directly to the simulator. The backend validates it and issues the simulator command through the trusted device boundary.

## 7. Charging-start timing

Check-in may occur before the reserved start, but it does not permit early energy transfer in v1. Charging may start from the scheduled booking time until the grace-period deadline.

An early-start policy can be added later, but would require checking preceding allocations and buffers atomically.

## 8. Alternative and failure flows

### Too early

Reject check-in and return the exact opening time. The booking remains `CONFIRMED`.

### Grace deadline passed

Reject normal check-in. The no-show process determines the final outcome. A support override requires a reason and is permitted only if capacity is still safely available.

### Wrong EVSE

Reject without revealing another driver’s booking information. Display the assigned EVSE and directions where appropriate.

If the assigned EVSE failed, the reassignment workflow may find a compatible replacement. Reassignment must commit before check-in is retried.

### EVSE faulted, offline, stale or maintained

Do not check in the driver as successful. Attempt eligible same-station reassignment. If no replacement exists, flag the booking for operational resolution; equipment failure must not result in `NO_SHOW`.

### Duplicate request

Return the existing successful result. Do not issue another authorization.

### Concurrent check-in requests

Exactly one state transition and one active authorization may be committed. Other requests return the same result or a version conflict.

### Existing session or consumed authorization

Reject creation of another authorization and return the current session state where the requester is authorized to see it.

### Simulator unavailable

Check-in may succeed only when EVSE status is sufficiently fresh and no blocking condition exists. Simulator unavailability after check-in leaves the booking `CHECKED_IN`; reconciliation determines whether starting remains possible.

## 9. Abandon check-in

Before charging starts, a driver may abandon check-in.

- Start authorization is revoked atomically.
- Booking returns from `CHECKED_IN` to `CONFIRMED` if its check-in window remains open.
- The driver may then retry check-in or cancel the booking.
- If the grace deadline has passed, the system resolves the booking instead of restoring it to `CONFIRMED`.
- The reversal is audited and does not extend the original booking window.

No abandonment is allowed after a session reaches `STARTING` or later.

## 10. No-show race protection

Check-in and no-show processing use the same authoritative booking record and concurrency control.

- If check-in commits first, no-show processing must not mark the booking `NO_SHOW`.
- If no-show commits first after the deadline, ordinary check-in fails.
- If equipment failure was recorded before the deadline, the booking is routed to reassignment or `FULFILMENT_FAILED`, not `NO_SHOW`.
- Jobs must use database time rather than worker-local time.

## 11. Security and privacy

- Authorization is checked server-side on every request.
- Rate limits apply per account, booking, EVSE and source address.
- Repeated scanning of unrelated EVSEs is treated as suspicious activity.
- Responses must not disclose whether another driver has booked an EVSE.
- QR codes contain no secrets.
- Logs exclude tokens and unnecessary location or personal data.
- Support overrides require MFA, scoped permission, justification and audit.

## 12. Stored information

- Booking ID and EVSE assignment
- Check-in timestamp in UTC
- Check-in method: `QR`, `MANUAL_IDENTIFIER` or `SUPPORT_OVERRIDE`
- Authorization ID, status, issuance time, expiry and consumption time
- Policy snapshot used for the time-window decision
- Actor and correlation ID
- Failure/rejection reason where applicable

## 13. Events

Emitted after transaction commit through the outbox:

- `DriverCheckedIn`
- `StartAuthorizationIssued`
- `CheckInAbandoned`
- `StartAuthorizationRevoked`
- `CheckInRejected` only where operationally useful and privacy-safe
- `BookingRequiresReassignment`
- `BookingFulfilmentAtRisk`

Events must be versioned and contain no reusable credentials.

## 14. Acceptance criteria

1. A booking owner can check in during the valid window at the assigned EVSE.
2. A different user cannot check in or inspect the booking.
3. Early and late requests are rejected with safe, specific reasons.
4. A wrong EVSE identifier cannot authorize charging.
5. Repeated requests create only one check-in and one active authorization.
6. Authorization cannot be reused or transferred.
7. Faulted, maintained, stale or incompatible EVSEs cannot produce successful normal check-in.
8. Equipment failure cannot incorrectly produce `NO_SHOW`.
9. Concurrent no-show and check-in operations produce one valid final state.
10. Cancellation, reassignment, suspension and abandonment revoke authorization.
11. Check-in never starts charging by itself.
12. Audit and outbox records are committed atomically with the state change.
13. Simulator or notification failure cannot create duplicate authorization.
14. QR codes and logs contain no secrets.

## 15. Approved dependencies

- Booking Lifecycle v1.0
- DR-11/12 Reservation
- DR-14/15 Reschedule and Cancel
- DR-17–20 Charging Session Lifecycle
- Maintenance, Fault and Reassignment Workflows v1.0
- Operator and Platform Permission Models

## 16. Decisions proposed for approval

1. Check-in opens 15 minutes before start and closes after the 15-minute grace period.
2. Charging cannot begin before the reserved start in v1.
3. A successful check-in creates one single-use start authorization.
4. The authorization expires at the grace-period deadline.
5. `CHECKED_IN → CONFIRMED` is allowed only through audited abandonment before session start and while the check-in window remains valid.
6. Equipment-related check-in failure triggers reassignment or fulfilment-failure handling, never no-show classification.
7. QR codes identify EVSEs but contain no authorization secrets.

## 17. Traceability

- Implements `FR-BKG-05`.
- Supports `FR-BKG-02`, `FR-CHG-01`, `FR-AUD-01` and `FR-PLT-01`.
- Primary verification: state-transition, authorization, concurrency, security, reassignment and reconciliation tests.