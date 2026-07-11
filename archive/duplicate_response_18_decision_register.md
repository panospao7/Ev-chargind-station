I consolidated every proposed decision from the gap-closing specifications. Decisions requiring architecture, provider, empirical, or legal validation remain provisional/open rather than being falsely finalized.

<12_decision_and_open_question_register_v1.0.md>
# Decision and Open-Question Register v1.0

## 1. Purpose

This register is the authoritative index of:

- Approved decisions
- Provisional decisions
- Deferred decisions
- Rejected or superseded alternatives
- Open questions requiring resolution
- Decision owners and resolution gates

Approval of this document ratifies all entries marked `APPROVED`. Provisional entries remain subject to validation through an Architecture Decision Record or specialist review.

---

## 2. Status definitions

- `APPROVED` — accepted project baseline.
- `PROVISIONAL` — preferred direction, requiring later validation.
- `DEFERRED` — intentionally excluded from the initial release.
- `REJECTED` — evaluated and not selected.
- `SUPERSEDED` — replaced by a newer decision.
- `OPEN` — unresolved.

---

## 3. Decision owners

Although this is an individual project, decisions are assigned to responsibility roles:

- **PO** — Product Owner
- **DA** — Domain Architect
- **BA** — Backend/Distributed Systems Architect
- **SA** — Security Architect
- **PA** — Privacy Reviewer
- **FA** — Frontend/UX Architect
- **CA** — Cloud/Operations Architect
- **QA** — Test Architect
- **AR** — Academic/Evaluation Reviewer

The project owner may hold all roles but must review decisions from each perspective separately.

---

## 4. Resolution gates

Because the project has no fixed calendar deadline, decision deadlines use planning gates:

- **G1:** Before domain foundation approval
- **G2:** Before microservice boundaries are approved
- **G3:** Before API, event and database contracts are approved
- **G4:** Before security and privacy architecture approval
- **G5:** Before cloud/deployment architecture approval
- **G6:** Before implementation-readiness approval
- **G7:** Before the affected feature is implemented
- **POST-MVP:** Deferred release planning

---

# 5. Confirmed project baseline

| ID | Decision | Status | Owner |
|---|---|---|---|
| DEC-BASE-01 | Implement the operational system as microservices. | APPROVED | BA |
| DEC-BASE-02 | Document but do not fully implement a modular-monolith alternative. | APPROVED | BA/AR |
| DEC-BASE-03 | Use Angular and TypeScript for the web client. | APPROVED | FA |
| DEC-BASE-04 | Use Java and Spring Boot for backend implementation. | APPROVED | BA |
| DEC-BASE-05 | Make the initial platform Greece-first, using EUR, kilometres and `Europe/Athens`. | APPROVED | PO/DA |
| DEC-BASE-06 | Store timestamps in UTC and convert for presentation. | APPROVED | DA |
| DEC-BASE-07 | Implement one responsive Angular application with role-protected areas. | APPROVED | FA |
| DEC-BASE-08 | Support drivers, operator staff, platform administrators, platform support, auditors and simulator devices. | APPROVED | DA |
| DEC-BASE-09 | Reserve an EVSE rather than an individual connector. | APPROVED | DA |
| DEC-BASE-10 | Treat one EVSE as capable of serving one vehicle at a time in v1. | APPROVED | DA |
| DEC-BASE-11 | Include simulated charging sessions separately from bookings. | APPROVED | DA |
| DEC-BASE-12 | Exclude real payments, real hardware control and claims of OCPP compliance. | APPROVED | PO/SA |
| DEC-BASE-13 | Include essential transactional email in the core release. | APPROVED | PO |
| DEC-BASE-14 | Keep SMS, native mobile applications and real network integrations deferred. | DEFERRED | PO |
| DEC-BASE-15 | Require implementation-readiness approval before implementation begins. | APPROVED | PO/AR |

---

# 6. Check-in decisions

Default owner: **DA/BA**. Deadline: **G1**, unless noted.

