Here is the final foundation index. It incorporates the consistency resolutions and keeps unresolved architecture decisions explicitly provisional.

<16_consolidated_system_specification_v1.0.md>
# Consolidated System Specification v1.0

**Document ID:** GOV-003  
**Version:** 1.0  
**Status:** IN_REVIEW  
**Owner:** Project Owner / Domain Architect  
**Last reviewed:** 2026-07-11  
**Supersedes:** None  
**Depends on:** GOV-001, GOV-002, REQ-001, REQ-002, DOM-001, DOM-002  
**Authoritative for:** System-level specification index and foundation baseline  

---

## 1. Purpose

This document is the primary entry point for the EV Charging Station Booking Platform specification.

It:

- Defines the approved system foundation.
- Identifies the authoritative specification for each concern.
- Summarizes scope, actors, domain concepts and critical behaviour.
- Records foundation-level architecture constraints.
- Links requirements to detailed specifications.
- Identifies provisional decisions and unresolved questions.
- Defines the conditions required before architecture and implementation.

This document intentionally does not duplicate every workflow, transition or acceptance criterion. Detailed behaviour remains in the linked authoritative specifications.

---

## 2. System overview

The project is a Greece-first, cloud-native platform enabling drivers to:

- Discover EV charging stations.
- Inspect connector compatibility and operational information.
- Evaluate availability for a requested interval.
- Reserve a compatible EVSE.
- Reschedule or cancel bookings.
- Check in at the assigned EVSE.
- Start, monitor and stop a simulated charging session.
- View booking and session history.
- Report infrastructure faults.
- Exercise supported privacy rights.

Operator staff can manage their organizations, charging infrastructure, tariffs, booking policies, maintenance, faults, bookings, simulator devices and operational analytics.

Platform administrators and support staff can perform scoped governance, support, investigation, privacy and emergency workflows.

A Charger Simulator provides authenticated, failure-capable, OCPP-inspired device behaviour without controlling real charging hardware.

---

## 3. Project constraints

| Concern | Decision |
|---|---|
| Team | Individual project |
| Implementation architecture | Microservices |
| Alternative architecture | Documented modular monolith |
| Frontend | Angular and TypeScript |
| Backend | Java and Spring Boot |
| Geography | Greece-first |
| Currency | EUR |
| Distance | Kilometres |
| Display timezone | `Europe/Athens` |
| Timestamp storage | UTC |
| Cloud | Not yet selected |
| Budget | Free or very low cost preferred |
| Frontend deployment | One role-aware responsive web application |
| Implementation gate | No implementation before readiness approval |

Authoritative source:

- [Project Constraints v1.0](../01_scope_and_requirements/01_project_constraints_v1.0.md)

---

## 4. System scope

### 4.1 Included

- Public station discovery through map and list views
- Search and filtering
- Station, EVSE, connector and tariff details
- Driver registration and verified accounts
- Saved vehicle and compatibility profiles
- EVSE-level booking
- Automatic or exact EVSE selection
- Booking holds, confirmation, rescheduling and cancellation
- QR or identifier-based check-in
- Simulated charging sessions
- Maintenance and fault workflows
- Booking reassignment
- Operator organization and staff management
- Administrator and platform-support capabilities
- Essential transactional email
- Privacy export and deletion workflows
- Audit logging and basic analytics
- OCPP-inspired Charger Simulator
- Cloud deployment and architectural evaluation

### 4.2 Deferred

- In-app, push and SMS notifications
- Payment-provider sandbox integration
- Real charging-network integration
- Internationalization beyond Greek and English
- Advanced pricing
- Demand forecasting
- Native mobile applications
- Fault-report and support-case attachments

### 4.3 Excluded from the initial release

- Real card processing
- Storage of payment-card data
- Real charger hardware control
- OCPP compliance or certification claims
- Scraped charging-station data
- International currencies and tax systems
- Full implementation of the modular-monolith alternative
- Marketing communications

Authoritative source:

- [System Scope v1.0](../01_scope_and_requirements/02_system_scope_v1.0.md)

---

## 5. Actors and roles

### 5.1 Driver

