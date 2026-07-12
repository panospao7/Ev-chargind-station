# Complete Testing and Quality Assurance Strategy v1.0

**Document ID:** ARC-013  
**Title:** Complete Testing and Quality Assurance Strategy  
**Version:** 1.0  
**Status:** IN_REVIEW  
**Owner:** QA / Test Architect  
**Last reviewed:** 2026-07-12  
**Depends on:** ARC-001–012, REQ-001, REQ-002, DOM-002  
**Authoritative for:** Test levels, quality gates, verification evidence, test data, resilience testing and release acceptance

---

## 1. Purpose

This strategy defines how the platform will verify:

- Functional requirements
- Lifecycle transitions
- Release-critical invariants
- REST, event and command contracts
- Security and privacy controls
- Frontend behaviour and accessibility
- Database constraints and migrations
- Distributed consistency
- Device simulation and failure handling
- Performance, resilience and recovery
- Deployment and operational readiness

Testing evidence must be traceable to stable requirement, decision and invariant IDs.

---

## 2. Quality principles

1. Test business invariants before implementation convenience.
2. Use real PostgreSQL for allocation and migration tests.
3. Use real RabbitMQ for messaging reliability tests.
4. Test failures, duplication, delay and reordering deliberately.
5. Test authorization at the authoritative owner.
6. Treat projections as non-authoritative in test expectations.
7. Never use mocks to prove distributed correctness.
8. Keep tests deterministic and reproducible.
9. Prefer isolated test data and disposable environments.
10. Store evidence as build artifacts.
11. A passing test suite does not replace architecture review.
12. No requirement is complete without verification evidence.

---

# 3. Test pyramid

| Level | Purpose | Target |
|---|---|---|
| Static analysis | Detect defects before execution | Every change |
| Unit | Verify isolated logic | Broad coverage |
| Component | Verify component/module behaviour | All critical modules |
| Integration | Verify real database, broker and provider boundaries | All service adapters |
| Contract | Verify REST, event and command compatibility | Every contract |
| End-to-end | Verify complete user journeys | Critical journeys |
| Concurrency | Verify races and invariants | Booking/session core |
| Resilience | Verify failure and recovery | Critical dependencies |
| Performance | Verify NFRs and capacity | Reference and double load |
| Security | Verify threats and controls | Release and regression |
| Accessibility | Verify WCAG target | Every major screen |
| Disaster recovery | Verify restoration objectives | Scheduled drills |

The test pyramid must remain weighted toward fast lower-level tests, while correctness-critical scenarios receive realistic integration coverage.

---

# 4. Verification ownership

| Area | Primary owner |
|---|---|
| Account and profile | Account Service |
| Infrastructure and operator workflows | Station Operations |
| Booking and allocation | Booking and Session Service |
| Device protocol | Device Integration |
| Search and analytics | Discovery and Insights |
| Email delivery | Notification Service |
| Support and governance | Governance and Support |
| Browser UX | Frontend |
| Cross-service workflows | QA with service owners |
| Security controls | Security Architect / QA |
| Backup and recovery | Cloud / Operations |
| Requirements evidence | QA / Product Owner |

Every service owns its tests, fixtures and test data builders.

---

# 5. Static quality checks

Every pull request runs:

- Java compilation
- TypeScript compilation
- Formatting verification
- Linting
- OpenAPI validation
- AsyncAPI validation
- JSON Schema validation
- Dependency vulnerability scan
- Secret scan
- Container scan
- Infrastructure manifest validation
- Architecture-rule tests
- Documentation link validation
- License-policy checks
- Unit and component tests

Required architecture checks include:

- No cross-service persistence imports
- No forbidden database dependencies
- No direct foreign-service database access
- No public exposure of internal APIs
- No browser bearer-token storage
- No unapproved shared business module
- No runtime migration execution
- No service using another service’s migration directory

---

# 6. Unit testing

## 6.1 Backend

Unit-test:

- State-transition guards
- Policy calculations
- Interval construction
- Tariff calculations
- Compatibility rules
- Reason-code mapping
- Idempotency decisions
- Retry classification
- Version handling
- Privacy redaction
- Authorization predicates
- Event and command serialization

