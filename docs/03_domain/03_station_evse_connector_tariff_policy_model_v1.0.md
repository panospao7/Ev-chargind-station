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
- Station ID
- Public EVSE identifier
- Operator-assigned label
- Maximum power
- Administrative state
- Current operational state
- Last heartbeat/status timestamp
- Simulator assignment
- Version for concurrent updates

**Administrative states**
- `ACTIVE`
- `DISABLED`
- `DEACTIVATED`

**Operational states**
- `AVAILABLE`
- `RESERVED`
- `OCCUPIED`
- `FAULTED`
- `OFFLINE`
- `UNKNOWN`
- `MAINTENANCE`

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

Next: **maintenance scheduling, fault handling and booking reassignment workflows**.