| ID | Decision | Status |
|---|---|---|
| DEC-CIN-01 | Check-in opens 15 minutes before the scheduled start. | APPROVED |
| DEC-CIN-02 | Normal check-in closes at the snapshotted grace deadline, initially 15 minutes after start. | APPROVED |
| DEC-CIN-03 | Charging cannot begin before the reserved start in v1. | APPROVED |
| DEC-CIN-04 | Successful check-in creates one single-use start authorization. | APPROVED |
| DEC-CIN-05 | Start authorization is bound to the booking, driver, EVSE and intended session. | APPROVED |
| DEC-CIN-06 | Start authorization expires no later than the grace deadline. | APPROVED |
| DEC-CIN-07 | Check-in may be abandoned only before session start and while the window remains valid. | APPROVED |
| DEC-CIN-08 | Equipment-related check-in failure routes to reassignment or fulfilment resolution, never directly to `NO_SHOW`. | APPROVED |
| DEC-CIN-09 | QR codes identify EVSEs but contain no authorization secret. | APPROVED |
| DEC-CIN-10 | The browser never communicates a reusable start credential directly to the simulator. | APPROVED |

---

# 7. Availability decisions

Default owner: **DA/BA**.

| ID | Decision | Status | Deadline |
|---|---|---|---|
| DEC-AVL-01 | Use half-open time intervals: `[start, end)`. | APPROVED | G1 |
| DEC-AVL-02 | Apply one post-booking turnaround buffer in v1. | APPROVED | G1 |
| DEC-AVL-03 | Make the Booking authority’s transactional allocation decision authoritative. | APPROVED | G1 |
| DEC-AVL-04 | Treat search availability as advisory and eventually consistent. | APPROVED | G1 |
| DEC-AVL-05 | Use `AVAILABLE`, `PLANNED_AVAILABLE`, `UNAVAILABLE`, `UNKNOWN` and `INCOMPATIBLE`. | APPROVED | G1 |
| DEC-AVL-06 | Keep administrative state, device-reported state and derived availability separate. | APPROVED | G1 |
| DEC-AVL-07 | Use a 60-minute near-term status horizon initially. | PROVISIONAL | G3 |
| DEC-AVL-08 | Define freshness initially as the greater of three heartbeat intervals or 180 seconds. | PROVISIONAL | G3 |
| DEC-AVL-09 | Permit future reservations despite temporary offline, stale or unknown status, labelled `PLANNED_AVAILABLE`. | APPROVED | G1 |
| DEC-AVL-10 | Never allow reservations over blocking maintenance, administrative closure or unresolved critical/emergency faults. | APPROVED | G1 |
| DEC-AVL-11 | A search without a requested interval is an operational summary, not reservation availability. | APPROVED | G1 |
| DEC-AVL-12 | Require the charging interval, but not necessarily the post-buffer, to fit opening hours. | APPROVED | G1 |
| DEC-AVL-13 | Release unused cancelled allocations immediately. | APPROVED | G1 |
| DEC-AVL-14 | Keep actual or uncertain EVSE occupation blocking until safe release is confirmed. | APPROVED | G1 |
| DEC-AVL-15 | Expired holds are non-blocking even if the cleanup worker has not processed them. | APPROVED | G1 |

---

# 8. Distributed consistency decisions

Default owner: **BA**.

| ID | Decision | Status | Deadline |
|---|---|---|---|
| DEC-DST-01 | Use transactional outbox publication. | APPROVED | G2 |
| DEC-DST-02 | Use idempotent inbox or equivalent consumer deduplication. | APPROVED | G2 |
| DEC-DST-03 | Guarantee at-least-once delivery rather than claim exactly-once delivery. | APPROVED | G2 |
| DEC-DST-04 | Prohibit cross-capability database transactions. | APPROVED | G2 |
| DEC-DST-05 | Prohibit direct writes to another capability’s database. | APPROVED | G2 |
| DEC-DST-06 | Use authoritative database time for business deadlines. | APPROVED | G2 |
| DEC-DST-07 | Treat device-command timeout as an uncertain outcome. | APPROVED | G1 |
| DEC-DST-08 | Keep capacity blocked while device/session outcome remains uncertain. | APPROVED | G1 |
| DEC-DST-09 | Use event choreography for simple projection updates. | APPROVED | G2 |
| DEC-DST-10 | Use explicit workflow coordination for privacy and complex operational workflows. | APPROVED | G2 |
| DEC-DST-11 | Prevent normal maintenance activation while bookings or sessions remain unresolved. | APPROVED | G1 |
| DEC-DST-12 | Set device confidence to `UNKNOWN` after maintenance until fresh evidence arrives. | APPROVED | G1 |
| DEC-DST-13 | Notification, search and analytics failures cannot reverse committed core operations. | APPROVED | G1 |
| DEC-DST-14 | Use aggregate versions or sequence numbers wherever event ordering matters. | APPROVED | G2 |
| DEC-DST-15 | Retain inbox deduplication records longer than the supported replay window. | APPROVED | G3 |
| DEC-DST-16 | Define confirmed-booking acknowledgement as durable database commit. | APPROVED | G1 |
| DEC-DST-17 | Do not wait for broker publication or email delivery before acknowledging confirmation. | APPROVED | G1 |
| DEC-DST-18 | Require safe replay/rebuild procedures for every asynchronous projection. | APPROVED | G3 |
| DEC-DST-19 | Background-worker correctness must not depend on one singleton scheduler. | APPROVED | G2 |
| DEC-DST-20 | Compensation is a new business action, not a distributed rollback. | APPROVED | G2 |