Unit tests must not claim to prove database exclusion constraints or distributed delivery.

## 6.2 Frontend

Unit-test:

- Feature stores
- Computed Signals
- Problem-code mapping
- Form validators
- Date/time formatting
- Route-context utilities
- Permission-aware presentation
- Workflow status mapping
- Localization helpers

---

# 7. Component and module testing

## Backend components

Test:

- Controllers and validation
- Application services
- Repository queries
- Transaction boundaries
- Outbox creation
- Inbox processing
- Authorization filters
- Error mapping
- Provider adapters

## Frontend components

Test:

- Accessible names and roles
- Form errors
- Loading/error/empty states
- Status badges
- Workflow progress
- Map/list synchronization
- Dialog focus management
- Responsive navigation
- Server-derived allowed actions
- Unknown enum fallback

Components must be tested with rendered DOM behaviour, not only class methods.

---

# 8. Database integration testing

All database tests use the selected PostgreSQL major version through Testcontainers or an equivalent disposable environment.

Required coverage:

- Fresh schema migration
- Upgrade migration
- Migration checksum validation
- Required extensions
- Exclusion constraints
- Partial unique indexes
- Foreign keys within service ownership
- Check constraints
- Range boundaries
- `If-Match` version conflicts
- Idempotency persistence
- Outbox/inbox atomicity
- Retention jobs
- Privacy tombstones
- Backup-compatible schema

H2 or another substitute database is not valid for allocation correctness tests.

---

# 9. Allocation and concurrency testing

The following scenarios are release-blocking:

1. Concurrent exact-EVSE Holds.
2. Concurrent automatic assignment.
3. Hold confirmation versus expiration.
4. Hold confirmation versus cancellation.
5. Booking creation versus maintenance block.
6. Rescheduling versus cancellation.
7. Rescheduling by two concurrent requests.
8. Reassignment versus check-in.
9. Check-in/start versus no-show.
10. Start versus cancellation.
11. Session overrun versus new allocation.
12. Stop completion versus new allocation.
13. Occupation uncertainty versus future booking.
14. Driver overlapping-booking race.
15. Deadlock caused by multi-EVSE maintenance.
16. Duplicate idempotency requests.
17. Exclusion-constraint violation mapping.
18. Database failure before and after commit.

Expected assertions:

- No overlapping confirmed planned claims.
- Exactly one conflicting operation wins.
- Failed rescheduling preserves the original.
- Expired Holds do not block.
- Uncertain occupation remains blocking.
- Duplicate requests have one business effect.
- Deadlocks are retried safely.
- Lock contention is not reported as unavailability.

Each critical race runs repeatedly, with a target of at least 1,000 iterations in the dedicated concurrency suite.

---

# 10. Lifecycle testing

For every lifecycle:

1. Test every permitted transition.
2. Test every prohibited transition.
3. Test terminal-state immutability.
4. Test authorization for each actor.
5. Test duplicate transition requests.
6. Test deadline boundaries.
7. Test stale-version updates.
8. Test audit creation.
9. Test outbox creation.
10. Test cross-lifecycle side effects.

Required lifecycles include:

- Booking
- Start Authorization
- Charging Session
- Account
- Operator Application
- Organization
- Station
- EVSE
- Maintenance
- Fault Incident
- Fault Report
- Status Override
- Machine Identity
- Device Connection
- Device Command
- Notification
- Support Case
- Privacy Request
- Privacy Export
- Account Deletion
- Invitation
- Ownership Transfer

Lifecycle tests use the canonical state names and transitions from DOM-002.

---

# 11. REST contract testing

Every REST operation requires:

- OpenAPI schema validation
- Request validation
- Success-response validation
- Problem Details validation
- Authentication test
- Authorization test
- Data-minimization test
- Idempotency test where required
- `ETag`/`If-Match` test where required
- Pagination and filtering test
- Rate-limit classification
- Backward-compatibility test

Critical cases:

- Duplicate booking Hold
- Changed payload with reused idempotency key
- Expired Hold confirmation
- Stale `If-Match`
- Cross-driver resource access
- Cross-organization access
- Missing support grant
- Invalid workflow reference
- `202 Accepted` with status polling
- Device timeout represented as uncertainty

Generated clients must compile against the approved OpenAPI document.

---

# 12. Event and command contract testing

Every message requires:

- JSON Schema validation
- Envelope validation
- Producer serialization test
- Consumer compatibility test
- Unknown optional-field tolerance
- Unsupported-major-version quarantine
- Duplicate delivery test
- Out-of-order test where applicable
- Correlation/causation propagation
- Data-classification validation
- Secret and PII leakage test

Required reliability scenarios:

- Outbox commit followed by broker outage
- Lost publisher confirmation
- Consumer crash before commit
- Consumer crash after commit
- Duplicate event
- Duplicate command
- Command ID reused with changed data
- Version gap
- Quarantine replay
- Unroutable mandatory message
- Retry exhaustion
- Privacy participant replay
- Capacity-block acknowledgement loss

---

# 13. Device and simulator testing

## Protocol

Test:

- Enrollment
- Certificate validation
- Revocation
- Wrong-station messages
- Wrong-EVSE messages
- Boot registration
- Heartbeats
- Status updates
- Start/stop commands
- Transaction events
- Meter values
- Sequence numbers
- Duplicate events
- Out-of-order events
- Invalid payloads
- Oversized payloads
- Unsupported protocol versions

## Failure injection

Deterministically simulate:

- Disconnects
- Delayed messages
- Dropped messages
- Duplicate messages
- Reordered messages
- Command rejection
- Command timeout
- Offline queueing
- Restart during an active session
- Restart with queued events
- Session overrun
- Stop uncertainty

Assertions:

- Command acceptance does not start charging.
- Only transaction-start evidence changes Session to `CHARGING`.
- Timeout remains uncertain.
- Meter duplicates do not inflate energy.
- Device cannot create or modify infrastructure.

---

# 14. Distributed workflow testing

Test workflows for:

- Account suspension
- Organization suspension
- Station closure
- Maintenance scheduling
- Maintenance activation
- Booking reassignment
- Privacy export
- Account deletion
- Emergency intervention
- Break-glass access
- Notification dispatch

For each workflow test:

- Normal completion
- Duplicate command
- Participant timeout
- Participant rejection
- Broker outage
- Coordinator restart
- Partial completion
- Retry
- Reconciliation
- Manual review
- Final audit evidence

A workflow must not be reported complete while mandatory participants remain unresolved.

---

# 15. Security testing

## Authentication

- PKCE flow
- State and nonce validation
- Redirect allowlisting
- Session fixation
- Session revocation
- MFA enforcement
- Recent-authentication checks
- Recovery enumeration
- Token expiry
- Wrong issuer/audience
- Algorithm rejection

## Authorization

- Driver object ownership
- Operator organization isolation
- Role hierarchy
- Removed membership
- Support-case scope
- Expired access grant
- Field-level masking
- Break-glass expiry
- Delegated assertion validation
- Service-to-service scope

## Application security

- SQL injection
- XSS
- CSRF
- SSRF
- Open redirect
- Path traversal
- Mass assignment
- Request-size abuse
- Rate-limit bypass
- Sensitive-data leakage
- Insecure direct object reference
- Log injection

## Infrastructure

- NetworkPolicy validation
- Non-root containers
- Read-only filesystem where required
- Image signature verification
- Secret scanning
- Kubernetes RBAC
- Broker ACLs
- Database role separation
- Public-port scanning
- Certificate validation

Security testing maps to the approved ASVS baseline and threat IDs in ARC-007.

---

# 16. Privacy testing

Required tests:

- Data inventory completeness
- Export completeness
- Export excludes unrelated data
- Export excludes secrets
- Authenticated download
- Expired download
- Deletion blocker detection
- Deletion idempotency
- Projection cleanup
- Pseudonymization correctness
- Anonymization aggregation
- Retention eligibility
- Legal/security hold exclusion
- Correction metadata
- Processing restriction
- Backup tombstone replay
- Privacy workflow participant failure
- No privacy content in logs, events or telemetry

