Document ID: REQ-001
Title: Functional Requirements and Traceability v1.1
Version: 1.1
Status: APPROVED
Owner: DA/BA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: All functional specifications
Authoritative for: Functional Requirements and Traceability Mapping

---

# Consolidated Functional Requirements Catalogue and Traceability Matrix v1.1

## 1. Purpose

This document replaces **Consolidated Functional Requirements Catalogue v1.0**.

It provides traceability between:

- Functional requirements
- Actors and use cases
- Authoritative specifications
- Business rules and invariants
- Provisional logical owners
- Planned interfaces
- Authoritative data
- Security controls
- Verification
- Implementation epics

Logical capability ownership does not yet represent final microservice boundaries.

---

# 2. Requirement terminology

- **MUST:** Required for the initial operational release.
- **SHOULD:** Planned but may be deferred without invalidating the operational core.
- **MAY:** Optional extension.
- **Authoritative owner:** The only capability permitted to make the final business decision and update the authoritative record.
- **Projection:** A non-authoritative, eventually consistent copy or read model.

Every MUST requirement must have:

1. An authoritative owner.
2. Acceptance criteria.
3. Automated verification.
4. Security controls.
5. An implementation epic.
6. Traceability to API/event/data designs before implementation.

---

# 3. Source specification register

| Key | Specification |
|---|---|
| SCOPE | System Scope v1 |
| ACT | Actors and Operational Scope v1.1 |
| NFR | Non-Functional Requirements v1.0 |
| DR-CAT | Driver Use-Case Catalogue |
| DR-DIS | DR-01–05 Station Discovery |
| DR-IAM | DR-06–10 Account, Authentication and Vehicle Profile |
| DR-BKG | DR-11/12 Create Booking |
| DR-VIEW | DR-13 Upcoming Booking Details |
| DR-RSC | DR-14/15 Reschedule and Cancel |
| DR-CIN | DR-16 Check-In and Arrival Authorization |
| DR-CHG | DR-17–20 Charging Session Lifecycle |
| DR-REM | DR-21–25 Remaining Driver Use Cases |
| BKG-LC | Booking Lifecycle and Policies |
| AVL | Availability Calculation Model |
| DOMAIN | Station, EVSE, Connector and Tariff Model |
| OP-CAT | Operator Use Cases and Roles |
| OP-DET | Detailed Operator Use Cases |
| MAINT | Maintenance, Fault and Reassignment Workflows |
| AD-SUP | Detailed Administrator and Support Use Cases |
| SIM | Charger Simulator Protocol |
| DIST | Background Processes and Distributed Consistency |
| PRV | Privacy, Retention, Export, Deletion and Anonymization |
| NOT | Notification Rules and Essential Email Matrix |
| PERM | Administrator and Platform Support Permission Model |

Where specifications overlap, the most focused specification is authoritative.

---

# 4. Repaired Functional Requirements Catalogue

## 4.1 Identity and access management

### FR-IAM-01 — Account lifecycle — MUST

The platform must support driver registration, email verification, authentication, recovery, suspension, reactivation, deletion initiation and identity-session revocation.

**Acceptance outcome:** Only verified, active accounts may create bookings or start charging sessions.

### FR-IAM-02 — Privileged MFA — MUST

All operator, administrator, platform-support and security-reviewer accounts must use MFA.

**Acceptance outcome:** A privileged role cannot access protected functionality without satisfying MFA policy.

### FR-IAM-03 — Authorization — MUST

Every protected operation must apply role, organization, resource-ownership, case-scope and exceptional-access rules as applicable.

**Acceptance outcome:** Gateway authorization alone is insufficient; the authoritative capability denies unauthorized access independently.

### FR-IAM-04 — Vehicle and compatibility profile — MUST

Drivers must be able to manage saved vehicles, connector compatibility, maximum AC/DC power and a default vehicle.

Drivers must also be able to book without saving a vehicle by manually selecting connector requirements.

