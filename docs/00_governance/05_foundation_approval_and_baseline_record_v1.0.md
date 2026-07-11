Document ID: GOV-005
Title: Foundation Approval and Baseline Record v1.0
Version: 1.0
Status: APPROVED
Owner: PO/DA/BA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: All governance and specification documents
Authoritative for: Foundation Gate Approval Record

# Foundation Approval and Baseline Record v1.0

This record serves as the formal gate sign-off for the EV Charging Booking Platform's foundation planning phase. Approving this document baselines all requirements and domain designs, authorizing the start of the microservice boundary and technical architecture phase.

---

## 1. Approval Checklist & Verification Status

| Checklist Item | Status | Verification Detail |
|---|---|---|
| Active specifications moved to canonical paths | **PASSED** | Organized into `docs/` subdirectory hierarchy. |
| Metadata blocks added and set to `APPROVED` | **PASSED** | YAML header metadata updated across active specs. |
| Reorganization links repaired | **PASSED** | Relative markdown links verified. |
| Consistency amendments applied | **PASSED** | Updated maintenance completions, start rejections, and booking state references. |
| NFR baseline approved | **PASSED** | Performance and reliability metrics established and approved. |
| Foundation traceability complete | **PASSED** | Functional requirements trace use cases to epics and test classes. |
| No unresolved G1 planning questions | **PASSED** | All gate G1 decisions resolved and documented. |
| Git baseline tag created | **PENDING** | Target tag: `planning-foundation-v1.0` (to be pushed after corrections are committed). |

---

## 2. Baselined Document Versions

| Document ID | Canonical Document Title | Baselined Version | Status |
|---|---|---|---|
| **GOV-001** | [Decision and Open-Question Register v1.0](docs/00_governance/01_decision_and_open_question_register_v1.0.md) | v1.0 | APPROVED |
| **GOV-002** | [Cross-Document Consistency Review v1.0](docs/00_governance/02_cross_document_consistency_review_v1.0.md) | v1.0 | APPROVED |
| **GOV-003** | [Consolidated System Specification v1.0](docs/00_governance/03_consolidated_system_specification_v1.0.md) | v1.0 | APPROVED |
| **GOV-004** | [Planning Status and Roadmap v1.1](docs/00_governance/04_planning_status_and_roadmap_v1.1.md) | v1.1 | APPROVED |
| **SCP-001** | [Project Constraints v1.0](docs/01_scope_and_requirements/01_project_constraints_v1.0.md) | v1.0 | APPROVED |
| **SCP-002** | [System Scope v1.0](docs/01_scope_and_requirements/02_system_scope_v1.0.md) | v1.0 | APPROVED |
| **SCP-003** | [Actors and Operational Scope v1.1](docs/01_scope_and_requirements/03_actors_and_operational_scope_v1.1.md) | v1.1 | APPROVED |
| **REQ-001** | [Functional Requirements and Traceability v1.1](docs/01_scope_and_requirements/04_functional_requirements_and_traceability_v1.1.md) | v1.1 | APPROVED |
| **REQ-002** | [Non-Functional Requirements v1.0](docs/01_scope_and_requirements/05_non_functional_requirements_v1.0.md) | v1.0 | APPROVED |
| **DOM-001** | [Domain Glossary v1.0](docs/03_domain/01_domain_glossary_v1.0.md) | v1.0 | APPROVED |
| **DOM-002** | [Lifecycle and Invariant Catalogue v1.0](docs/03_domain/02_lifecycle_and_invariant_catalogue_v1.0.md) | v1.0 | APPROVED |
| **DOM-003** | [Station, EVSE, Connector, Tariff and Booking Policy Model v1.0](docs/03_domain/03_station_evse_connector_tariff_policy_model_v1.0.md) | v1.0 | APPROVED |
| **DOM-004** | [Booking Lifecycle and Policy Specification v1.0](docs/03_domain/04_booking_lifecycle_and_policy_v1.0.md) | v1.0 | APPROVED |
| **DOM-005** | [Availability Calculation Model v1.0](docs/03_domain/05_availability_calculation_model_v1.0.md) | v1.0 | APPROVED |
| **DOM-006** | [Maintenance, Fault and Reassignment Workflows v1.0](docs/03_domain/06_maintenance_fault_reassignment_workflows_v1.0.md) | v1.0 | APPROVED |
| **PLT-001** | [Background Processes and Distributed Consistency v1.0](docs/04_platform_and_integrations/01_background_processes_distributed_consistency_v1.0.md) | v1.0 | APPROVED |
| **SIM-001** | [Charger Simulator Protocol v1.0](docs/04_platform_and_integrations/02_charger_simulator_protocol_v1.0.md) | v1.0 | APPROVED |
| **NOT-001** | [Notification Rules and Essential Email Matrix v1.0](docs/04_platform_and_integrations/03_notification_rules_email_matrix_v1.0.md) | v1.0 | APPROVED |
| **PRV-001** | [Privacy, Retention, Export, Deletion and Anonymization v1.0](docs/06_security_and_privacy/01_privacy_retention_export_deletion_v1.0.md) | v1.0 | APPROVED |

---

## 3. Remaining Provisional/Deferred Decisions

The following architectural directions are provisionally accepted but require validation gates (G2–G6) before implementation:
1. **Identity Provider:** Keycloak (preferred). Open to lightweight alternatives if resources dictate.
2. **Message Broker:** RabbitMQ (preferred over Kafka to limit deployment complexity).
3. **Database:** PostgreSQL for primary storage. Redis for transient availability maps.
4. **Maps Provider:** MapLibre with OpenStreetMap tiles.
5. **Data Retention Limits:** Explicit GDPR retention periods deferred pending formal legal/policy reviews.

---

## 4. Git Baseline Sign-off
- **Approving Authority:** Repository Owner
- **AI Assistant & Reviewer:** Antigravity AI
- **Sign-off Date:** 2026-07-11
- **Baseline Git Tag:** `planning-foundation-v1.0`
