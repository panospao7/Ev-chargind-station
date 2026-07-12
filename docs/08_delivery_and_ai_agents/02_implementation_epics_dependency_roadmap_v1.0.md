# Implementation Epics and Dependency Roadmap v1.0

**Document ID:** ARC-015  
**Title:** Implementation Epics and Dependency Roadmap  
**Version:** 1.0  
**Status:** IN_REVIEW  
**Owner:** Delivery Lead / Project Owner  
**Last reviewed:** 2026-07-12  
**Depends on:** ARC-001–014, GOV-003, GOV-005  
**Authoritative for:** Implementation sequencing, epic dependencies, milestones, vertical slices, delivery gates and roadmap status

## 1. Purpose

This roadmap converts the approved foundation and architecture into an implementation sequence.

It defines:

- Epics and deliverables
- Dependencies
- Vertical slices
- Architecture-enabler work
- Critical path
- Parallelizable work
- Proofs of concept
- Release increments
- Definition of Ready
- Definition of Done
- Milestones and exit criteria
- Implementation risks

Implementation begins only after the architecture baseline is approved and the remaining proof-of-concept decisions are resolved or explicitly accepted as provisional.

---

## 2. Delivery principles

1. Deliver vertical business slices rather than isolated technical layers.
2. Implement correctness-critical infrastructure before broad features.
3. Prove PostgreSQL allocation concurrency before booking UX expansion.
4. Prove BFF authentication before protected frontend workflows.
5. Prove REST and message contracts before service implementation diverges.
6. Keep the modular-monolith mapping continuously viable.
7. Prefer small reversible increments.
8. Do not build deferred capabilities early.
9. Every epic must produce executable evidence.
10. Every release must remain deployable.
11. Security, privacy and observability are built into each slice.
12. No manual production-only configuration is accepted as a dependency.

---

# 3. Roadmap status

| Area | Status |
|---|---|
| Product/domain foundation | Baselined |
| Architecture documents ARC-001–014 | Ready for approval/baselining |
| Implementation | Not started |
| Repository implementation structure | Not started |
| Technology proofs of concept | Not started |
| Production deployment | Not started |
| Production user onboarding | Prohibited |

---

# 4. Milestone model

## M0 — Architecture baseline

**Goal:** Approve and tag the architecture baseline.

Exit criteria:

- ARC-001–014 approved or exceptions recorded.
- Open questions assigned owners and deadlines.
- Technology proof-of-concept plan accepted.
- Repository implementation structure approved.
- `architecture-baseline-v1.0` tag created.
- No unresolved blocker affects the first implementation slice.

## M1 — Engineering runway

**Goal:** Establish build, test, security and local-development foundations.

Exit criteria:

- Monorepo implementation structure exists.
- CI executes required checks.
- Local dependencies start reproducibly.
- Coding standards and dependency locks are active.
- Testcontainers can start PostgreSQL and RabbitMQ.
- Basic observability works locally.
- No business feature is claimed complete.

## M2 — Secure platform core

**Goal:** Establish identity, BFF, service runtime and persistence foundations.

Exit criteria:

- Browser login works through the BFF.
- Service authorization is enforced.
- At least one service runs through the standard build/deploy path.
- Database migrations and outbox/inbox patterns work.
- Security and audit evidence exists.

## M3 — Discovery slice

**Goal:** Deliver public station discovery using synthetic infrastructure.

Exit criteria:

- Stations can be searched and viewed.
- Map/list equivalence works.
- Greek and English routes work.
- Projection freshness is visible.
- No discovery result claims authoritative reservation.

## M4 — Booking correctness slice

**Goal:** Deliver authoritative EVSE allocation and booking lifecycle.

Exit criteria:

- Holds, confirmation, cancellation and expiration work.
- Exact and automatic EVSE assignment work.
- PostgreSQL constraints and concurrency tests pass.
- No double booking occurs under the required race suite.
- Booking history is available.

## M5 — Charging fulfilment slice

**Goal:** Connect check-in, simulator commands and charging sessions.

Exit criteria:

- Check-in creates single-use authorization.
- Start enters `STARTING`.
- Only device transaction evidence creates `CHARGING`.
- Stop and uncertainty are represented correctly.
- Meter deduplication and session summaries work.
- Session overrun handling is tested.

## M6 — Operator operations slice

**Goal:** Enable operators to manage infrastructure and operational workflows.

Exit criteria:

- Organization and staff roles work.
- Stations, EVSEs and connectors can be configured.
- Tariff and policy versions are managed.
- Maintenance and fault workflows work.
- Operational restrictions affect Booking enforcement.

## M7 — Privacy, support and communications slice

**Goal:** Deliver essential notification, support and privacy workflows.

Exit criteria:

