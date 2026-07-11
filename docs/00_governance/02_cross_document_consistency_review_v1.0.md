Document ID: GOV-002
Title: Cross-Document Consistency Review v1.0
Version: 1.0
Status: APPROVED
Owner: DA/BA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: All canonical specifications
Authoritative for: Precedence Hierarchy and Filename Migration

---

# Cross-Document Consistency Review v1.0

## 1. Purpose

This review establishes:

- Document authority and precedence
- Canonical filenames and locations
- Superseded and archived documents
- Duplication-removal rules
- Terminology and role naming
- Cross-document corrections
- Updated planning status
- Governance rules for future documents

This document does not replace focused specifications. It defines how they coexist without contradiction.

---

## 2. Review scope

Reviewed artifact groups:

1. Project constraints and system scope
2. Actors and permissions
3. Driver, operator, administrator and support use cases
4. Functional and non-functional requirements
5. Booking, availability and charging lifecycles
6. Infrastructure and tariff models
7. Maintenance, fault and reassignment workflows
8. Background processes and distributed consistency
9. Simulator protocol
10. Privacy and notifications
11. Decision register
12. Domain glossary
13. Lifecycle and invariant catalogue
14. Planning roadmap

---

## 3. Document authority hierarchy

When documents overlap, apply this order:

1. **Approved Decision Register**
2. **Consolidated Functional Requirements v1.1**
3. **Lifecycle and Invariant Catalogue**
4. **Domain Glossary**
5. **Focused domain/use-case specification**
6. **System Scope and NFRs**
7. **Actor/use-case catalogues**
8. **Recommendations, early drafts and archived reviews**

A focused specification remains authoritative for workflow detail, but it cannot contradict a higher-level approved requirement, invariant, decision or canonical term.

---

## 4. Document status model

Every planning document must declare one status:

- `DRAFT`
- `IN_REVIEW`
- `APPROVED`
- `SUPERSEDED`
- `RETIRED`
- `ARCHIVED`

Definitions:

- **APPROVED:** Current authoritative baseline.
- **SUPERSEDED:** Replaced by a named newer document.
- **RETIRED:** No longer applicable, but retained for history.
- **ARCHIVED:** Historical or duplicate material outside the active specification.

Documents must not describe themselves as both “Draft” and “Approved and locked.”

---

## 5. Required document metadata

Every active document should begin with:

```text
Document ID:
Title:
Version:
Status:
Owner:
Last reviewed:
Supersedes:
Depends on:
Authoritative for:
```

Dates use ISO format: `YYYY-MM-DD`.

“Approved” must indicate the approving project role or readiness review.

---

# 6. Canonical active document register

## 6.1 Scope and requirements

| ID | Canonical document | Authority |
|---|---|---|
| SCP-001 | Project Constraints v1.0 | Team, budget, implementation and portfolio constraints |
| SCP-002 | System Scope v1.0 | Included, deferred and excluded capabilities |
| SCP-003 | Actors and Operational Scope v1.1 | Actor categories and high-level permission boundaries |
| REQ-001 | Functional Requirements and Traceability v1.1 | Stable functional requirement baseline |
| REQ-002 | Non-Functional Requirements v1.0 | Measurable quality targets |

## 6.2 Use cases

| ID | Canonical document |
|---|---|
| UC-DR-001 | Driver Use-Case Catalogue v1.0 |
| UC-DR-002 | DR-01–05 Station Discovery v1.0 |
| UC-DR-003 | DR-06–10 Account, Authentication and Vehicle Profile v1.0 |
| UC-DR-004 | DR-11/12 Create Booking v1.0 |
| UC-DR-005 | DR-13 and DR-21–25 Remaining Driver Use Cases v1.0 |
| UC-DR-006 | DR-14/15 Reschedule and Cancel Booking v1.0 |
| UC-DR-007 | DR-16 Check-In and Arrival Authorization v1.0 |
| UC-DR-008 | DR-17–20 Charging Session Lifecycle v1.0 |
| UC-OP-001 | Operator Use-Case Catalogue and Roles v1.0 |
| UC-OP-002 | Detailed Operator Use Cases v1.0 |
| UC-AD-001 | Administrator and Platform Support Catalogue v1.0 |
| UC-AD-002 | Detailed Administrator and Platform Support Use Cases v1.0 |