A public or registered user who discovers stations and, when verified and active, manages bookings and simulated charging sessions.

### 5.2 Operator roles

- `OPERATOR_OWNER`
- `OPERATOR_MANAGER`
- `OPERATOR_TECHNICIAN`
- `OPERATOR_SUPPORT`

Operator authorization always includes organization ownership.

### 5.3 Platform roles

- `PLATFORM_ADMINISTRATOR`
- `PLATFORM_SUPPORT`
- `AUDITOR_SECURITY_REVIEWER`

Platform Support access is case-scoped and time-limited.

### 5.4 Device actor

- `SIMULATOR_DEVICE`

A non-human machine identity restricted to its assigned station and EVSEs.

### 5.5 Authorization principles

- Default deny
- MFA for every privileged human role
- Per-request authorization by authoritative capabilities
- Driver ownership checks
- Operator organization boundaries
- Platform Support case scope
- Simulator assignment checks
- Audited privileged and break-glass actions
- No application role grants direct database access
- No silent administrator impersonation

Authoritative sources:

- [Actors and Operational Scope v1.1](../01_scope_and_requirements/03_actors_and_operational_scope_v1.1.md)
- [Operator Use-Case Catalogue and Roles](../02_use_cases/operator/01_operator_use_case_catalogue_and_roles_v1.0.md)
- [Administrator and Platform Support Catalogue](../02_use_cases/administration_support/01_administrator_platform_support_catalogue_v1.0.md)

---

## 6. Canonical domain model

### 6.1 Infrastructure hierarchy

**Operator Organization → Station → EVSE → Connector**

- A Station is a physical charging location.
- An EVSE is one independently reservable charging point.
- A Connector represents a compatible charging interface.
- One EVSE serves one vehicle at a time in v1.
- A Booking reserves the EVSE, not an individual Connector.
- The Booking records the required Connector Type.

### 6.2 Booking and usage

- A Booking represents planned access.
- An Allocation is the authoritative capacity claim.
- A Charging Session represents attempted or actual simulated usage.
- A Device Transaction is the simulator-side physical activity.
- A Reservation Mirror is a non-authoritative simulator copy of an allocation.

### 6.3 Status separation

The platform maintains separate:

1. Administrative State
2. Device-Reported State
3. Derived Availability

These must never be collapsed into one overloaded status.

### 6.4 Canonical availability results

- `AVAILABLE`
- `PLANNED_AVAILABLE`
- `UNAVAILABLE`
- `UNKNOWN`
- `INCOMPATIBLE`

Authoritative sources:

- [Domain Glossary v1.0](../03_domain/01_domain_glossary_v1.0.md)
- [Station, EVSE, Connector, Tariff and Policy Model](../03_domain/03_station_evse_connector_tariff_policy_model_v1.0.md)

---

## 7. Core user journeys

### 7.1 Driver journey

**Discover → inspect compatibility and availability → authenticate → create hold → confirm booking → check in → start charging → monitor → stop → inspect summary**

### 7.2 Operator journey

**Create organization → obtain approval → configure station and EVSEs → publish infrastructure → monitor operations → manage faults and maintenance → resolve affected bookings → inspect analytics**

### 7.3 Support journey

**Create or receive case → establish scope → inspect masked information → request authoritative workflow action → escalate if necessary → resolve and close**

### 7.4 Simulator journey

**Enroll → authenticate → boot/register → report heartbeat and status → receive commands → emit transaction and meter events → disconnect/reconnect → replay queued events → reconcile**

Detailed use-case index:

- [Driver Use-Case Catalogue](../02_use_cases/driver/01_driver_use_case_catalogue_v1.0.md)
- [Detailed Operator Use Cases](../02_use_cases/operator/02_detailed_operator_use_cases_v1.0.md)
- [Detailed Administrator and Platform Support Use Cases](../02_use_cases/administration_support/02_detailed_administrator_platform_support_use_cases_v1.0.md)

---

## 8. Booking model

### 8.1 Booking lifecycle

- `HELD`
- `CONFIRMED`
- `CHECKED_IN`
- `ACTIVE`
- `COMPLETED`
- `CANCELLED`
- `EXPIRED`
- `NO_SHOW`
- `FULFILMENT_FAILED`

