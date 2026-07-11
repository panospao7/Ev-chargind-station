Document ID: UC-DR-005
Title: DR-13 and DR-21-25 - Remaining Driver Use Cases v1.0
Version: 1.0
Status: APPROVED
Owner: PO/DA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: UC-DR-001
Authoritative for: History, Data Exports, Deletion, and Support Reporting

---

# Remaining Driver Use Cases v1.0

Covers DR-13 and DR-21–25. DR-24 and DR-25 are user-facing specifications built on the approved privacy workflows.

---

## DR-13 — View Upcoming Booking Details

### Goal

Allow a driver to inspect the authoritative current state of an upcoming booking and discover available actions.

### Preconditions

- Driver is authenticated.
- Booking belongs to the driver.
- Booking is not hidden by retention or privacy processing.

### Information displayed

- Public booking reference
- Current lifecycle state and outcome
- Station name, address and access instructions
- Assigned EVSE public label
- Required connector type
- Scheduled start/end in `Europe/Athens`
- Check-in opening and grace deadline
- Tariff and booking-policy snapshots
- Estimated cost and its calculation basis
- Cancellation and rescheduling eligibility
- Maintenance, fault or reassignment warnings
- Notification-delivery warnings
- Related session summary, where applicable
- State-history timeline containing user-safe reasons

### Available actions

Depending on state:

- Reschedule
- Cancel
- Check in
- Abandon check-in
- Start or stop charging
- Report a fault
- Open a support case
- View session summary

### Rules

- Booking capability data is authoritative.
- Device and session information displays freshness.
- Internal IDs, audit internals and device diagnostics are hidden.
- Another user receives a generic not-found response.
- Estimated costs are never represented as completed payments.
- The client cannot infer action eligibility from state alone; the server returns allowed actions.
- Refreshing or opening multiple tabs cannot change state.

### Acceptance criteria

1. Drivers can view only their bookings.
2. Current state and allowed actions are server-derived.
3. Stale device/session information is identified.
4. Tariff and policy snapshots remain unchanged after later configuration changes.
5. Terminal bookings expose no invalid state-changing actions.
6. Deep links require authentication and safe return navigation.

---

## DR-21 — View Booking and Charging History

### Goal

Provide a searchable, accessible history of the driver’s reservations and simulated charging sessions.

### Included records

- Confirmed and completed bookings
- Cancelled, expired and no-show bookings
- Fulfilment failures
- Completed and interrupted sessions
- Start-rejected sessions
- Estimated energy, duration and cost summaries

### Filters

- Date range
- Station
- Booking state
- Session outcome
- Connector type

### Rules

- Results are paginated and sorted newest first by default.
- Booking and session records remain linked but distinct.
- Historical facts use immutable tariff/policy snapshots.
- In-progress or uncertain outcomes are clearly labelled.
- History is not sourced from analytics projections.
- “Hide/archive” changes presentation only and is not deletion.
- Retention and deletion follow the privacy specification.
- History export uses DR-24 rather than an unrestricted screen scrape.

### Acceptance criteria

1. Results contain only the authenticated driver’s records.
2. Pagination and filters produce stable results.
3. Duplicate events cannot create duplicate history entries.
4. Interrupted and unresolved sessions are not shown as successful.
5. Stored UTC timestamps display correctly in `Europe/Athens`.
6. Retained records remain reproducible from authoritative data.

---

## DR-22 — Report Station or EVSE Fault

### Preconditions

- Authenticated active driver.
- Station or EVSE exists and is visible to the driver.
- A related booking/session is optional.

### Primary flow

1. Driver selects station, EVSE or related booking/session.
2. Selects a structured category:
   - Connector damaged
   - EVSE inaccessible
   - Screen/control problem
   - Charging failed
   - Incorrect status
   - Safety concern
   - Other
3. Selects perceived severity.
4. Adds an optional short description.
5. Reviews privacy and emergency guidance.
6. Submits using an idempotency key.
7. System creates a report and links it to an existing fault incident where appropriate.
8. Driver receives a public report reference.

### Rules

- Driver severity is advisory; operational severity is assigned separately.
- Safety emergencies display instructions to contact appropriate emergency/operator channels.
- Reports cannot directly change EVSE status.
- Duplicate reports may be grouped without exposing other reporters.
- Free text is sanitized, length-limited and excluded from analytics by default.
- Image/file attachments are deferred from v1.
- Operators see only the minimum reporter information.
- Malicious or repeated reports are rate-limited.
- Reports linked to failed fulfilment trigger operational review.