### FR-IAM-05 — Login-session management — SHOULD

Drivers should be able to inspect and revoke active identity sessions without exposing raw session identifiers or tokens.

---

## 4.2 Discovery

### FR-DIS-01 — Public discovery — MUST

Unauthenticated users must be able to browse, search and filter published Greek charging stations through accessible map and list views.

### FR-DIS-02 — Infrastructure details — MUST

Users must be able to inspect public station, EVSE, connector, power, tariff, opening-hours, access and operational-freshness information.

### FR-DIS-03 — Restorable search state — SHOULD

Search parameters should be represented in the URL so discovery results can be restored or shared without storing precise user-location history.

---

## 4.3 Availability

### FR-AVL-01 — Interval availability — MUST

The platform must derive availability for a requested interval from:

- Administrative eligibility
- Connector and power compatibility
- Opening hours and policy
- Existing allocations and buffers
- Charging sessions
- Maintenance
- Faults and overrides
- Device connectivity and freshness

### FR-AVL-02 — Availability confidence — MUST

The platform must distinguish:

- `AVAILABLE`
- `PLANNED_AVAILABLE`
- `UNAVAILABLE`
- `UNKNOWN`
- `INCOMPATIBLE`

Stale or unknown near-term status must never appear as confidently available.

### FR-AVL-03 — Availability authority — MUST

Search availability must be advisory. The authoritative booking transaction must revalidate all allocation rules before committing.

---

## 4.4 Booking

### FR-BKG-01 — Hold and confirm — MUST

A driver must be able to create a temporary EVSE hold and confirm it before expiry.

### FR-BKG-02 — Double-booking prevention — MUST

The platform must prevent overlapping EVSE allocations, including buffers, under concurrent requests.

Exactly one conflicting request may succeed.

### FR-BKG-03 — Atomic rescheduling — MUST

Rescheduling must atomically claim the replacement interval, update the booking and release the original interval.

If replacement fails, the original booking must remain unchanged.

### FR-BKG-04 — Booking lifecycle outcomes — MUST

The platform must support confirmation, cancellation, expiration, no-show classification, completion and fulfilment failure according to validated transitions.

### FR-BKG-05 — Check-in and start authorization — MUST

A booking owner must be able to check in at the assigned EVSE during the valid window and receive one short-lived, single-use start authorization.

### FR-BKG-06 — Immutable snapshots — MUST

Confirmed bookings must preserve immutable tariff and effective booking-policy snapshots.

### FR-BKG-07 — Booking details and actions — MUST

Drivers must be able to view authoritative upcoming booking details, state, warnings and server-derived permitted actions.

---

## 4.5 Charging sessions

### FR-CHG-01 — Session lifecycle — MUST

The platform must start, monitor, stop, interrupt and reconcile simulated charging sessions separately from booking state.

### FR-CHG-02 — Event correctness — MUST

Duplicate, delayed and out-of-order transaction and meter events must be processed safely without repeating state transitions or inflating energy.

### FR-CHG-03 — Reproducible summary — MUST

The platform must produce a reproducible session summary containing duration, energy, tariff snapshot, estimated cost, stop reason and completion outcome.

### FR-CHG-04 — Uncertain outcomes — MUST

A timed-out or disconnected start/stop outcome must remain visibly uncertain until reconciliation establishes the physical simulated result.

---

## 4.6 Driver records and assistance

### FR-HIS-01 — Booking and session history — MUST

Drivers must be able to view their authoritative booking and charging-session history with filtering, pagination and clear interrupted/uncertain outcomes.

### FR-FLT-01 — Driver fault reporting — SHOULD

Authenticated drivers should be able to submit structured station or EVSE fault reports and link them to a booking or session where applicable.

Reports must not directly alter operational state.

---

## 4.7 Operator and infrastructure management

### FR-OPS-01 — Organization and staff management — MUST

The platform must support operator applications, approval-dependent organization activation, organization profiles, invitations, roles, ownership transfer and organization audit history.