Terminal states:

- `COMPLETED`
- `CANCELLED`
- `EXPIRED`
- `NO_SHOW`
- `FULFILMENT_FAILED`

### 8.2 Initial policy defaults

| Policy | Default |
|---|---:|
| Hold duration | 5 minutes |
| Earliest booking | 15 minutes from current time |
| Advance-booking limit | 30 days |
| Minimum duration | 30 minutes |
| Maximum duration | 4 hours |
| Time increment | 15 minutes |
| Check-in opening | 15 minutes before start |
| Grace period | 15 minutes |
| Near-term status horizon | Provisional 60 minutes |

Effective policies are snapshotted on confirmation.

### 8.3 Booking authority

Only the Booking authority may:

- Claim EVSE capacity.
- Confirm a booking.
- Reschedule an allocation.
- Reassign an EVSE.
- Release allocation capacity.
- Make the final conflict decision.

Search availability is advisory.

### 8.4 Start rejection rule

For v1:

- Definitive start rejection produces session `START_REJECTED`.
- Charging never began.
- The Booking becomes `FULFILMENT_FAILED`.
- The same Booking cannot create another start attempt.
- Timeout remains uncertain and must be reconciled.

Authoritative sources:

- [Booking Lifecycle and Policy](../03_domain/04_booking_lifecycle_and_policy_v1.0.md)
- [Create Booking](../02_use_cases/driver/04_dr_11_12_create_booking_v1.0.md)
- [Reschedule and Cancel](../02_use_cases/driver/06_dr_14_15_reschedule_cancel_booking_v1.0.md)
- [Check-In and Authorization](../02_use_cases/driver/07_dr_16_check_in_authorization_v1.0.md)

---

## 9. Availability and double-booking prevention

### 9.1 Time model

Intervals use half-open semantics:

`[start, end)`

The effective allocation interval includes the post-booking turnaround buffer.

### 9.2 Availability inputs

- Administrative eligibility
- Connector and power compatibility
- Opening hours and exceptions
- Booking policy
- Existing allocations
- Active or uncertain sessions
- Maintenance
- Fault incidents
- Status overrides
- Device status freshness

### 9.3 Correctness rules

- Search cannot allocate capacity.
- Booking revalidates all inputs.
- Concurrent conflicting requests produce exactly one winner.
- Expired holds are non-blocking even before cleanup.
- Actual or uncertain occupation overrides planned availability.
- Failed rescheduling preserves the original allocation.
- Reassignment claims the replacement before releasing the original.
- Unused cancelled allocations are released immediately.

The exact PostgreSQL constraint and transaction-isolation strategy remain architecture decisions.

Authoritative source:

- [Availability Calculation Model v1.0](../03_domain/05_availability_calculation_model_v1.0.md)

---

## 10. Check-in and charging

### 10.1 Check-in

Check-in:

- Requires the Booking owner.
- Occurs during the valid window.
- Confirms the assigned EVSE.
- Creates one short-lived, single-use Start Authorization.
- Does not start energy transfer.

The EVSE QR code contains only a public identifier and no secret.

### 10.2 Charging Session lifecycle

- `STARTING`
- `CHARGING`
- `SUSPENDED`
- `STOPPING`
- `COMPLETED`
- `INTERRUPTED`
- `START_REJECTED`

### 10.3 Evidence rules

- Command acceptance does not prove charging began.
- `TransactionStarted` proves simulated charging began.
- Stop-command acceptance does not prove termination.
- `TransactionEnded` provides definitive termination evidence.
- Timeout means uncertainty.
- Uncertain occupation continues blocking capacity.

### 10.4 Booking relationship

| Session outcome | Booking outcome |
|---|---|
| Charging starts | `CHECKED_IN → ACTIVE` |
| Normal completion | `ACTIVE → COMPLETED` |
| Interrupted after energy | `ACTIVE → COMPLETED`, with interrupted session outcome |
| Definitive failure before energy | `FULFILMENT_FAILED` |

Authoritative source:

- [Charging Session Lifecycle](../02_use_cases/driver/08_dr_17_20_charging_session_lifecycle_v1.0.md)

