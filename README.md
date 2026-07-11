# EV Charging Booking Platform — Foundation Documentation

Welcome to the foundation specifications and planning documentation for the **EV Charging Booking Platform**. This repository contains the complete set of business analyses, domain models, use cases, and requirements.

---

## 📂 Documentation Directory Structure

The project documentation is organized into modular directories under the `docs/` folder:

### 1. [00_governance](docs/00_governance)
Holds overall decisions, project status registers, and documentation governance.
- **[GOV-001] Decision Register:** [01_decision_and_open_question_register_v1.0.md](docs/00_governance/01_decision_and_open_question_register_v1.0.md) — Authoritative log of approved and provisional design decisions.
- **[GOV-002] Consistency Review:** [02_cross_document_consistency_review_v1.0.md](docs/00_governance/02_cross_document_consistency_review_v1.0.md) — Precedence guidelines and domain term mapping.
- **[GOV-003] Consolidated System Specification:** [03_consolidated_system_specification_v1.0.md](docs/00_governance/03_consolidated_system_specification_v1.0.md) — The master specification registry.
- **[GOV-004] Planning Status & Roadmap:** [04_planning_status_and_roadmap_v1.1.md](docs/00_governance/04_planning_status_and_roadmap_v1.1.md) — Milestones, gap completions, and next steps.
- **[GOV-005] Foundation Approval & Baseline Record:** [05_foundation_approval_and_baseline_record_v1.0.md](docs/00_governance/05_foundation_approval_and_baseline_record_v1.0.md) — Verification checklist and formal gate sign-off.

### 2. [01_scope_and_requirements](docs/01_scope_and_requirements)
Defines project profile, system boundaries, and requirements.
- **[SCP-001] Project Constraints:** [01_project_constraints_v1.0.md](docs/01_scope_and_requirements/01_project_constraints_v1.0.md) — Stack constraints, budget limitations, and portfolio goals.
- **[SCP-002] System Scope:** [02_system_scope_v1.0.md](docs/01_scope_and_requirements/02_system_scope_v1.0.md) — Capabilities in-scope, deferred, or explicitly excluded.
- **[SCP-003] Actors & Operational Scope:** [03_actors_and_operational_scope_v1.1.md](docs/01_scope_and_requirements/03_actors_and_operational_scope_v1.1.md) — Actor roles and operational guidelines.
- **[REQ-001] Functional Requirements & Traceability:** [04_functional_requirements_and_traceability_v1.1.md](docs/01_scope_and_requirements/04_functional_requirements_and_traceability_v1.1.md) — Traceability matrix linking requirements to specifications and tests.
- **[REQ-002] Non-Functional Requirements:** [05_non_functional_requirements_v1.0.md](docs/01_scope_and_requirements/05_non_functional_requirements_v1.0.md) — Performance, reliability, security, and quality metrics.

### 3. [02_use_cases](docs/02_use_cases)
Contains actor journeys and detailed scenario specifications.
- **[driver](docs/02_use_cases/driver/)** — Discovery, creation of bookings, rescheduling/cancellations, check-in authorization, and charging sessions lifecycles.
- **[operator](docs/02_use_cases/operator/)** — Infrastructure management, staff operations, overrides, and telemetry.
- **[administration_support](docs/02_use_cases/administration_support/)** — Support tickets, user suspension, and moderation flows.

### 4. [03_domain](docs/03_domain)
Defines the core domain logic, terminology, and workflows.
- **[DOM-001] Domain Glossary:** [01_domain_glossary_v1.0.md](docs/03_domain/01_domain_glossary_v1.0.md) — Terminology baseline.
- **[DOM-002] Lifecycle & Invariant Catalogue:** [02_lifecycle_and_invariant_catalogue_v1.0.md](docs/03_domain/02_lifecycle_and_invariant_catalogue_v1.0.md) — Master state machines catalog and business rules.
- **[DOM-003] Infrastructure Schema:** [03_station_evse_connector_tariff_policy_model_v1.0.md](docs/03_domain/03_station_evse_connector_tariff_policy_model_v1.0.md) — Station and tariff domain designs.
- **[DOM-004] Booking Lifecycle:** [04_booking_lifecycle_and_policy_v1.0.md](docs/03_domain/04_booking_lifecycle_and_policy_v1.0.md) — Holds, transitions, and cancellation policies.
- **[DOM-005] Availability Model:** [05_availability_calculation_model_v1.0.md](docs/03_domain/05_availability_calculation_model_v1.0.md) — authoritative checks and search parameters.
- **[DOM-006] Workflows:** [06_maintenance_fault_reassignment_workflows_v1.0.md](docs/03_domain/06_maintenance_fault_reassignment_workflows_v1.0.md) — Maintenance planning and automatic booking reassignment.

### 5. [04_platform_and_integrations](docs/04_platform_and_integrations)
Specifies background logic, simulator protocols, and notification rules.
- **[PLT-001] Background Processes:** [01_background_processes_distributed_consistency_v1.0.md](docs/04_platform_and_integrations/01_background_processes_distributed_consistency_v1.0.md) — Outbox events, lockings, and reconciliation.
- **[SIM-001] Charger Simulator Protocol:** [02_charger_simulator_protocol_v1.0.md](docs/04_platform_and_integrations/02_charger_simulator_protocol_v1.0.md) — OCPP-inspired commands and heartbeats.
- **[NOT-001] Notification Rules:** [03_notification_rules_email_matrix_v1.0.md](docs/04_platform_and_integrations/03_notification_rules_email_matrix_v1.0.md) — Transactional emails matrix.

### 6. [06_security_and_privacy](docs/06_security_and_privacy)
- **[PRV-001] Privacy & Retention:** [01_privacy_retention_export_deletion_v1.0.md](docs/06_security_and_privacy/01_privacy_retention_export_deletion_v1.0.md) — GDPR data minimization, export, and deletion workflows.

---

## 🎯 Next Steps
With the foundation specifications approved and baselined as of version 1.0 (recorded in `GOV-005`), the project is now ready to transition into the **System Architecture Phase**, beginning with the **Domain Capability Map and Microservice Boundary Analysis v1.0**.