- Transactional email works through the provider abstraction.
- Support access is case-scoped.
- Privacy export and deletion workflows are idempotent.
- Tombstone restoration is tested.
- Secrets and personal data are minimized.

## M8 — Production-like hardening

**Goal:** Validate performance, security, resilience and deployment recovery.

Exit criteria:

- Reference and double-load tests meet approved targets.
- Security verification is complete.
- Accessibility verification is complete.
- Node, database and broker recovery drills pass.
- Location-recovery RTO is measured.
- Cost remains within approved limits.

## M9 — Implementation readiness review

**Goal:** Confirm the platform is ready for controlled release or academic demonstration.

Exit criteria:

- All mandatory requirements have evidence.
- No blocker or critical defect remains.
- Release artifact is reproducible.
- Runbooks are executable.
- Architecture and implementation deviations are documented.
- Final evaluation package is complete.

---

# 5. Epic catalogue

## EPIC-00 — Architecture baseline and delivery governance

**Purpose:** Close the architecture gate.

Deliverables:

- Approved ARC-001–014 documents
- Architecture decision register updates
- Open-question ownership
- Architecture baseline tag
- Implementation backlog conventions
- Requirement-to-epic mapping

Dependencies: Foundation baseline

Acceptance:

- Architecture authority and statuses are unambiguous.
- No implementation work starts under unresolved blocker status.

---

## EPIC-01 — Repository and developer runway

**Purpose:** Create the reproducible development environment.

Deliverables:

- Monorepo implementation directories
- Java/Maven baseline
- Angular/Node baseline
- Formatting and linting
- Local environment documentation
- Docker Compose dependencies
- Makefile or task-runner commands
- `.env.example` without secrets
- Test-data builders
- Developer troubleshooting guide

Dependencies: EPIC-00

Acceptance:

- A new developer can run tests and local dependencies from documented steps.
- No developer-specific absolute paths are required.

---

## EPIC-02 — CI/CD and supply-chain foundation

**Purpose:** Automate quality and artifact production.

Deliverables:

- Pull-request workflow
- Main build workflow
- Dependency automation
- Secret scanning
- CodeQL
- SBOM generation
- Container scanning
- Image signing/provenance
- GHCR publication
- Promotion pull-request mechanism
- Flux status integration

Dependencies: EPIC-01

Acceptance:

- A harmless sample change passes the complete PR pipeline.
- A deliberately vulnerable or unsigned artifact is blocked.
- Images are published only from trusted workflows.

---

## EPIC-03 — Shared technical platform kernel

**Purpose:** Implement approved technical primitives without creating a shared business domain.

Deliverables:

- Correlation and causation identifiers
- Problem Details model
- Idempotency abstraction
- Aggregate version handling
- Outbox persistence
- Inbox persistence
- Workflow persistence
- Audit-event persistence
- Secure logging
- Clock/database-time abstraction
- Message envelope validation
- Technical error mapping

Dependencies: EPIC-01, EPIC-02

Constraints:

- No Booking, Station or Session business logic.
- Shared libraries remain technical only.

Acceptance:

- Duplicate commands, event deliveries and retries behave deterministically.
- Technical libraries pass architecture-boundary tests.

---

## EPIC-04 — Identity Provider and BFF security

**Purpose:** Establish secure browser and service authentication.

Deliverables:

- Keycloak environment
- Realm/client configuration
- BFF login and logout
- Authorization Code + PKCE
- Server-side token storage
- Session rotation and expiry
- CSRF protection
- Audience-limited service tokens
- MFA/step-up proof of concept
- Service identity proof of concept
- Security headers and CORS policy

Dependencies: EPIC-01, EPIC-02, EPIC-03

Acceptance:

- Tokens never enter browser storage.
- Login, logout, session expiry and revocation work.
- Unauthorized and cross-audience calls fail.
- CSRF and object-level authorization tests pass.

---

## EPIC-05 — Persistence and migration foundation

**Purpose:** Establish service-owned PostgreSQL persistence.

Deliverables:

- PostgreSQL local environment
- Logical databases/schemas
- Service database roles
- Flyway structure
- Migration pipeline
- UUID/public-reference generation
- Version columns
- Retention-job framework
- Backup-compatible configuration
- Migration test fixtures

Dependencies: EPIC-01, EPIC-02, EPIC-03

Acceptance:

- Fresh and upgrade migrations pass.
- Runtime roles cannot execute DDL.
- Migration failure blocks deployment.
- Service ownership rules are enforced.

---

## EPIC-06 — Contract foundation

**Purpose:** Make REST and asynchronous contracts executable.

Deliverables:

- OpenAPI source structure
- AsyncAPI source structure
- JSON Schemas
- Problem Details schemas
- Generated Angular clients
- Generated Java interfaces/DTOs
- Compatibility checks
- Contract examples
- Contract-test harness