---

## 11. Maintenance, faults and reassignment

### 11.1 Maintenance lifecycle

- `SCHEDULED`
- `ACTIVE`
- `COMPLETED`
- `CANCELLED`

Permitted terminal handling:

- `SCHEDULED → CANCELLED`
- `ACTIVE → COMPLETED`

If active work is aborted, Maintenance still becomes `COMPLETED` with `completionOutcome = ABORTED`.

### 11.2 Fault model

Fault Reports and Fault Incidents are separate.

Fault Incident lifecycle:

- `OPEN`
- `ACKNOWLEDGED`
- `IN_PROGRESS`
- `RESOLVED`

### 11.3 Operational rules

- Normal maintenance cannot activate over unresolved bookings or sessions.
- Emergency maintenance requires elevated authority and justification.
- Maintenance completion sets operational confidence to `UNKNOWN`.
- Fresh device evidence is required before near-term use.
- Equipment failure never produces `NO_SHOW`.
- Reassignment must be idempotent and concurrency-safe.

Authoritative source:

- [Maintenance, Fault and Reassignment Workflows](../03_domain/06_maintenance_fault_reassignment_workflows_v1.0.md)

---

## 12. Distributed consistency

### 12.1 Core principles

- Each entity has one authoritative owner.
- A capability writes only its own data.
- Cross-capability database transactions are prohibited.
- Direct writes to another capability’s database are prohibited.
- Required events use a Transactional Outbox.
- Consumers use an Inbox or equivalent deduplication mechanism.
- Delivery is at least once.
- Consumers must be idempotent.
- Global event ordering is not assumed.
- Aggregate versions and sequence numbers handle ordering.
- Business deadlines use database time.

### 12.2 Consistency classification

Strong consistency is required for:

- Allocation
- Booking transitions
- Rescheduling
- Reassignment
- Check-in authorization
- Authorization consumption
- Session transitions

Eventual consistency is acceptable for:

- Search
- Analytics
- Notifications
- Dashboards
- Aggregated audit views

### 12.3 Failure isolation

Failure of search, analytics or email must not prevent management of committed bookings.

Device-command timeout remains uncertain until reconciliation.

Authoritative source:

- [Background Processes and Distributed Consistency](../04_platform_and_integrations/01_background_processes_distributed_consistency_v1.0.md)

---

## 13. Charger Simulator

The platform uses a custom:

> **OCPP 2.1-inspired simulator protocol**

It does not claim OCPP compliance, certification or wire compatibility.

### Required capabilities

- Machine enrollment and authentication
- Boot registration
- Heartbeats
- EVSE status reporting
- Reservation mirrors
- Start and stop commands
- Transaction events
- Meter values
- Fault injection
- Offline event queues
- Duplicate and out-of-order events
- Command rejection and timeout
- State reconciliation
- Deterministic failure scenarios

The deployed authentication target is certificate-based machine identity, subject to cloud validation.

Authoritative source:

- [Charger Simulator Protocol](../04_platform_and_integrations/02_charger_simulator_protocol_v1.0.md)

---

## 14. Notifications

Version 1 supports transactional email only.

### Mandatory categories

- Security
- Account lifecycle
- Material booking changes
- Operational failures
- Privacy workflows

### Optional categories

- Booking reminders
- Routine completion summaries
- Non-critical support updates

### Rules

- Notifications follow authoritative commit.
- Email is not the authoritative business record.
- Delivery failure cannot cancel a Booking.
- Raw action tokens never enter the broker.
- Provider acceptance and mailbox delivery are separate outcomes.
- Greek and English HTML and plain-text templates are required.
- Privacy exports are never attached to email.

Authoritative source:

- [Notification Rules and Essential Email Matrix](../04_platform_and_integrations/03_notification_rules_email_matrix_v1.0.md)

---

## 15. Privacy

### Core principles

- Data minimization
- Purpose limitation
- Configurable retention
- No precise search-location history by default
- No VIN, registration plate or home address in v1
- No unnecessary driver information in simulator messages
- Pseudonymized data remains personal data
- Long-term analytics uses anonymized aggregates

### Supported workflows

