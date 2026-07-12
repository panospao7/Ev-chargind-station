Document ID: GOV-004
Title: Planning Status and Roadmap v1.1
Version: 1.1
Status: APPROVED
Owner: PO/DA
Last reviewed: 2026-07-12
Supersedes: docs/00_governance/04_planning_status_and_roadmap_v1.0.md
Depends on: None
Authoritative for: Milestones and Planning Roadmaps

# Planning Status and Roadmap v1.1

This document outlines the current completion status of the planning foundation phase and details the upcoming milestones for the system architecture and implementation roadmap.

---

## 1. Planning and Design Status (Phase 1, 2, & 3 — APPROVED & COMPLETE)

All planning and system design phases have been fully completed and approved:
- **Phase 1: Planning Foundation (Gate G1)** — **APPROVED** (tagged as `planning-foundation-v1.2`).
- **Phase 2: Logical Boundaries, Consistency & Concurrency (Gate G2)** — **APPROVED** (ARC-001 through ARC-011 and ARC-017).
- **Phase 3: Technical API & Event Contracts (Gate G3)** — **APPROVED** (REST API contracts, AsyncAPI event schemas, and saga workflows in ARC-018 through ARC-021).
- **Phase 4: Security, Observability & UI Flows (Gate G4–G5)** — **APPROVED** (Threat model, runbooks, QA, and frontend flows).
- **Phase 5: Operations & Readiness (Gate G6)** — **APPROVED** (CI/CD, Gantt dependency roadmap, and Hetzner IaC deployment).

Logical planning is complete; technical contract specifications are complete and currently **IN_REVIEW**. Executable contract schema generation remains pending.

1. **Project Constraints and Scope:** Completed and baselined in [SCP-001] and [SCP-002].
2. **Actors and Capabilities:** Completed and baselined in [SCP-003].
3. **Use Cases (Driver, Operator, Admin):** Driver use cases completed under `docs/02_use_cases/driver/`, operator under `docs/02_use_cases/operator/`, and admin/support under `docs/02_use_cases/administration_support/`.
4. **Functional Requirements Catalogue:** Consolidated, cleaned of contradictions, and fully mapped to specifications in [REQ-001].
5. **Non-Functional Requirements:** baselined with clear performance and reliability targets in [REQ-002].
6. **Domain Models and Logic:** State-machine transitions, glossary definitions, availability algorithms, and workflows are fully specified in `docs/03_domain/`.
7. **Platform and Consistency Models:** Background process logic, transactional outboxes, and simulator contracts are baselined in `docs/04_platform_and_integrations/`.
8. **Security and Privacy:** privacy workflows and a provisional retention schedule, deletion workflows, and role-based policies are baselined in [PRV-001].

---

## 2. Completed Architecture Milestones (Phase 2 & 3)

Now that the foundation specifications are approved and baselined, the project shifts to the System Architecture phase. The work will proceed in the following sequential order:

### Milestone 1: Logical Boundaries & Communication (G2)
- **Domain Capability Map:** Group functional requirements into logical boundaries and determine data ownership.
- **Inter-service Consistency Matrix:** Define which services communicate synchronously (REST) vs. asynchronously (message queues) and set consistency boundaries.

### Milestone 2: Technical API & DB Contracts (G3)
- **REST API Contracts:** Define the OpenAPI/Swagger specs for all user-facing and operator endpoints.
- **Event and Command Contracts:** Define schema formats for asynchronous inter-service event broker communications.
- **Database Models & Migrations:** Design entity relation diagrams (ERDs) and define migration plans.
- **Definitive Double-Booking Prevention Design:** Detail transaction isolation levels and lock implementation patterns to guarantee zero booking overlap.

### Milestone 3: Security & Observability (G4–G5)
- **Threat Model & Security Architecture:** Define OAuth2 flow details, resource server token validations, and secret manager storage.
- **Observability Strategy:** Standardize logs, metrics, correlation IDs, and tracing parameters.
- **Frontend Screen Catalogue:** Map Angular routing and state store actions to backend endpoints.

### Milestone 4: Operations & Readiness (G6)
- **Cloud Deployment and Cost Design:** Outline containerization, container registry, IaC scripts, and cost projections.
- **Testing & CI/CD Pipelines:** Setup build, test, and validation automation scripts.
- **Final Readiness Review:** Formal checklist sign-off before implementation.

## 3. Release 1 MVP Scope & Deferrals

To ensure a realistic scope for an individual developer, the platform's features are divided into Release 1 (MVP) and Release 2 (Deferred):

### Release 1 MVP (In-Scope)
1. **Station Discovery:** Map/list browsing and filters in Greece (`Europe/Athens`).
2. **Connector Details:** Inspect EVSE connector compatibility.
3. **Interval Availability:** Projections mapping allocations, maintenance, and faults.
4. **Registration & Auth:** OIDC/PKCE verified sign-in/recovery, plus privileged MFA.
5. **Holds & Bookings:** Create/confirm booking holds, view bookings, cancel bookings.
6. **Station Operations:** Basic operator settings, EVSE configurations, and tariff profiles.
7. **Basic Analytics:** Simple metrics for utilization and cancellations.
8. **Double-Booking Proof:** Pessimistic lock ordering and constraints in Booking.
9. **Basic Simulation:** Simulator protocol showing start, stop, and heartbeats.
10. **Essential Emails:** Booking confirmation, cancellation, and security alerts.
11. **Cloud Deployment:** Hetzner container deployment baseline.

### Release 2 (Deferred to Later Releases)
1. **Operator Applications:** Full sign-up review workflow (handled manually in MVP).
2. **Staff Invitations:** Automated invitation emails (provisioned manually in MVP).
3. **Platform Support Cases:** Central ticket management system.
4. **Data Privacy Exports:** GDPR data packaging worker.
5. **Data Deletion Coordination:** Automated deletion workers.
6. **Advanced Simulation:** OCPP 2.1 sequence replay and queue reconciliation.
7. **Marketing Notifications:** Reminders, push alerts, and SMS integrations.