---

# 9. Simulator decisions

Default owner: **BA/SA**.

| ID | Decision | Status | Deadline |
|---|---|---|---|
| DEC-SIM-01 | Implement a custom OCPP 2.1 Edition 2-inspired protocol. | APPROVED | G1 |
| DEC-SIM-02 | Never claim OCPP compliance, certification or wire compatibility. | APPROVED | G1 |
| DEC-SIM-03 | Use versioned JSON over secure WebSockets as the primary device channel. | APPROVED | G2 |
| DEC-SIM-04 | Assign one machine identity per simulated charging station controller. | APPROVED | G2 |
| DEC-SIM-05 | Use certificate-based machine authentication as the deployed target. | PROVISIONAL | G5 |
| DEC-SIM-06 | Permit a restricted token/credential profile for local development only. | APPROVED | G4 |
| DEC-SIM-07 | Keep platform booking allocation authoritative. | APPROVED | G1 |
| DEC-SIM-08 | Treat device reservations as operational mirrors only. | APPROVED | G1 |
| DEC-SIM-09 | Separate transport receipt, command acceptance and physical completion. | APPROVED | G1 |
| DEC-SIM-10 | Require stable command IDs and event IDs. | APPROVED | G2 |
| DEC-SIM-11 | Use station-level and session-level sequence numbers. | APPROVED | G2 |
| DEC-SIM-12 | Permit an existing simulated session to continue while disconnected. | APPROVED | G1 |
| DEC-SIM-13 | Queue durable events while disconnected. | APPROVED | G2 |
| DEC-SIM-14 | Prohibit starting a new driver session while offline. | APPROVED | G1 |
| DEC-SIM-15 | Keep uncertain device outcomes capacity-blocking until reconciled. | APPROVED | G1 |
| DEC-SIM-16 | Support deterministic failure scenarios using configurable seeds. | APPROVED | G3 |
| DEC-SIM-17 | Prevent simulator inventory from creating platform infrastructure automatically. | APPROVED | G1 |
| DEC-SIM-18 | Calculate estimated cost in the platform, not the simulator. | APPROVED | G1 |
| DEC-SIM-19 | Exclude unnecessary driver information from device messages. | APPROVED | G4 |
| DEC-SIM-20 | Preserve offline queues and idempotency history across simulated restarts. | APPROVED | G3 |

---

# 10. Privacy decisions

Default owner: **PA/SA**.

