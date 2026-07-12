# EV Charging Booking Platform — Documentation Homepage

Welcome to the complete specifications, domain models, and architecture design documentation for the **EV Charging Booking Platform**.

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
- **[DOM-005] Availability Model:** [05_availability_calculation_model_v1.0.md](docs/03_domain/05_availability_calculation_model_v1.0.md) — Authoritative checks and search parameters.
- **[DOM-006] Workflows:** [06_maintenance_fault_reassignment_workflows_v1.0.md](docs/03_domain/06_maintenance_fault_reassignment_workflows_v1.0.md) — Maintenance planning and automatic booking reassignment.

### 5. [04_platform_and_integrations](docs/04_platform_and_integrations)
Specifies background logic, simulator protocols, and notification rules.
- **[PLT-001] Background Processes:** [01_background_processes_distributed_consistency_v1.0.md](docs/04_platform_and_integrations/01_background_processes_distributed_consistency_v1.0.md) — Outbox events, lockings, and reconciliation.
- **[SIM-001] Charger Simulator Protocol:** [02_charger_simulator_protocol_v1.0.md](docs/04_platform_and_integrations/02_charger_simulator_protocol_v1.0.md) — OCPP-inspired commands and heartbeats.
- **[NOT-001] Notification Rules:** [03_notification_rules_email_matrix_v1.0.md](docs/04_platform_and_integrations/03_notification_rules_email_matrix_v1.0.md) — Transactional emails matrix.

### 6. [05_architecture](docs/05_architecture)
Specifies boundaries, communications, contracts, database models, and concurrency designs.
- **[ARC-001] Boundary Analysis:** [01_domain_capability_map_boundary_analysis_v1.0.md](docs/05_architecture/01_domain_capability_map_boundary_analysis_v1.0.md) — Logical domain grouping and capability boundaries.
- **[ARC-002] Inter-Service Communication:** [02_inter_service_communication_consistency_v1.0.md](docs/05_architecture/02_inter_service_communication_consistency_v1.0.md) — Communication semantics and consistency matrix.
- **[ARC-003] REST API Contract Catalogue:** [03_rest_api_contract_catalogue_v1.0.md](docs/05_architecture/03_rest_api_contract_catalogue_v1.0.md) — Synchronous REST API surface specifications.
- **[ARC-004] Event & Command Contract Catalogue:** [04_event_command_contract_catalogue_v1.0.md](docs/05_architecture/04_event_command_contract_catalogue_v1.0.md) — Asynchronous integration events and commands schema.
- **[ARC-005] Database Models & Ownership:** [05_database_models_ownership_migration_v1.0.md](docs/05_architecture/05_database_models_ownership_migration_v1.0.md) — Schema ownership and migration strategy.
- **[ARC-006] Double-Booking Prevention Concurrency:** [06_double_booking_prevention_concurrency_v1.0.md](docs/05_architecture/06_double_booking_prevention_concurrency_v1.0.md) — Locking strategies and concurrency design.
- **[ARC-008] Frontend UX Flow Spec:** [07_frontend_ux_flow_specification_v1.0.md](docs/05_architecture/07_frontend_ux_flow_specification_v1.0.md) — Frontend BFF routing, screen maps, and UX flows.
- **[ARC-009] Technology Selection & ADRs:** [08_technology_selection_adr_set_v1.0.md](docs/05_architecture/08_technology_selection_adr_set_v1.0.md) — Framework choices and Architectural Decision Records.
- **[ARC-010] Cloud Provider & Cost Analysis:** [09_cloud_provider_cost_analysis_v1.0.md](docs/05_architecture/09_cloud_provider_cost_analysis_v1.0.md) — Budget allocations and cloud provider comparison.
- **[ARC-011] Deployment Architecture & IaC:** [10_deployment_architecture_iac_v1.0.md](docs/05_architecture/10_deployment_architecture_iac_v1.0.md) — Kubernetes, GitOps, and OpenTofu infrastructure.
- **[ARC-017] Modular-Monolith Alternative Design:** [11_modular_monolith_alternative_design_v1.0.md](docs/05_architecture/11_modular_monolith_alternative_design_v1.0.md) — Modular architecture, transactions, and fallback design.
- **[ARC-018] Contract Governance:** [12_contract_governance_and_decisions_v1.0.md](docs/05_architecture/12_contract_governance_and_decisions_v1.0.md) — OpenAPI/AsyncAPI rules and API decision register.
- **[ARC-019] Sync APIs & Interactions:** [13_service_interactions_and_sync_apis_v1.0.md](docs/05_architecture/13_service_interactions_and_sync_apis_v1.0.md) — HTTP REST endpoint definitions and sources of truth.
- **[ARC-020] Domain Events & Async Contracts:** [14_domain_events_and_async_contracts_v1.0.md](docs/05_architecture/14_domain_events_and_async_contracts_v1.0.md) — RabbitMQ message channel contracts and schemas.
- **[ARC-021] Major Saga Workflows:** [15_major_saga_workflows_v1.0.md](docs/05_architecture/15_major_saga_workflows_v1.0.md) — Orchestrated and choreographed transactions.
- **[GOV-006] Contradiction Register:** [06_contradiction_and_resolution_register_v1.0.md](docs/00_governance/06_contradiction_and_resolution_register_v1.0.md) — Resolution record for system contradictions.

### 7. [06_security_and_privacy](docs/06_security_and_privacy)
- **[PRV-001] Privacy & Retention:** [01_privacy_retention_export_deletion_v1.0.md](docs/06_security_and_privacy/01_privacy_retention_export_deletion_v1.0.md) — GDPR data deletion and anonymization workflows.
- **[ARC-007] Security Architecture:** [02_security_architecture_threat_model_v1.0.md](docs/06_security_and_privacy/02_security_architecture_threat_model_v1.0.md) — OAuth2/OIDC, BFF, security policies, and threat model.

### 8. [07_quality_and_operations](docs/07_quality_and_operations)
- **[ARC-012] Observability & Backup Runbooks:** [01_observability_backup_runbooks_v1.0.md](docs/07_quality_and_operations/01_observability_backup_runbooks_v1.0.md) — SLOs, logging rules, and disaster recovery.
- **[ARC-013] Testing Strategy & QA:** [02_testing_quality_assurance_strategy_v1.0.md](docs/07_quality_and_operations/02_testing_quality_assurance_strategy_v1.0.md) — Test levels, quality gates, and resilience verification.

### 9. [08_delivery_and_ai_agents](docs/08_delivery_and_ai_agents)
- **[ARC-014] CI/CD Strategy:** [01_cicd_repository_organization_v1.0.md](docs/08_delivery_and_ai_agents/01_cicd_repository_organization_v1.0.md) — Monorepo rules and GitHub Actions.
- **[ARC-015] Implementation Dependency Roadmap:** [02_implementation_epics_dependency_roadmap_v1.0.md](docs/08_delivery_and_ai_agents/02_implementation_epics_dependency_roadmap_v1.0.md) — Release epics and Gantt timeline.
- **[ARC-016] AI-Agent Review Gates & Rules:** [03_ai_agent_rules_review_gates_v1.0.md](docs/08_delivery_and_ai_agents/03_ai_agent_rules_review_gates_v1.0.md) — Governance rules and mandatory check gates.
