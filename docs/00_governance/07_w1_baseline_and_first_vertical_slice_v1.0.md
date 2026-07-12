Document ID: GOV-007
Title: Release 1 W1 Baseline and First Vertical Slice v1.0
Version: 1.0
Status: APPROVED
Owner: PO/DA
Last reviewed: 2026-07-12
Supersedes: None
Depends on: GOV-001, GOV-004, REQ-001, REQ-002, ARC-001, DOM-004, DOM-005
Authoritative for: W1 scope, first vertical slice, release applicability and W1 readiness boundaries

---

# Release 1 W1 Baseline and First Vertical Slice

## 1. Purpose

This document freezes the implementation order without changing the approved logical architecture.

Three concepts are distinct:

1. **Target platform scope** — the complete system described by the requirements.
2. **Implementation wave** — W1, W2 or W3.
3. **Vertical slice** — a demonstrable end-to-end path implemented first.

A requirement may remain `MUST` for the completed target platform while being assigned to W2 or W3.

## 2. W1 definition

W1 consists of:

- W1-S1: first complete vertical slice.
- W1-S2: remaining capabilities required for the W1 release.

No new service boundary, database ownership model or consistency model may be introduced during W1 unless implementation evidence identifies a genuine correctness defect.

## 3. W1-S1 first vertical slice

The first slice is:

> Seed station → public discovery → inspect interval availability → authenticate → create EVSE hold → confirm booking → view booking → cancel booking → check in → start simulated charging → monitor → stop → view session summary.

### S1-01 — Repeatable infrastructure seed

Provide a deterministic, service-owned bootstrap/reset mechanism containing:

- one active operator organization;
- at least two published Greek stations;
- at least two EVSEs per station;
- at least two connector/power combinations;
- opening hours;
- one active tariff version;
- one active booking-policy version;
- simulator assignment;
- driver, operator and administrator test identities;
- repeatable reset without cross-service database writes.

### S1-02 — Public station discovery

Support:

- accessible map and list views;
- geographic/map-bounds search;
- connector and power filters;
- station details;
- EVSE and connector details;
- tariff and opening-hours display;
- operational freshness display;
- Greek and English UI labels.

Map tiles and geocoding must be accessed through replaceable adapters. Routing is not part of S1.

### S1-03 — Interval availability

Support a requested interval using:

- half-open intervals `[start, end)`;
- 15-minute increments;
- 5-minute hold duration;
- 15-minute minimum duration;
- 4-hour maximum duration;
- 14-day advance-booking limit;
- 60-minute near-term horizon;
- 300-second freshness threshold;
- `AVAILABLE`, `PLANNED_AVAILABLE`, `UNAVAILABLE`, `UNKNOWN` and `INCOMPATIBLE`.

Search availability is advisory. Booking revalidates all rules authoritatively.

### S1-04 — Driver authentication

Support:

- registration;
- email verification;
- login/logout;
- recovery;
- verified active account enforcement;
- opaque BFF session cookie;
- CSRF protection;
- no browser-held service access token;
- server-side audience-limited service tokens.

Privileged test accounts use MFA.

### S1-05 — Hold and booking

Support both:

- automatic compatible EVSE assignment;
- explicit EVSE selection.

A booking operation must:

- be idempotent;
- use authoritative database time;
- create an exclusive `BOOKING_HOLD`;
- prevent driver and EVSE interval conflicts;
- use Booking-local enforcement projections;
- create audit and outbox records in the same transaction;
- confirm before expiry;
- snapshot tariff and effective booking policy;
- return a safe conflict result under concurrency.

### S1-06 — Booking management

Support:

- upcoming booking details;
- permitted-action calculation;
- cancellation;
- immediate release of unused capacity;
- booking history;
- clear display of uncertain or interrupted outcomes.

Rescheduling is W1-S2, not part of the first slice.

### S1-07 — Check-in and authorization

Support:

- QR/public EVSE identifier or manual identifier;
- check-in opening 15 minutes before start;
- 15-minute late-arrival grace;
- server-side EVSE verification;
- one single-use start authorization;
- authorization bound to booking, driver, EVSE and intended session;
- no authorization secret in QR codes, browser responses, logs or simulator messages.

### S1-08 — Simulated charging

Support the happy path:

