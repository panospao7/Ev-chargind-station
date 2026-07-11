## Maintenance, Fault and Reassignment Workflows v1.0

### Maintenance lifecycle

`SCHEDULED → ACTIVE → COMPLETED`

Alternative terminal state: `CANCELLED`.

Maintenance records include:

- Affected station or EVSE
- Start/end time
- Type and description
- Booking impact
- Creator and timestamps
- Completion/cancellation reason

### OP-13 — Schedule maintenance

1. Operator selects infrastructure and maintenance interval.
2. System detects affected bookings and active sessions.
3. Operator reviews impact before confirmation.
4. System attempts reassignment for affected bookings.
5. Unresolved bookings are flagged for operator action.
6. Maintenance is scheduled and drivers are notified.
7. At start time, affected infrastructure becomes `MAINTENANCE`.
8. At completion, operational status returns to `UNKNOWN` until fresh charger status arrives.

Maintenance cannot silently invalidate bookings or interrupt active sessions without an emergency reason.

### Fault lifecycle

`OPEN → ACKNOWLEDGED → IN_PROGRESS → RESOLVED`

A reopened fault returns to `OPEN`.

Faults may originate from:

- Charger simulator
- Driver report
- Operator
- Stale heartbeat detection
- Platform monitoring

Severity:

- `WARNING` — operation can continue
- `DEGRADED` — limited capability
- `CRITICAL` — EVSE immediately unavailable
- `EMERGENCY` — sessions may be stopped

Duplicate reports may be linked to one fault incident.

### OP-19 — Booking reassignment

1. Identify compatible EVSEs at the same station.
2. Revalidate connector, power, availability and maintenance.
3. Reserve the replacement atomically.
4. Update the booking assignment.
5. Release the original EVSE.
6. Record old/new assignments and reason.
7. Notify the driver.

Automatic reassignment is allowed only when the replacement:

- Supports the required connector
- Covers the complete reserved interval
- Has equal or greater required power
- Uses the same tariff or a cheaper one
- Does not change station or booking time

Otherwise, driver approval is required. A driver who explicitly selected an EVSE must also approve its replacement unless the failure occurs during check-in.

### Unresolved bookings

- Future booking: offer alternatives, then cancel with reason if unresolved.
- During check-in: mark `FULFILMENT_FAILED` if no replacement exists.
- Active session: complete with an `INTERRUPTED` outcome.
- Equipment failure never produces `NO_SHOW`.

### Core safeguards

- Reassignment is idempotent and concurrency-safe.
- Maintenance cannot overlap an EVSE allocation unnoticed.
- Emergency actions require a reason and audit record.
- Driver information shown to technicians is minimized.
- Status overrides expire automatically.
- Notifications occur after committed changes.

Next: **Administrator and platform-support use cases and permission model**.