### States

`SUBMITTED → TRIAGED → LINKED/INVESTIGATING → RESOLVED → CLOSED`

Alternative: `REJECTED_AS_INVALID`.

### Acceptance criteria

1. Reports are idempotent.
2. Drivers cannot inspect reports from other users.
3. Duplicate reports do not create duplicate fault incidents unnecessarily.
4. Reports cannot directly mark an EVSE unavailable.
5. Safety warnings are clear and accessible.
6. Resolution updates expose no internal technician information.

---

## DR-23 — Manage Notification Preferences

### Capabilities

Drivers may configure:

- Language: Greek or English
- 24-hour booking reminder
- 60-minute booking reminder
- Check-in-window reminder
- Routine session-completion summary
- Non-critical support updates

Drivers cannot disable:

- Verification and recovery
- Security changes
- Booking confirmation
- Rescheduling, reassignment or cancellation
- Fulfilment failure
- Account suspension
- Privacy-request messages
- Messages requiring action

### Rules

- Preferences apply prospectively.
- Obsolete scheduled reminders are cancelled after rescheduling or cancellation.
- Preference updates use optimistic versioning.
- Notification failure does not revert preference changes.
- Marketing consent is not included in v1.
- An undeliverable email warning remains visible in the application.

### Acceptance criteria

1. Optional preferences can be changed independently.
2. Mandatory categories cannot be disabled.
3. Duplicate updates produce one final preference version.
4. Updated preferences affect newly evaluated reminders.
5. Locale changes affect future messages, not historical records.

---

## DR-24 — Export Personal Data

### Primary flow

1. Driver opens the privacy area.
2. Performs recent reauthentication.
3. Selects full access export or applicable portability export.
4. System records the request and begins collection.
5. Driver can inspect workflow status.
6. Mandatory data owners contribute idempotently.
7. Package is assembled and reviewed automatically for required redactions.
8. Driver receives an email when ready.
9. Driver reauthenticates and downloads the package.
10. Package expires after seven days.

### Rules

- Canonical format: JSON.
- Optional readable CSV/PDF summaries may be included.
- Export is never attached to email.
- Another person’s data, credentials and protected security internals are excluded.
- Only one equivalent active export per driver is allowed.
- Repeated submission returns the active request.
- Partial or failed collection is not presented as complete.
- Download events are audited.
- Internal target: completion within 20 days, subject to review.

### Acceptance criteria

1. Export survives participant restarts.
2. Every mandatory participant is accounted for.
3. Packages are encrypted and expire.
4. Download links cannot be transferred safely to another user.
5. Secrets and unrelated personal data are absent.
6. Access and portability outputs are distinguishable.

---

## DR-25 — Request Account Deletion

### Primary flow

1. Driver performs recent reauthentication.
2. Reviews deletion effects and retained-data explanation.
3. Confirms through a single-use email action.
4. System validates blockers.
5. If eligible, seven-day cooling-off begins.
6. New bookings are disabled.
7. Driver may cancel during cooling-off.
8. After expiry, coordinated deletion/anonymization begins.
9. Identity sessions are revoked.
10. Mandatory participants complete their actions.
11. Completion is reported only after all mandatory acknowledgements.

### Blockers

- Upcoming or active booking
- Starting, active, stopping or uncertain session
- Open fulfilment workflow
- Applicable dispute, security investigation or retention hold
- Operator ownership requiring transfer

### Rules

- A blocker is explained safely.
- Deletion cannot be used to abandon an active session.
- Retained historical facts are minimized and anonymized/pseudonymized according to policy.
- Failed participants keep the request visible as incomplete.
- Backup-restoration procedures reapply deletion tombstones.
- A deleted account cannot be restored automatically.

### Acceptance criteria

1. Active obligations prevent unsafe deletion.
2. Cooling-off cancellation preserves the account.
3. Processing is idempotent.
4. Authentication is disabled before final completion.
5. Projections and caches receive deletion actions.
6. Completion is not claimed prematurely.

---

## Driver traceability

| Use case | Requirements |
|---|---|
| DR-13 | FR-BKG-01, FR-BKG-04, FR-AVL-02 |
| DR-21 | FR-CHG-03, FR-BKG-06 |
| DR-22 | FR-OPS-02, FR-SUP-01, FR-AUD-01 |
| DR-23 | FR-NOT-01 |
| DR-24 | FR-PRV-01 |
| DR-25 | FR-IAM-01, FR-PRV-01, FR-PLT-01 |