1. check-in;
2. start intent;
3. `StartChargingAtEVSE` command;
4. simulator acceptance;
5. `DeviceTransactionStarted`;
6. meter updates;
7. monitoring;
8. stop intent;
9. `DeviceTransactionEnded`;
10. reproducible session summary.

The summary must include:

- duration;
- energy;
- tariff snapshot;
- estimated cost;
- stop reason;
- completion outcome.

S1 must also demonstrate:

- duplicate start safety;
- explicit device rejection;
- timeout/disconnection remaining uncertain;
- no false success from command acceptance alone;
- no equipment failure classified as `NO_SHOW`.

### S1-09 — Messaging and projections

Implement:

- transactional outbox;
- idempotent inbox;
- at-least-once delivery;
- Booking-to-Discovery capacity projection;
- version/sequence validation;
- bounded retry;
- dead-letter/quarantine handling;
- projection rebuild/reset procedure.

Discovery must receive capacity projection data, not driver or account identity.

### S1-10 — Notifications and audit

S1 requires:

- asynchronous booking confirmation email;
- asynchronous cancellation email;
- security/account email through the approved identity flow;
- local mail catcher for development and test;
- immutable business/security audit evidence;
- notification failure never reversing a committed booking.

External production email-provider selection remains separate.

### S1-11 — Operational foundation

S1 must run:

- locally through Docker Compose;
- with PostgreSQL, RabbitMQ, Keycloak, mail catcher and simulator;
- with health/readiness endpoints;
- with structured logs;
- with correlation IDs;
- with metrics and traces for booking and charging workflows;
- in a demonstrator cloud deployment.

The cloud topology is provisional and must not claim production high availability yet.

## 4. W1-S2 completion scope

After S1 is stable, complete the remaining W1 capabilities:

- atomic rescheduling;
- basic operator station/EVSE/connector/tariff/policy management;
- maintenance, fault and override workflows;
- operator booking intervention;
- basic utilization and cancellation analytics;
- administrator suspension/reference-data controls;
- richer notification templates;
- full retry/quarantine/replay operations;
- broader deterministic simulator failure scenarios;
- privileged-action audit;
- backup and restore smoke testing;
- expanded concurrency and resilience testing.

## 5. Explicitly outside S1

These remain target-platform capabilities but are not first-slice work:

### W2

- full operator application approval workflow;
- automated staff invitations;
- support cases;
- notification preferences;
- advanced operator workflows;
- device reservation mirror.

### W3

- privacy export coordination;
- account deletion coordination;
- retention enforcement;
- advanced OCPP-inspired replay;
- SMS, push and marketing notifications;
- advanced disaster recovery.

### Not included in S1

- payments;
- real charger hardware;
- OCPP compliance claims;
- native mobile applications;
- routing;
- direct browser-to-simulator communication;
- multi-region production HA.

## 6. Status ledger

| Topic | Status | W1 treatment |
|---|---|---|
| Seven-service logical topology | APPROVED | Do not redesign |
| Booking and Session combined boundary | APPROVED | Keep together |
| PostgreSQL/Flyway | APPROVED | Use for authoritative data |
| RabbitMQ | APPROVED | Use for integration commands/events |
| Keycloak/BFF direction | APPROVED | Complete implementation PoC |
| OpenAPI 3.0.3 | APPROVED | Generate executable contracts |
| AsyncAPI 2.6.0 | APPROVED | Generate executable contracts |
| JSON Schema 2020-12 | APPROVED | Use for messages |
| MapLibre | APPROVED | Use replaceable provider adapter |
| Map tile/geocoding provider | OPEN | Seed coordinates; no routing in S1 |
| External email provider | OPEN | Use local catcher/test SMTP in S1 |
| Hetzner/Nuremberg deployment | PROVISIONAL | W1 demonstrator baseline |
| Kubernetes versus simpler deployment | OPEN | Docker Compose/container deployment first |
| Frontend realtime mechanism | PROVISIONAL | Use HTTP polling in S1 |
| Device certificate authentication | PROVISIONAL | Local restricted credential profile; target certificate profile later |
| Device reservation mirror | DEFERRED | W2; 60-minute horizon retained when implemented |
| Replay/deduplication retention | PROVISIONAL | Ordinary commands 24h; booking/session 7d; workflow lifetime for administrative/privacy actions |
| Retention/legal/controller decisions | OPEN/PROVISIONAL | W3/G5/G6; do not claim legal compliance |
| DPIA-style assessment | OPEN | Required before final readiness, not S1 coding |
| Real provider budgets/alerts | OPEN | Required before production-like G5 approval |