Privacy tests use synthetic identities and must not expose real personal data.

---

# 17. Frontend and UX testing

## Functional journeys

- Public search
- Station details
- Driver registration/login
- Vehicle management
- Booking Hold
- Booking confirmation
- Hold expiry
- Rescheduling
- Cancellation
- Check-in
- Start uncertainty
- Charging
- Stop uncertainty
- Session summary
- Fault report
- Operator application
- Infrastructure management
- Maintenance
- Fault Incident
- Simulator control
- Analytics
- Support case
- Privacy export
- Account deletion
- Emergency workflow

## Browser coverage

Minimum supported browser families:

- Current Chromium
- Current Firefox
- Current WebKit/Safari-equivalent
- Mobile Chromium
- Mobile Safari-equivalent

Exact browser versions are pinned by the E2E environment.

---

# 18. Accessibility testing

Each major screen requires:

- Automated axe checks
- Keyboard-only test
- Focus-order test
- Screen-reader smoke test
- 200% zoom
- 400% zoom/reflow
- Contrast verification
- Reduced-motion verification
- Error recovery test
- Accessible table/list alternative
- Map/list equivalence test

Accessibility defects affecting task completion are release blockers.

Automated accessibility checks are necessary but insufficient.

---

# 19. Performance testing

## 19.1 Reference workload

- 500 concurrent users
- 2,000 simulated EVSEs
- 50 API requests/second
- 100 device events/second

## 19.2 Double-load workload

- 1,000 concurrent users
- 4,000 simulated EVSEs
- 100 API requests/second
- 200 device events/second

## 19.3 Scenarios

- Public station search
- Availability projection reads
- Concurrent Holds
- Booking confirmation
- Rescheduling
- Cancellation
- Check-in
- Session telemetry
- Device event ingestion
- Notification queueing
- Projection rebuild
- Privacy export generation

Measure:

- p50/p95/p99 latency
- Error rate
- Throughput
- Database locks
- Connection pools
- Broker queue age
- CPU/memory
- Projection lag
- Event processing time
- Cost impact

The double-booking benchmark must report successful conflicting operations and prove that only one winner is committed.

---

# 20. Endurance and soak testing

Run a minimum eight-hour soak test with:

- Representative API traffic
- Device heartbeats
- Meter events
- Notification processing
- Projection updates
- Scheduled jobs
- Log/metric collection

Check:

- Memory leaks
- Queue growth
- Database bloat
- Connection leaks
- Duplicate effects
- Event lag
- Disk growth
- CPU throttling
- Certificate/session expiry behaviour

A 24-hour soak is required before implementation-readiness approval if the reference environment remains stable.

---

# 21. Resilience testing

Inject:

- PostgreSQL primary failure
- PostgreSQL standby failure
- RabbitMQ node failure
- Complete broker loss
- Keycloak Pod failure
- BFF Pod failure
- Device Integration Pod failure
- Search outage
- Notification provider outage
- Object-storage outage
- DNS delay
- Certificate renewal failure
- Network latency
- Packet loss
- Broker redelivery
- Consumer crash
- Node loss
- Flux reconciliation delay

Assertions:

- Core committed data remains durable.
- Allocation invariants remain true.
- Uncertain device outcomes remain explicit.
- Search/analytics/email failure does not reverse bookings.
- Recovery procedures complete within approved objectives or produce a documented variance.

---

# 22. Backup and disaster-recovery tests

## Daily automated checks

- WAL archive health
- Base-backup result
- K3s snapshot age
- Object-storage access
- Privacy-ledger write

## Weekly

- PostgreSQL restore smoke test
- Schema and constraint validation
- OpenTofu state recovery check

## Monthly

- Full application restore
- RabbitMQ reconstruction
- Keycloak login validation
- Privacy-tombstone replay
- Certificate/secret recovery

## Quarterly

- Node-loss drill
- PostgreSQL failover
- RabbitMQ node loss
- Secret rotation
- Simulator certificate compromise

## Twice yearly