Dependencies: EPIC-03, EPIC-05

Acceptance:

- Contracts validate in CI.
- Generated clients compile.
- Breaking changes are detected.
- Unknown optional response fields are tolerated.

---

## EPIC-07 — Station Operations core

**Purpose:** Implement authoritative operator infrastructure data.

Deliverables:

- Operator application
- Organization lifecycle
- Staff membership and roles
- Station lifecycle
- EVSE lifecycle
- Connector model
- Opening hours and exceptions
- Tariff versions
- Booking-policy versions
- Publication events
- Infrastructure projections for Booking

Dependencies: EPIC-04, EPIC-05, EPIC-06

Acceptance:

- Operator organization boundaries work.
- Published stations have valid infrastructure.
- Activated tariff/policy versions are immutable.
- Configuration events update Booking projections safely.

---

## EPIC-08 — Account and driver profile

**Purpose:** Implement application-account data separate from credentials.

Deliverables:

- Application account
- Driver profile
- Vehicle profiles
- Connector compatibility
- Account eligibility projection
- Notification preferences
- Account lifecycle
- Account audit

Dependencies: EPIC-04, EPIC-05, EPIC-06

Acceptance:

- Identity Provider and application account remain separate.
- Suspended/ineligible accounts cannot create new bookings.
- Driver ownership checks pass.
- Vehicle data excludes prohibited identifiers.

---

## EPIC-09 — Discovery and read projections

**Purpose:** Deliver public station search and details.

Deliverables:

- Station search projection
- EVSE/connector projection
- Advisory availability projection
- Map/list search API
- Filters and cursor pagination
- Freshness indicators
- Greek/English public routes
- Map provider abstraction
- List-equivalent accessible view

Dependencies: EPIC-07, EPIC-08, EPIC-06

Acceptance:

- Search remains non-authoritative.
- Stale/unknown status is visible.
- Public data excludes private allocations and personal information.
- Map failure does not remove list functionality.

---

## EPIC-10 — Allocation and Booking correctness

**Purpose:** Implement the central transactional boundary.

Deliverables:

- `evse_allocation_guard`
- `driver_schedule_guard`
- Capacity claims
- Operational occupation
- Booking Hold
- Exact EVSE assignment
- Automatic assignment
- Confirmation
- Expiration
- Cancellation
- Booking history
- Tariff/policy snapshots
- Idempotency and audit
- Outbox events

Dependencies: EPIC-05, EPIC-06, EPIC-07, EPIC-08

Acceptance:

- ARC-006 SQL and transaction design is implemented.
- Required race suite passes.
- PostgreSQL exclusion constraints are active.
- Failed rescheduling/reassignment preserves the original claim.
- No remote call occurs inside allocation transactions.

This is the primary critical-path epic.

---

## EPIC-11 — Booking management extensions

**Purpose:** Complete driver booking workflows.

Deliverables:

- Rescheduling
- Reassignment
- Driver overlap protection
- Operator booking view
- No-show processing
- Fulfilment-risk records
- Booking notifications
- Booking history and filters

Dependencies: EPIC-10, EPIC-09

Acceptance:

- Check-in/no-show/start races are tested.
- Operator views are organization-scoped.
- Equipment failure never becomes `NO_SHOW`.

---

## EPIC-12 — Check-in and start authorization

**Purpose:** Implement secure arrival authorization.

Deliverables:

- QR/public EVSE identification
- Manual EVSE identifier
- Check-in window
- Grace deadline
- Start Authorization issuance
- Authorization consumption
- Revocation and expiry
- Abandon check-in
- Check-in audit

Dependencies: EPIC-10, EPIC-04, EPIC-06

Acceptance:

- Authorization is single-use and bound to Booking, driver, EVSE and Session.
- QR codes contain no secrets.
- Check-in does not start charging.

---

## EPIC-13 — Device Integration and simulator

**Purpose:** Establish authenticated device communication.

Deliverables:

- Machine identity
- Simulator enrollment
- Certificate-based authentication
- WebSocket protocol
- Heartbeats
- Device status
- Command lifecycle
- Event inbox
- Sequence validation
- Offline queue
- Reconnection
- Failure injection
- Device normalization

Dependencies: EPIC-02, EPIC-03, EPIC-05, EPIC-06, EPIC-07, security PKI proof

Acceptance:

- A simulator cannot report unassigned EVSEs.
- Duplicate and out-of-order messages are handled.
- Command timeout remains uncertain.
- Device Integration cannot write Booking tables.

---

## EPIC-14 — Charging Session and metering

**Purpose:** Complete simulated charging fulfilment.

Deliverables:

- Session lifecycle
- Start command
- Transaction-start evidence
- Suspended/resumed state
- Stop command
- Transaction-ended evidence
- Meter sequence processing
- Energy accumulation
- Estimated cost
- Session summary
- Session overrun
- Reconciliation
- Operational occupation release

