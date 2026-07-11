# Detailed Administrator and Platform Support Use Cases v1.0 — Draft

## Global rules

- MFA is mandatory.
- Platform roles do not grant cloud/database/identity administration.
- Authorization is default-deny and validated by every owning capability.
- Administrators cannot silently impersonate users.
- Support access requires case scope.
- Break-glass access is exceptional, expiring, alerted and reviewed.
- Suspension/deactivation is preferred over destructive deletion.
- Privileged actions record actor, target, reason, correlation ID and before/after state.

---

# Administrator Use Cases

## AD-01 — Review Operator Applications

### Flow

1. Administrator opens pending application.
2. Reviews supplied organization and applicant information.
3. Requests clarification, approves or rejects.
4. Decision and reason commit atomically.
5. Organization and owner membership update.
6. Applicant receives notification.

### Rules

- Reviewer cannot be the applicant.
- Decision reasons use structured categories plus limited notes.
- Rejection does not delete the application record.
- Approval cannot bypass mandatory organization invariants.

## AD-02 — Suspend or Reactivate Users/Organizations

### Suspension types

- Security
- Abuse/fraud
- Operational
- Policy violation
- Administrative correction

### Rules

- Reason and scope are mandatory.
- User suspension revokes sessions and prevents new bookings.
- Organization suspension prevents new bookings and configuration changes.
- Existing bookings/sessions enter resolution workflows.
- Suspension does not silently cancel obligations.
- Reactivation requires validation that the blocking condition is resolved.
- Emergency suspension may commit immediately but remains reviewable.

## AD-03 — Moderate Stations and Operators

Capabilities:

- Unpublish unsafe/inaccurate stations
- Require correction
- Temporarily restrict publication
- Review repeated operational failures

Rules:

- Moderation is distinct from editing operator data.
- Administrator normally requests operator correction.
- Direct correction is limited to justified exceptional cases.
- Affected bookings require operational resolution.
- Public moderation reasons reveal no protected internal information.

## AD-04 — Manage Reference Data and Policy Limits

Includes:

- Connector types
- Fault/cancellation reason categories
- Platform policy minima/maxima
- Supported access types
- Analytics definitions

Rules:

- Reference values in historical records cannot be deleted.
- Deprecation replaces deletion.
- Changes are versioned.
- Policy-limit reductions require impact analysis.
- Invalidating existing bookings is prohibited.

## AD-05 — Investigate Incidents and Disputes

### Flow

1. Open or receive case.
2. Establish scope and assignment.
3. Access relevant masked records.
4. Reveal additional fields only when justified.
5. Link bookings, sessions, faults and audit events.
6. Record findings and corrective actions.
7. Resolve or escalate.

Rules:

- Investigation notes are access-controlled.
- Evidence is not altered.
- Another organization’s data is disclosed only when justified.
- Security incidents follow the separate incident-response plan.

## AD-06 — Emergency Booking or Session Intervention

Permitted actions may include:

- Cancel booking
- Revoke start authorization
- Stop active session
- Activate emergency maintenance
- Suspend compromised account/device
- Mark workflow for reconciliation

Requirements:

- Recent reauthentication and MFA
- Explicit emergency permission
- Mandatory reason
- Narrow resource scope
- Confirmation step
- Immediate audit and alert
- Post-action review

No intervention may fabricate device success or erase historical facts.

## AD-07 — Review Audit and Security Events

Administrators/security reviewers may:

- Filter and correlate events
- Inspect privileged actions
- Review failed authorization
- Review break-glass activity
- Export approved security reports

Rules:

- Audit data is read-only.
- Ordinary administrators cannot administer audit storage.
- Secrets are excluded.
- Search/export access is audited.
- Retention holds require separate authorization.

## AD-08 — Process Privacy Requests

The privacy workflow specification is authoritative.

Administrator/privacy reviewer responsibilities:

- Verify exceptional/manual requests
- Resolve scope or identity ambiguity
- Review redactions
- Record extensions or rejection grounds
- Approve justified holds
- Monitor participant completion

Administrators cannot download user exports without a separately justified case action.

## AD-09 — View Platform-Wide Analytics

