Document ID: DOM-004
Title: Booking Lifecycle and Policy Specification v1.0
Version: 1.0
Status: APPROVED
Owner: DA/BA
Last reviewed: 2026-07-11
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
- **NO_SHOW** — driver did not check in before the grace period ended.
- **FULFILMENT_FAILED** — booking could not be fulfilled because of equipment, platform failure, or definitive session start rejection.

A generic `FAILED` state should be avoided because it does not explain what failed.

### Recommended policies

- Hold duration: **5 minutes**
- Earliest booking: **15 minutes from now**
- Advance-booking limit: **30 days**
- Minimum duration: **30 minutes**
- Maximum duration: **4 hours**
- Check-in opens: **15 minutes before start**
- Late-arrival grace period: **15 minutes**
- Driver cancellation allowed until booking starts
- No financial penalty in the initial version
- Operator cancellation requires a reason
- Administrator overrides require a reason and audit entry
- The driver is notified of every significant status change

### Important rules

- State transitions are server-controlled and validated.
- Terminal bookings cannot be reopened.
- Rescheduling is atomic: either the new interval succeeds or the existing booking remains unchanged.
- Unused HELD or CONFIRMED bookings release the complete allocation immediately; a turnaround buffer remains only following actual charging or uncertain session use.
- A no-show automatically releases the EVSE.
- Equipment failure must not incorrectly classify the driver as a no-show.
- Booking and charging-session lifecycles remain separate but connected.
- Every transition records actor, timestamp, reason, and previous/new state.

After approving this, the next detailed use cases are:

1. **DR-14 — Reschedule booking**
2. **DR-15 — Cancel booking**
3. **DR-16 — Check in**
4. **DR-17–20 — Charging-session lifecycle**