Dependencies: EPIC-10, EPIC-12, EPIC-13

Acceptance:

- Only physical start evidence creates `CHARGING`.
- Duplicate meter events do not inflate energy.
- Stop acceptance does not finalize the Session.
- Uncertainty remains blocking.
- Interrupted Sessions are represented distinctly.

---

## EPIC-15 — Operator operational workflows

**Purpose:** Implement maintenance, faults and operational controls.

Deliverables:

- Maintenance scheduling
- Impact preview
- Capacity-block workflow
- Maintenance activation/completion
- Fault Reports
- Fault Incidents
- Status Overrides
- Reassignment workflows
- Simulator assignment
- Operational dashboards

Dependencies: EPIC-07, EPIC-10, EPIC-13

Acceptance:

- Maintenance blocks are installed before activation.
- Booking and maintenance races are safe.
- Emergency actions are scoped and audited.
- Device state and derived availability remain separate.

---

## EPIC-16 — Notification service

**Purpose:** Deliver essential transactional email.

Deliverables:

- Notification rules
- Template versioning
- Greek/English templates
- Outbox-driven triggers
- Provider adapter
- Retry and suppression
- Bounce/complaint handling
- Delivery history
- Obsolete notification handling

Dependencies: EPIC-03, EPIC-04, EPIC-06, EPIC-08, EPIC-07

Acceptance:

- Email failure never reverses business state.
- Tokens do not enter broker messages.
- Mandatory notifications cannot be preference-suppressed.
- Provider webhooks are authenticated and idempotent.

---

## EPIC-17 — Support and governance

**Purpose:** Implement case-scoped operational support.

Deliverables:

- Support Cases
- Assignments
- Temporary access grants
- Masked views
- Reveal workflow
- Operator application review
- Suspension requests
- Emergency interventions
- Break-glass workflow
- Central audit projection

Dependencies: EPIC-04, EPIC-07, EPIC-08, EPIC-10, EPIC-15

Acceptance:

- Support cannot browse outside case scope.
- Foreign business data changes occur through authoritative owners.
- Break-glass expires and is audited.
- Emergency actions cannot bypass invariants.

---

## EPIC-18 — Privacy and data lifecycle

**Purpose:** Deliver privacy workflows and retention controls.

Deliverables:

- Data inventory
- Access export
- Portability export where applicable
- Processing restriction
- Account deletion
- Participant commands
- Privacy Recovery Ledger
- Tombstones
- Retention jobs
- Projection cleanup
- Export expiration
- Restore replay

Dependencies: EPIC-08, EPIC-03, EPIC-05, all personal-data owners

Acceptance:

- Deletion cannot complete with unresolved mandatory participants.
- Exports exclude secrets and unrelated personal data.
- Tombstones survive restore and projection rebuild.
- Pseudonymization is not labelled anonymization.

---

## EPIC-19 — Frontend shells and design system

**Purpose:** Implement the shared Angular application structure.

Deliverables:

- Public, Driver, Operator and Platform shells
- Routing and route-context handling
- Design tokens
- Accessible shared components
- Localization infrastructure
- API-client adapters
- Problem Details handling
- Session-expiry handling
- Navigation and role-area selection

Dependencies: EPIC-04, EPIC-06

Acceptance:

- Browser communicates only with BFF.
- No OAuth token is stored in browser-accessible storage.
- Greek and English route handling works.
- Accessibility foundation passes automated and manual checks.

---

## EPIC-20 — Driver frontend vertical slice

**Purpose:** Deliver the complete driver journey.

Deliverables:

- Discovery
- Station details
- Vehicle selection
- Booking creation
- Hold countdown
- Confirmation
- Upcoming bookings
- Booking detail
- Reschedule/cancel
- Check-in
- Charging Session
- History
- Fault report
- Support access
- Privacy centre

Dependencies: EPIC-09, EPIC-10, EPIC-12, EPIC-14, EPIC-16, EPIC-18, EPIC-19

Acceptance:

- End-to-end Driver journey passes in Greek and English.
- Conflict, expiry, rejection and uncertainty states are usable.
- Map/list equivalence is verified.

---

## EPIC-21 — Operator frontend vertical slice

**Purpose:** Deliver operator management and operations.

Deliverables:

- Organization dashboard
- Staff
- Station/EVSE configuration
- Tariffs and policies
- Maintenance
- Faults
- Booking impact
- Simulator control
- Analytics
- Audit

Dependencies: EPIC-07, EPIC-13, EPIC-15, EPIC-19

Acceptance:

- Cross-organization access is denied.
- Technician/support views minimize driver information.
- Operational states remain distinct.

---

## EPIC-22 — Platform frontend vertical slice