- Access export
- Applicable portability export
- Rectification
- Restriction
- Account deletion
- Retention and anonymization

Deletion cannot complete while active or uncertain obligations remain.

Retention values remain provisional until privacy and deployment review.

Authoritative source:

- [Privacy, Retention, Export, Deletion and Anonymization](../06_security_and_privacy/01_privacy_retention_export_deletion_v1.0.md)

---

## 16. Functional requirements

The stable functional baseline is organized into:

- Identity and access
- Discovery
- Availability
- Booking
- Charging
- History and assistance
- Operator management
- Administration and support
- Simulator
- Distributed platform processes
- Notifications
- Privacy
- Audit

Every `MUST` requirement requires:

- Authoritative ownership
- Acceptance criteria
- Automated verification
- Security controls
- Implementation epic
- API, event and data traceability before implementation

Authoritative source:

- [Functional Requirements and Traceability v1.1](../01_scope_and_requirements/04_functional_requirements_and_traceability_v1.1.md)

---

## 17. Non-functional requirements

Reference load:

- 500 concurrent users
- 2,000 simulated EVSEs
- 50 API requests per second
- 100 charger events per second

Principal targets:

- Search/station API p95 no greater than 1.5 seconds
- Availability p95 no greater than 1 second
- Booking/check-in p95 no greater than 2 seconds
- 99% of charger events reflected within 10 seconds
- 99.5% monthly core availability target
- RPO no greater than 5 minutes
- RTO no greater than 60 minutes
- Capacity validation at twice reference load
- OWASP ASVS Level 2 baseline
- WCAG 2.2 AA target
- Greek and English interfaces
- Structured logs, metrics and distributed traces
- Automated unit, integration, contract, security, accessibility and end-to-end tests

Authoritative source:

- [Non-Functional Requirements v1.0](../01_scope_and_requirements/05_non_functional_requirements_v1.0.md)

---

## 18. Release-critical invariants

The following are implementation-blocking correctness rules:

1. One EVSE cannot have overlapping active allocations.
2. Search cannot reserve capacity.
3. Expired holds cannot block allocation.
4. Failed rescheduling cannot remove the original allocation.
5. Drivers access only their own records.
6. Operators access only their organization’s resources.
7. Platform Support access is case-scoped.
8. Start Authorization is single-use.
9. One EVSE cannot have two active Charging Sessions.
10. Duplicate meter events cannot inflate energy or cost.
11. Command timeout cannot be reported as success or rejection.
12. Equipment failure cannot produce `NO_SHOW`.
13. Maintenance cannot normally activate over unresolved obligations.
14. Projection failure cannot reverse committed business state.
15. Privacy completion cannot be reported prematurely.
16. Required audit and outbox evidence commits with authoritative changes.
17. Secrets cannot appear in events, logs or QR codes.
18. Terminal lifecycle states cannot be reopened unless explicitly allowed.

Authoritative source:

- [Lifecycle and Invariant Catalogue v1.0](../03_domain/02_lifecycle_and_invariant_catalogue_v1.0.md)

---

## 19. Decision governance

Decisions are classified as:

- `APPROVED`
- `PROVISIONAL`
- `DEFERRED`
- `REJECTED`
- `SUPERSEDED`
- `OPEN`

Important provisional choices include:

- Keycloak
- PostgreSQL
- RabbitMQ
- MapLibre
- Certificate-based simulator identity
- Near-term availability horizon
- Status-freshness threshold
- Retention periods
- Backup duration

Important unresolved areas include:

- Final microservice boundaries
- Database deployment and ownership model
- Allocation constraint implementation
- Service-to-service authentication
- Cloud provider and deployment platform
- Email, map and observability providers
- Complete threat model
- Frontend architecture
- Test tooling
- Repository strategy
- AI-agent governance

Authoritative source:

- [Decision and Open-Question Register v1.0](01_decision_and_open_question_register_v1.0.md)

---

## 20. Specification authority

When documents overlap, use this precedence:

1. Decision and Open-Question Register
2. Functional Requirements and Traceability
3. Lifecycle and Invariant Catalogue
4. Domain Glossary
5. Focused use-case or domain specification
6. System Scope and NFRs
7. Actor and use-case catalogues
8. Archived recommendations and reviews