## 7. W1 blocking rule

No `OPEN` decision blocks S1 unless it affects:

- booking correctness;
- authentication/authorization;
- data ownership;
- executable API/message meaning;
- local reproducibility;
- security of test credentials;
- cloud deployment of the demonstrator.

Provider, legal, routing, advanced privacy and HA questions are tracked openly but do not block local S1 implementation.

## 8. Required repository patches

### GOV-004

Replace the current Release 1 list with:

- W1-S1 first vertical slice;
- W1-S2 completion scope;
- W2 and W3 deferrals.

Explicitly add check-in and simulated charging to W1.

### REQ-001

Add fields to every requirement:

- `targetPriority`;
- `releaseWave`;
- `sliceApplicability`;
- `authoritativeOwner`;
- `verification`;
- `implementationEpic`.

Set:

- booking/check-in/charging requirements to W1;
- full operator applications and invitations to W2;
- privacy workflows to W3;
- rescheduling to W1-S2.

### GOV-001

Add decisions:

- `DEC-W1-01` — approve W1-S1 scope;
- `DEC-W1-02` — approve W1-S2 completion scope;
- `DEC-W1-03` — use polling provisionally for S1;
- `DEC-W1-04` — use local mail catcher/test SMTP until provider selection;
- `DEC-W1-05` — use Hetzner as provisional demonstrator baseline;
- `DEC-W1-06` — defer reservation mirror to W2;
- `DEC-W1-07` — no W1-critical open question may remain unresolved.

### GOV-006

Contradiction entries created and resolved during correction cycles 5-7:

- `CON-175`: roadmap omitted the core charging journey from W1 — resolved via GOV-004/W1-S1 scope and GOV-007 §3 S1-08.
- `CON-176`: full operator application capability was labelled W1 in REQ-001 but W2 in GOV-004 — resolved via W1-S2/W2 split: OPS-01 tagged W1-S2, operator-application sub-features move to W2.
- `CON-177`: device reservation mirror was described as W1 in some requirements and W2 in ARC-018 — resolved via W1-S1/S2/W2 allocation per GOV-007 §4.

GAP-001 (maintenance API scope) and GAP-002 (retry authorization) are VERIFIED and closed.

## 9. Approval record

GOV-007 was accepted on 2026-07-12. All approval criteria met:

1. ✅ The S1 journey is accepted exactly as written.
2. ✅ W1-S2 is explicitly separated from S1.
3. ✅ All W1 requirements have a wave and slice tag (W1-S1/W1-S2/W2/W3 in REQ-001).
4. ✅ No W1-critical decision remains `OPEN`.
5. ✅ Provisional decisions have an owner and validation gate.
6. ✅ W2/W3 capabilities remain documented rather than silently deleted.
7. ✅ The roadmap (GOV-004), requirements (REQ-001) and decision register (GOV-001) contain the same W1 scope.
8. ✅ G3 executable contracts remain a separate next gate.

## 10. Immediate next action

GOV-004, REQ-001 and GOV-001 have been patched with GOV-007 scope. The patches are:

- GOV-004 §3: W1 split into S1+S2 per GOV-007 §3-4.
- REQ-001 §4: Wave tags updated to W1-S1/W1-S2 per GOV-007 §3.
- GOV-001 §16b: W1 planning decisions (DEC-W1-01 through DEC-W1-27) recorded.

**Correction required (2026-07-12 review):** G3 executable CI validation at 27091c7 failed with 15 errors and 14 warnings. CON-175 and CON-176 opened; contract-dependent VERIFIED rows returned to PATCHED. G3-executable status corrected to `IN_REVIEW — VALIDATION_FAILED`.

Remaining work after baseline approval:

1. Fix G3 CI validation (OpenAPI, AsyncAPI, JSON Schema, registries, docs, security).
2. Add ENG-001 local engineering foundation.
3. Implement ENG-001 repository skeleton.
4. Create the W1 traceability table.
5. Begin persistence design and executable contract generation.
6. Do not start feature implementation until G3 CI is green and W1 persistence/API contract gate is approved.
