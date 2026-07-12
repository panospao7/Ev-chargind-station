Document ID: UC-OP-001
Title: Operator Use-Case Catalogue and Roles v1.0
Version: 1.0
Status: APPROVED
Owner: PO/DA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: SCP-003
Authoritative for: Operator Roles, Station Lifecycle, and Override Rules

---

## Operator Use Cases and Organization Model v1.0

### Organization lifecycle

`PENDING_APPROVAL → ACTIVE → SUSPENDED → CLOSED`

- Platform administrators approve organizations.
- Suspended organizations cannot create stations or accept new bookings.
- Existing bookings require resolution before closure.
- Every station belongs to exactly one operator organization.

### Operator roles

**Owner**
- Full organization access
- Manage organization settings and staff
- Transfer ownership
- Request organization closure

**Manager**
- Manage stations, EVSEs, connectors, tariffs and booking policies
- Manage operational staff
- View bookings, sessions and analytics
- Cannot transfer ownership or close the organization

**Technician**
- Update operational status
- Schedule maintenance
- Inspect faults and simulator information
- Cannot access user details, tariffs or organization settings

**Support agent**
- View relevant bookings
- Assist drivers and perform permitted cancellations/reassignments
- Personal information is minimized
- Cannot modify infrastructure or tariffs

## Operator use-case catalogue

### Organization and staff

- **OP-01:** Create operator application
- **OP-02:** Manage organization profile
- **OP-03:** Invite, remove and change staff roles
- **OP-04:** Transfer organization ownership
- **OP-05:** View organization audit history

### Station management

- **OP-06:** Create and edit stations
- **OP-07:** Configure location, access instructions, amenities and hours
- **OP-08:** Publish, temporarily close or deactivate a station
- **OP-09:** Create and manage EVSEs and connectors
- **OP-10:** Configure booking rules, buffers and advance limits
- **OP-11:** Configure tariffs

Station lifecycle:

`DRAFT → PUBLISHED → TEMPORARILY_CLOSED → DEACTIVATED`

### Operations

- **OP-12:** Monitor EVSE status and heartbeat freshness
- **OP-13:** Schedule maintenance
- **OP-14:** Record faults and repair progress
- **OP-15:** Apply manual status overrides
- **OP-16:** Operate the charger simulator
- **OP-17:** View active charging sessions

### Booking management

- **OP-18:** View bookings for owned stations
- **OP-19:** Reassign a booking to a compatible EVSE
- **OP-20:** Cancel a booking with a mandatory reason
- **OP-21:** Resolve equipment-related fulfilment failures
- **OP-22:** View limited driver contact information when necessary

### Analytics

- **OP-23:** View utilization, energy and session statistics
- **OP-24:** View cancellation, no-show and failure rates
- **OP-25:** Export organization reports

## Important rules

- Operators access only resources owned by their organization.
- Authorization checks role **and** resource ownership.
- An EVSE with future bookings cannot be deleted; it must be deactivated.
- Maintenance conflicts must trigger reassignment or cancellation workflows.
- Tariff changes never modify existing booking snapshots.
- Manual overrides expire automatically and require a reason.
- Staff invitations are single-use and expiring.
- MFA is mandatory for all operator accounts.
- Sensitive actions are immutable audit events.
- Analytics must not expose unnecessary driver information.
