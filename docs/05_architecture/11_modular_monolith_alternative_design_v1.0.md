# Modular-Monolith Alternative Design v1.0

**Document ID:** ARC-017  
**Version:** 1.0  
**Status:** IN_REVIEW  
**Owner:** Architecture Lead  
**Last reviewed:** 2026-07-12  
**Depends on:** ARC-001–016  
**Authoritative for:** Modular-monolith alternative boundaries, ownership, communication, transactions, deployment, testing and extraction strategy

---

## 1. Purpose

This document defines a modular-monolith alternative to the approved microservice architecture.

It:

- Preserves the approved domain model and invariants.
- Maps microservice boundaries to enforceable modules.
- Defines module-owned schemas and internal APIs.
- Identifies permitted local transactions.
- Keeps Device Integration as a separate process.
- Defines deployment and testing.
- Establishes extraction paths to microservices.
- Provides a lower-cost fallback and architecture-comparison baseline.

This document does not select the modular monolith as the primary architecture or authorize its implementation.

---

## 2. Objectives

The alternative must:

1. Preserve feature parity.
2. Prevent double booking with the ARC-006 design.
3. Maintain clear data ownership.
4. Avoid a traditional tightly coupled monolith.
5. Reduce deployment and distributed-systems complexity.
6. Keep module boundaries extractable.
7. Preserve security and tenancy controls.
8. Avoid duplicate domain models.
9. Support one developer effectively.
10. Permit objective comparison with microservices.

---

## 3. Selected topology

```text
Angular Web Client
        |
        v
Edge BFF
        |
        v
Platform Modular Monolith
├── account
├── station-operations
├── booking-session
├── discovery-insights
├── notification
├── governance-support
└── platform-kernel
        |
        +------ PostgreSQL
        |
        +------ RabbitMQ ------ Device Gateway
                                   |
                                   v
                            Charger Simulators
```

External platform components remain:

- Identity Provider
- Edge BFF
- PostgreSQL
- RabbitMQ
- Object storage
- Email provider
- Map provider
- Observability infrastructure
- Charger Simulator

The Device Gateway remains a separate deployable because it owns an external machine trust boundary, WebSocket connections, device credentials, protocol validation and connection-oriented scaling.

---

## 4. Deployables

### Custom deployables

1. `web-client`
2. `edge-bff`
3. `platform-monolith`
4. `device-gateway`
5. `charger-simulator`

### Supporting components

- Keycloak
- PostgreSQL
- RabbitMQ
- Object storage
- Email provider
- Observability stack

The platform monolith must not absorb the Identity Provider, BFF or simulator runtime.

---

## 5. Module map

| Module | Microservice equivalent |
|---|---|
| `account` | Account Service |
| `station-operations` | Station Operations Service |
| `booking-session` | Booking and Session Service |
| `discovery-insights` | Discovery and Insights Service |
| `notification` | Notification Service |
| `governance-support` | Platform Governance and Support Service |
| `platform-kernel` | Approved technical primitives only |
| External `device-gateway` | Device Integration Service |

Module names, responsibilities and exclusions mirror ARC-001.

---

## 6. Module structure

Recommended Java package structure:

```text
gr.evbooking.platform
├── account
│   ├── api
│   ├── application
│   ├── domain
│   ├── infrastructure
│   └── internal
├── stationoperations
├── bookingsession
├── discoveryinsights
├── notification
├── governancesupport
└── platformkernel
```

Each business module contains:

- `api`: callable public module interface
- `application`: use cases and orchestration
- `domain`: aggregates, policies and invariants
- `infrastructure`: persistence and external adapters
- `internal`: implementation inaccessible to other modules

Other modules may depend only on the target module’s `api` package and approved integration events.

---

## 7. Module dependency rules

Permitted dependency direction:

```text
account --------------\
station-operations ----\
booking-session --------> platform-kernel
discovery-insights -----/
notification ----------/
governance-support ----/
```

Business modules must not depend directly on another module’s:

- Domain package
- Persistence package
- Repository
- Database tables
- Internal implementation
- Mutable entities

Cross-module dependencies use:

1. Public module interfaces
2. Immutable request/response records
3. Domain/integration events
4. Workflow references

