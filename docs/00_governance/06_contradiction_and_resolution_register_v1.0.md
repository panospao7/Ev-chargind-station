Document ID: GOV-06
Title: Contradiction and Resolution Register v1.0
Version: 1.0
Status: APPROVED
Owner: PO/DA/BA
Last reviewed: 2026-07-12
Supersedes: None
Depends on: All system specifications
Authoritative for: Resolution status of system contradictions

# Contradiction and Resolution Register v1.0

This register lists and tracks all verified system contradictions and their authoritative resolutions. A contradiction is resolved when all affected authoritative documents are patched.

| ID | Topic | Conflicting Documents | Canonical Decision | Status |
|---|---|---|---|---|
| **CON-001** | Service Topology | ARC-001 vs ARC-019/021 | Retain the approved seven-service topology (combined Booking and Session). | **VERIFIED** |
| **CON-002** | Booking/Session split | ARC-001 vs ARC-019/021 | Keep Booking and Session combined in one service. | **VERIFIED** |
| **CON-003** | Service Name Drift | Network/Gateway vs Station Ops/Device Integration | Standardize on: Account, Station Operations, Booking and Session, Device Integration, Discovery and Insights, Notification, Platform Governance. | **VERIFIED** |
| **CON-004** | Document Status Mismatch | Metadata vs Body status | Downgrade ARC-019, ARC-020, and ARC-021 to `IN_REVIEW`. | **VERIFIED** |
| **CON-005** | Readiness Claim | GOV-004 readiness wording | Correct status to: "Logical planning is complete; technical OpenAPI/AsyncAPI schemas are in review." | **VERIFIED** |
| **CON-006** | Gate G3 complete status | GOV-004 vs actual contracts | Mark OpenAPI and AsyncAPI schemas as completed in specification but pending code generation. | **VERIFIED** |
| **CON-007** | Booking inputs / dependencies | ARC-019 vs local projections | Final booking allocation uses Booking-local projections. No remote calls are made during locks. | **VERIFIED** |
| **CON-008** | BFF and user-token delegation | ARC-007 JWT forwarding | Asymmetric Token Exchange delegation selected for service-to-service calls. | **VERIFIED** |
| **CON-009** | Actor context in JWT claims | ARC-007 JWT headers vs claims | Actor context is propagated in cryptographically signed JWT claims, not HTTP headers. | **VERIFIED** |
| **CON-010** | Org membership revocation | Token lifetime vs versioning | Validate organization membership version stamp locally against membership projection. | **VERIFIED** |
| **CON-011** | Restriction workflow | DOM-006 vs ARC-019/021 | Separate Maintenance planning (`DRAFT` to `COMPLETED`) from Capacity restriction (`FREEZE` to `RELEASED`). | **VERIFIED** |
| **CON-012** | Operator application summary | DOM-002 vs operator catalogue | Add `WITHDRAWN` state to the Operator Application catalogue summary. | **VERIFIED** |
| **CON-013** | Organization event names | ARC-020 organization events | Use application-specific events: `OperatorApplicationSubmitted`, `OperatorApplicationApproved`. | **VERIFIED** |
| **CON-014** | Privacy coordinator | Account vs Governance service | Governance Service coordinates privacy workflow; Account Service handles local deletion. | **VERIFIED** |
| **CON-015** | Booking hold representation | Glossary vs Booking spec | Represents hold as a state (`HELD`) of the Booking aggregate and `BOOKING_HOLD` claim. | **VERIFIED** |
| **CON-016** | Session overrun | Planned allocation extension | Planned allocation ends; a separate operational-occupation claim tracks physical overrun. | **VERIFIED** |
| **CON-017** | Critical faults blocking rules | Faults block vs near-term | Faults only block near-term bookings (within 15 minutes of scheduled start). | **VERIFIED** |
| **CON-018** | Command transport | RabbitMQ vs REST | RabbitMQ is the authoritative command transport; REST queries status. | **VERIFIED** |
| **CON-019** | OpenAPI version | REST catalogue specification | Standardize on OpenAPI 3.0.3. | **VERIFIED** |
| **CON-020** | HTTP error detail standard | RFC 7807 vs RFC 9457 | Standardize on RFC 9457. | **VERIFIED** |
| **CON-021** | Problem codes | API contract catalogue | Standardize problem-code naming convention (e.g. `VERSION_CONFLICT`). | **VERIFIED** |
| **CON-022** | Allocation conflict error codes | Error taxonomy mismatch | Standardize on `EVSE_ALLOCATION_CONFLICT` across all API and database models. | **VERIFIED** |
| **CON-023** | Bootstrapping strategy | Liquibase vs API seeding | Liquibase seeds schema/immutable refs; API runner seeds domain aggregates. | **VERIFIED** |
| **CON-024** | Outage matrix wording | "Succeeds" during broker failure | Clarify that the business transaction commits and succeeds, while publishing remains pending. | **VERIFIED** |
| **CON-025** | Release Wave tags | Deferred Release 2 rules | Every contract and saga has a `Release applicability: W1 | W2 | W3` tag. | **VERIFIED** |
| **CON-026** | Errata resolution record | GOV-005errata resolved | Add explicit item-level closure evidence table in GOV-005. | **VERIFIED** |
| **CON-027** | Roadmap wording | Roadmap completed milestones | Correct upcoming/completed milestone language in GOV-004. | **VERIFIED** |
| **CON-028** | Duplicate superseded drafts | Archive duplicate files | Remove duplicate file, mark remaining file non-authoritative. | **VERIFIED** |
| **CON-029** | README description | README typos | Correct duplicate README wording. | **VERIFIED** |
| **CON-030** | Metadata review dates | Metadata block timestamps | Standardize all modified files' review dates to 2026-07-12. | **VERIFIED** |