### FR-OPS-02 — Infrastructure management — MUST

Authorized operators must be able to manage stations, EVSEs, connectors, opening hours, access information, tariffs and booking policies.

### FR-OPS-03 — Operational workflows — MUST

Authorized operators must be able to monitor device state, manage maintenance, faults, expiring overrides, booking impact and fulfilment resolution.

### FR-OPS-04 — Booking intervention — MUST

Scoped operator staff must be able to inspect owned-station bookings and perform permitted reassignment or cancellation through the Booking authority.

### FR-OPS-05 — Operator analytics and exports — SHOULD

Owners and managers should be able to view organization-level utilization, energy, cancellation, no-show and failure analytics and export aggregated reports.

---

## 4.8 Administration and support

### FR-ADM-01 — Platform governance — MUST

Administrators must be able to approve/suspend operators, suspend users, moderate stations and manage versioned platform reference data and policy limits.

### FR-ADM-02 — Incident and emergency intervention — MUST

Authorized administrators must be able to investigate incidents and perform justified, MFA-protected, audited emergency interventions without fabricating device outcomes.

### FR-ADM-03 — Platform analytics — SHOULD

Administrators should be able to inspect aggregated platform-wide operational analytics with freshness and metric definitions.

### FR-SUP-01 — Scoped support cases — MUST

Platform support must manage assigned support cases and access only the masked user, booking and operational data needed for those cases.

### FR-SUP-02 — Support assistance and escalation — MUST

Support must be able to request permitted cancellation, reassignment and fulfilment workflows through authoritative capabilities and escalate operational, privacy or security incidents.

---

## 4.9 Charger simulation

### FR-SIM-01 — Simulator machine identity — MUST

Every simulated charging station must use a unique, revocable machine identity scoped to its assigned station and EVSEs.

### FR-SIM-02 — Commands and events — MUST

The simulator must support versioned boot, heartbeat, status, reservation-mirror, start, stop, transaction, meter, fault and reconciliation communication.

### FR-SIM-03 — Failure behaviour — MUST

The simulator must support deterministic duplicate, delayed, dropped and out-of-order events, disconnections, command rejection, timeout and queued offline delivery.

### FR-SIM-04 — Protocol representation — MUST

The platform must describe the simulator as an OCPP-inspired custom protocol and must not claim OCPP compliance, certification or wire compatibility.

---

## 4.10 Distributed platform processes

### FR-PLT-01 — Transactional event publication — MUST

Authoritative changes requiring asynchronous propagation must use transactional outbox delivery.

### FR-PLT-02 — Idempotent consumption — MUST

Asynchronous consumers must safely handle at-least-once delivery through inbox/deduplication controls.

### FR-PLT-03 — Retry and dead-letter processing — MUST

Transient failures must use bounded retries with backoff and jitter. Exhausted or invalid messages must enter controlled dead-letter handling.

### FR-PLT-04 — Scheduled lifecycle processing — MUST

The platform must process hold expiry, no-shows, maintenance transitions, status freshness, reminders, reconciliation and retention safely across multiple workers.

### FR-PLT-05 — Reconciliation — MUST

The platform must reconcile uncertain device commands, missing event sequences, conflicting state and partial cross-capability workflows.

### FR-PLT-06 — Projection isolation — MUST

Search, analytics, notification and dashboard projections must remain non-authoritative and must not block committed core operations when unavailable.

---

## 4.11 Notifications

### FR-NOT-01 — Essential transactional email — MUST

The platform must send essential security, account, booking, operational and privacy email asynchronously after authoritative commit.

### FR-NOT-02 — Notification preferences — SHOULD

Drivers should be able to configure optional reminders and routine summaries but must not disable essential messages.

### FR-NOT-03 — Secure action links — MUST

Verification, recovery, invitation, email-change and privacy-download links must be expiring, purpose-bound and single-use where applicable.

---

## 4.12 Privacy

### FR-PRV-01 — Data access and portability — MUST

