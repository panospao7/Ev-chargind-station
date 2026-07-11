Document ID: UC-OP-002
Title: Detailed Operator Use Cases v1.0
Version: 1.0
Status: APPROVED
Owner: PO/DA/BA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: UC-OP-001
Authoritative for: OP-01 to OP-25 Flow Details and Rules

---

# Detailed Operator Use Cases v1.0

## Global operator rules

- MFA is mandatory.
- Authorization combines role, organization ownership and resource scope.
- Infrastructure history is deactivated, not physically deleted.
- Sensitive changes require recent reauthentication where specified.
- Commands use idempotency keys.
- All privileged changes produce immutable audit evidence.
- Optimistic versioning prevents lost configuration updates.
- Technicians cannot access tariffs or unnecessary driver data.
- Operator support access is distinct from platform support access.

---

# Organization and Staff

## OP-01 — Create Operator Application

### Actor
Verified driver/account holder applying as an operator owner.

### Flow

1. Submit organization display/legal name, contact details and operational description.
2. Accept operator terms and privacy information.
3. System creates organization in `PENDING_APPROVAL`.
4. Applicant becomes pending owner.
5. Administrator reviews through AD-01.
6. Approval activates the organization and owner role.
7. Rejection records a reason and permits corrected resubmission where allowed.

### Rules

- Duplicate pending applications are prohibited.
- Stations cannot be published before approval.
- Uploaded legal documents are excluded from v1.
- Application data is private to applicant and authorized administrators.

### Acceptance

- Approval cannot be performed by the applicant.
- Repeated submissions do not create duplicate organizations.
- Rejection reasons are safe and auditable.

## OP-02 — Manage Organization Profile

Owner or manager updates:

- Display/contact details
- Support information
- Default booking policies
- Default timezone/currency settings

Legal-name or ownership-sensitive changes require owner permission and review where configured. Changes never rewrite station or booking snapshots.

## OP-03 — Invite, Remove and Change Staff Roles

### Flow

1. Owner/manager enters invitee email and permitted role.
2. System creates a single-use 48-hour invitation.
3. Invitee authenticates, verifies email and accepts.
4. Membership becomes active.
5. Changes/removals revoke organization access immediately.

### Rules

- Managers cannot grant `OWNER`.
- Managers cannot remove/change owners.
- The last owner cannot be removed.
- Role changes revoke stale authorization sessions where required.
- Invitations do not reveal whether an unrelated account exists.

## OP-04 — Transfer Organization Ownership

- Initiated by current owner.
- Requires recent reauthentication and MFA.
- Target must be an active organization member.
- Target explicitly accepts.
- Transfer commits atomically.
- Organization must always retain one owner.
- Pending transfer expires and is cancellable.
- Both parties are notified and the action is audited.

## OP-05 — View Organization Audit History

Owners and permitted managers may inspect organization-scoped audit events.

Filters:

- Date
- Actor
- Resource
- Action
- Outcome
- Correlation ID

Secrets and unrelated platform-security data are excluded. Audit records cannot be edited or deleted through application APIs.

---

# Station and Infrastructure Management

## OP-06 — Create and Edit Stations

### Creation

Stations begin as `DRAFT`.

Required before publication:

- Name
- Valid coordinates/address
- Opening hours
- Access type
- Applicable tariff
- Booking policy
- At least one active EVSE with connector

### Rules

- Public and internal identifiers differ.
- Coordinates must fall within supported validation rules.
- Editing uses optimistic version checks.
- Material changes affecting future bookings trigger impact analysis.
- Historical infrastructure is not hard deleted.

## OP-07 — Configure Location, Access, Amenities and Hours

Supports:

- Address and map position
- Access instructions
- Amenities
- Weekly hours
- Holiday exceptions
- Temporary closures

Rules:

- Time values use station timezone.
- DST ambiguity is validated.
- Reduced hours conflicting with bookings require resolution.
- Public instructions cannot contain secrets or unsafe HTML.

