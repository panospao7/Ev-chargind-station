Document ID: GOV-003
Title: Consolidated System Specification v1.0
Version: 1.0
Status: APPROVED
Owner: PO/DA
Last reviewed: 2026-07-12
Supersedes: None
Depends on: All canonical documents
Authoritative for: Specification Index and Entry Point

# Consolidated System Specification v1.0

This specification serves as the master entry point and index for all approved design documentation, use cases, domain models, and technical architecture specifications of the EV Charging Booking Platform.

---

## 1. Project Specifications Registry

### 1.1 Governance & Process
- **[GOV-001] Decision Register:** [01_decision_and_open_question_register_v1.0.md](01_decision_and_open_question_register_v1.0.md)
- **[GOV-002] Consistency Review:** [02_cross_document_consistency_review_v1.0.md](02_cross_document_consistency_review_v1.0.md)
- **[GOV-004] Planning Status & Roadmap:** [04_planning_status_and_roadmap_v1.1.md](04_planning_status_and_roadmap_v1.1.md)
- **[GOV-005] Foundation Approval & Baseline Record:** [05_foundation_approval_and_baseline_record_v1.0.md](05_foundation_approval_and_baseline_record_v1.0.md)

### 1.2 System Scope & Requirements
- **[SCP-001] Project Constraints:** [../01_scope_and_requirements/01_project_constraints_v1.0.md](../01_scope_and_requirements/01_project_constraints_v1.0.md)
- **[SCP-002] System Scope:** [../01_scope_and_requirements/02_system_scope_v1.0.md](../01_scope_and_requirements/02_system_scope_v1.0.md)
- **[SCP-003] Actors & Operational Scope:** [../01_scope_and_requirements/03_actors_and_operational_scope_v1.1.md](../01_scope_and_requirements/03_actors_and_operational_scope_v1.1.md)
- **[REQ-001] Functional Requirements & Traceability Matrix:** [../01_scope_and_requirements/04_functional_requirements_and_traceability_v1.1.md](../01_scope_and_requirements/04_functional_requirements_and_traceability_v1.1.md)
- **[REQ-002] Non-Functional Requirements:** [../01_scope_and_requirements/05_non_functional_requirements_v1.0.md](../01_scope_and_requirements/05_non_functional_requirements_v1.0.md)

### 1.3 Use Cases
#### Driver Journey
- **[UC-DR-001] Driver Use-Case Catalogue:** [../02_use_cases/driver/01_driver_use_case_catalogue_v1.0.md](../02_use_cases/driver/01_driver_use_case_catalogue_v1.0.md)
- **[UC-DR-002] DR-01-05 Station Discovery:** [../02_use_cases/driver/02_dr_01_05_station_discovery_v1.0.md](../02_use_cases/driver/02_dr_01_05_station_discovery_v1.0.md)
- **[UC-DR-003] DR-06-10 Account, Auth & Vehicle Profile:** [../02_use_cases/driver/03_dr_06_10_account_auth_vehicle_profile_v1.0.md](../02_use_cases/driver/03_dr_06_10_account_auth_vehicle_profile_v1.0.md)
- **[UC-DR-004] DR-11/12 Create Booking:** [../02_use_cases/driver/04_dr_11_12_create_booking_v1.0.md](../02_use_cases/driver/04_dr_11_12_create_booking_v1.0.md)
- **[UC-DR-005] DR-13, 21-25 Remaining Driver Use Cases:** [../02_use_cases/driver/05_dr_13_21_25_remaining_driver_use_cases_v1.0.md](../02_use_cases/driver/05_dr_13_21_25_remaining_driver_use_cases_v1.0.md)
- **[UC-DR-006] DR-14/15 Reschedule & Cancel Booking:** [../02_use_cases/driver/06_dr_14_15_reschedule_cancel_booking_v1.0.md](../02_use_cases/driver/06_dr_14_15_reschedule_cancel_booking_v1.0.md)
- **[UC-DR-007] DR-16 Check-In & Arrival Authorization:** [../02_use_cases/driver/07_dr_16_check_in_authorization_v1.0.md](../02_use_cases/driver/07_dr_16_check_in_authorization_v1.0.md)
- **[UC-DR-008] DR-17-20 Charging Session Lifecycle:** [../02_use_cases/driver/08_dr_17_20_charging_session_lifecycle_v1.0.md](../02_use_cases/driver/08_dr_17_20_charging_session_lifecycle_v1.0.md)