The platform must coordinate secure access and applicable portability exports across all mandatory data owners.

### FR-PRV-02 — Deletion and anonymization — MUST

The platform must coordinate account deletion, pseudonymization, anonymization and projection cleanup without deleting unresolved obligations.

### FR-PRV-03 — Retention enforcement — MUST

Versioned retention jobs must delete, redact, aggregate or anonymize eligible data and reapply completed privacy actions after backup restoration.

### FR-PRV-04 — Privacy restrictions and corrections — SHOULD

The platform should support correction metadata and restriction of processing without destructively rewriting historical facts.

---

## 4.13 Audit

### FR-AUD-01 — Immutable business and security audit — MUST

Security-sensitive and business-critical actions must create immutable audit evidence containing actor, action, target, time, reason, outcome and correlation information.

### FR-AUD-02 — Privileged access audit — MUST

Data reveal, emergency intervention, dead-letter replay, privacy review and break-glass actions must be separately auditable and reviewable.

---

# 5. Full Forward Traceability Matrix

Interfaces listed below are logical operations. They are not final REST endpoints or event schemas.

## 5.1 Identity, discovery and availability

| Requirement | Use cases | Sources | Provisional owner | Logical interface | Authoritative data | Security | Primary verification | Epic |
|---|---|---|---|---|---|---|---|---|
| IAM-01 | DR-06, DR-07, DR-25, AD-02 | DR-IAM, PRV, AD-SUP | Identity + Profile | Register, Verify, Recover, Suspend, DeleteAccount | Identity account, profile lifecycle | OIDC, PKCE, generic errors, rate limits | Account lifecycle and enumeration tests | EP-IAM-01 |
| IAM-02 | OP-01–25, AD-01–09, SUP-01–03 | DR-IAM, OP-DET, AD-SUP | Identity | EnforceMfaPolicy | MFA policy and factors | MFA, recent authentication | Privileged-login tests | EP-IAM-02 |
| IAM-03 | All protected use cases | ACT, PERM, OP-DET, AD-SUP | Every owner | Authorize(action, scope) | Roles, memberships, case scope | Default deny, ownership, least privilege | Authorization matrix tests | EP-SEC-01 |
| IAM-04 | DR-08, DR-09 | DR-IAM | Profile | ManageVehicle, ResolveCompatibility | Vehicle and connector preferences | Owner-only access | CRUD and compatibility tests | EP-PRO-01 |
| IAM-05 | DR-10 | DR-IAM | Identity | ListSessions, RevokeSession | Identity sessions | No token exposure, reauthentication | Revocation tests | EP-IAM-03 |
| DIS-01 | DR-01–03 | DR-DIS | Discovery | SearchStations | Search projection | Public-safe fields, location consent | Search/filter/accessibility tests | EP-DIS-01 |
| DIS-02 | DR-04 | DR-DIS, DOMAIN | Discovery | GetStationDetails | Infrastructure projection | Public-field allowlist | Detail and leakage tests | EP-DIS-02 |
| DIS-03 | DR-01–05 | DR-DIS | Web client | EncodeSearchState | Browser URL state | No precise-history retention | Restore/share tests | EP-WEB-01 |
| AVL-01 | DR-03–05, DR-11–16, OP-13, OP-19 | AVL | Booking for authority; Discovery for projection | EvaluateAvailability | Allocations plus authoritative dependencies | Input validation, safe reason codes | Interval and policy tests | EP-AVL-01 |
| AVL-02 | DR-04, DR-05, OP-12 | AVL, SIM | Device + Availability | GetStatusConfidence | Heartbeats and derived result | No false availability | Fresh/stale/unknown tests | EP-AVL-02 |
| AVL-03 | DR-11, DR-12, DR-14 | AVL, DR-BKG | Booking | AllocateEvse | Booking allocation | Transaction isolation | Search-to-book race tests | EP-BKG-CORE |

## 5.2 Booking and charging