Circular compile-time dependencies are prohibited.

---

## 8. Platform kernel

The `platform-kernel` may contain only stable technical concerns:

- Correlation and causation identifiers
- Problem Details
- Database-time abstraction
- Idempotency infrastructure
- Outbox/inbox infrastructure
- Audit infrastructure
- Secure logging
- Event-envelope support
- Workflow primitives
- Technical test utilities

It must not contain:

- Booking rules
- Station rules
- Shared business entities
- Organization authorization
- Lifecycle transition rules
- Tariff logic
- Privacy policy
- Device protocol rules

A shared technical kernel must not become a hidden shared domain.

---

## 9. Data ownership

Use one physical PostgreSQL cluster and initially one application database with module-owned schemas:

```text
account.*
station_operations.*
booking_session.*
discovery_insights.*
notification.*
governance_support.*
platform_kernel.*
```

The Device Gateway owns a separate logical database:

```text
device_integration_db
```

Keycloak owns its own logical database.

### Rules

1. Every table belongs to exactly one module.
2. Only the owning module’s repositories may access its schema.
3. Cross-schema SQL is prohibited.
4. Cross-schema foreign keys are prohibited.
5. Cross-module ORM relationships are prohibited.
6. Reporting uses projections, not cross-schema joins.
7. Migration ownership remains module-specific.
8. Database credentials alone do not enforce module isolation inside the monolith; architecture tests and code review are mandatory.

Separate schemas preserve extraction seams but are not equivalent to service-level security isolation.

---

## 10. Migration organization

```text
platform-monolith/
└── src/main/resources/db/migration/
    ├── account/
    ├── station-operations/
    ├── booking-session/
    ├── discovery-insights/
    ├── notification/
    ├── governance-support/
    └── platform-kernel/
```

Each module has:

- Independent migration numbering or namespace
- Named constraints
- Declared schema ownership
- Fresh-install tests
- Upgrade tests
- Expand–migrate–contract rules

Applied migrations remain immutable.

A module cannot modify another module’s tables through migration.

---

## 11. Internal communication

### 11.1 Synchronous module calls

Use synchronous calls when:

- The caller needs an immediate result.
- Both capabilities execute within the monolith.
- The operation does not create prohibited ownership coupling.
- Failure semantics are explicit.

Examples:

- Governance requests an authoritative Booking action.
- Notification resolves a recipient.
- Station Operations requests a non-binding booking-impact preview.

Calls use public module interfaces rather than HTTP.

### 11.2 Internal events

Use internal events for:

- Projection updates
- Notification triggers
- Audit projection
- Analytics
- Eventually consistent state
- Non-critical workflow continuation

Events must remain versioned and serializable so they can later become broker integration events.

### 11.3 External messages

RabbitMQ remains mandatory for communication with the separate Device Gateway and may be used for:

- Device commands
- Device-normalized evidence
- Durable cross-process reconciliation
- External notification/provider workflows where justified

---

## 12. Transaction rules

### 12.1 Default rule

A module owns its transaction and writes only its schema.

### 12.2 Permitted shared local transaction

A cross-module local transaction is permitted only when all conditions hold:

1. The operation is explicitly approved in this document or an ADR.
2. It preserves one business consistency boundary.
3. Each module is invoked through its public interface.
4. No module directly accesses another module’s tables.
5. The transaction has no remote call.
6. Audit and event records commit atomically.
7. The coupling is documented as an extraction cost.

### 12.3 Approved local consistency boundary

Booking, Allocation, Check-In, Start Authorization, Charging Session, Metering and Operational Occupation remain one `booking-session` module and one transaction boundary.

No cross-module transaction is needed for allocation.

### 12.4 Cross-module transaction posture

The initial design does **not** approve general cross-module transactions.

Account restrictions, maintenance blocks, privacy workflows and governance actions retain coordinated-workflow semantics even though modules share a process.

This preserves behaviour compatible with later service extraction.

---

## 13. Internal event delivery

Business-state transaction:

1. Apply authoritative state.
2. Record local audit evidence.
3. Insert event into a durable event/outbox table.
4. Commit.
5. Internal dispatcher delivers the event to registered module consumers.
6. Consumer effects commit with inbox/deduplication evidence.
7. External events are published to RabbitMQ where required.

