Document ID: DOM-004
Title: Booking Lifecycle and Policy Specification v1.0
Version: 1.0
Status: APPROVED
Owner: DA/BA
Last reviewed: 2026-07-12
Supersedes: None
Depends on: DOM-001, DOM-003
Authoritative for: Booking State Machine Transitions and Policies

---

## Booking Lifecycle and Policy Specification v1.0

### States

- **HELD** — EVSE capacity temporarily reserved during confirmation.
- **CONFIRMED** — reservation accepted.
- **CHECKED_IN** — driver validated at the assigned EVSE.
- **ACTIVE** — charging session started.
- **COMPLETED** — charging session concluded.
- **CANCELLED** — cancelled by driver, operator, or administrator.
- **EXPIRED** — hold was not confirmed in time.
- **NO_SHOW** — Driver did not check in before the grace deadline.
- **FULFILMENT_FAILED** — booking could not be fulfilled because of equipment, platform failure, or definitive session start rejection.

A generic `FAILED` state should be avoided because it does not explain what failed.

### Approved Release 1 MVP Policies

- Hold duration: **5 minutes**
- Earliest booking: **15 minutes from now**
- Advance-booking limit (Release 1 MVP): **14 days**
- Minimum duration: **15 minutes**
- Maximum duration: **4 hours**
- Check-in opens: **15 minutes before start**
- Late-arrival grace period: **15 minutes**
- Near-term horizon: **60 minutes**
- Near-term operational evidence freshness threshold: **5 minutes**
- Driver cancellation allowed until booking starts
- No financial penalty in the initial version
- Operator cancellation requires a reason
- Administrator overrides require a reason and audit entry
- The driver is notified of every significant status change

### Important rules

- State transitions are server-controlled and validated.
- Terminal bookings cannot be reopened.
- A hold is represented as a state of the Booking aggregate (HELD) and a capacity claim of kind BOOKING_HOLD. There is no independent Booking-Hold aggregate.
- Rescheduling is atomic: either the new interval succeeds or the existing booking remains unchanged.
- Unused HELD or CONFIRMED bookings release the complete allocation immediately; a turnaround buffer remains only following actual charging or uncertain session use.
- A no-show automatically releases the EVSE.
- Equipment failure must not incorrectly classify the driver as a no-show.
- Booking and charging-session lifecycles remain separate but connected.
- Every transition records actor, timestamp, reason, and previous/new state.

### Authoritative Booking-Input Strategy
- The Booking authority enforces allocations and holds using local, versioned read-only projections of Station Operations config data (station eligibility, connector compatibility, active tariffs, policy limits, and active blocking restrictions).
- The Booking authority must not execute remote REST or broker calls to external services while holding database transaction locks.
- Verification is performed entirely against the Booking capability's local replication database schemas. Local configuration projections must be kept fresh through asynchronous events, and the Booking authority fails closed if critical projection data is stale or unavailable.