| Requirement | Use cases | Sources | Provisional owner | Logical interface/events | Authoritative data | Security | Primary verification | Epic |
|---|---|---|---|---|---|---|---|---|
| BKG-01 | DR-11, DR-12 | DR-BKG, BKG-LC | Booking | CreateHold, ConfirmHold; BookingConfirmed | Booking and hold | Verified owner, idempotency | Hold/expiry tests | EP-BKG-01 |
| BKG-02 | DR-11, DR-12, DR-14, OP-19 | DR-BKG, AVL | Booking | ClaimAllocation | Allocation interval | Transactional exclusion | Concurrent double-booking tests | EP-BKG-CORE |
| BKG-03 | DR-14 | DR-RSC | Booking | RescheduleBooking | Booking and allocation | Ownership, versioning | Atomic failure/race tests | EP-BKG-02 |
| BKG-04 | DR-15, background no-show/expiry | BKG-LC, DR-RSC, DIST | Booking | Cancel, Expire, MarkNoShow, MarkFailure | Booking lifecycle | State and actor validation | Transition/race tests | EP-BKG-03 |
| BKG-05 | DR-16, DR-17 | DR-CIN, DR-CHG | Booking | CheckIn, Issue/ConsumeAuthorization | Check-in and authorization | Single-use binding, expiry | Reuse/concurrency tests | EP-BKG-04 |
| BKG-06 | DR-11–15, DR-20, DR-21 | DR-BKG, DOMAIN | Booking | SnapshotTariffAndPolicy | Immutable snapshots | Write-once controls | Historical reproducibility tests | EP-BKG-05 |
| BKG-07 | DR-13 | DR-VIEW | Booking query | GetBooking, GetAllowedActions | Booking aggregate | Owner-only access | Object authorization tests | EP-BKG-06 |
| CHG-01 | DR-17–20, OP-17, AD-06 | DR-CHG, SIM | Charging | Start, Stop, ReconcileSession | Session aggregate | Booking authorization, scoped intervention | Lifecycle tests | EP-CHG-01 |
| CHG-02 | DR-18, DR-20 | DR-CHG, SIM, DIST | Charging | ApplyTransactionEvent | Event inbox and meter sequence | Machine identity, validation | Duplicate/order tests | EP-CHG-02 |
| CHG-03 | DR-20, DR-21 | DR-CHG | Charging | FinalizeSummary | Final session data | Owner/operator scope | Cost reproduction tests | EP-CHG-03 |
| CHG-04 | DR-17–20, OP-17, AD-06 | DR-CHG, DIST | Charging | ReconcileCommandOutcome | Command/session uncertainty | No fabricated completion | Timeout/disconnect tests | EP-CHG-04 |
| HIS-01 | DR-21 | DR-REM | Booking + Charging query | ListHistory | Authoritative booking/session records | Owner-only, pagination | History isolation tests | EP-HIS-01 |
| FLT-01 | DR-22 | DR-REM, MAINT | Operations | SubmitFaultReport | Driver report and fault links | Rate limits, sanitization | Duplicate/report privacy tests | EP-FLT-01 |

## 5.3 Operator, administration and support