This intentionally preserves:

- At-least-once processing
- Idempotent consumers
- Replay
- Projection rebuilding
- Workflow recovery
- Future microservice extraction

In-memory-only event delivery is insufficient for release-critical workflows.

---

## 14. Booking and allocation module

The `booking-session` module owns:

- Booking
- Booking Hold
- EVSE Allocation
- Capacity Block
- Driver Schedule Claim
- Check-In
- Start Authorization
- Charging Session
- Meter values
- Session Summary
- Operational Occupation
- Reconciliation
- Booking history

ARC-006 applies without semantic change.

Required PostgreSQL controls remain:

- EVSE guard rows
- Driver guard rows
- GiST exclusion constraints
- Half-open ranges
- Database time
- Explicit lock order
- Idempotency
- Real PostgreSQL race tests

The modular monolith must not weaken allocation correctness because fewer deployables exist.

---

## 15. Station Operations module

Owns:

- Operator applications
- Organizations
- Memberships
- Stations
- EVSEs
- Connectors
- Tariffs
- Booking policies
- Maintenance
- Fault Reports
- Fault Incidents
- Status Overrides
- Simulator assignments

Maintenance capacity remains enforced through `booking-session`.

Even in one process:

1. Station Operations creates a workflow.
2. Booking installs the block.
3. Booking records the result.
4. Station Operations completes scheduling.

This preserves workflow and extraction semantics.

---

## 16. Account module

Owns:

- Application account
- Driver profile
- Vehicles
- Compatibility
- Preferences
- Account state
- Privacy coordination
- Privacy tombstones

Credentials, sessions, MFA and identity tokens remain owned by Keycloak.

Booking eligibility may be queried through the Account module interface and replicated into Booking enforcement data.

Booking creation should continue using Booking-local eligibility data so that later service extraction does not redesign allocation.

---

## 17. Discovery and Insights module

Owns non-authoritative projections:

- Public station search
- Advisory availability
- Geographic views
- Operator analytics
- Platform analytics
- Projection checkpoints
- Report exports

It consumes durable events from:

- Account, where necessary
- Station Operations
- Booking and Session
- Device Gateway

Discovery data must not be queried by Booking to make authoritative decisions.

---

## 18. Notification module

Owns:

- Notification records
- Template versions
- Delivery attempts
- Reminder schedules
- Suppression
- Provider outcomes

Notification failures cannot roll back source-module business transactions.

Ordinary notification triggers remain durable events.

The module does not send email within a Booking or Station transaction.

---

## 19. Governance and Support module

Owns:

- Support Cases
- Temporary access grants
- Administrative investigations
- Emergency interventions
- Break-glass workflows
- Central audit projection

It requests changes through public module interfaces.

It cannot directly update foreign module tables despite sharing the same process and database.

---

## 20. Device Gateway

The Device Gateway remains separate because it has:

- Machine identities
- mTLS
- Long-lived WebSockets
- Connection ownership
- Device event sequencing
- Command dispatch
- Offline replay
- Protocol validation
- Simulator-specific scaling
- An untrusted external boundary

Communication with the monolith uses RabbitMQ contracts from ARC-004.

The Device Gateway cannot access the monolith database.

---

## 21. REST API

The external API remains compatible with ARC-003.

The BFF calls the platform monolith rather than seven separate business services.

Options:

- One API hostname
- One monolith audience
- Route ownership by module
- Stable operation IDs
- Same versioning and Problem Details semantics

Internal service REST operations become module interfaces when both participants are in the monolith.

They should remain represented conceptually in documentation to preserve extraction mapping.

---

## 22. Security model

The modular monolith retains:

- BFF browser security
- Keycloak
- MFA
- Resource-level authorization
- Organization ownership
- Case-scoped support grants
- Step-up authentication
- Audit evidence
- Device mTLS
- Secret minimization

Security consequence:

- A compromise of the monolith process potentially exposes more business capabilities than compromise of one microservice.

Mitigations:

- Strict module authorization
- Minimal runtime database grants where practical
- No cloud or device private keys
- Process hardening
- Egress restrictions
- Internal module-boundary tests
- Detailed audit
- Stronger review of shared runtime changes