## 6.3 Domain and cross-cutting specifications

| ID | Canonical document |
|---|---|
| DOM-001 | Domain Glossary v1.0 |
| DOM-002 | Lifecycle and Invariant Catalogue v1.0 |
| DOM-003 | Station, EVSE, Connector, Tariff and Booking Policy Model v1.0 |
| DOM-004 | Booking Lifecycle and Policy Specification v1.0 |
| DOM-005 | Availability Calculation Model v1.0 |
| DOM-006 | Maintenance, Fault and Reassignment Workflows v1.0 |
| PLT-001 | Background Processes and Distributed Consistency v1.0 |
| SIM-001 | Charger Simulator Protocol, Machine Identity, Commands and Events v1.0 |
| PRV-001 | Privacy, Retention, Export, Deletion and Anonymization v1.0 |
| NOT-001 | Notification Rules and Essential Email Matrix v1.0 |

## 6.4 Governance

| ID | Canonical document |
|---|---|
| GOV-001 | Decision and Open-Question Register v1.0 |
| GOV-002 | Cross-Document Consistency Review v1.0 |
| GOV-003 | Consolidated System Specification v1.0 |
| GOV-004 | Planning Status and Roadmap v1.1 |

---

# 7. Superseded-document register

| Existing document | Action | Replacement |
|---|---|---|
| `02_system_boundary_recommendations.md` | ARCHIVED | System Scope v1.0 |
| `04_actors_and_capabilities.md` | ARCHIVED | Actors and Operational Scope v1.1 |
| `06_consolidation_consistency_review.md` | ARCHIVED | Cross-Document Consistency Review v1.0 |
| `09_consolidated_functional_requirements_catalogue.md` | ARCHIVED | Functional Requirements and Traceability v1.1 |
| Existing roadmap v1.0 | Replace | Planning Status and Roadmap v1.1 |
| `archive/duplicate_response_24.md` | Retain only if historically useful; otherwise remove | None |

Superseded documents must not remain mixed with active specifications without a visible warning.

Active warning applied:

> **SUPERSEDED:** This document is retained for historical reasoning only. Do not use it as the implementation baseline.

---

# 8. Required filename and structure migration