| Requirement | Use cases | Sources | Provisional owner | Logical interface | Authoritative data | Security | Verification | Epic |
|---|---|---|---|---|---|---|---|---|
| OPS-01 | OP-01–05 | OP-DET | Organization | Apply, Invite, ChangeRole, TransferOwner | Organization and memberships | MFA, role hierarchy | Membership tests | EP-ORG-01 |
| OPS-02 | OP-06–11 | OP-DET, DOMAIN | Infrastructure | ManageStation/Evse/Connector/Tariff/Policy | Infrastructure configuration | Organization ownership, versioning | Invariant tests | EP-INF-01 |
| OPS-03 | OP-12–17, OP-21 | OP-DET, MAINT, SIM | Operations | ScheduleMaintenance, ManageFault, Override | Maintenance, fault, override | Technician scope, reasons | Workflow tests | EP-OPS-01 |
| OPS-04 | OP-18–22 | OP-DET, MAINT | Booking with operator facade | ViewOwnedBookings, Reassign, Cancel | Booking authority | Ownership and limited PII | Operator-scope tests | EP-OPS-02 |
| OPS-05 | OP-23–25 | OP-DET | Analytics | Query/ExportOrganizationMetrics | Analytics projection | Owner/manager only | Aggregation/privacy tests | EP-ANA-01 |
| ADM-01 | AD-01–04 | AD-SUP | Administration | Approve, Suspend, Moderate, ManageReference | Governance records | MFA, separation of duty | Admin permission tests | EP-ADM-01 |
| ADM-02 | AD-05–08 | AD-SUP, PRV | Administration/workflow owners | Investigate, EmergencyIntervene | Cases and intervention records | Recent MFA, reason, review | Break-glass tests | EP-ADM-02 |
| ADM-03 | AD-09 | AD-SUP | Analytics | PlatformMetrics | Analytics projection | Aggregated access | Analytics access tests | EP-ANA-02 |
| SUP-01 | SUP-01 | AD-SUP | Support | Create/Assign/UpdateCase | Support cases | Case-scoped access | Case isolation tests | EP-SUP-01 |
| SUP-02 | SUP-02, SUP-03 | AD-SUP | Support coordinator | RequestOwnerAction, Escalate | Case/workflow references | No direct foreign writes | Delegation tests | EP-SUP-02 |

## 5.4 Simulator and distributed processes

| Requirement | Use cases/processes | Sources | Owner | Logical interface/events | Data | Security | Verification | Epic |
|---|---|---|---|---|---|---|---|---|
| SIM-01 | OP-16, device actor | SIM | Device | Enroll, Authenticate, Revoke | Machine identity | TLS, credential rotation | Enrollment tests | EP-SIM-01 |
| SIM-02 | DR-17–20, OP-12, OP-16 | SIM | Device | Boot, Heartbeat, Start/Stop, Transaction/Meter events | Device/command history | Schema and assignment checks | Protocol-contract tests | EP-SIM-02 |
| SIM-03 | Test/system actor | SIM | Simulator | SetProfile, InjectFault | Scenario and seed | Operator scope | Deterministic resilience tests | EP-SIM-03 |
| SIM-04 | Documentation/system | SIM | Architecture governance | Protocol metadata | Version declarations | No false claims | Documentation review | EP-ARC-01 |
| PLT-01 | All event-producing operations | DIST | Every authoritative owner | AppendOutbox | Business transaction + outbox | Minimal payload | Broker-outage tests | EP-PLT-01 |
| PLT-02 | All consumers | DIST | Every consumer | ConsumeWithInbox | Inbox records | Consumer identity | Replay tests | EP-PLT-02 |
| PLT-03 | Async failures | DIST | Platform messaging | Retry, Quarantine, Replay | Retry/DLQ records | Scoped replay | DLQ tests | EP-PLT-03 |
| PLT-04 | Holds, no-shows, maintenance, retention | DIST | Owning capability | Scheduled transitions | Job leases and entities | Database time | Multi-worker race tests | EP-PLT-04 |
| PLT-05 | Device/privacy/operations workflows | DIST | Workflow coordinator | ReconcileWorkflow | Workflow state | Audited manual resolution | Partial-failure tests | EP-PLT-05 |
| PLT-06 | Search, analytics, notification | DIST | Projection owners | Build/RebuildProjection | Read models | Non-authoritative isolation | Projection outage tests | EP-PLT-06 |

## 5.5 Notifications, privacy and audit