**Purpose:** Deliver governance and support workflows.

Deliverables:

- Operator application review
- Support queue
- Case workspace
- Masked-field reveal
- Emergency intervention
- Break-glass
- Privacy review
- Audit/security screens

Dependencies: EPIC-17, EPIC-18, EPIC-19

Acceptance:

- Case scope is visible.
- Privileged actions require recent authentication and reason.
- Break-glass expiry is visible.

---

## EPIC-23 — Deployment and infrastructure

**Purpose:** Deploy the platform reproducibly.

Deliverables:

- OpenTofu modules
- Hetzner network and nodes
- K3s bootstrap
- Flux
- Traefik
- cert-manager
- CloudNativePG
- RabbitMQ Operators
- Keycloak Operator
- step-ca
- NetworkPolicies
- SOPS/age
- Database migration Jobs
- Promotion workflow

Dependencies: EPIC-02, EPIC-05, EPIC-13, architecture deployment approval

Acceptance:

- Reference environment can be rebuilt from code.
- One-node failure is survivable.
- Images deploy by digest.
- Secrets remain encrypted.

---

## EPIC-24 — Observability and operational readiness

**Purpose:** Make the platform diagnosable and recoverable.

Deliverables:

- Metrics
- Logs
- Traces
- Dashboards
- Alert rules
- SLO recording rules
- Backup monitoring
- Restore tests
- Runbooks
- Incident templates
- Cost monitoring
- Recovery evidence

Dependencies: EPIC-23, all deployed services

Acceptance:

- Every actionable alert has a runbook.
- Backup restoration succeeds.
- No sensitive telemetry leakage exists.
- RPO/RTO evidence is recorded.

---

## EPIC-25 — Performance, security and resilience hardening

**Purpose:** Validate the complete platform.

Deliverables:

- Reference-load tests
- Double-load tests
- Eight-hour soak
- Security verification
- Accessibility verification
- Node-loss drill
- PostgreSQL failover
- RabbitMQ recovery
- Keycloak recovery
- Simulator certificate compromise
- Location-recovery drill
- Cost review

Dependencies: EPIC-20, EPIC-21, EPIC-22, EPIC-23, EPIC-24

Acceptance:

- Approved NFR targets are met or exceptions accepted.
- No blocker/critical defects remain.
- Recovery objectives are demonstrated.

---

## EPIC-26 — Final implementation-readiness and evaluation package

**Purpose:** Prepare the project for controlled release, demonstration and evaluation.

Deliverables:

- Final traceability matrix
- Architecture deviation report
- Known-risk register
- Demonstration dataset
- Evaluation scenarios
- Deployment guide
- User guide
- Operator guide
- Security evidence
- Test evidence
- Recovery evidence
- Final readiness report

Dependencies: EPIC-25

Acceptance:

- Readiness review passes.
- All required evidence is indexed.
- Demonstration can be repeated from a clean environment.

---

# 6. Dependency graph

```text
EPIC-00
  └── EPIC-01
       ├── EPIC-02
       ├── EPIC-03
       └── EPIC-05
            └── EPIC-06
                 ├── EPIC-07
                 ├── EPIC-08
                 ├── EPIC-13
                 └── EPIC-19

EPIC-07 + EPIC-08 + EPIC-05 + EPIC-06
  └── EPIC-10
       ├── EPIC-11
       ├── EPIC-12
       │    └── EPIC-14
       └── EPIC-15

EPIC-07 + EPIC-08
  └── EPIC-09

EPIC-09 + EPIC-10 + EPIC-12 + EPIC-14 + EPIC-19
  └── EPIC-20

EPIC-13 + EPIC-15 + EPIC-19
  └── EPIC-21

EPIC-17 + EPIC-18 + EPIC-19
  └── EPIC-22

EPIC-02 + EPIC-05 + EPIC-13
  └── EPIC-23
       └── EPIC-24

EPIC-16, EPIC-17, EPIC-18
  integrate across the vertical slices

EPIC-20 + EPIC-21 + EPIC-22 + EPIC-23 + EPIC-24
  └── EPIC-25
       └── EPIC-26
```

---

# 7. Critical path

The critical path is:

```text
EPIC-00
→ EPIC-01
→ EPIC-03/05/06
→ EPIC-07/08
→ EPIC-10
→ EPIC-12/13
→ EPIC-14
→ EPIC-20
→ EPIC-23/24
→ EPIC-25
→ EPIC-26
```

The highest-risk item is EPIC-10 because allocation correctness affects:

- Booking
- Maintenance
- Reassignment
- Check-in
- Charging
- Session overrun
- Privacy deletion blockers
- Operator operations
- Frontend user journeys

No broad frontend implementation should outrun the allocation and contract foundations.

---

# 8. Parallelizable work

The following can proceed in parallel after EPIC-01:

### Track A — Security

- EPIC-04
- Identity/BFF proof of concept
- Service authentication

### Track B — Persistence

- EPIC-05
- Migration and database-role foundations

### Track C — Contracts

- EPIC-06
- REST/event schema validation

### Track D — Frontend foundation

- EPIC-19
- Design system and routing

### Track E — Infrastructure

- EPIC-23 preparation
- OpenTofu modules
- K3s bootstrap proof

### Track F — Simulator

- EPIC-13 protocol and SQLite queue

These tracks must converge through approved contracts and integration tests.

---

# 9. Recommended first vertical slices

## Slice V1 — Public discovery

Includes:

- Synthetic Station data
- Station projection
- Search API
- Public Angular search
- Map/list fallback
- Greek/English
- Basic observability

Purpose: Validate frontend, BFF, REST, projection and deployment flow.

## Slice V2 — Booking correctness

Includes:

- Account eligibility
- Station/EVSE projection
- Hold creation
- Exact EVSE assignment
- Confirmation
- Cancellation
- Expiration
- Allocation concurrency tests

Purpose: Prove the core business invariant before charging.

## Slice V3 — Simulated charging

Includes:

- Check-in
- Start Authorization
- Simulator enrollment
- Start command
- Transaction-start evidence
- Session state
- Stop command
- Session summary

Purpose: Prove device uncertainty and planned-versus-actual usage.

## Slice V4 — Operator operations

Includes:

- Organization
- Station configuration
- Maintenance
- Fault Incident
- Capacity blocks
- Reassignment

Purpose: Prove operational control over booked infrastructure.

## Slice V5 — Privacy and support

Includes:

- Support case
- Scoped access
- Export
- Deletion
- Tombstone
- Transactional notification

Purpose: Prove cross-service privacy and governance workflows.

---

# 10. Proof-of-concept order

Before full implementation:

1. **POC-01:** Keycloak + BFF login, session and token exchange.
2. **POC-02:** OpenAPI 3.1.2 generation for Angular and Spring.
3. **POC-03:** PostgreSQL 18 allocation constraint and lock protocol.
4. **POC-04:** RabbitMQ outbox/inbox and quorum queue behaviour.
5. **POC-05:** ARM64 multi-architecture image build.
6. **POC-06:** K3s + Flux + CloudNativePG deployment.
7. **POC-07:** Simulator mTLS and WebSocket reconnect.
8. **POC-08:** MapLibre CSP and accessible list fallback.
9. **POC-09:** Backup restoration and privacy-tombstone replay.
10. **POC-10:** End-to-end trace propagation.

A failed proof of concept creates an architecture issue before dependent epics proceed.

---

# 11. Release increments

## Release R0 — Engineering baseline

Contains:

- Repository
- CI
- Local runtime
- Contracts
- Shared technical kernel
- Basic BFF authentication
- Initial deployment

## Release R1 — Discovery demonstration

Contains:

- Public Station discovery
- Search/list/map
- Station details
- Freshness labels
- Basic localization

## Release R2 — Booking MVP

Contains:

- Account eligibility
- Holds
- Confirmation
- Cancellation
- Expiration
- Exact/automatic assignment
- Booking history

## Release R3 — Charging MVP

Contains:

- Check-in
- Start Authorization
- Simulator
- Session start/stop
- Metering
- Session summary
- Uncertainty handling

## Release R4 — Operations MVP

Contains:

- Operator organization
- Infrastructure management
- Maintenance
- Faults
- Reassignment
- Simulator controls
- Analytics projections

## Release R5 — Governance and privacy

Contains:

- Support
- Notifications
- Privacy export
- Deletion
- Retention
- Audit views
- Emergency workflows

## Release R6 — Readiness candidate

Contains:

- Performance
- Security
- Accessibility
- Resilience
- Disaster recovery
- Runbooks
- Final traceability

---

# 12. Deferred scope

The following remain deferred unless explicitly reapproved:

- Payment processing
- Real charger hardware
- OCPP compliance
- Native mobile applications
- SMS/push notifications
- In-app notification centre
- Marketing communications
- International currencies
- Advanced pricing
- Demand forecasting
- Search-engine SSR
- Offline booking/check-in/start/stop
- Kafka
- Redis as a required dependency
- Elasticsearch/OpenSearch
- Service mesh
- Multi-region active-active deployment
- Full modular-monolith implementation
- Attachments in fault/support reports

Deferred work must not enter the critical path.

---

# 13. Epic Definition of Ready

An epic is ready when:

1. Scope and exclusions are documented.
2. Requirements are linked.
3. Domain terms are canonical.
4. Dependencies are identified.
5. API/event/database impacts are known.
6. Security and privacy impacts are assessed.
7. Test categories are defined.
8. Acceptance criteria are measurable.
9. Required design decisions are approved.
10. Test data is available.
11. External provider assumptions are identified.
12. The epic can be split into reviewable vertical slices.

