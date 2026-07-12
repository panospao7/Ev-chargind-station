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

## 1. Planning and Design Status

The design phase is structured into separate gates:
- **Gate G1: Planning Foundation** — **APPROVED** (tagged as `planning-foundation-v1.2`).
- **Gate G2: Logical Architecture** — **APPROVED** (capability boundaries, communication, REST/event contracts, database models, concurrency, frontend UX, and technology selection in ARC-001 through ARC-009).
- **Gate G3: Contract Catalogues & Executable Schemas** — **LOGICAL APPROVED; EXECUTABLE IN_REVIEW** (ARC-018–021 logical contracts approved. OpenAPI, AsyncAPI and JSON Schema executable contracts exist; CI validation must pass green before VERIFIED status.).
- **Gate G4: Security Architecture** — **APPROVED** (ARC-007 security architecture and SEC-001 implementation proof plan approved).
- **Gate G5: Cloud and Operations** — **IN_REVIEW** (ARC-010, ARC-011 cloud and deployment architecture in review).
- **Gate G6: Testing, Quality and Readiness** — **PENDING** (ARC-012, ARC-013 dependent on earlier gates).

**Implementation-enablement plans** — **APPROVED** (GOV-007 W1 baseline, ENG-001 engineering foundation, ARC-022 persistence/contracts, SEC-001 security proofs, ARC-023 UX implementation contract).

**Implementation readiness** — **PENDING** (executable contract validation, security proofs, and engineering foundation must demonstrate green CI before business-feature implementation begins).

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

Now that the foundation specifications are approved and baselined, the project shifts to the System Architecture phase. The design phase has completed milestones as follows:

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

## 3. Release 1 W1 Scope — Two Increments

W1 is divided into two controlled increments as defined in GOV-007:

### W1-S1: First Vertical Slice

The first demonstrable end-to-end path:

1. **Repeatable Infrastructure Seed:** Deterministic bootstrap with test operator, stations, EVSEs, tariffs, policies, simulator assignment and identities. [W1-S1]
2. **Public Station Discovery:** Map/list browsing, geographic search, connector/power filters, station/EVSE details, tariff and opening-hours display, operational freshness. [W1-S1]
3. **Interval Availability:** Projections using half-open intervals, 15-min increments, 5-min hold, 15-min min, 4-hr max, 14-day advance, 60-min near-term, 300-sec freshness. [W1-S1]
4. **Driver Authentication:** Registration, email verification, login/logout, recovery, BFF session cookie, CSRF, no browser-held token. [W1-S1]
5. **Hold and Booking:** Automatic/explicit EVSE assignment, idempotent booking, BOOKING_HOLD, interval conflict prevention, enforcement projections, snapshot tariff/policy. [W1-S1]
6. **Booking Management:** Upcoming details, permitted actions, cancellation, capacity release, history, uncertain/interrupted display. [W1-S1]
7. **Check-in and Authorization:** QR/manual EVSE identifier, 15-min check-in window, 15-min late grace, single-use start authorization, no secrets in QR/logs/responses. [W1-S1]
8. **Simulated Charging:** Full happy path — start intent, simulator acceptance, DeviceTransactionStarted, meter updates, monitoring, stop, DeviceTransactionEnded, session summary with duration/energy/tariff/cost/reason. Duplicate safety, rejection, timeout and equipment-failure handling. [W1-S1]
9. **Messaging and Projections:** Transactional outbox, idempotent inbox, at-least-once, Booking-to-Discovery capacity projection, version validation, bounded retry, quarantine, projection rebuild. [W1-S1]
10. **Notifications and Audit:** Booking confirmation/cancellation emails, security emails, local mail catcher, immutable audit. [W1-S1]
11. **Operational Foundation:** Docker Compose with PostgreSQL, RabbitMQ, Keycloak, mail catcher, simulator, health/readiness, structured logs, correlation IDs, metrics/traces, demonstrator cloud deployment. [W1-S1]

### W1-S2: Remaining W1 Completion

After S1 is stable, complete:

12. **Atomic Rescheduling:** Same/different EVSE interval change within the same booking transaction. [W1-S2]
13. **Station Operations:** Operator station/EVSE/connector/tariff/policy CRUD. [W1-S2]
14. **Maintenance, Fault and Override Workflows:** Full lifecycle with capacity restriction integration. [W1-S2]
15. **Operator Booking Intervention:** Authorized cancellation, reassignment, emergency stop. [W1-S2]
16. **Basic Analytics:** Utilization and cancellation metrics. [W1-S2]
17. **Administrator Controls:** Suspension and reference-data management. [W1-S2]
18. **Notification Templates:** Richer email content. [W1-S2]
19. **Retry/Quarantine/Replay:** Full dead-letter operations. [W1-S2]
20. **Simulator Failure Scenarios:** Broader deterministic failure testing. [W1-S2]
21. **Privileged-action Audit:** Extended audit coverage. [W1-S2]
22. **Backup/Restore Smoke Testing.** [W1-S2]
23. **Expanded Concurrency and Resilience Testing.** [W1-S2]

### Wave applicability: W2

1. **Operator Applications:** Full sign-up review workflow including operator application lifecycle events, approval saga and automated organization creation (handled manually with pre-seeded operators in W1). [W2]
2. **Staff Invitations:** Automated invitation workflow lifecycle, emails, and acceptance handling (provisioned manually in W1). [W2]
3. **Platform Support Cases:** Central ticket management system. [W2]
4. **Notification Preferences.** [W2]
5. **Advanced Operator Workflows.** [W2]
6. **Device Reservation Mirror.** [W2]

### Wave applicability: W3

7. **Data Privacy Exports:** GDPR data packaging worker. [W3]
8. **Data Deletion Coordination:** Automated deletion workers. [W3]
9. **Advanced Simulation:** OCPP 2.1 sequence replay and queue reconciliation. [W3]
10. **SMS, Push and Marketing Notifications.** [W3]
11. **Advanced Disaster Recovery.** [W3]
