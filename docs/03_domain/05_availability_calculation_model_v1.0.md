Document ID: DOM-005
Title: Availability Calculation Model v1.0
Version: 1.0
Status: APPROVED
Owner: DA/BA
Last reviewed: 2026-07-12
Supersedes: None
Depends on: DOM-001, DOM-003
Authoritative for: Authoritative Check Algorithm and Interval Availability

---

# Availability Calculation Model v1.0

## 1. Purpose

Define one consistent meaning of EVSE availability for discovery, booking, rescheduling, maintenance, check-in, charging and reassignment.

Availability is calculated for a specific EVSE, requested interval and compatibility requirement. Station availability is an aggregation of eligible EVSE results.

## 2. Authority rule

- Search and station-detail availability is advisory and may use an eventually consistent read model.
- The Booking capability performs the authoritative calculation inside the allocation transaction.
- Every booking, rescheduling, reassignment and check-in operation revalidates relevant conditions.
- A positive search result never guarantees allocation.
- Only a committed allocation decision reserves capacity.

## 3. Availability dimensions

Availability MUST NOT be represented by one overloaded EVSE status. The result contains separate dimensions:

1. **Administrative eligibility** — whether the operator, station and EVSE are enabled for reservations.
2. **Compatibility** — connector type and required charging power.
3. **Schedule eligibility** — opening hours, access rules, policies and maintenance.
4. **Capacity availability** — conflicting bookings, holds and charging sessions.
5. **Operational confidence** — live, stale, offline, faulted or unknown device information.
6. **Final booking decision** — whether allocation is currently permitted.

## 4. Time model

- All persisted timestamps use UTC.
- Opening hours and user-facing times use the station timezone, initially `Europe/Athens`.
- Intervals are half-open: `[start, end)`. An interval ending exactly when another begins does not overlap unless a buffer applies.
- Start and duration use 15-minute increments.
- Duration must satisfy the policy snapshot’s minimum and maximum values.
- Database time is authoritative for expiry and deadline decisions.
- DST transitions must be converted using the station timezone before UTC persistence. Non-existent or ambiguous local times must be rejected or explicitly disambiguated.

### Allocation interval

For v1, the turnaround buffer is applied after the reserved charging interval:

`allocationStart = bookingStart`

`allocationEnd = bookingEnd + turnaroundBuffer`

The complete allocation interval participates in conflict detection. Opening-hours validation applies to the driver’s charging interval; operators may separately require the buffer to remain inside operational hours.

## 5. Request inputs

An interval calculation requires:

- Station or geographic scope
- Requested start and end/duration
- Required connector type
- Optional minimum power
- Optional exact EVSE
- Driver identity for authoritative operations
- Operation type: search, create, reschedule, reassignment or check-in
- Evaluation timestamp

Without a requested interval, the platform displays only a current operational summary—not a claim that the EVSE is reservable.

An explicit “available now” search uses the next valid 15-minute boundary and the minimum booking duration, clearly shown to the user.

## 6. Evaluation algorithm

For each candidate EVSE, evaluate in this order.

### Step 1 — Validate request

Reject invalid, past, incorrectly aligned, too short, too long or excessively advanced intervals.

### Step 2 — Administrative eligibility

The candidate is ineligible when:

- Operator organization is not `ACTIVE`.
- Station is not `PUBLISHED` or is temporarily closed.
- EVSE administrative state is not `ACTIVE`.
- Reservations are disabled by policy.
- Required station access conditions are not satisfied.

Administrative state is separate from device-reported state.

### Step 3 — Compatibility

The EVSE must:

- Offer at least one active compatible connector.
- Meet any explicitly requested minimum power.
- Support one vehicle at a time under the v1 EVSE-level allocation model.

Connector selection records the required connector type, but allocation blocks the entire EVSE.

### Step 4 — Opening hours and policy

The full charging interval must fall inside station opening hours, including holiday and exceptional closures.

The request must satisfy:

- Advance-booking window
- Duration limits
- Start increment
- Station or organization booking policy
- Any access restrictions

The station policy overrides organization defaults. The effective policy is snapshotted when the booking is confirmed.

### Step 5 — Maintenance and operational blocks

Reject an interval overlapping:

- Scheduled or active blocking maintenance
- Emergency closure
- Unexpired blocking status override
- Critical/emergency fault impact interval
- Deactivation scheduled before or during the booking

Maintenance activation cannot silently invalidate an existing allocation. Existing bookings must first be reassigned, cancelled or explicitly handled through the emergency workflow.

### Step 6 — Capacity conflicts

A candidate is unavailable if its allocation interval overlaps:

- An unexpired `HELD` booking
- `CONFIRMED`, `CHECKED_IN` or `ACTIVE` booking allocation
- A session in `AUTHORIZING`, `STARTING`, `CHARGING`, `SUSPENDED`, `STOPPING` or `FINALIZING`
- A completed/interrupted session whose turnaround release time has not passed
- An unresolved session whose physical outcome is uncertain

Terminal `CANCELLED`, `EXPIRED`, `NO_SHOW` and `FULFILMENT_FAILED` bookings do not block capacity unless actual EVSE usage occurred and its release buffer remains active.

A hold blocks only until its `expiresAt` timestamp. Expired holds must be made non-blocking during allocation; a delayed cleanup job cannot remain the sole authority.

### Step 7 — Operational-state policy
*This policy governs how live device status affects booking eligibility based on time.*

- **Near-Term Horizon:** Fixed 60 minutes before requested start. (Release applicability: W1)
- **Freshness Threshold:** Fixed 300 seconds (5 minutes). Expected heartbeat frequency must be configured below this threshold (e.g., 60-second intervals produce 3 heartbeats within the window). (Release applicability: W1)

**For near-term reservations (starting within the 60-minute horizon):**
- `AVAILABLE` with fresh status (age within freshness threshold) may be booked.
- `RESERVED` or `OCCUPIED` is rejected unless the state is explained by the same authoritative booking/session.
- `OFFLINE`, `UNKNOWN`, stale status (age exceeds threshold), or missing heartbeat is not bookable.
- `FAULTED` or `MAINTENANCE` is not bookable.
- Any temporary current offline or stale evidence blocks booking.

**For future reservations (starting beyond the 60-minute horizon):**
- Temporary current `OFFLINE`, stale, or `UNKNOWN` device status does not alone prevent booking.
- The result is marked `PLANNED_AVAILABLE` with reduced operational confidence.
- A fault **with** an explicit/predictive impact interval blocks every overlapping booking interval.
- A fault **without** predictive impact information is treated as current operational evidence only, and does not block bookings starting after the near-term horizon (60 minutes).
- Blocking maintenance, administrative disablement, station closure, and unresolved faults with explicit impact intervals prevent booking.
- Availability is dynamically re-evaluated as the booking approaches check-in.

**Freshness States:**
- `LIVE`: Age is within the configured freshness threshold.
- `STALE`: Age exceeds the freshness threshold.
- `UNKNOWN`: No reliable status has been received.

### Step 8 — Final result

An EVSE result contains:

- `AVAILABLE` — currently allocatable with sufficient confidence.
- `PLANNED_AVAILABLE` — future interval is allocatable, but current device condition is not predictive.
- `UNAVAILABLE` — a definite rule prevents allocation.
- `UNKNOWN` — dependencies failed or available information is insufficient for a safe decision.

`UNKNOWN` is never silently converted to `AVAILABLE` for near-term allocation.

## 7. Conflict rule

Two allocation intervals conflict when each starts before the other ends.

Conflict detection applies to effective allocation intervals including buffers. Double-booking prevention MUST be enforced by the authoritative datastore/transaction, not only by an application-level pre-check.

For concurrent conflicting requests:

- Exactly one allocation may commit.
- Losing requests receive a conflict result and safe alternatives.
- Idempotent retries return the original outcome.
- Auto-assignment may retry another compatible candidate.

## 8. Active-session overruns

Actual EVSE occupation overrides planned availability.

If a session continues beyond its booking:

- The EVSE remains unavailable.
- Subsequent affected bookings are marked at risk.
- Reassignment or operational intervention begins.
- Search projections are updated asynchronously.
- Capacity is released only after confirmed session termination plus the applicable turnaround buffer.

An uncertain stop outcome remains blocking until reconciliation establishes the physical state.

## 9. Station-level aggregation

For the requested compatibility and interval:

- `AVAILABLE`: at least one compatible EVSE is `AVAILABLE`.
- `PLANNED_AVAILABLE`: none is live-available, but at least one is planned-available.
- `UNAVAILABLE`: every compatible candidate has a definite blocking reason.
- `UNKNOWN`: no candidate is available and at least one potentially eligible candidate cannot be safely evaluated.
- `INCOMPATIBLE`: no EVSE satisfies connector/power requirements.

Results include compatible and bookable EVSE counts. Exact counts may change before booking confirmation.

## 10. Reason codes

Internal results use structured reason codes, including:

- `INVALID_INTERVAL`
- `OUTSIDE_ADVANCE_WINDOW`
- `OPERATOR_INACTIVE`
- `STATION_UNPUBLISHED`
- `STATION_CLOSED`
- `OUTSIDE_OPENING_HOURS`
- `RESERVATIONS_DISABLED`
- `EVSE_INACTIVE`
- `NO_COMPATIBLE_CONNECTOR`
- `INSUFFICIENT_POWER`
- `MAINTENANCE_CONFLICT`
- `BLOCKING_FAULT`
- `STATUS_OVERRIDE`
- `EVSE_ALLOCATION_CONFLICT`
- `ACTIVE_SESSION`
- `SESSION_OUTCOME_UNCERTAIN`
- `EVSE_STALE_TELEMETRY` (offline)
- `EVSE_STALE_TELEMETRY`
- `STATUS_UNKNOWN`
- `DEPENDENCY_UNAVAILABLE`

Public responses expose safe explanations without booking-owner, device-security or internal infrastructure details.

## 11. Search read model

The discovery projection may combine station data, allocation summaries, maintenance and device status asynchronously.

Every response should include:

- `evaluatedAt`
- Requested interval and timezone
- Availability classification
- Compatible/bookable counts
- Operational-confidence/freshness indicator
- Safe reason codes
- Projection age or last-updated timestamp

If booking or device projections are delayed, results are marked stale or unknown. Search failure must not prevent users from managing already committed bookings.

## 12. Rescheduling and reassignment

### Rescheduling

The old allocation remains valid until the new allocation is successfully claimed. Claiming the new interval, updating the booking and releasing the old interval occur atomically.

### Reassignment

A replacement EVSE must satisfy the complete calculation for the original interval, connector, power and policy requirements. The original assignment is released only after the replacement commits.

## 13. Cancellation and release

- Cancelling an unused future `HELD` or `CONFIRMED` booking releases its complete allocation immediately.
- A turnaround buffer is retained only where check-in or actual/uncertain EVSE usage requires it.
- Cancelling or abandoning check-in revokes start authorization atomically.
- Capacity projections update after the authoritative transaction commits.

## 14. Check-in relationship

Check-in performs a stricter near-term revalidation:

- Correct assigned EVSE
- Valid window and ownership
- Fresh device status
- No maintenance, fault or blocking override
- No conflicting session
- Valid connector compatibility

Failure caused by equipment routes to reassignment or fulfilment handling and cannot classify the driver as a no-show.

## 15. Acceptance criteria

1. Search results are clearly advisory.
2. Booking revalidation can reject an EVSE shown as available moments earlier.
3. Concurrent conflicting allocations produce exactly one winner.
4. Adjacent bookings are permitted only when their buffered allocation intervals do not overlap.
5. Unexpired holds block capacity; expired holds do not.
6. Stale or unknown near-term EVSEs cannot be booked.
7. Temporary offline/stale status may produce planned future availability, never confident live availability.
8. Blocking faults and maintenance prevent all overlapping reservations.
9. Active and uncertain sessions override planned availability.
10. Cancellation of an unused booking releases capacity immediately.
11. Rescheduling failure preserves the original allocation.
12. Station aggregation correctly handles mixed available, unavailable and unknown EVSEs.
13. Opening hours are evaluated correctly across holidays and DST changes.
14. Public reason messages reveal no other driver’s information.
15. Availability remains reconstructable from authoritative inputs and evaluation rules.

## 16. Required tests

- Boundary and half-open interval tests
- Buffer overlap tests
- Hold-expiry race tests
- High-concurrency allocation tests
- Idempotent retry tests
- Auto-assignment contention tests
- Maintenance and fault overlap tests
- Near-term stale/offline tests
- Future planned-availability tests
- Active-session overrun tests
- Reschedule/reassignment atomicity tests
- Cancellation-release tests
- DST and holiday-hours tests
- Search-projection delay/failure tests
- Public-information leakage tests

## 17. Approved decisions

1. Use half-open intervals `[start, end)`.
2. Apply one post-booking turnaround buffer in v1.
3. Set the near-term operational-status horizon to 60 minutes.
4. Set freshness to a fixed 300 seconds (5 minutes); expected heartbeat frequency is configured below that threshold.
5. Permit future booking despite temporary offline/stale/unknown status, labelled `PLANNED_AVAILABLE`.
6. Never permit booking over blocking maintenance, administrative closure or unresolved critical/emergency faults.
7. Treat no-time searches as operational summaries, not reservation availability.
8. Require the complete charging interval—but not necessarily the post-buffer—to fit opening hours.
9. Release unused cancelled allocations immediately.
10. Keep administrative, device-reported and derived availability states separate.
11. Explicit fault-impact intervals block every overlapping interval; current offline/stale evidence without a predictive interval blocks near-term bookings only.

## 18. Traceability

Primarily implements:

- `FR-AVL-01`
- `FR-AVL-02`
- `FR-BKG-01`
- `FR-BKG-02`
- `FR-BKG-03`
- `FR-OPS-02`
- `FR-CHG-01`
- `FR-PLT-01`

Primary verification: interval, concurrency, lifecycle, resilience and projection-consistency tests.