- Full location-loss recovery
- Clean-room infrastructure rebuild
- Offline root/intermediate recovery validation

Record:

- Actual RPO
- Actual RTO
- Backup identifier
- Recovery target
- Manual actions
- Data-integrity result
- Privacy result
- Corrective actions

---

# 23. Property-based testing

Use property-based testing for:

- Time intervals and buffers
- Booking lifecycle action sequences
- Allocation operations
- Driver overlap rules
- Meter sequences
- Event ordering
- Duplicate delivery
- Retry histories
- Privacy participant results
- Retention eligibility
- Permission combinations

Core properties:

1. Active confirmed claims do not overlap per EVSE.
2. Active driver claims do not overlap.
3. Expired Holds do not block.
4. Duplicate messages have one business effect.
5. Older versions cannot overwrite newer state.
6. Terminal states do not reopen.
7. Meter energy is not double-counted.
8. Failed rescheduling preserves the old allocation.
9. Privacy completion requires all mandatory participants.
10. Unauthorized actors never obtain a successful state change.

Random seeds are recorded for every failure.

---

# 24. Mutation testing

Mutation testing is applied selectively to high-value domain logic:

- Allocation conflict predicates
- Lifecycle guards
- Authorization predicates
- Tariff/cost calculation
- Privacy completion rules
- Event deduplication
- Meter acceptance

Mutation score is not a universal quality metric.

Initial targets:

- Allocation and lifecycle modules: 80% surviving-mutant kill target
- Security authorization modules: 90%
- Ordinary presentation code: no mandatory mutation target

Surviving mutants require review or documented justification.

---

# 25. Test data strategy

## Data classes

- Synthetic public data
- Synthetic driver data
- Synthetic operator data
- Synthetic device data
- Fault-injection scenarios
- Anonymized fixtures only if approved

Rules:

1. No production personal data in development or CI.
2. Fixtures use stable builders, not hand-copied JSON.
3. Test data includes valid and invalid lifecycle states.
4. Boundary times include DST transitions.
5. Test data includes stale and unknown device states.
6. Test data includes conflicting versions and sequence gaps.
7. Large datasets are generated deterministically.
8. Secrets are test-only and injected securely.
9. Export tests verify data minimization.
10. Database cleanup is reliable and isolated.

---

# 26. Time and timezone testing

Test:

- UTC persistence
- `Europe/Athens` presentation
- Opening hours
- DST repeated local times
- Non-existent local times
- Hold expiry boundaries
- Grace deadline boundaries
- Maintenance intervals
- Month/year boundaries
- Leap days
- Clock skew between device and server
- Database time versus client time

Authoritative lifecycle tests must use database time, not the test machine’s local clock.

---

# 27. Test environments

| Environment | Purpose |
|---|---|
| Local | Fast unit/component development |
| CI disposable | Unit, integration, contract and migration tests |
| Ephemeral preview | Service integration and E2E |
| Reference | Performance, resilience and operational tests |
| Recovery environment | Backup and DR tests |
| Production | Controlled smoke tests only |

Environments must be isolated by:

- Credentials
- Identity realm
- Databases
- Broker virtual host
- Object-storage prefixes/buckets
- Email destination allowlist
- Map/API keys
- Observability tenancy

---

# 28. Quality gates

## Pull request gate

- Build passes
- Static checks pass
- Unit/component tests pass
- Contract schemas validate
- No critical security findings
- No secret findings
- Architecture rules pass
- Documentation links pass

## Merge gate

- Integration tests pass
- Migration tests pass
- Contract compatibility passes
- Container scan passes
- SBOM generated
- Required review approvals exist

## Release-candidate gate

- Full critical E2E suite
- Security regression suite
- Accessibility suite
- Concurrency suite
- Performance suite
- Resilience smoke suite
- Backup verification
- Traceability report
- No unresolved release-blocking defects

## Implementation-readiness gate

- All MUST requirements have verification mappings.
- All release-critical invariants have automated tests.
- ARC-006 concurrency tests pass.
- Security threat controls have tests.
- NFR targets have measured evidence.
- Recovery objectives have drill evidence.
- CI/CD gates are operational.
- Test data and environments are documented.
- Known residual risks are accepted.