---

# 14. Story Definition of Ready

A story is ready when:

- It has one clear outcome.
- It identifies the owning service/module.
- It links to a requirement.
- It identifies state transitions or data changes.
- It identifies authorization rules.
- It identifies failure behaviour.
- It has acceptance criteria.
- It identifies test level(s).
- It has no unresolved blocker.
- It can be completed within a small iteration.

---

# 15. Definition of Done

A story is done when:

1. Code is implemented.
2. Required migrations are included.
3. Contracts are updated.
4. Authorization is implemented and tested.
5. Audit/outbox/inbox behaviour is included where applicable.
6. Unit and integration tests pass.
7. Relevant frontend/accessibility tests pass.
8. Documentation is updated.
9. Observability is included.
10. Security scanning passes.
11. No prohibited terminology or architecture coupling exists.
12. CI passes.
13. The pull request is reviewed and merged.
14. The feature is deployable through the approved path.

---

# 16. Epic acceptance evidence

Every epic produces:

- Scope statement
- Requirement mapping
- Design notes
- Code
- Migrations
- REST contracts
- Event/command contracts
- Automated tests
- Security evidence
- Observability evidence
- Updated runbook where needed
- Demo scenario
- Known limitations
- Release notes

---

# 17. Risk-first implementation order

Highest-risk areas must be addressed early:

1. PostgreSQL allocation concurrency
2. BFF and Identity Provider integration
3. Service-to-service identity
4. RabbitMQ outbox/inbox reliability
5. Simulator mTLS and uncertainty
6. ARM64 deployment compatibility
7. PostgreSQL failover and backup restoration
8. Accessibility of map/list discovery
9. Privacy deletion coordination
10. Cost of the reference environment

Low-risk areas may follow:

- Additional analytics charts
- Visual refinements
- Nonessential dashboards
- Expanded report exports
- Cosmetic design improvements

---

# 18. Dependency and risk register

| Risk | Affected epics | Mitigation |
|---|---|---|
| OpenAPI generator incompatibility | EPIC-06, 19–22 | Run POC-02 first |
| PostgreSQL constraint performance | EPIC-10 | Run POC-03 and load tests |
| RabbitMQ quorum resource usage | EPIC-13–18, 23 | Run POC-04 and size limits |
| ARM64 library/image incompatibility | EPIC-02, 13, 23 | Multi-arch CI early |
| Keycloak/BFF token exchange failure | EPIC-04 | POC-01 before protected UI |
| Cold DR exceeds 60-minute RTO | EPIC-23–25 | Timed restore early |
| Shared PostgreSQL cluster saturation | All services | Connection budgets and load testing |
| Privacy workflow scope expands | EPIC-18 | Keep participant contract explicit |
| Frontend state complexity grows | EPIC-19–22 | Feature stores; reassess only with evidence |
| Operator workflows delay core booking | EPIC-15 | Deliver after R3 |
| Unstable external providers | EPIC-09, 16, 23 | Provider adapters and test doubles |
| CI becomes too slow | All | Path classification and nightly suites |
| Infrastructure drift | EPIC-23 | Flux and OpenTofu ownership rules |

---

# 19. Milestone review gates

## Gate G0 — Architecture approved

Required:

- ARC-001–014 approved
- Exceptions recorded
- Baseline tag created

## Gate G1 — Engineering runway complete

Required:

- CI
- Local environment
- Contract validation
- Basic security scanning
- Testcontainers

## Gate G2 — Core correctness proven

Required:

- EPIC-10 complete
- ARC-006 race suite passes
- Allocation integrity scan passes
- No blocker defects

## Gate G3 — Charging proven

Required:

- EPIC-14 complete
- Device uncertainty tests pass
- Simulator reconnect works
- Session summaries are reproducible

## Gate G4 — Operational control proven

Required:

- Maintenance/fault/reassignment workflows pass
- Capacity-block race tests pass
- Operator tenancy tests pass

## Gate G5 — Privacy and governance proven

Required:

- Export/deletion tests pass
- Support case scope passes
- Tombstone restoration passes
- Notification secrets are absent

## Gate G6 — Production-like hardening complete

Required:

- NFR evidence
- Security verification
- Accessibility verification
- Recovery drills
- Runbooks
- Cost review

## Gate G7 — Implementation readiness

Required:

- Final traceability
- Release candidate
- No blockers
- Accepted residual risks
- Demonstration and evaluation package

---

# 20. Roadmap sequencing summary

