Document ID: GOV-004
Title: Planning Status and Roadmap v1.1
Version: 1.1
Status: APPROVED
Owner: PO/DA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: None
Authoritative for: Milestones and Planning Roadmaps

---

Of the **eight identified foundation gaps**, gaps **1–7 now have draft specifications**. The only direct gap remaining is:

## Immediate remaining gap

### 8. Repair the Functional Requirements Catalogue and Traceability Matrix

Update it so every requirement maps to:

- Actor/use case
- Authoritative specification
- Business rules
- Planned owning capability
- API/command/event contract
- Data entity
- Security controls
- Acceptance tests
- Implementation epic

It must now reference the new documents for:

- DR-16 check-in
- Availability
- Distributed consistency
- Simulator protocol
- Privacy
- Notifications
- Remaining driver/operator/admin/support use cases

## Foundation consolidation before architecture

We should then complete:

1. **Decision and Open-Question Register**
   - Approve/reject all proposed decisions in the new drafts.
   - Record unresolved decisions, owners and deadlines.

2. **Domain Glossary**
   - Standard definitions for Station, EVSE, Connector, Allocation, Booking, Session, Availability, Fault, etc.

3. **Lifecycle and Invariant Catalogue**
   - Consolidate every state machine and permitted transition.
   - Detect conflicting or unreachable states.

4. **Cross-document consistency review**
   - Remove duplication.
   - Identify authoritative documents.
   - Correct filenames, headings and roadmap status.
   - Resolve terminology and role naming.

5. **Consolidated System Specification v1.0**
   - One indexed entry point linking all approved specifications.

## Architecture planning after foundation approval

Then we proceed in this order:

1. Microservice boundaries and data ownership  
2. Inter-service communication and consistency matrix  
3. REST API contracts  
4. Event and command contracts  
5. Database models and migrations  
6. Definitive double-booking implementation design  
7. Security architecture and threat model  
8. Frontend architecture, screens and UX flows  
9. Final technology selections  
10. Cloud provider and cost analysis  
11. Deployment architecture and infrastructure as code  
12. Observability, backups and operational runbooks  
13. Complete testing strategy  
14. CI/CD and repository organization  
15. Implementation epics and dependency roadmap  
16. AI-agent rules, responsibilities and review gates  
17. Modular-monolith alternative design  
18. Architecture comparison and evaluation plan  
19. Final implementation-readiness review  

Therefore, the **next document should be the repaired Consolidated Functional Requirements Catalogue and Full Traceability Matrix v1.1**. After that, we should consolidate decisions and domain terminology before designing services.