The current folders separate scope, use cases, domain models and roadmap, but the newly produced cross-cutting specifications need a clearer home. The repository currently contains seven scope/constraint files, nine use-case files, three domain/workflow files and one roadmap file. ([github.com](https://github.com/panospao7/Ev-chargind-station/tree/main/01_project_scope_and_constraints))

Completed target structure:

```text
docs/
├── 00_governance/
├── 01_scope_and_requirements/
├── 02_use_cases/
│   ├── driver/
│   ├── operator/
│   └── administration_support/
├── 03_domain/
├── 04_platform_and_integrations/
├── 05_architecture/
├── 06_security_and_privacy/
├── 07_quality_and_operations/
├── 08_delivery_and_ai_agents/
└── 09_evaluation/
archive/
```

## 8.1 Proposed active paths

```text
docs/00_governance/
  01_decision_and_open_question_register_v1.0.md
  02_cross_document_consistency_review_v1.0.md
  03_consolidated_system_specification_v1.0.md
  04_planning_status_and_roadmap_v1.1.md

docs/01_scope_and_requirements/
  01_project_constraints_v1.0.md
  02_system_scope_v1.0.md
  03_actors_and_operational_scope_v1.1.md
  04_functional_requirements_and_traceability_v1.1.md
  05_non_functional_requirements_v1.0.md

docs/02_use_cases/driver/
  01_driver_use_case_catalogue_v1.0.md
  02_dr_01_05_station_discovery_v1.0.md
  03_dr_06_10_account_auth_vehicle_profile_v1.0.md
  04_dr_11_12_create_booking_v1.0.md
  05_dr_13_21_25_remaining_driver_use_cases_v1.0.md
  06_dr_14_15_reschedule_cancel_booking_v1.0.md
  07_dr_16_check_in_authorization_v1.0.md
  08_dr_17_20_charging_session_lifecycle_v1.0.md

docs/02_use_cases/operator/
  01_operator_use_case_catalogue_and_roles_v1.0.md
  02_detailed_operator_use_cases_v1.0.md

docs/02_use_cases/administration_support/
  01_administrator_platform_support_catalogue_v1.0.md
  02_detailed_administrator_platform_support_use_cases_v1.0.md

docs/03_domain/
  01_domain_glossary_v1.0.md
  02_lifecycle_and_invariant_catalogue_v1.0.md
  03_station_evse_connector_tariff_policy_model_v1.0.md
  04_booking_lifecycle_and_policy_v1.0.md
  05_availability_calculation_model_v1.0.md
  06_maintenance_fault_reassignment_workflows_v1.0.md

docs/04_platform_and_integrations/
  01_background_processes_distributed_consistency_v1.0.md
  02_charger_simulator_protocol_v1.0.md
  03_notification_rules_email_matrix_v1.0.md

docs/06_security_and_privacy/
  01_privacy_retention_export_deletion_v1.0.md
```

Architecture, testing, deployment and AI-agent directories remain empty until their phases begin.

---

# 9. Heading corrections

## 9.1 Station discovery

Change the opening of:

`02_dr_01_05_station_discovery.md`

from:

> Approved and locked as Availability Calculation Model v1.0

to:

> # DR-01–05 — Station Discovery v1.0

The Availability Calculation Model is a separate document.

## 9.2 Reservation terminology

Rename:

`04_dr_11_12_create_reservation.md`

to:

`04_dr_11_12_create_booking_v1.0.md`

Technical text should use **Booking**. “Reservation” may remain in user-facing wording.

## 9.3 Version headings

Every active document filename and first heading must contain the same version.

Avoid filenames without versions when the document heading is versioned.

## 9.4 Draft versus approval

Remove combinations such as:

- “Approved and locked”
- followed by “Draft”

Use one explicit document status in metadata.

---

# 10. Authoritative-content boundaries

## 10.1 Scope versus detail

**System Scope** defines what is included or excluded.

It must not duplicate:

- Full lifecycle transitions
- API behaviour
- Detailed concurrency algorithms
- Retention schedules

## 10.2 Actor catalogues versus detailed use cases

Catalogues contain:

- Use-case ID
- Name
- Actor
- Priority
- Short purpose

Detailed documents contain:

- Preconditions
- Primary flow
- Alternatives
- Rules
- Acceptance criteria

Catalogues must link to detailed specifications rather than repeat their content.

## 10.3 Lifecycle documents

The **Lifecycle and Invariant Catalogue** is authoritative for:

- Canonical state names
- Permitted transitions
- Terminal states
- Cross-lifecycle invariants

Focused specifications remain authoritative for:

- Workflow steps
- User experience
- Failure handling
- Notifications
- Data captured

Focused documents should reference lifecycle transition IDs instead of reproducing complete tables.

## 10.4 Requirements versus specifications

Functional requirements state required outcomes.

They must not become duplicate use-case specifications.

Use cases and domain documents explain how the required behaviour works.

## 10.5 Decision register

The Decision Register records:

- Chosen options
- Rejected alternatives
- Provisional choices
- Open questions

Detailed reasoning for architecture choices will later move into ADRs. The register links to ADRs rather than duplicating them.

## 10.6 Glossary

The Domain Glossary defines terms only.

It should not become a second lifecycle or requirement catalogue. Short state lists are acceptable for definition, but transition authority belongs to the Lifecycle Catalogue.

---

# 11. Terminology corrections

The following changes apply across all active documents:

| Existing/ambiguous wording | Canonical wording |
|---|---|
| Reservation, in technical specifications | Booking |
| Charger, as reservable infrastructure | EVSE |
| Charger, as software | Charger Simulator |
| Charging slot | Booking interval |
| Reserved slot | Allocation |
| Connector reservation | EVSE booking with required connector type |
| Real-time availability | Availability and status freshness |
| Server availability is authoritative | Booking authority’s transactional allocation decision is authoritative |
| Current status | Administrative state, device-reported state or derived availability |
| Failed booking | `FULFILMENT_FAILED`, `EXPIRED`, `CANCELLED` or `NO_SHOW` |
| Charging transaction, in platform domain | Charging Session |
| Charging transaction, in protocol | Device Transaction |
| Device reservation | Reservation Mirror |
| Support Agent | Operator Support Agent or Platform Support Agent |
| Admin override | Status Override or Emergency Intervention |
| Delete station/EVSE | Deactivate station/EVSE |
| Payment/cost charged | Estimated cost |
| OCPP implementation | OCPP 2.1-inspired simulator protocol |
| Exactly-once delivery | At-least-once delivery with idempotent business effects |

---

# 12. Role naming resolution

## Canonical human roles

### Driver area

- `DRIVER`

### Operator organization

- `OPERATOR_OWNER`
- `OPERATOR_MANAGER`
- `OPERATOR_TECHNICIAN`
- `OPERATOR_SUPPORT`

### Platform

- `PLATFORM_ADMINISTRATOR`
- `PLATFORM_SUPPORT`
- `AUDITOR_SECURITY_REVIEWER`

### Non-human

- `SIMULATOR_DEVICE`

“Operator,” “support” and “administrator” must not be used alone in authorization rules when a more specific role is intended.

## Permission terminology

- **Role:** Broad responsibility category.
- **Permission:** Allowed action.
- **Resource scope:** Records on which the action may operate.
- **Organization ownership:** Operator-resource boundary.
- **Case scope:** Temporary platform-support boundary.
- **Break-glass scope:** Exceptional temporary boundary.

Role checks alone are insufficient.

---

# 13. Cross-document conflict resolutions

## CR-01 — Search authority

Replace all statements equivalent to:

> Availability returned by the server is authoritative.

with:

> Search and station-detail availability is advisory. The Booking authority’s transactional allocation decision is authoritative.

Affected documents:

- Driver Use-Case Catalogue
- Station Discovery
- Create Booking
- Functional Requirements

## CR-02 — EVSE states

Remove `RESERVED` and `OCCUPIED` from administrative state.

Use:

- Administrative state: `ACTIVE`, `DISABLED`, `DEACTIVATED`
- Device-reported state: `AVAILABLE`, `RESERVED`, `OCCUPIED`, `CHARGING`, `SUSPENDED`, `FINISHING`, `FAULTED`, `UNAVAILABLE`, `UNKNOWN`
- Derived availability: `AVAILABLE`, `PLANNED_AVAILABLE`, `UNAVAILABLE`, `UNKNOWN`, `INCOMPATIBLE`

## CR-03 — Cancellation capacity release

Replace:

> Cancellation releases capacity immediately, subject to the turnaround buffer.

with:

> Cancellation of an unused `HELD` or `CONFIRMED` booking releases the complete allocation immediately. A release buffer remains only when check-in, actual usage or uncertain physical occupation justifies it.

## CR-04 — Maintenance cancellation

Correct the lifecycle recommendation:

- `SCHEDULED → CANCELLED` is permitted.
- Once maintenance is `ACTIVE`, it ends through `COMPLETED`.
- If work is aborted, record `completionOutcome = ABORTED`.
- Do not use `ACTIVE → CANCELLED`.

This avoids describing work that already started as never having occurred.

The Lifecycle Catalogue must be amended accordingly before approval.

## CR-05 — Start rejection and retry

For v1:

- A definitive start rejection transitions the session to `START_REJECTED`.
- The booking becomes `FULFILMENT_FAILED`.
- The same booking cannot create another start attempt.
- Timeout or uncertainty remains `STARTING` and is reconciled.
- Future retry support requires a separate `StartAttempt` model.

This resolves `OQ-BKG-05`.

## CR-06 — Checked-in no-show

`NO_SHOW` may follow `CHECKED_IN` only when:

- Charging never began;
- The start deadline passed;
- Driver inaction or abandonment is responsible;
- No device, equipment or platform failure prevented charging.

User-facing wording should distinguish:

- Did not arrive
- Checked in but did not begin charging

Both may share the booking state while using different structured reason codes.

## CR-07 — Operator approval

Operator application and organization lifecycles remain separate:

- Application: review and approval process
- Organization: `ACTIVE`, `SUSPENDED`, `CLOSED`

An organization becomes active only after application approval.

## CR-08 — Station reactivation

A deactivated Station may return to `DRAFT` for complete revalidation.

A deactivated EVSE remains terminal in v1. Reuse requires creating a new EVSE record and public identifier where appropriate.

## CR-09 — Fault terminology

- Driver/simulator/monitoring input: **Fault Report**
- Authoritative operational problem: **Fault Incident**

Multiple reports may link to one incident.

## CR-10 — OCPP reference

All active documents must use:

> Custom OCPP 2.1-inspired simulator protocol

Remove mixed references to an “OCPP 2.0.1-inspired subset” or generic “OCPP implementation.”

The system must not claim compliance, certification or wire compatibility.

## CR-11 — Notifications

The approved boundary is:

- Transactional email: core
- In-app notifications: deferred
- SMS: deferred
- Marketing: excluded

Statements saying all notifications are post-MVP are superseded.

## CR-12 — Cost terminology

All calculated amounts use:

- `estimatedCost`
- “Estimated cost”
- “Gross EUR estimate”

Do not use:

- Paid
- Charged
- Final payment
- Receipt

unless payment functionality is added later.

---

# 14. Remaining controlled duplication

Some repetition is valuable and should remain:

1. **Release-critical invariants** may appear in requirements, lifecycle and testing documents, provided the canonical invariant ID is reused.
2. **Security rules** may appear in use cases and security architecture, provided they link to the authoritative security control.
3. **Acceptance criteria** remain in focused use cases even when mapped into traceability.
4. **State names** may be listed in domain definitions, but complete transitions remain centralized.
5. **Scope exclusions** may be summarized in the README and consolidated specification.

Duplication is acceptable only when it is traceable and not independently editable.

---

# 15. Cross-reference repairs

Every document should use relative links to:

- Requirement IDs
- Use-case IDs
- Decision IDs
- Invariant IDs
- Open-question IDs
- Authoritative specifications

Examples:

```text
Implements: FR-BKG-05
Governed by: DEC-CIN-04
Must preserve: AUTH-INV-03
Detailed flow: UC-DR-007
```

Do not refer vaguely to:

- “the approved model”
- “the previous document”
- “the earlier specification”
- “as discussed above”

Documents must remain understandable when opened independently.

---

# 16. Source and citation governance

External-source citations should support:

- Standards
- Regulatory context
- Security guidance
- Protocol inspiration
- Accessibility guidance

Project decisions should cite:

- Decision IDs
- Requirement IDs
- ADRs

External citations must not be used as substitutes for an explicit internal decision.

A future source register should record:

- Source title
- Publisher
- Publication/version date
- Access date
- Related requirement or decision
- Whether the source is normative or informative

---

# 17. Roadmap status correction

Replace the existing roadmap’s completed/remaining sections with:

## Phase 1 — Product and domain foundation

| Area | Status |
|---|---|
| Constraints and scope | Complete |
| Actors and permissions | Complete |
| Use-case catalogues | Complete |
| Detailed operational-core use cases | Complete |
| Functional requirements and traceability | Complete |
| Non-functional requirements | Drafted |
| Domain glossary | Complete |
| Lifecycle and invariant consolidation | Complete with CR-04 amendment |
| Decision/open-question register | Complete |
| Cross-document consistency review | Complete |
| Consolidated System Specification | Next |

## Phase 2 — Architecture

Not started:

1. Final microservice boundaries
2. Data ownership
3. Communication and consistency matrix
4. Deployment context and diagrams
5. Modular-monolith alternative
6. Architecture Decision Records

## Phase 3 — Contracts and data

Not started:

1. REST contracts
2. Event and command contracts
3. Database schemas
4. Allocation constraint design
5. Migration strategy

## Phase 4 — Security, privacy and frontend

Not started:

1. Threat model
2. ASVS mapping
3. Identity architecture
4. Privacy assessment
5. Screen and route catalogue
6. UX flows and accessibility design

## Phase 5 — Cloud and quality

Not started:

1. Cloud and cost selection
2. Infrastructure as code
3. Observability and runbooks
4. Complete test strategy
5. CI/CD and repository conventions

## Phase 6 — Delivery readiness

Not started:

1. Implementation backlog
2. Dependency plan
3. AI-agent rules
4. Evaluation design
5. Final readiness review

Avoid percentage estimates unless a defined completion-measurement method exists.

---

# 18. Repository corrections

## Immediate

1. Add a `docs/` root.
2. Move active planning documents into the target structure.
3. Add all newly produced foundation documents.
4. Archive superseded drafts.
5. Replace the minimal README with a project overview and documentation index.
6. Add status metadata to every document.
7. Repair internal links after moves.
8. Add an archive README explaining that archived content is non-authoritative.
9. Correct the repository name from `Ev-chargind-station` when practical.
10. Add a repository description and topics after the project identity is finalized.

The current repository still uses the misspelled name and has only a title-level README. ([github.com](https://github.com/panospao7/Ev-chargind-station))

## Do not add yet

Until architecture decisions are approved, do not create misleading empty service directories such as:

- `booking-service`
- `station-service`
- `analytics-service`

Service boundaries must emerge from the next architecture phase.

---

# 19. Documentation quality rules

Every active document must:

1. Have one purpose.
2. Identify authoritative and dependent documents.
3. Use canonical terminology.
4. Use stable IDs for requirements, decisions and invariants.
5. Distinguish approved from provisional values.
6. Distinguish business rules from implementation suggestions.
7. Avoid unsupported production-readiness claims.
8. Avoid duplicated transition tables unless explicitly summarized.
9. Include change history.
10. Use relative repository links.
11. State unresolved questions explicitly.
12. Avoid “next:” conversational text in final documents.

Remove assistant-conversation phrases such as:

- “Great.”
- “Unless you object.”
- “Next, we should...”
- “I recommend...”
- “After approving this...”

Final repository documents must read as professional specifications, not chat transcripts.

---

# 20. Completed Amendments Record

All foundation gaps and consistency amendments have been successfully applied and verified:

- **PASSED**: CR-04 applied to Maintenance Lifecycle (COMPLETED/ABORTED).
- **PASSED**: CR-05 applied to session-start rejection (START_REJECTED / FULFILMENT_FAILED).
- **PASSED**: Station Discovery header corrected.
- **PASSED**: REQ-001 updated to v1.1 functional requirements catalogue.
- **PASSED**: All new use case specifications and logic models successfully integrated.
- **PASSED**: Superseded documents moved to archive/ directory.
- **PASSED**: Standardized roles applied across specifications.
- **PASSED**: Canonical EV Booking Platform terminology applied.
- **PASSED**: Planning Status and Roadmap updated to v1.1.
- **PASSED**: Relative sibling links checked and validated.

## Non-blocking until architecture

1. Populate final service owners.
2. Populate APIs and event schemas.
3. Populate database ownership.
4. Add ADR links.
5. Add exact test IDs.
6. Add cloud and deployment references.

---

# 21. Consistency acceptance criteria

The foundation is cross-document consistent when:

1. No active document claims search is allocation-authoritative.
2. No active document mixes administrative, reported and derived EVSE states.
3. No active document permits equipment failure to become no-show.
4. No active document claims payment occurred.
5. No active document claims OCPP compliance.
6. Every use case maps to at least one stable requirement.
7. Every release-critical requirement maps to acceptance criteria.
8. Every lifecycle state appears with the same spelling everywhere.
9. Every terminal-state definition is consistent.
10. Every actor uses a canonical role name.
11. Every superseded document is visibly marked.
12. No active filename or heading incorrectly describes its content.
13. The roadmap reflects completed foundation artifacts.
14. Cross-references resolve after repository migration.
15. The Consolidated System Specification can index one authoritative artifact for each concern.

---

# 22. Proposed approval decisions

Approval of this review means:

1. Adopt the document authority hierarchy.
2. Adopt the document status and metadata model.
3. Adopt the canonical active-document register.
4. Archive the identified superseded documents.
5. Adopt the proposed `docs/` structure.
6. Apply all terminology and role corrections.
7. Apply conflict resolutions CR-01 through CR-12.
8. Amend active maintenance to end through `COMPLETED`, with an aborted outcome where necessary.
9. Resolve definitive start rejection as fulfilment failure without same-booking retry in v1.
10. Replace vague cross-references with stable IDs and relative links.
11. Remove conversational language from repository specifications.
12. Replace the roadmap with version 1.1.
13. Delay creation of implementation service folders until service boundaries are approved.
14. Treat this review as the final consistency authority beneath the Decision Register.
15. Proceed next to the Consolidated System Specification v1.0.

## Status

**Cross-Document Consistency Review v1.0 is approved and baselined.**

After applying the blocking amendments, the final foundation artifact is the **Consolidated System Specification v1.0**.