| ID | Decision | Status | Deadline |
|---|---|---|---|
| DEC-PRV-01 | Do not retain precise search-location history by default. | APPROVED | G1 |
| DEC-PRV-02 | Exclude VIN, registration plate and home address from v1. | APPROVED | G1 |
| DEC-PRV-03 | Separate access, portability, rectification, restriction and deletion workflows. | APPROVED | G1 |
| DEC-PRV-04 | Use JSON as the canonical privacy-export format. | APPROVED | G1 |
| DEC-PRV-05 | Permit optional CSV/PDF readable export views. | APPROVED | G3 |
| DEC-PRV-06 | Make generated privacy exports available for seven days. | APPROVED | G4 |
| DEC-PRV-07 | Use a seven-day deletion cooling-off period. | PROVISIONAL | G4 |
| DEC-PRV-08 | Block deletion while active or uncertain obligations remain. | APPROVED | G1 |
| DEC-PRV-09 | Preserve historical facts through correction metadata rather than destructive rewriting. | APPROVED | G1 |
| DEC-PRV-10 | Treat pseudonymized data as personal data. | APPROVED | G1 |
| DEC-PRV-11 | Use anonymized aggregates for long-term analytics. | APPROVED | G4 |
| DEC-PRV-12 | Use a versioned retention engine rather than scattered cleanup logic. | APPROVED | G3 |
| DEC-PRV-13 | Adopt the draft retention schedule only provisionally. | PROVISIONAL | G4 |
| DEC-PRV-14 | Use 35-day rolling backup retention initially. | PROVISIONAL | G5 |
| DEC-PRV-15 | Reapply privacy tombstones after restoring backups. | APPROVED | G5 |
| DEC-PRV-16 | Require legal/security holds to be scoped, expiring and audited. | APPROVED | G4 |
| DEC-PRV-17 | Prohibit user-level analytics profiles unless later justified. | APPROVED | G1 |
| DEC-PRV-18 | Perform a DPIA-style assessment before implementation readiness. | APPROVED | G6 |
| DEC-PRV-19 | Maintain a personal-data inventory and processing register. | APPROVED | G4 |
| DEC-PRV-20 | Keep controller/processor, legal-basis and international-transfer conclusions open until provider selection. | APPROVED | G5 |
| DEC-PRV-21 | Never attach personal-data export archives to email. | APPROVED | G1 |
| DEC-PRV-22 | Do not claim legal compliance solely from implementing these controls. | APPROVED | G1 |

---

# 11. Notification decisions

Default owner: **PO/BA/SA**.

| ID | Decision | Status | Deadline |
|---|---|---|---|
| DEC-NOT-01 | Support transactional email only in v1. | APPROVED | G1 |
| DEC-NOT-02 | Defer SMS, push and in-app notifications. | DEFERRED | POST-MVP |
| DEC-NOT-03 | Make security, material booking, account and privacy messages mandatory. | APPROVED | G1 |
| DEC-NOT-04 | Allow users to disable reminders and routine session summaries. | APPROVED | G1 |
| DEC-NOT-05 | Deliver application notifications asynchronously after authoritative commit. | APPROVED | G2 |
| DEC-NOT-06 | Keep identity action-link generation in the identity provider. | APPROVED | G2 |
| DEC-NOT-07 | Never place raw identity action tokens on the message broker. | APPROVED | G4 |
| DEC-NOT-08 | Use a 24-hour verification-link lifetime. | PROVISIONAL | G4 |
| DEC-NOT-09 | Use a 30-minute password-reset lifetime. | PROVISIONAL | G4 |
| DEC-NOT-10 | Use a 30-minute email-change lifetime. | PROVISIONAL | G4 |
| DEC-NOT-11 | Use a 48-hour operator-invitation lifetime. | PROVISIONAL | G4 |
| DEC-NOT-12 | Use a seven-day privacy-export download lifetime. | APPROVED | G4 |
| DEC-NOT-13 | Use a 24-hour deletion-confirmation lifetime. | PROVISIONAL | G4 |
| DEC-NOT-14 | Schedule optional reminders at 24 hours, 60 minutes and check-in opening. | PROVISIONAL | G3 |
| DEC-NOT-15 | Deduplicate by type, recipient, aggregate and version/milestone. | APPROVED | G3 |
| DEC-NOT-16 | Suppress obsolete unsent notifications after newer lifecycle changes. | APPROVED | G3 |
| DEC-NOT-17 | Treat provider acceptance and mailbox delivery as distinct outcomes. | APPROVED | G1 |
| DEC-NOT-18 | Never cancel a booking solely because email delivery failed. | APPROVED | G1 |
| DEC-NOT-19 | Require Greek and English HTML and plain-text templates. | APPROVED | G3 |
| DEC-NOT-20 | Configure SPF, DKIM and DMARC for deployed email. | APPROVED | G5 |
| DEC-NOT-21 | Use a local mail catcher and a staging destination allowlist. | APPROVED | G5 |
| DEC-NOT-22 | Prohibit arbitrary operator-authored email to drivers. | APPROVED | G1 |
| DEC-NOT-23 | Display an application warning when essential email is undeliverable. | APPROVED | G3 |
| DEC-NOT-24 | Do not synchronously wait for email transport during booking operations. | APPROVED | G1 |