---

## 23. Deployment model

### Minimum production-like topology

- Two or three platform-monolith replicas
- Two BFF replicas
- Two Device Gateway replicas
- PostgreSQL
- RabbitMQ
- Keycloak
- Supporting infrastructure

The monolith is stateless except for:

- Database data
- Server-side sessions in dedicated storage
- Durable messaging/workflow records

### Scaling

The monolith scales as one unit.

Advantages:

- Simpler deployment
- Fewer Pods
- Fewer connection pools
- Fewer images and pipelines

Disadvantages:

- Discovery load may scale Booking code unnecessarily.
- One memory leak affects all modules.
- One rollout redeploys all modules.
- Module-specific resource scaling is unavailable.

---

## 24. Expected deployment reduction

Compared with seven business services, the alternative reduces:

- Business-service Deployments from seven to one
- Service-specific database connection pools
- Internal service tokens
- Internal REST calls
- Internal queues
- NetworkPolicies
- Migration Jobs
- Dashboards and alerts
- Image-build pipelines

It does not eliminate:

- BFF
- Device Gateway
- Identity Provider
- PostgreSQL
- RabbitMQ
- Backups
- Observability
- Security controls
- Contract governance

---

## 25. Failure isolation

| Failure | Impact |
|---|---|
| Account module defect | May destabilize entire monolith |
| Discovery query overload | May affect Booking without resource controls |
| Notification defect | May affect process unless isolated |
| Monolith rollout failure | All business modules affected |
| Device Gateway failure | Device workflows affected; monolith remains available |
| RabbitMQ failure | Device and async workflows delayed |
| PostgreSQL failure | All authoritative modules affected |
| Keycloak failure | New authentication affected |

Required protections:

- Bounded executors
- Separate connection-pool budgets where feasible
- Query timeouts
- Bulkheads for provider adapters
- Rate limits
- Feature-level health indicators
- Graceful degradation
- Notification and analytics worker isolation

---

## 26. Testing strategy

All ARC-013 requirements remain applicable.

Additional module tests:

1. Module dependency-cycle detection.
2. Forbidden-package access.
3. Cross-schema SQL detection.
4. Public-interface-only access.
5. Module event compatibility.
6. Module-specific application-context tests.
7. Module isolation tests.
8. Full-monolith startup tests.
9. Module migration ownership tests.
10. Extraction-readiness tests.

Allocation, security, privacy, accessibility and recovery tests are not reduced.

---

## 27. Architecture enforcement

CI must fail on:

- Import of another module’s `internal`, `domain` or `infrastructure` package
- Repository access across module boundaries
- SQL referencing another module schema
- Circular module dependency
- Shared mutable business entity
- Direct notification/provider calls inside foreign module transactions
- Discovery data used as Booking authority
- Governance direct table modification
- Device Gateway database access

Architecture tests should produce a module dependency diagram as evidence.

---

## 28. Build and repository organization

The monolith alternative would use:

```text
apps/
├── web/
├── bff/
└── platform-monolith/
    ├── account/
    ├── station-operations/
    ├── booking-session/
    ├── discovery-insights/
    ├── notification/
    ├── governance-support/
    └── platform-kernel/

services/
└── device-gateway/
```

This structure is conceptual until the architecture comparison selects an implementation target.

Do not create this duplicate implementation structure while microservices remain selected.

---

## 29. Extraction strategy

A module may be extracted when:

- It has explicit public interfaces.
- It owns its schema.
- It consumes and produces serializable events.
- It has no foreign-table access.
- Its transaction boundaries are understood.
- Its authorization inputs are portable.
- Operational benefit exceeds distributed-system cost.

### Extraction sequence

1. Identify candidate module.
2. Freeze its public interface.
3. Introduce remote-compatible DTOs.
4. Route calls through an adapter.
5. Move its tables to a new database.
6. Synchronize changes during migration.
7. Replace local calls with REST or messages.
8. Move background workers.
9. Validate contract and failure behaviour.
10. Remove local implementation.

---

## 30. Preferred extraction candidates

### Lowest risk

1. Notification
2. Discovery and Insights
3. Governance and Support

Reasons:

- Event-driven or projection-oriented
- Few strong consistency requirements
- Clear failure isolation value

### Medium risk

4. Account
5. Station Operations

Requires:

- Authorization replication
- Privacy workflows
- Maintenance enforcement coordination

### Already separate

6. Device Gateway

### Highest risk

7. Booking and Session

Booking, Allocation and Charging should remain together unless future scaling and organizational evidence justify separation.

---

## 31. Migration from microservices to modular monolith

If the microservice implementation proves operationally excessive:

1. Preserve existing contracts.
2. Move service code into modules without merging domains.
3. Keep service databases separate initially.
4. Replace remote clients with local adapters.
5. Preserve outbox/inbox behaviour.
6. Consolidate deployments incrementally.
7. Consolidate physical database infrastructure only after ownership tests pass.
8. Retain Device Gateway separately.
9. Compare SLO and cost changes.
10. Record the architecture decision.

A “big bang” rewrite is prohibited.

---

## 32. Feature parity

The alternative must support the same:

- Actors and roles
- Booking lifecycle
- Allocation invariants
- Charging lifecycle
- Simulator semantics
- Maintenance/fault workflows
- Privacy workflows
- Notifications
- Audit
- Accessibility
- Localization
- Security controls
- Recovery objectives
- REST contracts
- Device message contracts

Architecture comparison must not evaluate a feature-reduced monolith against a full microservice system.

---

## 33. Operational comparison summary

| Concern | Modular monolith | Microservices |
|---|---|---|
| Deployable business units | 1 | 7 |
| Device process | Separate | Separate |
| Business databases | Shared physical, schema-owned | Logically separate |
| Internal calls | Local interfaces | REST/messages |
| Distributed workflows | Fewer, but preserved where valuable | More |
| Independent scaling | Limited | Strong |
| Failure isolation | Lower | Higher |
| Deployment complexity | Lower | Higher |
| Local development | Easier | Harder |
| Boundary enforcement | Code/tests | Process/database/network |
| Extraction flexibility | Designed seams | Already extracted |
| Operational cost | Lower | Higher |

---

## 34. Suitability

The modular monolith is preferred when:

- One development team owns the whole platform.
- Workload remains moderate.
- Independent scaling is not demonstrated.
- Cloud budget is strongly constrained.
- Deployment simplicity outweighs isolation.
- Faster feature delivery is required.
- Service boundaries remain uncertain.

Microservices are preferred when:

- Independent deployment is valuable.
- Device and discovery workloads scale differently.
- Failure isolation is a priority.
- Teams own separate capabilities.
- Operational maturity exists.
- The project explicitly evaluates distributed systems.

---

## 35. Risks

| Risk | Mitigation |
|---|---|
| Modules degrade into layers | Package and dependency enforcement |
| Cross-schema shortcuts | SQL scanning and code review |
| One deployment affects all features | Strong rollout and smoke testing |
| Discovery overload affects Booking | Resource isolation and query limits |
| Shared database creates coupling | Schema ownership and no cross-schema SQL |
| Internal events become in-memory only | Durable event/outbox processing |
| Easier transactions create hidden coupling | Transaction approval rules |
| Security blast radius increases | Runtime hardening and authorization |
| Future extraction becomes difficult | Serializable contracts and module APIs |
| Duplicate architecture documentation drifts | Shared authoritative domain/contracts |

---

## 36. Decisions proposed for approval