Authoritative source:

- [Cross-Document Consistency Review v1.0](02_cross_document_consistency_review_v1.0.md)

---

## 21. Complete specification index

### Governance

- [Decision and Open-Question Register](01_decision_and_open_question_register_v1.0.md)
- [Cross-Document Consistency Review](02_cross_document_consistency_review_v1.0.md)
- [Planning Status and Roadmap](04_planning_status_and_roadmap_v1.1.md)

### Scope and requirements

- [Project Constraints](../01_scope_and_requirements/01_project_constraints_v1.0.md)
- [System Scope](../01_scope_and_requirements/02_system_scope_v1.0.md)
- [Actors and Operational Scope](../01_scope_and_requirements/03_actors_and_operational_scope_v1.1.md)
- [Functional Requirements and Traceability](../01_scope_and_requirements/04_functional_requirements_and_traceability_v1.1.md)
- [Non-Functional Requirements](../01_scope_and_requirements/05_non_functional_requirements_v1.0.md)

### Driver use cases

- [Driver Use-Case Catalogue](../02_use_cases/driver/01_driver_use_case_catalogue_v1.0.md)
- [DR-01–05 Station Discovery](../02_use_cases/driver/02_dr_01_05_station_discovery_v1.0.md)
- [DR-06–10 Account and Vehicle Profile](../02_use_cases/driver/03_dr_06_10_account_auth_vehicle_profile_v1.0.md)
- [DR-11/12 Create Booking](../02_use_cases/driver/04_dr_11_12_create_booking_v1.0.md)
- [DR-13 and DR-21–25 Remaining Use Cases](../02_use_cases/driver/05_dr_13_21_25_remaining_driver_use_cases_v1.0.md)
- [DR-14/15 Reschedule and Cancel](../02_use_cases/driver/06_dr_14_15_reschedule_cancel_booking_v1.0.md)
- [DR-16 Check-In and Authorization](../02_use_cases/driver/07_dr_16_check_in_authorization_v1.0.md)
- [DR-17–20 Charging Session Lifecycle](../02_use_cases/driver/08_dr_17_20_charging_session_lifecycle_v1.0.md)

### Operator use cases

- [Operator Catalogue and Roles](../02_use_cases/operator/01_operator_use_case_catalogue_and_roles_v1.0.md)
- [Detailed Operator Use Cases](../02_use_cases/operator/02_detailed_operator_use_cases_v1.0.md)

### Administration and support

- [Administrator and Platform Support Catalogue](../02_use_cases/administration_support/01_administrator_platform_support_catalogue_v1.0.md)
- [Detailed Administrator and Platform Support Use Cases](../02_use_cases/administration_support/02_detailed_administrator_platform_support_use_cases_v1.0.md)

### Domain specifications

- [Domain Glossary](../03_domain/01_domain_glossary_v1.0.md)
- [Lifecycle and Invariant Catalogue](../03_domain/02_lifecycle_and_invariant_catalogue_v1.0.md)
- [Infrastructure and Tariff Model](../03_domain/03_station_evse_connector_tariff_policy_model_v1.0.md)
- [Booking Lifecycle and Policy](../03_domain/04_booking_lifecycle_and_policy_v1.0.md)
- [Availability Calculation Model](../03_domain/05_availability_calculation_model_v1.0.md)
- [Maintenance, Fault and Reassignment](../03_domain/06_maintenance_fault_reassignment_workflows_v1.0.md)

### Platform and integrations

- [Background Processes and Distributed Consistency](../04_platform_and_integrations/01_background_processes_distributed_consistency_v1.0.md)
- [Charger Simulator Protocol](../04_platform_and_integrations/02_charger_simulator_protocol_v1.0.md)
- [Notification Rules and Email Matrix](../04_platform_and_integrations/03_notification_rules_email_matrix_v1.0.md)

### Security and privacy

- [Privacy, Retention, Export, Deletion and Anonymization](../06_security_and_privacy/01_privacy_retention_export_deletion_v1.0.md)

---

## 22. Architecture constraints