---

# 12. Remaining use-case decisions

Default owner: **PO/DA/SA**.

| ID | Decision | Status |
|---|---|---|
| DEC-UC-01 | Defer driver fault-report attachments from v1. | DEFERRED |
| DEC-UC-02 | Defer support-case attachments from v1. | DEFERRED |
| DEC-UC-03 | Source booking/session history from authoritative operational data, not analytics. | APPROVED |
| DEC-UC-04 | Allow users to hide/archive history without treating it as deletion. | APPROVED |
| DEC-UC-05 | Exclude legal-document uploads from the simulated operator-application MVP. | APPROVED |
| DEC-UC-06 | Require the recipient to accept an operator ownership transfer. | APPROVED |
| DEC-UC-07 | Prohibit driver-level bulk exports through operator analytics. | APPROVED |
| DEC-UC-08 | Permit ordinary manual overrides to restrict availability only. | APPROVED |
| DEC-UC-09 | Prevent ordinary overrides from bypassing allocation, safety, maintenance or fault rules. | APPROVED |
| DEC-UC-10 | Require case-scoped, expiring platform-support access. | APPROVED |
| DEC-UC-11 | Keep direct administrator correction of operator business data exceptional. | APPROVED |
| DEC-UC-12 | Separate audit review permissions from audit-storage administration. | APPROVED |

---

# 13. Requirements-governance decisions

Default owner: **DA/QA/AR**.

| ID | Decision | Status |
|---|---|---|
| DEC-REQ-01 | Functional Requirements v1.1 replaces v1.0. | APPROVED |
| DEC-REQ-02 | Requirement IDs become stable after approval. | APPROVED |
| DEC-REQ-03 | Never silently reuse an ID for a different requirement. | APPROVED |
| DEC-REQ-04 | Mark removed requirements `RETIRED` rather than deleting them. | APPROVED |
| DEC-REQ-05 | Require change history and impact analysis for modified requirements. | APPROVED |
| DEC-REQ-06 | Treat `MUST` requirements as the operational release baseline. | APPROVED |
| DEC-REQ-07 | Defer `SHOULD` requirements only through a recorded release decision. | APPROVED |
| DEC-REQ-08 | Keep logical capability owners provisional until architecture approval. | APPROVED |
| DEC-REQ-09 | Trace every final architecture and implementation artifact to requirements. | APPROVED |
| DEC-REQ-10 | Do not declare a requirement complete without automated verification evidence. | APPROVED |
| DEC-REQ-11 | Require API, event, data, security, test and backlog mappings before implementation readiness. | APPROVED |

---

# 14. Provisional technology decisions

| ID | Decision | Status | Owner | Deadline |
|---|---|---|---|---|
| DEC-TECH-01 | Use Keycloak as the identity provider. | PROVISIONAL | SA/BA | G2 |
| DEC-TECH-02 | Use PostgreSQL as the primary transactional database technology. | PROVISIONAL | BA | G2 |
| DEC-TECH-03 | Use RabbitMQ as the initial message broker. | PROVISIONAL | BA/CA | G2 |
| DEC-TECH-04 | Use MapLibre with OpenStreetMap-based map data. | PROVISIONAL | FA/CA | G3 |
| DEC-TECH-05 | Use Redis only where a measured requirement justifies it. | APPROVED | BA |
| DEC-TECH-06 | Display gross EUR estimates with versioned tariff/tax snapshots. | APPROVED | DA |
| DEC-TECH-07 | Select the cloud platform after architecture and cost analysis. | OPEN | CA | G5 |
| DEC-TECH-08 | Select the transactional email provider after deployment analysis. | OPEN | CA/SA | G5 |
| DEC-TECH-09 | Select observability tooling after deployment-topology design. | OPEN | CA | G5 |
| DEC-TECH-10 | Prefer managed free/low-cost services when they do not undermine architectural goals. | APPROVED | PO/CA |

---

# 15. Rejected and superseded alternatives