| Requirement | Use cases | Sources | Owner | Logical interface/events | Data | Security | Verification | Epic |
|---|---|---|---|---|---|---|---|---|
| NOT-01 | DR-06/07, booking lifecycle, privacy | NOT | Notification/Identity | RequestNotification, Dispatch | Delivery records | Minimal PII, provider secrets | Provider-outage tests | EP-NOT-01 |
| NOT-02 | DR-23 | DR-REM, NOT | Profile + Notification | UpdatePreferences | Preferences | Owner-only | Preference tests | EP-NOT-02 |
| NOT-03 | DR-06/07/24/25, OP-03 | NOT, PRV | Identity/Privacy | CreateActionLink | Token metadata | Single-use, expiry | Link-security tests | EP-NOT-03 |
| PRV-01 | DR-24, AD-08 | PRV | Privacy coordinator | Request/AssembleExport | Export workflow | Reauthentication, encryption | Completeness tests | EP-PRV-01 |
| PRV-02 | DR-25, AD-08 | PRV | Privacy coordinator | Delete/AnonymizeAccount | Deletion workflow/tombstones | Blocker checks | Deletion propagation tests | EP-PRV-02 |
| PRV-03 | Background retention | PRV, DIST | Every data owner | ApplyRetention | Retention rules/evidence | Holds, least privilege | Retention/restore tests | EP-PRV-03 |
| PRV-04 | Privacy administration | PRV | Privacy coordinator | Correct, Restrict | Correction/restriction records | Reviewer scope | Restriction tests | EP-PRV-04 |
| AUD-01 | All critical use cases | PERM, DIST | Audit | RecordAuditFact | Append-only audit | Tamper resistance | Audit completeness tests | EP-AUD-01 |
| AUD-02 | AD-05–08, support reveal, DLQ replay | AD-SUP, PRV, DIST | Audit/security | RecordPrivilegedAction | Privileged audit | Separation of duty | Break-glass/reveal tests | EP-AUD-02 |

---

# 6. Reverse Use-Case Coverage

## Driver coverage

| Use cases | Covered requirements |
|---|---|
| DR-01–05 | DIS-01–03, AVL-01–03 |
| DR-06–07 | IAM-01, NOT-01, NOT-03, AUD-01 |
| DR-08–09 | IAM-04 |
| DR-10 | IAM-05 |
| DR-11–12 | BKG-01, BKG-02, BKG-06, AVL-03 |
| DR-13 | BKG-07 |
| DR-14 | BKG-03 |
| DR-15 | BKG-04 |
| DR-16 | BKG-05 |
| DR-17–20 | CHG-01–04 |
| DR-21 | HIS-01 |
| DR-22 | FLT-01 |
| DR-23 | NOT-02 |
| DR-24 | PRV-01 |
| DR-25 | IAM-01, PRV-02 |

All DR-01–25 use cases have requirement coverage.

## Operator coverage

| Use cases | Covered requirements |
|---|---|
| OP-01–05 | OPS-01 |
| OP-06–11 | OPS-02 |
| OP-12–17 | OPS-03, SIM-01–03 |
| OP-18–22 | OPS-04 |
| OP-23–25 | OPS-05 |

All OP-01–25 use cases have requirement coverage.

## Administrator/support coverage

| Use cases | Covered requirements |
|---|---|
| AD-01–04 | ADM-01 |
| AD-05–08 | ADM-02, AUD-02, PRV-01–04 |
| AD-09 | ADM-03 |
| SUP-01 | SUP-01 |
| SUP-02–03 | SUP-02 |

All AD-01–09 and SUP-01–03 use cases have requirement coverage.

---

# 7. Requirement-to-NFR Traceability

| NFR area | Principal functional requirements |
|---|---|
| Performance | DIS-01/02, AVL-01, BKG-01–03, SIM-02 |
| Reliability | BKG-02–05, CHG-04, PLT-01–06 |
| Durability | BKG-01, PLT-01, AUD-01 |
| Recovery | PLT-01–06, SIM-03, PRV-03 |
| Security | IAM-01–03, BKG-05, SIM-01, NOT-03, AUD-01/02 |
| Privacy | IAM-04, HIS-01, PRV-01–04, NOT-01 |
| Accessibility | DIS-01/02, BKG-07, HIS-01, NOT-01 |
| Operability | OPS-03, ADM-02, SIM-02/03, PLT-03–05 |
| Maintainability | SIM-04, PLT-01–06, AUD-01 |
| Observability | CHG-04, OPS-03, SIM-02, PLT-01–06 |