| ID | Decision |
|---|---|
| ARC-MONO-01 | Maintain a modular-monolith design as an approved alternative, not a second implementation commitment. |
| ARC-MONO-02 | Map business modules directly to the approved microservice boundaries. |
| ARC-MONO-03 | Use one platform-monolith deployable for six business modules. |
| ARC-MONO-04 | Keep Device Integration as a separate Device Gateway deployable. |
| ARC-MONO-05 | Keep the BFF and Identity Provider outside the monolith. |
| ARC-MONO-06 | Use module-owned PostgreSQL schemas. |
| ARC-MONO-07 | Prohibit cross-schema SQL and cross-module persistence access. |
| ARC-MONO-08 | Permit inter-module calls only through public module interfaces. |
| ARC-MONO-09 | Preserve durable events, outbox/inbox and workflow state. |
| ARC-MONO-10 | Do not use general cross-module database transactions. |
| ARC-MONO-11 | Preserve ARC-006 unchanged inside the Booking and Session module. |
| ARC-MONO-12 | Preserve REST and device-message semantic compatibility. |
| ARC-MONO-13 | Enforce boundaries through package rules, architecture tests and SQL checks. |
| ARC-MONO-14 | Scale the platform monolith as one unit initially. |
| ARC-MONO-15 | Prefer Notification and Discovery as first extraction candidates. |
| ARC-MONO-16 | Require feature, security and recovery parity in architecture comparison. |
| ARC-MONO-17 | Prohibit simultaneous full implementation of both architectures. |
| ARC-MONO-18 | Select one implementation architecture during the final comparison gate. |

---

## 37. Open questions

| ID | Question | Resolution phase |
|---|---|---|
| ARC-MONO-OQ-01 | Whether the BFF may share a runtime image with static frontend assets | Architecture comparison |
| ARC-MONO-OQ-02 | Whether internal durable events require RabbitMQ or only database-backed dispatch | Comparison proof of concept |
| ARC-MONO-OQ-03 | Whether one runtime role can adequately enforce schema ownership | Security comparison |
| ARC-MONO-OQ-04 | Whether Notification workers need process-level isolation | Resilience evaluation |
| ARC-MONO-OQ-05 | Whether Discovery workload threatens Booking latency | Performance evaluation |
| ARC-MONO-OQ-06 | Exact module-boundary enforcement tooling | Implementation proof of concept |
| ARC-MONO-OQ-07 | Whether one Flyway history or one history per module is preferable | Data proof of concept |
| ARC-MONO-OQ-08 | Whether the monolith can meet the same recovery objectives at materially lower cost | Architecture comparison |
| ARC-MONO-OQ-09 | Which architecture becomes the final implementation target | ARC-018 |
| ARC-MONO-OQ-10 | Whether a small monolith proof of concept should be built for objective comparison | ARC-018 |

---

## 38. Acceptance criteria

This alternative is approved when:

1. Every microservice capability maps to one module or explicit external process.
2. Every authoritative entity has one module owner.
3. Booking allocation remains one strong transaction boundary.
4. Device Integration remains isolated from business persistence.
5. Cross-schema SQL is prohibited.
6. Internal APIs are explicit.
7. Durable event and workflow semantics are preserved.
8. Security roles and resource authorization remain unchanged.
9. Feature scope is equivalent to the microservice design.
10. Testing and recovery requirements are not weakened.
11. Extraction paths are documented.
12. Operational advantages and disadvantages are explicit.
13. No duplicate implementation is authorized.
14. The design can be evaluated objectively against microservices.

---

## 39. Consequences

### Positive

- Lower deployment complexity
- Fewer runtime components
- Easier local development
- Reduced internal network communication
- Lower likely infrastructure cost
- Simpler tracing and debugging
- Clear fallback if microservices prove excessive
- Preserved extraction seams

### Negative

- Larger security and failure blast radius
- No independent scaling for business modules
- One rollout affects all modules
- Boundary enforcement relies more heavily on discipline and tests
- Shared database infrastructure increases coupling risk
- Distributed-system evaluation is reduced
- A resource-heavy module may affect unrelated capabilities

These trade-offs are accepted for evaluation as an alternative, not yet as the implementation choice.

---

## 40. Current recommendation

Retain the balanced microservice architecture as the current primary candidate.

Use this modular-monolith design as:

- A required architecture alternative
- A cost and complexity baseline
- A fallback
- A boundary-validation mechanism
- A potential implementation choice only if ARC-018 demonstrates a materially better project outcome

Implementation remains prohibited until the final architecture comparison and implementation-readiness review are complete.

---

## 41. Next architecture artifact

The next document is:

**Architecture Comparison and Evaluation Plan v1.0**

It must compare:

- Microservices
- Modular monolith
- Cost
- Complexity
- Performance
- Reliability
- Security
- Scalability
- Developer productivity
- Deployment effort
- Recovery
- Boundary quality
- Academic evaluation value
- Proof-of-concept evidence
- Final selection criteria