#### Operator Portal
- **[UC-OP-001] Operator Catalogue & Roles:** [../02_use_cases/operator/01_operator_use_case_catalogue_and_roles_v1.0.md](../02_use_cases/operator/01_operator_use_case_catalogue_and_roles_v1.0.md)
- **[UC-OP-002] Detailed Operator Use Cases:** [../02_use_cases/operator/02_detailed_operator_use_cases_v1.0.md](../02_use_cases/operator/02_detailed_operator_use_cases_v1.0.md)

#### Admin & Platform Support
- **[UC-AD-001] Admin & Support Catalogue:** [../02_use_cases/administration_support/01_administrator_platform_support_catalogue_v1.0.md](../02_use_cases/administration_support/01_administrator_platform_support_catalogue_v1.0.md)
- **[UC-AD-002] Detailed Admin & Support Use Cases:** [../02_use_cases/administration_support/02_detailed_administrator_platform_support_use_cases_v1.0.md](../02_use_cases/administration_support/02_detailed_administrator_platform_support_use_cases_v1.0.md)

### 1.4 Domain Models & Logic
- **[DOM-001] Domain Glossary:** [../03_domain/01_domain_glossary_v1.0.md](../03_domain/01_domain_glossary_v1.0.md)
- **[DOM-002] Lifecycle & Invariant Catalogue:** [../03_domain/02_lifecycle_and_invariant_catalogue_v1.0.md](../03_domain/02_lifecycle_and_invariant_catalogue_v1.0.md)
- **[DOM-003] Infrastructure Schema & Tariff Model:** [../03_domain/03_station_evse_connector_tariff_policy_model_v1.0.md](../03_domain/03_station_evse_connector_tariff_policy_model_v1.0.md)
- **[DOM-004] Booking Lifecycle Specification:** [../03_domain/04_booking_lifecycle_and_policy_v1.0.md](../03_domain/04_booking_lifecycle_and_policy_v1.0.md)
- **[DOM-005] Availability Calculation Model:** [../03_domain/05_availability_calculation_model_v1.0.md](../03_domain/05_availability_calculation_model_v1.0.md)
- **[DOM-006] Workflows (Maintenance/Faults/Reassignments):** [../03_domain/06_maintenance_fault_reassignment_workflows_v1.0.md](../03_domain/06_maintenance_fault_reassignment_workflows_v1.0.md)

### 1.5 Platform & Integration Specifications
- **[PLT-001] Background Processes & Consistency:** [../04_platform_and_integrations/01_background_processes_distributed_consistency_v1.0.md](../04_platform_and_integrations/01_background_processes_distributed_consistency_v1.0.md)
- **[SIM-001] Charger Simulator Protocol:** [../04_platform_and_integrations/02_charger_simulator_protocol_v1.0.md](../04_platform_and_integrations/02_charger_simulator_protocol_v1.0.md)
- **[NOT-001] Notification Rules & Email Matrix:** [../04_platform_and_integrations/03_notification_rules_email_matrix_v1.0.md](../04_platform_and_integrations/03_notification_rules_email_matrix_v1.0.md)

### 1.6 Security & Privacy
- **[PRV-001] Privacy, Retention & Deletion:** [../06_security_and_privacy/01_privacy_retention_export_deletion_v1.0.md](../06_security_and_privacy/01_privacy_retention_export_deletion_v1.0.md)
- **[ARC-007] Security Architecture & Threat Model:** [../06_security_and_privacy/02_security_architecture_threat_model_v1.0.md](../06_security_and_privacy/02_security_architecture_threat_model_v1.0.md)