| ID | Alternative | Status | Reason |
|---|---|---|---|
| REJ-01 | Reserve an individual connector rather than the EVSE. | REJECTED | The v1 EVSE serves one vehicle at a time. |
| REJ-02 | Treat search availability as authoritative. | REJECTED | Search projections are eventually consistent. |
| REJ-03 | Use one overloaded EVSE status for administration, device state and availability. | REJECTED | It creates ambiguous and unsafe decisions. |
| REJ-04 | Use a generic booking `FAILED` state. | REJECTED | `FULFILMENT_FAILED` and other outcomes provide meaningful semantics. |
| REJ-05 | Mark interrupted sessions as ordinary successful completion. | REJECTED | Interrupted outcome must remain visible. |
| REJ-06 | Classify equipment failure as driver no-show. | REJECTED | It assigns fault incorrectly. |
| REJ-07 | Release uncertain occupied capacity after a timeout alone. | REJECTED | Timeout does not prove physical termination. |
| REJ-08 | Claim exactly-once event delivery. | REJECTED | The design uses at-least-once delivery plus idempotency. |
| REJ-09 | Use cross-service distributed database transactions. | REJECTED | Violates ownership and increases coupling. |
| REJ-10 | Let services write directly to another service’s database. | REJECTED | Breaks data ownership. |
| REJ-11 | Require email delivery before confirming a booking. | REJECTED | Provider failure must not block committed bookings. |
| REJ-12 | Include authorization secrets in EVSE QR codes. | REJECTED | Identifiers are public and provide no authority. |
| REJ-13 | Allow drivers to communicate directly with simulator devices. | REJECTED | Violates the trusted backend authorization boundary. |
| REJ-14 | Let simulator inventory automatically create infrastructure. | REJECTED | Infrastructure is operator-managed authoritative data. |
| REJ-15 | Calculate final cost inside the simulator. | REJECTED | Tariff snapshots and accepted meter data belong to platform capabilities. |
| REJ-16 | Start new driver sessions while simulator is offline. | REJECTED | Authorization cannot be validated safely. |
| REJ-17 | Describe the custom simulator as OCPP-compliant. | REJECTED | The implementation is inspired only. |
| REJ-18 | Keep transactional email deferred. | SUPERSEDED | Essential transactional email is now foundational. |
| REJ-19 | Build separate driver, operator and administrator frontends for v1. | REJECTED | One role-aware Angular application is sufficient initially. |
| REJ-20 | Hard-delete infrastructure with historical records. | REJECTED | Deactivation preserves integrity and traceability. |
| REJ-21 | Permit silent administrator impersonation. | REJECTED | Violates accountability and least privilege. |
| REJ-22 | Retain precise driver search-location history by default. | REJECTED | Unnecessary privacy risk. |
| REJ-23 | Treat pseudonymized data as anonymous. | REJECTED | Re-identification remains possible. |
| REJ-24 | Permit arbitrary operator-authored email. | REJECTED | Creates abuse, privacy and audit risks. |
| REJ-25 | Begin implementation before readiness approval. | REJECTED | Contradicts the planning-first project goal. |

---

# 16. Open-question register

## Architecture and ownership

| ID | Question | Owner | Deadline |
|---|---|---|---|
| OQ-ARC-01 | What are the final microservice boundaries? | BA/DA | G2 |
| OQ-ARC-02 | Which logical capabilities may safely share one service without weakening ownership? | BA | G2 |
| OQ-ARC-03 | Which workflows require an explicit coordinator and where will it reside? | BA | G2 |
| OQ-ARC-04 | Will the API gateway also act as a backend-for-frontend, or will those concerns remain separate? | BA/FA | G2 |
| OQ-ARC-05 | How will the modular-monolith alternative map the same domain boundaries? | BA/AR | G6 |

## Data and consistency

| ID | Question | Owner | Deadline |
|---|---|---|---|
| OQ-DAT-01 | Will each service use a separate PostgreSQL database, separate schema, or separate logical ownership in one instance? | BA/CA | G2 |
| OQ-DAT-02 | Which database mechanism will enforce non-overlapping EVSE allocations? | BA | G3 |
| OQ-DAT-03 | What transaction-isolation level is required for allocation and rescheduling? | BA/QA | G3 |
| OQ-DAT-04 | What is the supported broker/event replay window? | BA/CA | G3 |
| OQ-DAT-05 | How long must inbox, outbox and command-result deduplication records be retained? | BA/PA | G3 |
| OQ-DAT-06 | What event store/source-data strategy will support projection rebuilds? | BA | G3 |
| OQ-DAT-07 | Which data must be queried synchronously during authoritative booking allocation? | BA | G2 |