## OP-08 — Publish, Temporarily Close or Deactivate Station

### Publish

Allowed only when publication invariants pass.

### Temporary closure

- Requires interval and reason.
- Affected bookings are identified.
- Closure activates only after normal impact resolution unless emergency authority is used.

### Deactivation

- Prevents new bookings.
- Existing obligations must be resolved.
- Historical records remain accessible.
- Reactivation of a deactivated station requires explicit validation and authorization.

## OP-09 — Manage EVSEs and Connectors

Capabilities:

- Create EVSE
- Add/edit connectors
- Assign public labels
- Configure power
- Activate, disable or deactivate
- Assign simulator identity

Rules:

- EVSE requires one valid connector before activation.
- Existing bookings prevent destructive removal.
- Connector changes conflicting with bookings require resolution.
- Simulator inventory is reconciled but cannot create infrastructure.
- Lost updates produce version conflicts.

## OP-10 — Configure Booking Rules

Configurable within platform limits:

- Minimum/maximum duration
- Advance window
- Start increment
- Hold duration
- Check-in window
- Grace period
- Turnaround buffer

Rules:

- Station policy overrides organization defaults.
- Platform-enforced safe minima/maxima cannot be bypassed.
- Existing bookings retain snapshots.
- Invalid combinations are rejected.
- Policy changes show impact before activation.

## OP-11 — Configure Tariffs

Supports versioned:

- Energy price
- Time price
- Session fee
- Idle fee
- Tax rate
- Validity period

Rules:

- Currency is EUR in v1.
- Overlapping applicable tariff periods are prohibited.
- Published station requires an active tariff.
- Existing snapshots never change.
- Estimated amounts are not represented as payments.
- Tariff changes require manager/owner permission.

---

# Operations

## OP-12 — Monitor EVSE Status and Heartbeat Freshness

Display:

- Administrative state
- Device-reported state
- Derived availability
- Last heartbeat
- Status freshness
- Active fault/maintenance
- Current session summary
- Reconciliation status

Rules:

- States are never merged into one ambiguous value.
- Stale/unknown values are visually distinct.
- Technicians see no unnecessary driver data.
- Dashboard projections expose their update time.

## OP-13 — Schedule Maintenance

The dedicated Maintenance, Fault and Reassignment specification is authoritative.

Additional acceptance:

- Impact preview lists affected bookings/sessions.
- Normal maintenance cannot activate with unresolved conflicts.
- Completion returns device confidence to `UNKNOWN`.
- Emergency activation requires elevated permission and reason.

## OP-14 — Record Faults and Repair Progress

### Flow

1. Create or acknowledge fault.
2. Assign severity and scope.
3. Link duplicate simulator/driver reports.
4. Move through `OPEN → ACKNOWLEDGED → IN_PROGRESS → RESOLVED`.
5. Record diagnosis and safe resolution information.
6. Clear operational block only when justified.
7. Await fresh device status where required.

Rules:

- Fault records and device states remain distinct.
- Resolution does not automatically prove EVSE availability.
- Reopening returns to `OPEN`.
- Technician notes exclude unnecessary personal data.

## OP-15 — Apply Manual Status Override

Override includes:

- Target
- Intended reported/derived operational restriction
- Reason
- Start and expiry
- Actor

Rules:

- Overrides expire automatically.
- Ordinary overrides can make equipment less available, not bypass authoritative maintenance, fault or booking rules.
- Extension is a new audited action.
- Emergency overrides require elevated scope.
- Device-reported state remains visible separately.

## OP-16 — Operate Charger Simulator

Technicians may:

- Start/stop assigned simulator
- Select approved scenario
- Inject/clear faults
- Trigger status reports
- Inspect sanitized event/command history
- Reset simulator safely

They cannot:

- Create bookings
- Forge users
- Bypass start authorization
- Alter tariff snapshots
- Access other organizations
- delete immutable event history