The architecture phase must preserve:

1. Booking allocation as one strong consistency boundary.
2. Single authoritative ownership for business data.
3. No cross-service database writes.
4. No distributed database transactions.
5. Transactional Outbox and idempotent consumers.
6. Explicit handling of duplicate and out-of-order events.
7. Failure isolation for search, analytics and notifications.
8. Server-side authorization in every service.
9. Separate administrative, reported and derived states.
10. Explicit uncertain device outcomes.
11. Versioned APIs, commands and events.
12. Backward-compatible migrations and rollback safety.
13. Traceability to stable requirement and invariant IDs.
14. Cloud portability where practical.
15. A documented modular-monolith mapping of the same boundaries.

These constraints do not predetermine the number or names of microservices.

---

## 23. Foundation completion status

| Foundation artifact | Status |
|---|---|
| Project constraints | Complete |
| System scope | Complete |
| Actors and permissions | Complete |
| Use-case catalogues | Complete |
| Detailed operational use cases | Complete |
| Functional requirements | Complete |
| Non-functional requirements | Drafted |
| Availability model | Complete |
| Distributed consistency model | Complete |
| Simulator protocol | Complete |
| Privacy workflows | Complete |
| Notification matrix | Complete |
| Decision register | Complete |
| Domain glossary | Complete |
| Lifecycle and invariant catalogue | Complete with consistency amendments |
| Cross-document review | Complete |
| Consolidated System Specification | In review |

“Complete” means documented at the foundation-planning level. It does not mean implemented or architecture-ready without review.

---

## 24. Foundation approval checklist

Before proceeding to architecture:

- [ ] Approve this Consolidated System Specification.
- [ ] Approve or record exceptions for all foundation documents.
- [ ] Apply repository filename and path migration.
- [ ] Archive superseded documents.
- [ ] Apply canonical terminology corrections.
- [ ] Apply Maintenance lifecycle correction.
- [ ] Apply definitive start-rejection rule.
- [ ] Repair all relative links.
- [ ] Replace the roadmap with v1.1.
- [ ] Confirm every use case maps to a requirement.
- [ ] Confirm every release-critical invariant has an intended test category.
- [ ] Confirm no unresolved G1 decision remains.
- [ ] Record the foundation baseline version or Git tag.

Recommended foundation tag:

`planning-foundation-v1.0`

---

## 25. Next phase: architecture planning

Architecture planning begins only after the foundation checklist passes.

Required architecture sequence:

1. Domain capability decomposition
2. Microservice boundary candidates
3. Final service responsibilities
4. Data ownership
5. Synchronous communication matrix
6. Asynchronous communication matrix
7. Workflow coordination model
8. Service-to-service security
9. Deployment context
10. Architecture diagrams
11. Architecture Decision Records
12. Modular-monolith alternative mapping
13. Architecture readiness review

The first architecture artifact should be:

> **Domain Capability Map and Microservice Boundary Analysis v1.0**

Service boundaries must be justified by:

- Business responsibility
- Consistency requirements
- Data ownership
- Change frequency
- Scalability
- Failure isolation
- Security boundaries
- Operational cost
- Individual-project feasibility

---

## 26. Change governance

After approval:

1. This document becomes the system-level index.
2. It does not replace focused specifications.
3. Changes to scope require impact analysis.
4. Changes to stable requirements require version history.
5. Changes to lifecycle states require invariant and migration review.
6. Architecture decisions require ADRs.
7. New terminology requires glossary approval.
8. New personal data requires data-inventory review.
9. New asynchronous communication requires retry, idempotency and replay design.
10. Implementation artifacts must trace to requirements and invariants.
11. AI agents must follow the approved authority hierarchy.
12. Archived documents must never be used as implementation authority.

---

## 27. Approval effect

Approval of Consolidated System Specification v1.0 means:

- The product and domain foundation is baselined.
- Linked active specifications become the planning authority.
- The terminology, lifecycle and consistency resolutions become mandatory.
- Remaining architecture and technology choices remain unresolved where indicated.
- Implementation remains prohibited.
- The project may proceed to architecture planning.
</16_consolidated_system_specification_v1.0.md>