## Availability and booking policy

| ID | Question | Owner | Deadline |
|---|---|---|---|
| OQ-BKG-01 | Validate or adjust the 60-minute near-term horizon. | DA/QA | G3 |
| OQ-BKG-02 | Validate or adjust the heartbeat freshness formula. | BA/QA | G3 |
| OQ-BKG-03 | Should operators be allowed to require turnaround buffers to fit within opening hours? | DA | G3 |
| OQ-BKG-04 | What exact platform-wide minimum and maximum policy limits may operators configure? | PO/DA | G3 |
| OQ-BKG-05 | May a permanently rejected start be retried using the same session aggregate, or must a new start attempt record be created? | DA/BA | G3 |
| OQ-BKG-06 | What support override, if any, is permitted after the check-in grace deadline? | PO/SA | G4 |
| OQ-BKG-07 | How should future bookings be reviewed automatically when they enter the near-term horizon? | BA/DA | G3 |

## Identity and security

| ID | Question | Owner | Deadline |
|---|---|---|---|
| OQ-SEC-01 | Confirm Keycloak through an identity-provider ADR and proof of concept. | SA/BA | G2 |
| OQ-SEC-02 | Define service-to-service authentication and authorization. | SA/BA | G4 |
| OQ-SEC-03 | Determine whether deployed simulator mTLS is practical on the selected cloud platform. | SA/CA | G5 |
| OQ-SEC-04 | Select secrets-management technology. | SA/CA | G5 |
| OQ-SEC-05 | Define rate-limit values and abuse thresholds. | SA/QA | G4 |
| OQ-SEC-06 | Define session/token lifetimes and revocation behaviour. | SA | G4 |
| OQ-SEC-07 | Define the break-glass approver and independent review workflow for an individual project demonstration. | SA/AR | G4 |
| OQ-SEC-08 | Complete the threat model and OWASP ASVS control mapping. | SA | G4 |

## Privacy and compliance

| ID | Question | Owner | Deadline |
|---|---|---|---|
| OQ-PRV-01 | Approve or revise each provisional retention period. | PA | G4 |
| OQ-PRV-02 | Confirm whether the seven-day deletion cooling-off period should remain. | PO/PA | G4 |
| OQ-PRV-03 | Determine controller/processor responsibilities for the selected deployment. | PA | G5 |
| OQ-PRV-04 | Document candidate and approved legal bases for each processing purpose. | PA | G5 |
| OQ-PRV-05 | Review provider data locations and international-transfer implications. | PA/CA | G5 |
| OQ-PRV-06 | Define the anonymization-risk assessment and small-cell suppression threshold. | PA/QA | G4 |
| OQ-PRV-07 | Determine whether a formal DPIA is required and complete the project assessment. | PA | G6 |
| OQ-PRV-08 | Define how privacy actions are reapplied during disaster recovery. | PA/CA | G5 |

## Notification and external providers

| ID | Question | Owner | Deadline |
|---|---|---|---|
| OQ-NOT-01 | Select the email provider and sandbox/local-development solution. | CA/BA | G5 |
| OQ-NOT-02 | Validate action-link lifetimes against identity-provider capabilities. | SA/BA | G4 |
| OQ-NOT-03 | Validate the proposed reminder schedule through UX review. | PO/FA | G3 |
| OQ-NOT-04 | Define handling when mandatory email remains permanently undeliverable. | PO/SA | G4 |
| OQ-NOT-05 | Define sender domain and SPF/DKIM/DMARC deployment ownership. | CA/SA | G5 |

## Frontend and maps

| ID | Question | Owner | Deadline |
|---|---|---|---|
| OQ-UI-01 | Define the complete screen and route catalogue. | FA | G3 |
| OQ-UI-02 | Define responsive, accessibility and keyboard interaction patterns for map/list discovery. | FA/QA | G3 |
| OQ-UI-03 | Select map tile, geocoding and routing providers compatible with the project’s cost and usage requirements. | FA/CA | G5 |
| OQ-UI-04 | Define Greek/English translation ownership and terminology. | FA/DA | G3 |
| OQ-UI-05 | Determine frontend state-management and API-client patterns. | FA | G3 |