Exact measurable verification remains governed by NFR v1.0 and the future testing strategy.

---

# 8. Critical Correctness Invariants

The following invariants are release-blocking:

1. One EVSE cannot have two overlapping active allocations.
2. Expired holds do not block new authoritative allocation.
3. Search results cannot reserve capacity.
4. Failed rescheduling cannot remove the original allocation.
5. A user cannot access another driver’s booking or session.
6. An operator cannot access another organization’s resources.
7. A start authorization can be consumed only once.
8. One EVSE cannot have two active charging sessions.
9. Duplicate meter events cannot inflate energy or estimated cost.
10. Timed-out device commands cannot be reported as successful.
11. Equipment failure cannot classify a driver as a no-show.
12. Maintenance cannot normally activate over unresolved obligations.
13. Notification/analytics failure cannot reverse a committed booking.
14. Privacy deletion cannot be reported complete while mandatory participants remain incomplete.
15. Audit and outbox evidence must commit with the authoritative state change where required.

Every invariant requires automated scenario and concurrency coverage.

---

# 9. Architecture Traceability Placeholders

The following columns must be added after architecture design:

- Final microservice owner
- Synchronous API endpoint
- Asynchronous command/event schema
- Database/schema/table ownership
- Cache/read-model ownership
- Deployment component
- Observability dashboard and alerts
- Threat-model reference
- Test suite/file
- Backlog story IDs
- Release milestone

A requirement cannot be marked **implementation-ready** until these fields are populated.

---

# 10. Changes from v1.0

1. Added missing new specifications to traceability.
2. Split the ambiguous former `FR-IAM-04`.
3. Made saved vehicles and compatibility `MUST`.
4. Kept login-session inspection/revocation as `SHOULD`.
5. Added explicit search-versus-booking authority requirement.
6. Added upcoming booking-details coverage.
7. Added driver history and fault-reporting coverage.
8. Split simulator identity, protocol, failure and representation requirements.
9. Split the former broad platform-process requirement into six verifiable requirements.
10. Split notification delivery, preferences and secure links.
11. Split privacy export, deletion, retention and correction/restriction.
12. Added separate privileged-access auditing.
13. Expanded administration, support and operator requirements.
14. Added forward and reverse traceability.
15. Added implementation epics.
16. Added release-blocking correctness invariants.
17. Marked logical ownership and interfaces as provisional.

---

# 11. Remaining open traceability items

These are intentionally deferred until their planning phases:

1. Final microservice boundaries.
2. Final API paths and request/response schemas.
3. Event topic/queue names and complete payload schemas.
4. Database constraints and indexes.
5. Cloud components.
6. Final security threat mitigations.
7. Exact automated test IDs.
8. Backlog story decomposition.
9. Final retention approvals.
10. SHOULD requirements included in the first implementation release.

---

# 12. Approval decisions

Approval of v1.1 means:

1. This document replaces Functional Requirements Catalogue v1.0.
2. Requirement IDs in this document become stable.
3. Existing IDs must not be silently reused for different meanings.
4. Removed requirements must be marked `RETIRED`, not deleted.
5. Changed requirements require version history and impact analysis.
6. MUST requirements define the operational release baseline.
7. SHOULD requirements may be deferred only through a recorded release decision.
8. Logical owners remain provisional until microservice boundaries are approved.
9. Every final architecture and implementation artifact must trace back to these requirements.
10. A requirement cannot be declared complete without automated verification evidence.

## Status

**Gap 8 is closed at the requirements-planning level, pending approval of this v1.1 catalogue.**
</11_consolidated_functional_requirements_and_traceability_v1.1.md>
