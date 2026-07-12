Yes. This is the final planning layer before writing Flyway migrations and implementation code. The repository already contains ARC-005/006 and executable-contract folders, but ARC-005 remains conceptual and `IN_REVIEW`; the current lifecycle registry conflicts with approved DOM-002; and the current BFF/internal OpenAPI files do not yet cover the complete W1 journey. ([raw.githubusercontent.com](https://raw.githubusercontent.com/panospao7/Ev-chargind-station/main/docs/05_architecture/05_database_models_ownership_migration_v1.0.md))

Add the following planning artifact:

<16_w1_persistence_and_contract_finalization_v1.0.md>
# W1 Persistence and Executable Contract Finalization v1.0

## Document metadata

- **Document ID:** ARC-022
- **Version:** 1.0
- **Status:** IN_REVIEW
- **Owner:** Backend/Data Architect and Contract Architect
- **Authoritative for:** W1 physical persistence baseline, migration sequence, executable REST/message contracts, registries and cross-artifact validation
- **Refines:** ARC-003, ARC-004, ARC-005, ARC-006, ARC-018, ARC-019, ARC-020
- **Depends on:** DOM-002, DOM-004, DOM-005, GOV-007, ENG-001
- **Does not supersede:** domain lifecycle authority, service boundaries or security architecture

---

# 1. Purpose

This document finalizes persistence and executable contracts for:

- W1-S1 first vertical slice;
- W1-S2 operational completion;
- Booking consistency and charging-session correctness;
- service-owned migrations;
- REST APIs;
- messages and message schemas;
- problem-code, lifecycle, policy and traceability registries.

Persistence and contracts must be designed together:

> No W1 table may exist without an owning capability and use case. No W1 API or message may be approved without an owning service, persistence effect and verification test.

---

# 2. Authority order

When artifacts disagree, use:

1. GOV-001/GOV-007 approved decisions and W1 scope
2. DOM-002/004/005 domain states, policies and invariants
3. ARC-001 service ownership
4. ARC-006 concurrency and locking
5. ARC-022 physical persistence rules
6. ARC-003/019 REST semantics
7. ARC-020 message inventory
8. Executable registries and schemas

A disagreement blocks merging the affected migration or contract.

---

# 3. Corrections required to the current baseline

Before approval:

1. ARC-005 must change from conceptual models to the canonical W1 table catalogue.
2. Replace stale claim kinds:
   - `BOOKING` → `BOOKING_ALLOCATION`
   - `MAINTENANCE` → `MAINTENANCE_BLOCK`
3. Add:
   - `capacity_restriction`
   - `session_attempt`
   - `accepted_device_evidence`
   - retry-safe check-in/authorization structures
4. Remove `ChargingService` as an owner name. Use `Booking and Session Service`.
5. Remove noncanonical Booking states:
   - `PENDING_HOLD`
   - `HOLD_EXPIRED`
6. Use:
   - `HELD`
   - `EXPIRED`
7. Set `VERSION_CONFLICT` to HTTP 412 for failed `If-Match`.
8. Replace the current lifecycle registry with one generated directly from DOM-002.
9. Expand the current Driver BFF contract to include hold, confirmation, viewing, cancellation, history and summary operations.
10. Charging start and stop must return `202 Accepted`, not claim physical completion.
11. Treat current machine-readable contracts as `IN_REVIEW` until every gate in Section 15 passes.

---

# 4. W1 persistence ownership

| Database | W1 authoritative data |
|---|---|
| `account_db` | Account, profile, vehicles, account eligibility |
| `station_operations_db` | Organization, station, EVSE, connector, tariffs, policies, maintenance, faults, assignments |
| `booking_session_db` | Bookings, claims, restrictions, check-ins, authorizations, sessions, attempts, occupation, meter evidence |
| `device_integration_db` | Machine identity, connections, device state, commands, receipts and reconciliation |
| `discovery_insights_db` | Rebuildable public discovery and availability projections |
| `notification_db` | Notification records and delivery attempts |
| `governance_support_db` | W1 audit projection and privileged intervention evidence |
| `bff_session_db` | Opaque application sessions and encrypted server-side OAuth material |
| Keycloak database | Credentials, MFA, verification and identity sessions |

Cross-service database foreign keys, joins and writes remain prohibited.

---

# 5. Booking and Session physical model

## 5.1 `booking`

Required columns:

- `booking_ref uuid` — primary key
- `public_ref varchar(32)` — unique
- `account_ref uuid`
- `vehicle_ref uuid null`
- `organization_ref uuid`
- `station_ref uuid`
- `evse_ref uuid`
- `assignment_mode varchar(24)`
- `required_connector_type varchar(32)`
- `minimum_power_w integer null`
- `state varchar(32)`
- `scheduled_interval tstzrange`
- `allocation_interval tstzrange`
- `hold_expires_at timestamptz null`
- `grace_deadline timestamptz`
- `checked_in_at timestamptz null`
- `activated_at timestamptz null`
- `terminal_at timestamptz null`
- `tariff_snapshot_ref uuid null`
- `policy_snapshot_ref uuid null`
- `outcome_reason_code varchar(64) null`
- `outcome_detail jsonb null`
- `version bigint`
- `created_at timestamptz`
- `updated_at timestamptz`

Permitted states:

- `HELD`
- `CONFIRMED`
- `CHECKED_IN`
- `ACTIVE`
- `COMPLETED`
- `CANCELLED`
- `EXPIRED`
- `NO_SHOW`
- `FULFILMENT_FAILED`
- `DRIVER_ABANDONED`

Required constraints:

- intervals are finite, non-empty and half-open;
- scheduled interval is contained within allocation interval;
- `HELD` requires `hold_expires_at`;
- confirmed-or-later bookings require tariff and policy snapshots;
- terminal states require `terminal_at`;
- `ACTIVE` requires `activated_at`;
- public reference is unique;
- state transitions are enforced by service logic and transition tests.

## 5.2 `booking_transition`

Append-only state history:

- `transition_ref uuid`
- `booking_ref uuid`
- `from_state varchar(32) null`
- `to_state varchar(32)`
- `booking_version bigint`
- `actor_type varchar(32)`
- `actor_ref uuid null`
- `reason_code varchar(64) null`
- `safe_detail jsonb null`
- `occurred_at timestamptz`
- `correlation_id uuid`

Unique:

- `(booking_ref, booking_version)`

Runtime role may insert but not update or delete.

## 5.3 Guard tables

### `evse_allocation_guard`

- `evse_ref uuid` primary key
- `source_configuration_version bigint`
- `allocation_enabled boolean`
- `last_allocated_at timestamptz null`
- `updated_at timestamptz`

### `driver_schedule_guard`

- `account_ref uuid` primary key
- `booking_eligible boolean`
- `source_account_version bigint`
- `updated_at timestamptz`

Missing guard rows cause fail-closed rejection.

## 5.4 `capacity_claim`

Required columns:

- `claim_ref uuid`
- `evse_ref uuid`
- `claim_kind varchar(32)`
- `source_ref uuid`
- `booking_ref uuid null`
- `effective_interval tstzrange`
- `state varchar(16)`
- `hold_expires_at timestamptz null`
- `source_version bigint`
- `released_at timestamptz null`
- `release_reason varchar(64) null`
- timestamps

Kinds:

- `BOOKING_HOLD`
- `BOOKING_ALLOCATION`
- `MAINTENANCE_BLOCK`
- `EMERGENCY_BLOCK`
- `OPERATOR_RESTRICTION`

States:

- `ACTIVE`
- `RELEASED`

Required database protections:

- GiST exclusion on active non-hold claims by EVSE and interval;
- unique active source claim;
- hold expiry required only for `BOOKING_HOLD`;
- half-open, finite intervals;
- active hold/allocation uniqueness for one Booking;
- runtime role cannot disable or alter constraints.

Unexpired holds are protected through the EVSE guard and conflict query because database-time-dependent expiry cannot safely appear in a permanent partial-index predicate.

## 5.5 `capacity_restriction`

Required columns:

- `restriction_ref uuid`
- `evse_ref uuid`
- `restriction_kind varchar(32)`
- `scope_type varchar(32)`
- `scope_ref uuid null`
- `effective_interval tstzrange`
- `source_ref uuid`
- `source_workflow_version bigint`
- `idempotency_key varchar(128)`
- `state varchar(16)`
- `reason_code varchar(64)`
- `authorized_by_ref uuid null`
- `authorized_at timestamptz null`
- `frozen_at timestamptz`
- `blocked_at timestamptz null`
- `released_at timestamptz null`
- `version bigint`
- timestamps

States:

- `FREEZE`
- `BLOCKED`
- `RELEASED`

Rules:

- `FREEZE` blocks new allocations through the guarded restriction query;
- no capacity claim is inserted at `FREEZE`;
- `BLOCKED` requires the matching block claim;
- `RELEASED` requires the block claim to be released;
- emergency restrictions require authorization evidence;
- transitions use optimistic version checks;
- one EVSE restriction row is created for each station/organization fan-out target.

## 5.6 `driver_schedule_claim`

Columns:

- `claim_ref uuid`
- `account_ref uuid`
- `booking_ref uuid`
- `claim_kind varchar(24)`
- `charging_interval tstzrange`
- `state varchar(16)`
- `hold_expires_at timestamptz null`
- timestamps

Kinds:

- `BOOKING_HOLD`
- `BOOKING`

Use a GiST exclusion constraint for active confirmed `BOOKING` claims. Driver holds use the driver guard and database-time conflict query.

## 5.7 Immutable snapshots

### `tariff_snapshot`

- source tariff/version
- currency
- tax rate
- canonical pricing JSON
- schema version
- content hash
- created time

### `policy_snapshot`

- source policy/version
- timing rules
- cancellation rules
- retry policy
- turnaround buffer
- canonical JSON
- schema version
- content hash
- created time

Snapshot rows are immutable. Optional content-hash deduplication may be used.

---

# 6. Check-in and charging persistence

## 6.1 `booking_check_in`

Multiple check-ins are permitted after temporary withdrawal.

Columns:

- `check_in_ref uuid`
- `booking_ref uuid`
- `attempt_number smallint`
- `evse_ref uuid`
- `method varchar(24)`
- `state varchar(24)`
- `checked_in_at timestamptz`
- `ended_at timestamptz null`
- `outcome_reason varchar(64) null`
- `actor_ref uuid`
- timestamps

States:

- `ACTIVE`
- `WITHDRAWN`
- `REVOKED`
- `COMPLETED`

Constraints:

- unique `(booking_ref, attempt_number)`;
- at most one `ACTIVE` check-in per Booking.

## 6.2 `start_authorization`

Columns:

- `authorization_ref uuid`
- `booking_ref uuid`
- `check_in_ref uuid`
- `account_ref uuid`
- `evse_ref uuid`
- `session_ref uuid null`
- `attempt_number smallint`
- `secret_hash bytea`
- `state varchar(16)`
- `issued_at timestamptz`
- `expires_at timestamptz`
- `consumed_at timestamptz null`
- `revoked_at timestamptz null`
- `version bigint`

States:

- `ISSUED`
- `CONSUMED`
- `EXPIRED`
- `REVOKED`

Constraints:

- at most one `ISSUED` authorization per Booking;
- authorization secret is never stored in plaintext;
- one authorization may be consumed by only one SessionAttempt;
- retry authorization expiry never exceeds the original grace deadline.

## 6.3 `charging_session`

Columns:

- `session_ref uuid`
- `public_ref varchar(32)`
- `booking_ref uuid`
- `account_ref uuid`
- `station_ref uuid`
- `evse_ref uuid`
- `state varchar(24)`
- `uncertain boolean`
- `device_transaction_ref varchar(128) null`
- `confirmed_start_at timestamptz null`
- `confirmed_end_at timestamptz null`
- `latest_meter_sequence bigint null`
- `latest_energy_wh bigint`
- `current_power_w integer null`
- `stop_reason varchar(64) null`
- `outcome_code varchar(64) null`
- `version bigint`
- timestamps

Persistent states:

- `STARTING`
- `CHARGING`
- `SUSPENDED`
- `STOPPING`
- `COMPLETED`
- `INTERRUPTED`
- `START_REJECTED`

`FINALIZING` is a processing phase, not a persistent lifecycle state.

Constraints:

- one ChargingSession per Booking;
- only physical transaction-start evidence permits `CHARGING`;
- terminal states require outcome data;
- `START_REJECTED` cannot contain accepted energy.

## 6.4 `session_attempt`

Columns:

- `attempt_ref uuid`
- `session_ref uuid`
- `booking_ref uuid`
- `attempt_number smallint`
- `authorization_ref uuid`
- `command_ref uuid`
- `state varchar(40)`
- `started_at timestamptz`
- `device_accepted_at timestamptz null`
- `timed_out_at timestamptz null`
- `resolved_at timestamptz null`
- `resolved_by_ref uuid null`
- `resolution_evidence jsonb null`
- `outcome_reason varchar(64) null`
- `version bigint`
- timestamps

States:

- `AUTHORIZING`
- `STARTING`
- `DEVICE_ACCEPTED`
- `TIMED_OUT`
- `RECONCILING`
- `ATTEMPT_REJECTED`
- `TRANSACTION_STARTED`
- `UNRESOLVED_REQUIRES_ACTION`

Constraints:

- unique `(session_ref, attempt_number)`;
- unique `authorization_ref`;
- unique `command_ref`;
- at most one unresolved attempt per session;
- unresolved state requires escalation metadata;
- only authorized evidence resolves `UNRESOLVED_REQUIRES_ACTION`.

## 6.5 `operational_occupation`

Columns:

- `occupation_ref uuid`
- `evse_ref uuid`
- `booking_ref uuid`
- `session_ref uuid`
- `attempt_ref uuid`
- `account_ref uuid`
- `state varchar(16)`
- `blocking_interval tstzrange`
- `last_evidence_at timestamptz null`
- `released_at timestamptz null`
- `reconciliation_ref uuid null`
- `version bigint`
- timestamps

States:

- `ACTIVE`
- `UNCERTAIN`
- `RELEASED`

Constraints:

- unique `attempt_ref`;
- at most one unreleased occupation per EVSE;
- lower bound is finite;
- upper bound may be unbounded only for uncertainty;
- physical occupation is not included in planned-capacity exclusion constraints.

## 6.6 Device evidence and meter data

### `accepted_device_evidence`

- `evidence_ref uuid`
- `device_event_ref varchar(128)` unique
- `evidence_type varchar(32)`
- `station_ref uuid`
- `evse_ref uuid`
- `session_ref uuid null`
- `attempt_ref uuid null`
- `device_sequence bigint`
- `observed_at timestamptz`
- `received_at timestamptz`
- `payload_hash varchar(128)`
- `safe_payload jsonb`

### `meter_sample`

- `sample_ref uuid`
- `session_ref uuid`
- `attempt_ref uuid`
- `device_event_ref varchar(128)`
- `meter_sequence bigint`
- `observed_at timestamptz`
- `received_at timestamptz`
- `energy_wh bigint`
- `power_w integer null`
- optional voltage/current
- `validation_state varchar(16)`

Unique:

- `(session_ref, meter_sequence)`
- `device_event_ref`

## 6.7 `session_summary`

One immutable row per terminal Session:

- session/booking references
- confirmed start/end
- duration seconds
- energy Wh
- estimated amount as `numeric`
- currency
- tariff snapshot
- outcome
- stop reason
- calculation version
- summary hash
- finalized time

---

# 7. Reconciliation and projection tables

## 7.1 Booking-owned enforcement projections

Required:

- `account_eligibility_projection`
- `bookable_evse_projection`
- `evse_connector_projection`
- `station_schedule_projection`
- `tariff_version_projection`
- `policy_version_projection`
- `device_operational_projection`
- `fault_impact_projection`
- `projection_checkpoint`

Every projection records:

- source reference;
- source aggregate version;
- effective time;
- received time;
- projection state;
- last reconciliation time.

An older source version cannot replace a newer version.

## 7.2 Workflow tables

Required:

- `booking_fulfilment_risk`
- `reconciliation_workflow`
- `workflow_participant_result`

Workflows persist independently from broker messages.

---

# 8. Common integration persistence

Every message-producing or consuming service owns its own copies.

## 8.1 `outbox_message`

Required fields:

- message ID
- kind
- versioned type
- aggregate type/reference/version
- workflow/correlation/causation IDs
- classification
- payload
- occurred/available/published times
- attempt count
- state
- safe failure category

States:

- `PENDING`
- `PUBLISHED`
- `QUARANTINED`

Unique event-fact protection:

- `(aggregate_type, aggregate_ref, aggregate_version, message_type)`

## 8.2 `inbox_message`

Primary uniqueness:

- `(consumer_name, message_id)`

Fields include:

- message type;
- aggregate version;
- received time;
- completed time;
- processing outcome;
- attempt count;
- safe failure category.

Inbox completion and business effects commit in one transaction.

## 8.3 `idempotency_record`

Unique scope:

- principal/service identity;
- operation;
- target resource;
- idempotency key.

Fields:

- request fingerprint;
- state;
- status code;
- result reference or bounded response;
- created/completed/expiry times.

Retention:

- Booking/session operations: seven days;
- ordinary W1 commands: 24 hours;
- long-running administrative workflows: workflow lifetime.

## 8.4 `audit_event`

Append-only:

- actor;
- calling service;
- action;
- target;
- reason;
- outcome;
- safe before/after summaries;
- correlation;
- classification;
- occurrence time.

Runtime role may insert but not update/delete.

---

# 9. Other W1 service tables

## Account Service

W1-S1:

- `application_account`
- `driver_profile`
- `vehicle_profile`
- common integration/audit tables

W1-S2:

- `notification_preference`
- account restriction evidence

## Station Operations Service

W1-S1:

- `operator_organization`
- `organization_member`
- `station`
- `station_opening_period`
- `station_schedule_exception`
- `evse`
- `connector`
- `tariff`
- `tariff_version`
- `tariff_component`
- `booking_policy`
- `booking_policy_version`
- `simulator_assignment`

W1-S2:

- `maintenance`
- `fault_report`
- `fault_incident`
- `status_override`

## Device Integration Service

- `machine_identity`
- `simulator_assignment_projection`
- `device_connection`
- `station_heartbeat`
- `evse_reported_state`
- `device_command`
- `device_command_result`
- `device_message_receipt`
- `device_station_sequence`
- `device_session_sequence`
- `device_event_quarantine`
- `device_reconciliation`

## Discovery and Insights Service

- `station_search_projection`
- `evse_search_projection`
- `connector_search_projection`
- `advisory_availability_projection`
- `projection_checkpoint`

No account, driver or vehicle identifiers are stored in W1 discovery projections.

## Notification Service

- `notification`
- `notification_delivery_attempt`
- `notification_template`
- `provider_webhook_inbox`
- common integration/audit tables

---

# 10. Index and constraint baseline

Required indexes include:

- upcoming bookings by account/start;
- booking history by account/terminal time;
- bookings by EVSE/interval;
- holds by expiry;
- no-show candidates by state/grace deadline;
- active sessions by EVSE/account;
- attempts by session/attempt number;
- unreconciled attempts by deadline;
- meter samples by session/sequence;
- capacity claims through GiST;
- active restrictions by EVSE/interval;
- unreleased occupations by EVSE;
- pending outbox by `available_at`;
- idempotency expiry;
- projection source version;
- workflow state/deadline.

Every constraint and index has an explicit stable name.

Database constraint names map to stable problem codes; raw PostgreSQL messages are never returned.

---

# 11. Lock and transaction procedures

Global lock order:

1. Driver guards in ascending UUID order
2. EVSE guards in ascending UUID order
3. Bookings in ascending UUID order
4. Sessions and attempts
5. Restrictions and claims
6. Supporting workflow rows

Rules:

- isolation level: `READ COMMITTED`;
- obtain one database time per correctness transaction;
- no network or broker call while locks are held;
- revalidate all projections after acquiring locks;
- retry the complete transaction after deadlock/serialization failure;
- bounded lock timeout;
- constraint violation after locking maps to a concurrency conflict and emits diagnostics.

Required procedure specifications:

- exact-EVSE hold;
- automatic assignment;
- confirmation;
- expiry;
- cancellation;
- atomic rescheduling;
- check-in;
- start/retry;
- accepted device start;
- device rejection;
- timeout/reconciliation;
- stop/session end;
- maintenance freeze/block/release;
- emergency block;
- occupation overrun.

---

# 12. Migration sequence

Each service owns independent Flyway migrations.

Booking and Session initial sequence:

```text
V001__extensions_and_schema_baseline.sql
V002__integration_idempotency_and_audit.sql
V003__enforcement_projections_and_guards.sql
V004__booking_snapshots_and_history.sql
V005__capacity_restrictions_and_schedule_claims.sql
V006__check_in_authorization_session_and_attempt.sql
V007__occupation_device_evidence_and_metering.sql
V008__summary_risk_and_reconciliation.sql
V009__constraints_partial_indexes_and_gist.sql
V010__w1_reference_data.sql
```

Rules:

- applied migrations are immutable;
- corrections use new migrations;
- no automatic production down migrations;
- runtime roles have no DDL rights;
- extension and constraint validation is part of readiness;
- migration tests run against the selected PostgreSQL version;
- migrations must work from empty database and from previous supported schema;
- migration checksums are verified in CI.

---

# 13. Seed and reset strategy

## Seed data

Use a versioned seed manifest and service-owned bootstrap runners.

Seed:

- test identities in Keycloak;
- one operator organization;
- two Greek stations;
- at least four EVSEs;
- multiple connector/power combinations;
- opening hours;
- tariffs and policies;
- simulator assignments.

Use predefined deterministic references.

Business seed data must pass normal domain validation and produce normal outbox events.

## Reset

Local full reset:

1. stop applications;
2. purge broker queues;
3. recreate local service databases/volumes;
4. run migrations;
5. import Keycloak realm;
6. execute service bootstrap runners;
7. wait for projections;
8. verify expected seed hash.

Production builds must not contain reset endpoints.

---

# 14. W1 executable REST contracts

## Public Discovery API

Required operations:

- `GET /api/v1/stations`
- `GET /api/v1/stations/{stationRef}`
- `GET /api/v1/stations/{stationRef}/availability`

## Driver BFF API

Required W1-S1 operations:

- `GET /api/v1/session`
- `POST /api/v1/session/logout`
- `GET /api/v1/me`
- `PATCH /api/v1/me`
- vehicle list/create/update/delete
- `POST /api/v1/bookings/holds`
- `POST /api/v1/bookings/{bookingRef}/confirm`
- `GET /api/v1/bookings/{bookingRef}`
- `GET /api/v1/bookings/upcoming`
- `GET /api/v1/bookings/history`
- `POST /api/v1/bookings/{bookingRef}/cancel`
- `POST /api/v1/bookings/{bookingRef}/check-in`
- `POST /api/v1/bookings/{bookingRef}/abandon-check-in`
- `POST /api/v1/bookings/{bookingRef}/charging-session/start`
- `GET /api/v1/bookings/{bookingRef}/charging-session`
- `POST /api/v1/bookings/{bookingRef}/charging-session/stop`
- `GET /api/v1/bookings/{bookingRef}/charging-session/summary`

W1-S2 additionally includes rescheduling and operator-management APIs.

## Response rules

- hold creation: `201`;
- confirmation/cancellation/check-in: `200`;
- start/stop: `202`;
- asynchronous response includes operation, command and attempt references;
- start/stop never claim physical completion;
- mutations require `Idempotency-Key`;
- versioned administrative mutation requires `If-Match`;
- BFF APIs use opaque session cookie and CSRF protection;
- internal APIs use audience-specific service tokens.

Every operation defines:

- unique `operationId`;
- `x-release-wave`;
- `x-slice-applicability`;
- requirement IDs;
- authoritative owner;
- authorization rule;
- idempotency rule;
- request/response examples;
- all applicable Problem Details responses.

---

# 15. AsyncAPI and message schemas

## Canonical envelope

Use CloudEvents-compatible structured JSON with:

- ID;
- source;
- versioned type;
- subject;
- occurrence time;
- data schema;
- correlation/causation IDs;
- trace context;
- aggregate reference/version;
- classification;
- payload.

## W1 commands

- `StartChargingAtEVSE`
- `StopChargingAtEVSE`
- `RequestDeviceSnapshot`
- `CreateCapacityFreeze`
- `FinalizeCapacityBlock`
- `ReleaseCapacityRestriction`

Each command has exactly one logical handler and defined accepted, rejected, timed-out and unresolved outcomes.

## W1 booking/session events

- `BookingHeld`
- `BookingConfirmed`
- `BookingHoldExpired`
- `BookingCancelled`
- `BookingCheckedIn`
- `BookingCheckInAbandoned`
- `BookingActivated`
- `BookingCompleted`
- `BookingNoShow`
- `BookingFulfilmentFailed`
- `ChargingSessionStarted`
- `ChargingSessionProgressed`
- `ChargingSessionCompleted`
- `ChargingSessionInterrupted`
- `ChargingStartRejected`
- `ChargingOutcomeUnresolved`

## Capacity projection events

- `EVSECapacityChanged`
- `StationBookableCountChanged`
- `AvailabilityProjectionInvalidated`

These contain no account or driver reference.

## Restriction outcomes

- `CapacityFreezeCommitted`
- `CapacityFreezeRejected`
- `CapacityBlockCommitted`
- `CapacityBlockRejected`
- `CapacityRestrictionReleased`
- `CapacityRestrictionReleaseRejected`

## Device facts

- `DeviceCommandResult`
- `DeviceTransactionStarted`
- `DeviceTransactionProgressed`
- `DeviceTransactionEnded`
- `DeviceHeartbeatReceived`
- `DeviceHeartbeatStale`
- `DeviceFaultRaised`

## Schema rule

Canonical event/command payload schemas use JSON Schema 2020-12.

Because OpenAPI 3.0.3 Schema Objects and standalone JSON Schema 2020-12 are different dialects:

- OpenAPI models remain OpenAPI Schema Objects;
- message schemas remain standalone JSON Schema 2020-12;
- direct cross-dialect reuse is prohibited;
- shared conceptual models are generated from an approved source or maintained with equivalence tests;
- W1 message schemas use a compatibility-safe subset;
- the AsyncAPI 2.6 toolchain must prove every external schema reference and selected schema format.

---

# 16. Executable registries

## `lifecycles-v1.yaml`

Must reproduce DOM-002 exactly.

It must not contain:

- `ChargingService`
- `PENDING_HOLD`
- `HOLD_EXPIRED`
- `ACTIVE → CANCELLED` for Booking

Every lifecycle contains:

- aggregate;
- owning service/module;
- persistent states;
- processing-only phases;
- terminal/quasi-terminal flags;
- transitions;
- guards;
- integration facts.

## `problem-codes-v1.yaml`

Canonical key statuses:

- `VERSION_CONFLICT` — 412
- `PRECONDITION_REQUIRED` — 428
- `IDEMPOTENCY_KEY_REUSED` — 409
- `EVSE_ALLOCATION_CONFLICT` — 409
- `ALLOCATION_BUSY` — 409, retryable
- `NO_COMPATIBLE_EVSE` — 422
- `INVALID_INTERVAL` — 422
- `BOOKING_HOLD_EXPIRED` — 409
- `BOOKING_STATE_CONFLICT` — 409
- `CHECK_IN_WINDOW_CLOSED` — 409
- `WRONG_EVSE` — 409
- `EVSE_STALE_TELEMETRY` — 503
- `EVSE_OFFLINE` — 503
- `STATUS_UNKNOWN` — 503
- `MAINTENANCE_CONFLICT` — 409
- `SESSION_STATE_CONFLICT` — 409
- `START_AUTHORIZATION_EXPIRED` — 409
- `START_AUTHORIZATION_INVALID` — 403
- `START_AUTHORIZATION_CONSUMED` — 409
- `START_ATTEMPT_LIMIT_REACHED` — 409
- `START_RETRY_NOT_ELIGIBLE` — 409
- `SESSION_OUTCOME_UNRESOLVED` — 409

Asynchronous uncertain outcomes are represented in operation/session status, not falsely returned as successful physical completion.

## Other registries

Required:

- `messages-v1.yaml`
- `policies-v1.yaml`
- `traceability-v1.yaml`

Every W1 requirement must trace to:

- API operation or message;
- owning service;
- database tables;
- security rule;
- test;
- implementation epic.

---

# 17. Contract fixtures and tests

Every operation/message requires:

- one valid example;
- malformed example;
- semantically invalid example;
- authorization failure;
- idempotency retry;
- changed-payload idempotency failure;
- documented problem response;
- data-minimization assertion;
- provider test;
- consumer test where applicable.

Critical cross-persistence tests:

1. Concurrent exact-EVSE holds produce one winner.
2. Hold and idempotency result commit together.
3. Confirmation after expiry fails.
4. Cancellation releases both claim types.
5. Check-in and no-show race has one valid result.
6. Duplicate start creates one SessionAttempt.
7. Rejected retry creates one new authorization and attempt.
8. Unresolved attempt blocks retry and capacity.
9. Freeze prevents new allocations.
10. Outbox failure does not reverse a committed business operation.
11. Duplicate inbox delivery has one effect.
12. Discovery messages contain no subject identifiers.

---

# 18. Work packages

| ID | Work package |
|---|---|
| DATA-001 | Patch ARC-005 to canonical table names and ownership |
| DATA-002 | Finalize Booking and Session physical schema |
| DATA-003 | Finalize W1 schemas for other services |
| DATA-004 | Finalize indexes, constraints and roles |
| DATA-005 | Define Flyway migration sequence |
| DATA-006 | Define seed/reset manifest |
| API-001 | Rebuild Driver BFF OpenAPI |
| API-002 | Complete public discovery OpenAPI |
| API-003 | Complete internal service APIs |
| MSG-001 | Finalize canonical message registry |
| MSG-002 | Complete AsyncAPI channels and operations |
| SCH-001 | Complete JSON Schemas and fixtures |
| REG-001 | Replace lifecycle registry |
| REG-002 | Normalize problem-code registry |
| REG-003 | Complete traceability/policy registries |
| TEST-001 | Add migration and constraint tests |
| TEST-002 | Add provider/consumer contract tests |
| TEST-003 | Add concurrency and outbox/inbox tests |
| GOV-001 | Record evidence and approve ARC-022 |

---

# 19. Definition of done

ARC-022 is approved only when:

1. ARC-005 and ARC-006 use identical table and claim names.
2. Every W1 table has one owner.
3. Every W1 aggregate has a persistence model.
4. Empty and upgrade migrations succeed.
5. Runtime roles cannot execute DDL.
6. Required PostgreSQL constraints are present and validated.
7. All mandatory concurrency tests pass.
8. OpenAPI documents validate and contain the complete W1 operation set.
9. AsyncAPI and every referenced schema validate.
10. Lifecycle registry exactly matches DOM-002.
11. Problem codes have one HTTP meaning.
12. Every message is registered once.
13. Every W1 requirement has persistence/API/message/test traceability.
14. Seed/reset is deterministic.
15. Contract examples pass schema validation.
16. Security and privacy checks reject subject identifiers in Discovery.
17. CI is green from a clean checkout.
18. Governance records immutable verification evidence.

---

# 20. Recommended commit sequence

1. `docs: add ARC-022 W1 persistence and contract baseline`
2. `docs: reconcile ARC-005 and ARC-006 physical models`
3. `contracts: replace lifecycle and problem-code registries`
4. `contracts: complete W1 OpenAPI operation surfaces`
5. `contracts: complete message registry AsyncAPI and schemas`
6. `data: add W1 Flyway migrations and seed manifest`
7. `test: verify migrations constraints concurrency and contracts`
8. `governance: approve ARC-022 after green validation`

Business-feature implementation may begin only after commits 1–5 establish stable persistence and contract meaning. Migrations and implementation may then proceed incrementally behind those approved contracts.
</16_w1_persistence_and_contract_finalization_v1.0.md>

The first concrete action should be **patching ARC-005 and replacing `lifecycles-v1.yaml`**, because migrations and APIs cannot safely be generated while the authoritative lifecycle and physical table names disagree.