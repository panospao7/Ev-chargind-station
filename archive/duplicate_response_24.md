Approved as **Booking Lifecycle v1.0**.

## DR-14 — Reschedule Booking

**Allowed when:** Booking is `CONFIRMED` and has not reached its check-in window.

### Flow

1. Driver opens an upcoming booking.
2. Selects a new date, start time, duration, or compatible EVSE.
3. System previews availability and recalculates the price estimate.
4. Driver confirms the change.
5. Server revalidates availability.
6. In one atomic transaction, the system:
   - Claims the new interval.
   - Updates the booking and tariff snapshot.
   - Releases the previous interval.
   - Records an audit entry.
7. Driver receives the updated confirmation.

### Rules

- Initially, rescheduling remains within the same station.
- If the new interval is unavailable, the original booking remains unchanged.
- Rescheduling `CHECKED_IN`, `ACTIVE`, or terminal bookings is prohibited.
- Duplicate requests are handled through idempotency keys.
- Concurrent updates use booking version checks.
- Changing stations requires cancelling and creating a new booking.

## DR-15 — Cancel Booking

### Driver cancellation

Permitted while `HELD` or `CONFIRMED`. A checked-in driver must first abandon check-in. An `ACTIVE` booking cannot be cancelled—the charging session must be stopped instead.

### Operator/admin cancellation

Permitted when operationally necessary, such as:

- EVSE failure
- Emergency maintenance
- Station closure
- Fraud or abuse
- Administrative correction

A reason is mandatory.

### Cancellation results

- Booking becomes `CANCELLED`.
- Reserved capacity is released.
- Actor, reason, timestamp, and previous state are audited.
- Related check-in authorization is revoked.
- Notification and analytics events are emitted after commit.
- Notification failure does not reverse cancellation.
- Repeating the request returns the existing cancelled result.

### Additional rule

Rather than separate states such as `CANCELLED_BY_DRIVER` and `CANCELLED_BY_OPERATOR`, we retain one `CANCELLED` state with structured metadata describing **who cancelled it and why**.

