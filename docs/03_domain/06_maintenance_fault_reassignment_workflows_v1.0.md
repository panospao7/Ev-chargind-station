Document ID: DOM-006
Title: Maintenance, Fault and Reassignment Workflows v1.0
Version: 1.0
Status: APPROVED
Owner: DA/BA/QA
Last reviewed: 2026-07-12
Supersedes: None
Depends on: DOM-003, DOM-004
Authoritative for: Maintenance scheduling, faults lifecycles, and reassignment checks

---

## Maintenance, Fault and Reassignment Workflows v1.0

### Dual State Machine Model
*To prevent race conditions where a new booking enters during the impact-resolution phase, the system uses two decoupled lifecycles coordinated via a two-phase handshake.*

1. **Maintenance Planning Record (Owned by Station Operations Service):**
   `DRAFT → PROPOSED → ENFORCEMENT_PENDING → IMPACT_RESOLUTION → SCHEDULED → ACTIVE → COMPLETED | CANCELLED | FAILED`
2. **Booking Capacity Restriction (Owned by Booking and Session Service):**
   `FREEZE → BLOCKED → RELEASED`

Maintenance records include:
- Affected station or EVSE
- Start/end time
- Type and description
- Booking impact
- Creator and timestamps
- Completion/cancellation reason

### OP-13 — Schedule maintenance
**Release applicability:** W1

1. **Plan Proposal:** Operator selects infrastructure and maintenance interval. The plan starts in `DRAFT` and transitions to `PROPOSED` (Release applicability: W1).
2. **Freeze Handshake:** Station Operations Service sends a capacity restriction request to Booking and Session Service, transitioning the plan to `ENFORCEMENT_PENDING`. Booking immediately commits a `FREEZE` status on the restriction. This prevents any new bookings or holds from entering the affected interval (Release applicability: W1).
3. **Impact Resolution:** While the restriction is in `FREEZE`, the plan transitions to `IMPACT_RESOLUTION`. The Booking module automatically attempts to reassign affected bookings or flags conflicts for operator action (Release applicability: W1).
4. **Final Block Commit:** Once conflicting bookings are resolved, Booking and Session Service finalizes the capacity restriction to `BLOCKED`. The Maintenance Plan transitions to `SCHEDULED` (or `ACTIVE` if immediate) (Release applicability: W1).
5. **Execution & Release:** At start time, affected infrastructure becomes `MAINTENANCE`. Upon normal completion or cancellation before execution, the capacity restriction transitions to `RELEASED` and the infrastructure status is updated based on fresh device evidence confirming safe state. A failed active maintenance operation leaves the restriction `BLOCKED` until Station Operations provides explicit safe withdrawal or a replacement restriction is installed (Release applicability: W1).

Maintenance cannot silently invalidate bookings or interrupt active sessions without an emergency reason.

### Fault lifecycle

`OPEN → ACKNOWLEDGED → IN_PROGRESS → RESOLVED`

A reopened fault returns to `OPEN`.

Faults may originate from:
- Device Integration Service telemetry/evidence
- Driver reports
- Operator actions
- Stale heartbeat detection
- Platform monitoring

Severity:
- `WARNING` — operation can continue
- `DEGRADED` — limited capability
- `CRITICAL` — EVSE immediately unavailable
- `EMERGENCY` — sessions may be stopped

Duplicate reports may be linked to one fault incident.

### OP-19 — Booking reassignment
**Release applicability:** W1

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

### Immediate Restriction Handshake Summary
The restriction handshake operates as follows:
- **Station Operations (Plan):** `PROPOSED` → `ENFORCEMENT_PENDING` → `IMPACT_RESOLUTION` → `SCHEDULED`
- **Booking and Session (Restriction):** (Request received) → `FREEZE` → (Conflicts resolved) → `BLOCKED`
- **Abort/Fail Path:** If Booking fails to freeze or resolve conflicts within timeout, the plan transitions to `FAILED` and the restriction transitions to `RELEASED` (or manual override is requested).