| Sequence | Primary epics | Outcome |
|---:|---|---|
| 1 | EPIC-00–03 | Governance, repository and technical kernel |
| 2 | EPIC-04–06 | Security, persistence and contracts |
| 3 | EPIC-07–09 | Infrastructure, account and discovery |
| 4 | EPIC-10–12 | Booking, allocation and check-in |
| 5 | EPIC-13–14 | Simulator and charging |
| 6 | EPIC-15–18 | Operations, notifications, support and privacy |
| 7 | EPIC-19–22 | Frontend vertical slices |
| 8 | EPIC-23–24 | Deployment and operations |
| 9 | EPIC-25 | Hardening and recovery |
| 10 | EPIC-26 | Final readiness and evaluation |

---

# 21. Implementation metrics

Track:

- Completed epics
- Completed vertical slices
- Requirements with passing evidence
- Invariants with passing evidence
- Open blockers
- Defect escape rate
- Lead time from merge to deployment
- CI duration
- Flaky-test rate
- Allocation race pass rate
- Contract-breaking changes
- Security findings by severity
- Accessibility defects
- SLO performance
- Backup/restore success
- Monthly cloud cost
- Documentation drift

Velocity is not used as the primary quality metric.

---

# 22. Decisions proposed for approval

| ID | Decision |
|---|---|
| ARC-ROAD-01 | Use the epic sequence defined in this document. |
| ARC-ROAD-02 | Make EPIC-10 allocation correctness the principal implementation gate. |
| ARC-ROAD-03 | Require technology proofs of concept before dependent full epics. |
| ARC-ROAD-04 | Use vertical slices for releases. |
| ARC-ROAD-05 | Implement security, privacy and observability within each slice. |
| ARC-ROAD-06 | Keep deferred capabilities outside the implementation critical path. |
| ARC-ROAD-07 | Use one monorepo and one platform release train initially. |
| ARC-ROAD-08 | Require every epic to produce automated verification evidence. |
| ARC-ROAD-09 | Require architecture and implementation gates before release progression. |
| ARC-ROAD-10 | Use the definitions of ready and done in this document. |
| ARC-ROAD-11 | Track reliability, correctness, security and cost metrics in addition to delivery progress. |
| ARC-ROAD-12 | Do not declare implementation-ready until recovery and allocation evidence passes. |

---

# 23. Open questions

| ID | Question | Resolution phase |
|---|---|---|
| ARC-ROAD-OQ-01 | Exact sprint/iteration length | Delivery planning |
| ARC-ROAD-OQ-02 | Final issue-tracking tool | Repository setup |
| ARC-ROAD-OQ-03 | Exact release dates | Project scheduling |
| ARC-ROAD-OQ-04 | Whether frontend and backend work proceed concurrently after R1 | Capacity review |
| ARC-ROAD-OQ-05 | Academic evaluation scenario priorities | Evaluation planning |
| ARC-ROAD-OQ-06 | Final demonstration dataset size | Demo planning |
| ARC-ROAD-OQ-07 | Whether EPIC-17 and EPIC-18 are required for the first public demonstration | Scope review |
| ARC-ROAD-OQ-08 | Final service naming if boundaries change after proof of concept | Architecture review |

---

# 24. Acceptance criteria

This roadmap is approved when:

1. Every architecture decision maps to implementation work.
2. Every functional requirement maps to an epic and release.
3. Every release-critical invariant maps to a gate and test suite.
4. Dependencies are explicit.
5. The critical path is understood.
6. Parallel work is identified without violating ownership.
7. Proofs of concept precede high-risk implementation.
8. Deferred scope is protected from accidental expansion.
9. Every epic has measurable acceptance criteria.
10. Definition of Ready and Done are adopted.
11. Deployment and recovery work are included before final readiness.
12. The roadmap does not claim implementation completion prematurely.

---

# 25. Consequences

## Positive

- Clear implementation order
- Early validation of the highest-risk decisions
- Strong vertical-slice delivery
- Explicit dependency management
- Traceability from architecture to releases
- Security and privacy integrated into delivery
- Recovery and operations included before readiness
- Practical scope control for one developer

## Negative

- Core booking correctness delays broad feature development.
- Infrastructure and testing work begins early.
- The monorepo requires disciplined ownership.
- Full readiness requires substantial evidence, not only working screens.
- Several technically attractive features remain deferred.
- Recovery drills and concurrency testing add significant schedule cost.

These costs are accepted because the project’s highest risk is not feature count; it is delivering a distributed charging-booking platform without violating allocation, security, privacy or recovery invariants.

---

# 26. Next architecture artifact

The next document is:

**AI-Agent Rules, Responsibilities and Review Gates v1.0**

It must define:

- Agent roles
- Allowed and prohibited actions
- Context-loading order
- Document authority
- Change-impact analysis
- Coding and contract rules
- Security restrictions
- Review gates
- Human approval requirements
- Handoff format
- Evidence requirements
- Drift detection
- Autonomous-operation boundaries