Includes:

- Station/EVSE counts
- Booking demand
- Utilization
- Failure/no-show rates
- Simulator connectivity
- Workflow backlog
- Platform reliability

Rules:

- Data is aggregated and eventually consistent.
- Freshness and metric definitions are shown.
- Analytics does not grant access to individual records.
- Sensitive breakdowns require explicit authorization.

---

# Platform Support Use Cases

## SUP-01 — Create and Manage Support Cases

### Case creation

May originate from:

- Driver
- Operator
- Automated fulfilment workflow
- Administrator
- Security/operations escalation

### Required data

- Public case reference
- Requester type
- Category
- Priority
- Related resource references
- Description
- Assignment
- Status and timestamps

### Lifecycle

`OPEN → IN_PROGRESS → WAITING_FOR_USER/OPERATOR → RESOLVED → CLOSED`

A closed case may be reopened only through an audited transition.

### Rules

- Free text is sanitized and minimized.
- File attachments are deferred from v1.
- Cases use organization/user scope.
- Support cannot browse cases without assignment or queue permission.
- SLA timers and ageing are visible.

## SUP-02 — Assist With Bookings and Equipment Failures

Support may:

- View relevant booking state
- Explain policy and lifecycle outcomes
- Cancel where permission permits
- Initiate reassignment workflow
- Link fault incidents
- Escalate fulfilment failure
- Resend an existing approved notification
- Request operator action

Support cannot:

- Create a booking as the driver
- Change tariff snapshots
- Mark charging successful
- Bypass allocation checks
- Forge check-in
- Directly edit authoritative device state

Every state-changing action is performed through the owning capability.

## SUP-03 — Escalate Technical or Security Incident

### Flow

1. Classify issue as operational, technical, privacy or security.
2. Record severity and affected scope.
3. Preserve correlation IDs and safe evidence.
4. Assign appropriate responder.
5. Restrict case access where necessary.
6. Notify on-call/security reviewers.
7. Track mitigation and resolution.
8. Link post-incident review.

Rules:

- Support does not independently declare legal breach notification.
- Credentials are never copied into cases.
- Emergency action uses AD-06 rather than informal workarounds.
- Escalation remains traceable to the original case.

---

# Temporary Support Access

Support access record includes:

- Agent
- Assigned case
- Resource scope
- Permitted actions
- Start and expiry
- Reveal actions
- Reason
- Reviewer where required

Access expires automatically when:

- Case closes
- Assignment changes
- Time limit passes
- Agent role is removed
- Security intervention revokes access

---

# Break-Glass Workflow

States:

`REQUESTED → APPROVED/EMERGENCY_ACTIVATED → ACTIVE → EXPIRED/REVOKED → REVIEWED`

Controls:

1. Use only when ordinary permissions are insufficient and delay creates material risk.
2. Require MFA and recent reauthentication.
3. Restrict scope and duration.
4. Record justification before access where possible.
5. Alert independent reviewers immediately.
6. Record every action.
7. Revoke automatically.
8. Require post-use review.

Break-glass cannot disable audit logging or reveal credentials.

---

# Acceptance criteria

1. Support cannot access unrelated cases or bookings.
2. Masked fields remain masked without justified reveal.
3. Reveal access expires and is audited.
4. Administrator suspension triggers required downstream resolution.
5. Emergency intervention never fabricates a successful charging outcome.
6. Audit records cannot be edited through application APIs.
7. Reference data is deprecated rather than destructively removed.
8. Privacy exports cannot be accessed casually by administrators.
9. Analytics access does not expose individual user history.
10. Break-glass use triggers alerts and review.
11. Platform roles do not grant infrastructure administration.
12. Every state-changing support action is delegated to its authoritative owner.

## Traceability

| Use cases | Requirements |
|---|---|
| AD-01–04 | FR-ADM-01, FR-OPS-01 |
| AD-05–07 | FR-AUD-01, FR-SUP-01 |
| AD-08 | FR-PRV-01 |
| AD-09 | FR-ADM-01 |
| SUP-01–03 | FR-SUP-01, FR-OPS-02, FR-PLT-01 |