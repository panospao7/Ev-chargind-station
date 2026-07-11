## Consolidated Functional Requirements Catalogue v1.0

Keywords: **MUST** = release-critical; **SHOULD** = desirable but deferrable.

### Identity and access
- **FR-IAM-01 MUST:** Register, verify, authenticate, recover, suspend and delete accounts.
- **FR-IAM-02 MUST:** Enforce MFA for operator/admin roles.
- **FR-IAM-03 MUST:** Apply role, organization and resource-ownership authorization.
- **FR-IAM-04 SHOULD:** Manage saved vehicles, compatibility and active sessions.

### Discovery and availability
- **FR-DIS-01 MUST:** Publicly browse, search and filter Greek stations.
- **FR-DIS-02 MUST:** Show station, EVSE, connector, tariff and status details.
- **FR-AVL-01 MUST:** Derive interval availability from bookings, sessions, maintenance, hours, compatibility and status freshness.
- **FR-AVL-02 MUST:** Clearly distinguish live, stale and unknown information.

### Bookings
- **FR-BKG-01 MUST:** Hold and confirm a compatible EVSE atomically.
- **FR-BKG-02 MUST:** Prevent overlapping allocations under concurrency.
- **FR-BKG-03 MUST:** Reschedule atomically without losing the original booking.
- **FR-BKG-04 MUST:** Cancel, expire and process no-shows according to policy.
- **FR-BKG-05 MUST:** Support QR/identifier check-in and single-use start authorization.
- **FR-BKG-06 MUST:** Preserve tariff and policy snapshots.

### Charging
- **FR-CHG-01 MUST:** Start, monitor, stop, interrupt and reconcile simulated sessions.
- **FR-CHG-02 MUST:** Process duplicate/out-of-order meter events safely.
- **FR-CHG-03 MUST:** Produce reproducible session summaries and cost estimates.

### Operator and infrastructure
- **FR-OPS-01 MUST:** Manage organizations, staff, stations, EVSEs, connectors, tariffs and policies.
- **FR-OPS-02 MUST:** Manage maintenance, faults, overrides and reassignment.
- **FR-OPS-03 SHOULD:** Provide operational analytics and exports.

### Administration and simulation
- **FR-ADM-01 MUST:** Support operator approval, suspension, moderation and audited interventions.
- **FR-SUP-01 MUST:** Manage scoped support cases with masked personal data.
- **FR-SIM-01 MUST:** Simulate authenticated chargers, commands, telemetry and failures.

### Platform processes
- **FR-PLT-01 MUST:** Use idempotent events, outbox delivery, retries, dead-letter handling and reconciliation.
- **FR-NOT-01 MUST:** Send essential transactional email.
- **FR-PRV-01 MUST:** Support data export, deletion, retention and anonymization.
- **FR-AUD-01 MUST:** Record immutable security and business audit events.

## Traceability summary

| Requirement groups | Approved sources | Primary verification |
|---|---|---|
| IAM | DR-06–10, permission model | Security/API tests |
| DIS/AVL | DR-01–05, availability model | Search and interval tests |
| BKG | DR-11–16, booking lifecycle | Concurrency/state tests |
| CHG | DR-17–20 | Simulator/reconciliation tests |
| OPS | OP-01–25 | Ownership/workflow tests |
| ADM/SUP | AD-01–09, SUP-01–03 | Authorization/audit tests |
| SIM/PLT | Simulator and consistency specs | Contract/resilience tests |
| NOT/PRV/AUD | Privacy/failure specification | Integration/compliance tests |

Every MUST requirement will later map to API contracts, events, data ownership, backlog items and automated acceptance tests.

**Next:** measurable **Non-Functional Requirements v1.0** covering performance, availability, security, accessibility, privacy, scalability, resilience and operability.