### 1.7 Technical Architecture Specifications
- **[ARC-001] Boundary Analysis:** [../05_architecture/01_domain_capability_map_boundary_analysis_v1.0.md](../05_architecture/01_domain_capability_map_boundary_analysis_v1.0.md)
- **[ARC-002] Inter-Service Communication:** [../05_architecture/02_inter_service_communication_consistency_v1.0.md](../05_architecture/02_inter_service_communication_consistency_v1.0.md)
- **[ARC-003] REST API Contract Catalogue:** [../05_architecture/03_rest_api_contract_catalogue_v1.0.md](../05_architecture/03_rest_api_contract_catalogue_v1.0.md)
- **[ARC-004] Event & Command Contract Catalogue:** [../05_architecture/04_event_command_contract_catalogue_v1.0.md](../05_architecture/04_event_command_contract_catalogue_v1.0.md)
- **[ARC-005] Database Models & Ownership:** [../05_architecture/05_database_models_ownership_migration_v1.0.md](../05_architecture/05_database_models_ownership_migration_v1.0.md)
- **[ARC-006] Double-Booking Prevention Concurrency:** [../05_architecture/06_double_booking_prevention_concurrency_v1.0.md](../05_architecture/06_double_booking_prevention_concurrency_v1.0.md)
- **[ARC-008] Frontend UX Flow Spec:** [../05_architecture/07_frontend_ux_flow_specification_v1.0.md](../05_architecture/07_frontend_ux_flow_specification_v1.0.md)
- **[ARC-009] Technology Selection & ADRs:** [../05_architecture/08_technology_selection_adr_set_v1.0.md](../05_architecture/08_technology_selection_adr_set_v1.0.md)
- **[ARC-010] Cloud Provider & Cost Analysis:** [../05_architecture/09_cloud_provider_cost_analysis_v1.0.md](../05_architecture/09_cloud_provider_cost_analysis_v1.0.md)
- **[ARC-011] Deployment Architecture & IaC:** [../05_architecture/10_deployment_architecture_iac_v1.0.md](../05_architecture/10_deployment_architecture_iac_v1.0.md)
- **[ARC-017] Modular-Monolith Alternative Design:** [../05_architecture/11_modular_monolith_alternative_design_v1.0.md](../05_architecture/11_modular_monolith_alternative_design_v1.0.md)

### 1.8 Quality, Operations & Delivery
- **[ARC-012] Observability & Backup Runbooks:** [../07_quality_and_operations/01_observability_backup_runbooks_v1.0.md](../07_quality_and_operations/01_observability_backup_runbooks_v1.0.md)
- **[ARC-013] Testing Strategy & QA:** [../07_quality_and_operations/02_testing_quality_assurance_strategy_v1.0.md](../07_quality_and_operations/02_testing_quality_assurance_strategy_v1.0.md)
- **[ARC-014] CI/CD Strategy:** [../08_delivery_and_ai_agents/01_cicd_repository_organization_v1.0.md](../08_delivery_and_ai_agents/01_cicd_repository_organization_v1.0.md)
- **[ARC-015] Implementation Dependency Roadmap:** [../08_delivery_and_ai_agents/02_implementation_epics_dependency_roadmap_v1.0.md](../08_delivery_and_ai_agents/02_implementation_epics_dependency_roadmap_v1.0.md)
- **[ARC-016] AI-Agent Review Gates & Rules:** [../08_delivery_and_ai_agents/03_ai_agent_rules_review_gates_v1.0.md](../08_delivery_and_ai_agents/03_ai_agent_rules_review_gates_v1.0.md)

---

## 2. Document Governance Rules
1. **Approval Status:** All production deliverables have their status updated to `APPROVED` upon closeout.
2. **Version Alignment:** Version labels must align between filenames and first headings.
3. **Precedence Hierarchy:** In case of overlap, the Approved Decision Register `GOV-001` and consolidated functional requirements `REQ-001` override lower-level documents.