---

# 29. Defect classification

| Severity | Definition | Release treatment |
|---|---|---|
| Blocker | Data loss, security bypass, double booking, privacy breach, unrecoverable deployment | Must fix |
| Critical | Core workflow unusable or invariant violation likely | Must fix |
| Major | Important feature materially degraded | Fix or formally accept |
| Minor | Limited functional or usability defect | May defer |
| Trivial | Cosmetic/documentation issue | Backlog |

Examples of automatic Blockers:

- Two conflicting allocations commit.
- Driver accesses another driver’s Booking.
- Equipment failure becomes `NO_SHOW`.
- Command timeout is reported as success.
- Privacy deletion is reported complete prematurely.
- Secret appears in logs or messages.
- Migration destroys historical data.
- Recovery cannot restore a valid database.

---

# 30. Coverage policy

Coverage is reported by:

- Lines
- Branches
- Functions
- Requirements
- State transitions
- Invariants
- API operations
- Event/command messages
- Security controls
- User journeys

Coverage thresholds are not used as the only quality gate.

Initial code-coverage targets:

| Area | Line | Branch |
|---|---:|---:|
| Allocation/lifecycle core | 85% | 80% |
| Security authorization | 90% | 85% |
| API/application services | 80% | 75% |
| Event consumers | 80% | 75% |
| Frontend feature logic | 75% | 70% |
| Shared UI | 70% | 65% |

Any uncovered release-critical path requires explicit review.

---

# 31. Traceability evidence

Maintain a machine-readable verification matrix with:

- Requirement ID
- Invariant ID
- Decision/ADR
- Test ID
- Test level
- Test location
- Environment
- Last result
- Evidence artifact
- Release/milestone
- Owner
- Defect references

Examples:

| Requirement | Verification |
|---|---|
| `FR-BKG-02` | Allocation concurrency suite, exclusion-constraint tests |
| `FR-BKG-03` | Atomic rescheduling race tests |
| `FR-BKG-05` | Check-in authorization E2E and single-use tests |
| `FR-CHG-02` | Duplicate/out-of-order device-event tests |
| `FR-PRV-02` | Deletion propagation and tombstone-restore tests |
| `FR-AUD-01` | Audit/outbox atomicity tests |
| `G-INV-01` | PostgreSQL exclusion and concurrency suite |
| `G-INV-23` | Device timeout/reconciliation tests |

---

# 32. Test reporting

Each pipeline publishes:

- Pass/fail summary
- Test duration
- Flaky-test report
- Coverage report
- Contract compatibility report
- Security scan report
- Accessibility report
- Performance report
- Traceability report
- Mutation report where applicable
- Artifact links
- Environment/version information

A test result is invalid if the environment version or database schema is unknown.

---

# 33. Flaky-test policy

1. A flaky test is a defect in the test system or product until explained.
2. Flaky tests are quarantined only temporarily.
3. Quarantine requires owner and expiry date.
4. Release-blocking tests cannot remain indefinitely quarantined.
5. Rerunning until green is not a valid quality strategy.
6. Concurrency tests record seed, timing and database state.
7. Flaky-test trends are reviewed weekly.

---

# 34. Decisions proposed for approval

| ID | Decision |
|---|---|
| ARC-QA-01 | Adopt the complete test pyramid described in this document. |
| ARC-QA-02 | Use real PostgreSQL for allocation, migration and persistence correctness tests. |
| ARC-QA-03 | Use real RabbitMQ for messaging reliability and contract tests. |
| ARC-QA-04 | Treat allocation, lifecycle, security and privacy tests as release-critical. |
| ARC-QA-05 | Require repeated concurrency testing for all allocation races. |
| ARC-QA-06 | Use property-based testing for allocation, lifecycle, messaging and privacy logic. |
| ARC-QA-07 | Use Testcontainers for disposable integration dependencies. |
| ARC-QA-08 | Use OpenAPI and AsyncAPI contract tests as CI gates. |
| ARC-QA-09 | Require accessibility testing beyond automated checks. |
| ARC-QA-10 | Require performance testing at reference and double load. |
| ARC-QA-11 | Require resilience and disaster-recovery drills before readiness approval. |
| ARC-QA-12 | Prohibit production personal data in test environments. |
| ARC-QA-13 | Use synthetic deterministic data with recorded seeds. |
| ARC-QA-14 | Apply mutation testing selectively to critical domain logic. |
| ARC-QA-15 | Require requirement/invariant/test traceability. |
| ARC-QA-16 | Treat unresolved critical security, privacy or integrity defects as release blockers. |
| ARC-QA-17 | Require flaky tests to have owners and expiry dates. |
| ARC-QA-18 | Use measured evidence rather than coverage percentage alone. |

