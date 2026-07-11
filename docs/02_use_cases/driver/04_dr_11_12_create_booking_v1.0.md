Document ID: UC-DR-004
Title: DR-11/12 - Create Booking v1.0
Version: 1.0
Status: APPROVED
Owner: DA/BA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: UC-DR-001, DOM-005
Authoritative for: Booking Allocation and Concurrency Check

---

## DR-11/12 — Create EVSE Reservation v1.0

**Goal:** Reserve one compatible EVSE for a defined start time and duration, either automatically assigned or explicitly selected.

### Preconditions
- Driver is authenticated, verified, active, and authorized to book.
- A connector type or saved vehicle is selected.
- Station accepts reservations during the requested period.
- Driver has no overlapping booking or active session.

### Primary flow
1. Driver selects station, date, start time, duration, and vehicle/connector.
2. System displays compatible EVSEs, estimated price, tariff, and availability.
3. Driver chooses automatic assignment or a specific EVSE.
4. Server revalidates all information; displayed availability is never authoritative.
5. System atomically creates a temporary **HELD** booking for one EVSE.
6. Driver reviews the assignment, price estimate, and cancellation rules.
7. Driver confirms before the hold expires.
8. Booking becomes **CONFIRMED** and receives a public booking reference.
9. A tariff snapshot is stored so later tariff changes do not alter the estimate.
10. Confirmation email is queued; analytics and audit events are recorded.

### Alternative and failure flows
- Selected EVSE was taken concurrently: return a conflict and alternatives.
- Automatic candidate was taken: attempt another compatible EVSE.
- Hold expires: release capacity; confirmation is rejected.
- Duplicate request: return the original result using its idempotency key.
- Invalid time, closed station, maintenance, incompatibility, or overlap: reject with a specific reason.
- Notification/analytics failure does not invalidate a committed booking.
- Unexpected failure before commit creates no booking.

### Core business rules
- Start and duration use 15-minute increments.
- A confirmed booking identifies exactly one EVSE and required connector type.
- Booking intervals include configured turnaround buffers.
- Overlapping `HELD`, `CONFIRMED`, `CHECKED_IN`, or `ACTIVE` bookings are prohibited.
- Only the Booking service may authorize reservable capacity.
- Concurrent conflicting requests must produce exactly one winner.
- One active hold per driver; holds are rate-limited.
- Suggested defaults: 5-minute hold, 30-minute minimum, 4-hour maximum, 30-day advance window.
- Near-term reservations require fresh operational status. Future reservations may ignore temporary offline status but never planned maintenance or permanent unavailability.

### Acceptance tests
- Two concurrent requests cannot reserve the same EVSE/time.
- Auto-assignment selects only compatible EVSEs.
- Expired holds release availability.
- Confirmation after expiry fails.
- Retrying the same request cannot create duplicates.
- Unauthorized users cannot book or inspect another driver’s booking.

**Decisions to approve:** suggested timing defaults and the distinction between temporary offline status for near-term versus future bookings.