## Cloud and operations

| ID | Question | Owner | Deadline |
|---|---|---|---|
| OQ-OPS-01 | Select the cloud provider and region. | CA | G5 |
| OQ-OPS-02 | Decide between Kubernetes and a simpler managed container platform. | CA/BA | G5 |
| OQ-OPS-03 | Define production-like, staging and local deployment topologies. | CA | G5 |
| OQ-OPS-04 | Select logging, metrics and tracing technologies. | CA | G5 |
| OQ-OPS-05 | Define backup, point-in-time recovery and restoration implementation. | CA | G5 |
| OQ-OPS-06 | Establish actual monthly budget limits and cost alerts. | PO/CA | G5 |
| OQ-OPS-07 | Define the implementation of broker, database and provider failure isolation. | CA/BA | G5 |

## Testing and evaluation

| ID | Question | Owner | Deadline |
|---|---|---|---|
| OQ-QA-01 | Define exact automated test levels and tools. | QA | G6 |
| OQ-QA-02 | Define how concurrent double-booking tests will run repeatedly and deterministically. | QA/BA | G3 |
| OQ-QA-03 | Define the environment used to validate reference and double load. | QA/CA | G5 |
| OQ-QA-04 | Define measurable microservices-versus-monolith comparison experiments. | AR/QA | G6 |
| OQ-QA-05 | Define accessibility testing tools and manual checks. | QA/FA | G6 |
| OQ-QA-06 | Define security scanning, penetration and dependency-testing scope. | QA/SA | G6 |
| OQ-QA-07 | Define acceptance-evidence storage and traceability to requirement IDs. | QA | G6 |

## Repository and delivery

| ID | Question | Owner | Deadline |
|---|---|---|---|
| OQ-DEL-01 | Rename the repository to correct `chargind`. | PO | Before active implementation |
| OQ-DEL-02 | Select monorepo versus multi-repository organization. | BA/CA | G2 |
| OQ-DEL-03 | Define branching, commit, review and release conventions. | CA/QA | G6 |
| OQ-DEL-04 | Define CI/CD pipelines and quality gates. | CA/QA | G6 |
| OQ-DEL-05 | Define AI-agent roles, permissions and mandatory review gates. | PO/QA | G6 |
| OQ-DEL-06 | Define the implementation epic order and release milestones. | PO/BA | G6 |

---

# 17. Decision review rules

1. Every open question must be resolved by its gate.
2. Every provisional technology decision requires an ADR.
3. Legal/privacy decisions require review under the final deployment context.
4. Performance-derived values require test evidence.
5. A decision change must record:
   - Previous decision
   - New decision
   - Reason
   - Affected requirements and specifications
   - Migration or documentation impact
6. Rejected alternatives remain in the register to preserve architectural reasoning.
7. Implementation cannot begin while a release-critical question has passed its gate unresolved.
8. Open questions that do not affect the initial release may be explicitly deferred.
9. “We will decide during implementation” is not an acceptable resolution for a foundational decision.
10. Approval of this register does not automatically approve future ADRs.

---

# 18. Current decision summary

| Status | Approximate count |
|---|---:|
| Approved | 120+ |
| Provisional | 17 |
| Deferred | 5 |
| Rejected/superseded | 25 |
| Open questions | 60+ |

The quantity of open questions is expected because final architecture, security, cloud, UX and implementation planning have not begun. None invalidates the approved product/domain foundation, but all release-critical questions must be resolved before implementation readiness.

---

# 19. Approval effect

Approval of Decision and Open-Question Register v1.0 means:

1. Entries marked `APPROVED` become the foundation baseline.
2. Entries marked `PROVISIONAL` remain preferred but revisable.
3. Deferred and rejected functionality remains outside the initial release.
4. Open questions become tracked planning obligations.
5. Gate deadlines become mandatory readiness conditions.
6. Changes require recorded impact analysis.
7. The next foundation artifact is the **Domain Glossary v1.0**.
</12_decision_and_open_question_register_v1.0.md>