---

# 35. Open questions

| ID | Question | Resolution phase |
|---|---|---|
| ARC-QA-OQ-01 | Final CI test-runner matrix | CI/CD design |
| ARC-QA-OQ-02 | Exact load-testing tool | Technology selection |
| ARC-QA-OQ-03 | Final browser/version support matrix | Frontend readiness |
| ARC-QA-OQ-04 | Exact ASVS control-to-test mapping | Security verification |
| ARC-QA-OQ-05 | Final performance thresholds for infrastructure components | Load testing |
| ARC-QA-OQ-06 | Mutation-testing tool and execution frequency | CI/CD design |
| ARC-QA-OQ-07 | Final accessibility screen inventory | Frontend completion |
| ARC-QA-OQ-08 | Exact test-data retention policy | Privacy review |
| ARC-QA-OQ-09 | Whether contract tests run against deployed preview environments | CI/CD design |
| ARC-QA-OQ-10 | Final test artifact retention | Delivery/operations |
| ARC-QA-OQ-11 | Exact recovery-drill schedule | Operations |
| ARC-QA-OQ-12 | Formal academic evaluation metrics | Evaluation planning |

---

# 36. Acceptance criteria

This strategy is approved when:

1. Every MUST requirement has a verification category.
2. Every release-critical invariant has automated coverage.
3. Every lifecycle has permitted and prohibited-transition tests.
4. Allocation correctness is tested against real PostgreSQL.
5. Messaging correctness is tested against real RabbitMQ.
6. REST and asynchronous contracts are CI-validated.
7. Security threats have mapped tests.
8. Privacy workflows have deletion/export/restore tests.
9. Frontend critical journeys have E2E tests.
10. WCAG 2.2 AA verification includes manual testing.
11. Reference and double-load performance tests are defined.
12. Resilience tests cover node, database, broker and provider failure.
13. Backup and disaster-recovery drills produce evidence.
14. Test data contains no unauthorized personal information.
15. Coverage, mutation and traceability reports are generated.
16. Flaky tests cannot remain indefinitely quarantined.
17. Quality gates can block release automatically.
18. Residual risks are documented and approved.

---

# 37. Consequences

## Positive

- Strong verification of booking correctness
- Realistic distributed-system testing
- Explicit security and privacy evidence
- Repeatable recovery testing
- Accessible and localized frontend verification
- Traceability from requirements to tests
- Early detection of schema and contract breaks
- Measured performance and resilience

## Negative

- Real PostgreSQL/RabbitMQ tests require more CI resources.
- Concurrency tests can be slower and harder to diagnose.
- Manual accessibility and recovery testing require specialist effort.
- Test environments and artifacts create storage costs.
- Property-based and mutation testing increase pipeline time.
- Maintaining traceability requires governance discipline.

These costs are accepted because a microservice platform cannot be considered implementation-ready based only on unit tests and happy-path demonstrations.

---

# 38. Next architecture artifact

The next document is:

**CI/CD and Repository Organization Strategy v1.0**

It must define:

- Monorepo structure
- Branching and pull requests
- Build pipelines
- Contract generation
- Database migration pipelines
- Container image creation and signing
- Security gates
- Test stages
- Environment promotion
- GitOps integration
- Release/versioning
- Artifact retention
- Dependency automation
- Rollback and forward-fix procedures