The dedicated simulator protocol specification is authoritative.

## OP-17 — View Active Charging Sessions

Display:

- Station/EVSE
- Session state
- Start time
- Duration
- Power and energy
- Last update
- Fault/suspension status
- Reconciliation state

Driver identity is masked unless operationally necessary and authorized. Ordinary operators cannot stop sessions except through an approved emergency workflow.

---

# Booking Operations

## OP-18 — View Owned-Station Bookings

Filters:

- Station/EVSE
- Date range
- State
- Operational-risk status
- Reassignment requirement

Rules:

- Only bookings for owned stations are visible.
- Driver details are minimized.
- Search availability projections are not used as booking history.
- Bulk unrestricted export is prohibited.

## OP-19 — Reassign Booking

The dedicated Maintenance, Fault and Reassignment specification is authoritative.

Additional rules:

- Replacement is atomically claimed before original release.
- Driver approval is required where defined.
- Tariff cannot become more expensive without approval.
- Duplicate requests return the same workflow outcome.

## OP-20 — Cancel Booking

### Preconditions

- Booking belongs to an owned station.
- Actor has booking-support permission.
- Booking is cancellable according to state or emergency authority.

### Flow

1. Select structured cancellation reason.
2. Review impact and alternatives.
3. Confirm.
4. Booking capability validates ownership and state.
5. Cancellation commits and releases unused capacity.
6. Start authorization is revoked.
7. Driver notification and analytics follow after commit.

Rules:

- Reason is mandatory.
- Active sessions cannot be cancelled; use emergency stop.
- Operator cancellation cannot classify the driver as a no-show.
- Repeated cancellation is idempotent.
- Internal notes are not exposed automatically to the driver.

## OP-21 — Resolve Fulfilment Failure

Workflow:

1. Identify failure source.
2. Confirm whether charging ever began.
3. Attempt eligible reassignment where useful.
4. Resolve booking as:
   - Reassigned
   - Cancelled operationally
   - `FULFILMENT_FAILED`
   - Completed with interrupted outcome
5. Record responsibility and resolution.
6. Notify driver.
7. Link fault/support case.

Equipment failure never becomes `NO_SHOW`.

## OP-22 — View Limited Driver Contact Information

Access requires:

- Assigned operational/support task
- Permitted role
- Current need
- Recorded reason

Rules:

- Contact data is masked by default.
- Reveal is time-limited.
- Technicians normally cannot reveal it.
- Bulk access is prohibited.
- Every reveal is audited.
- Contact information cannot be copied into technician notes unnecessarily.

---

# Analytics

## OP-23 — View Utilization, Energy and Session Statistics

Metrics include:

- EVSE utilization
- Booking occupancy
- Session duration
- Estimated energy
- Operational availability
- Peak periods

Rules:

- Analytics is eventually consistent.
- Freshness is shown.
- User-level profiling is excluded.
- Small groups are suppressed where privacy risk exists.

## OP-24 — View Cancellation, No-Show and Failure Rates

Metrics distinguish:

- Driver cancellation
- Operator cancellation
- No-show
- Fulfilment failure
- Interrupted session
- Start rejection
- Maintenance impact

Definitions are versioned so metrics remain reproducible.

## OP-25 — Export Organization Reports

- Available to owner/manager.
- Filters and metric definitions are recorded.
- CSV is required; PDF optional.
- Exports contain aggregated operational data.
- Driver-level bulk exports are prohibited.
- Export generation is asynchronous, expiring and audited.

---

## Operator acceptance summary

1. Cross-organization access always fails.
2. Owner-only actions cannot be performed by managers.
3. Technicians cannot modify tariffs or view driver identity.
4. Historical configuration remains reproducible.
5. Infrastructure changes cannot silently invalidate bookings.
6. Suspended organizations cannot accept new bookings.
7. All sensitive actions are idempotent and audited.
8. Analytics failure does not block operations.
