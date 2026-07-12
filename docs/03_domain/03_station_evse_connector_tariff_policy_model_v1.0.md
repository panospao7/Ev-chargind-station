Document ID: DOM-003
Title: Station, EVSE, Connector, Tariff and Booking Policy Model v1.0
Version: 1.0
Status: APPROVED
Owner: DA/BA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: DOM-001
Authoritative for: Infrastructure Schema and Immutable Tariff Snapshot Requirements

---

## Station, EVSE, Connector and Tariff Domain Model v1.0

### 1. Operator Organization

Owns stations and their infrastructure.

**Core data**
- Internal ID and public reference
- Legal/display name
- Status
- Contact and support details
- Default booking policies
- Creation/update timestamps

---

### 2. Station

A physical charging location.

**Core data**
- Operator ID
- Name and description
- Address and Greek postcode
- Latitude/longitude
- `Europe/Athens` timezone
- Opening hours and holiday exceptions
- Access type and instructions
- Amenities
- Publication status
- Default tariff and booking policy

A station can contain multiple EVSEs but cannot exist without an operator.

---

### 3. EVSE

A separately reservable charging point that can serve one vehicle at a time.

**Core data**
- Station ID (Authoritative source: Station Operations Service)
- Public EVSE identifier (Authoritative source: Station Operations Service)
- Operator-assigned label (Authoritative source: Station Operations Service)
- Maximum power (Authoritative source: Station Operations Service)
- Administrative state (Authoritative source: Station Operations Service)
- Current operational state shown with EVSE (Non-authoritative projection; Authoritative source: Device Integration Service)
- Last heartbeat/status timestamp (Authoritative source: Device Integration Service)
- Simulator assignment (Authoritative source: Station Operations Service)
- Version for concurrent updates (Authoritative source: Station Operations Service)

**Administrative states**
- `ACTIVE`
- `DISABLED`
- `DEACTIVATED`

**Operational states (Normalised against Glossary)**
- `AVAILABLE` — Online, available for bookings.
- `OCCUPIED` — Connector physically connected to a vehicle.
- `CHARGING` — Physical energy transfer in progress.
- `SUSPENDED` — Energy transfer temporarily paused (vehicle/grid).
- `FAULTED` — Active equipment fault detected.
- `OFFLINE` — Heartbeats missing beyond freshness threshold.
- `MAINTENANCE` — Scheduled maintenance block active.
- `UNKNOWN` — Initial or unverified state.

Administrative and operational states remain separate. For example, an active EVSE may temporarily be offline.

---

### 4. Connector

A charging interface offered by an EVSE.

**Core data**
- EVSE ID
- Connector type
- AC or DC
- Maximum voltage, current and power
- Cable attached or socket-only
- Status where connector-level status is supported

Initial connector types:

- Type 2
- CCS Combo 2
- CHAdeMO

An EVSE may expose several connectors, but its booking blocks the entire EVSE unless future hardware explicitly supports simultaneous charging.

---

### 5. Tariff

A versioned pricing definition assigned to a station or EVSE.

**Possible components**
- Price per kWh
- Price per minute
- Session-start fee
- Idle fee after charging ends
- Applicable tax rate
- Validity period

Initial MVP should support:

- Energy price
- Time price
- Session fee
- Simple idle fee

Every booking receives an immutable **tariff snapshot** containing the applicable components, currency, tax information and calculation version. Later tariff changes cannot alter existing snapshots.

Because payments are excluded, calculated costs are labelled **estimated**.

---

### 6. Booking Policy

Configurable at organization or station level:

- Minimum/maximum duration
- Advance-booking window
- Start-time increment
- Hold duration
- Check-in window
- Grace period
- Turnaround buffer
- Cancellation rules

Station settings override organization defaults.

### Domain invariants

- Public identifiers are different from internal database IDs.
- Published stations require valid coordinates, hours, one tariff and at least one active EVSE.
- An EVSE requires at least one valid connector before activation.
- Infrastructure with historical records is deactivated, never physically deleted.
- Tariff validity periods cannot ambiguously overlap for the same scope.
- Status timestamps and all lifecycle changes are audited.
- Optimistic versioning prevents lost operator updates.
