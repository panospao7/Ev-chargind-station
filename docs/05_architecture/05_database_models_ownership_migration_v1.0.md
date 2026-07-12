Document ID: ARC-005  
Title: Database Models, Ownership and Migration Strategy  
Version: 1.0  
Status: IN_REVIEW  
Owner: Backend / Data Architect  
Last reviewed: 2026-07-11  
Depends on: ARC-001, ARC-002, ARC-003, ARC-004, DOM-002, PRV-001  
Authoritative for: Logical database ownership, principal tables, identifiers, persistence constraints, indexing, retention implementation and schema migration governance  

# Database Models, Ownership and Migration Strategy v1.0

## 1. Purpose

This document defines:

- Logical database ownership by service
- Principal relational models and aggregate boundaries
- Cross-service reference rules
- Identifier and timestamp conventions
- Capacity and operational-occupation records
- Outbox, inbox and idempotency persistence
- Audit and privacy records
- Indexing and partitioning principles
- Schema and data migration governance
- Backward-compatible deployment patterns
- Retention, backup and restoration considerations

It does not finalize:

- The exact double-booking transaction algorithm
- PostgreSQL deployment topology
- Cloud backup technology
- Encryption-key infrastructure
- ORM mappings
- Physical partition counts
- Final retention periods

Those decisions are completed in later architecture phases.

---

## 2. Technology posture

PostgreSQL remains the preferred relational database technology, pending the final technology ADR.

Reasons include:

- Transactional consistency
- Native range types
- Exclusion constraints
- JSON support
- Mature indexing
- Declarative partitioning
- Native UUID support
- Strong migration-tool support

PostgreSQL range types and GiST exclusion constraints can enforce non-overlapping intervals. PostgreSQL 18 also introduces `WITHOUT OVERLAPS` temporal constraints, but this design does not require PostgreSQL 18 until the deployed version is approved. ([postgresql.org](https://www.postgresql.org/docs/current/rangetypes.html?utm_source=openai))

Flyway is the preferred migration tool for the Spring Boot services, pending final technology approval. Flyway tracks ordered versioned migrations in a schema-history table and supports repeatable migrations for objects such as views or functions. ([documentation.red-gate.com](https://documentation.red-gate.com/flyway/flyway-concepts/migrations?utm_source=openai))

---

## 3. Database ownership principles

1. Every table has one owning service.
2. Only the owning service may write its tables.
3. Services do not query another service’s database.
4. Cross-service foreign keys are prohibited.
5. Cross-service joins are prohibited.
6. Every service owns its migrations.
7. Every service uses separate database credentials.
8. Shared physical infrastructure does not imply shared ownership.
9. Projections are explicitly non-authoritative.
10. Replicated source data records its source version and update time.
11. Historical facts are preserved according to lifecycle and retention policy.
12. Database constraints enforce release-critical invariants where practical.
13. Application validation does not replace datastore constraints.
14. Database constraints do not replace service authorization.
15. Business deadlines use a database-sourced instant.

---

## 4. Logical database topology

Recommended logical databases:

| Logical database | Owner |
|---|---|
| `account_db` | Account Service |
| `station_operations_db` | Station Operations Service |
| `booking_session_db` | Booking and Session Service |
| `device_integration_db` | Device Integration Service |
| `discovery_insights_db` | Discovery and Insights Service |
| `notification_db` | Notification Service |
| `governance_support_db` | Platform Governance and Support Service |

The Identity Provider owns its own database and schema.

## 4.1 Physical deployment options

### Preferred isolation

One PostgreSQL cluster may host separate databases, users and migration histories for each service.

### Low-cost fallback

One physical database may contain service-owned schemas when provider limitations justify it.

The fallback still requires:

- One schema owner per service
- One runtime role per service
- No cross-schema grants
- No cross-schema SQL
- Independent migration locations
- Architecture tests detecting prohibited access

Separate databases are preferred because they make accidental cross-service joins and writes more difficult.

---

## 5. Database roles

Each logical database should define:

| Role | Purpose |
|---|---|
| `<service>_owner` | Owns database objects; not used by application |
| `<service>_migrator` | Applies approved migrations |
| `<service>_runtime` | Application DML with no DDL rights |
| `<service>_readonly_ops` | Restricted operational diagnostics |
| `<service>_retention` | Executes approved retention actions where separated |

Rules:

- Runtime credentials cannot alter schemas.
- Migration credentials are unavailable to ordinary application instances.
- Read-only access excludes secret and privacy-restricted columns where possible.
- Database superuser credentials are never used by services.
- Audit deletion requires a separately authorized retention path.

---

## 6. Identifier model

## 6.1 Entity reference

Every authoritative entity uses a UUID `entity_ref` as its primary key and stable integration reference.

Rules:

- Generated by the authoritative owner.
- Globally unique.
- Never reassigned.
- May appear in internal APIs and messages.
- Must not be placed in public browser URLs unless explicitly approved.
- Carries no authorization meaning.

UUIDv7 is preferred for newly generated entity references because it is time ordered, but UUIDv4 remains acceptable where UUIDv7 support is unavailable. PostgreSQL 18 provides native UUIDv4 and UUIDv7 generation; application-side generation avoids requiring a specific database version. ([postgresql.org](https://www.postgresql.org/docs/current/functions-uuid.html?utm_source=openai))

## 6.2 Public reference

User-visible entities additionally receive an opaque public reference.

Examples:

- `BKG-7K4M2P9X`
- `CASE-Q8D4W2H7`
- `FLT-M2R9C5P1`

Requirements:

- Random and non-sequential
- Unique per resource type
- Not derived from row count
- Not an authorization secret
- Stable for the resource lifetime
- Indexed with a unique constraint

## 6.3 External service reference

A service stores another service’s entity reference as an ordinary UUID column such as:

- `account_ref`
- `station_ref`
- `evse_ref`
- `organization_ref`

No database foreign key is created for these columns.

Referential validity is maintained through:

- Validated commands and APIs
- Integration events
- Enforcement projections
- Reconciliation
- Privacy tombstones

---

## 7. Common column conventions

Authoritative mutable entities normally contain:

| Column | Type | Purpose |
|---|---|---|
| `entity_ref` | `uuid` | Primary/integration identity |
| `public_ref` | `varchar` | Optional user-facing reference |
| `version` | `bigint` | Optimistic concurrency version |
| `created_at` | `timestamptz` | Creation time |
| `updated_at` | `timestamptz` | Latest authoritative update |
| `created_by_ref` | `uuid` | Optional actor reference |
| `updated_by_ref` | `uuid` | Optional actor reference |

Rules:

- `version` starts at 1 and increments on every authoritative mutation.
- API `ETag` values derive from the version.
- Timestamps use `timestamptz`.
- Business intervals use half-open semantics.
- Free-text columns have explicit maximum lengths.
- Money uses `numeric`, never floating point.
- Electrical measurements use integers in canonical units where practical.
- Lifecycle states use text columns with check constraints.
- PostgreSQL enum types are avoided initially to simplify controlled state evolution.
- `jsonb` is used selectively, not as a replacement for relational modelling.

## 7.1 Database time

Each correctness-critical transaction obtains one authoritative database time and uses it consistently for:

- Hold expiry
- Check-in deadline
- Authorization expiry
- No-show evaluation
- Command expiry
- Retention eligibility

Long-running workers re-evaluate time inside each item’s transaction.

---

## 8. JSON usage policy

Appropriate `jsonb` uses:

- Immutable tariff snapshots
- Immutable policy snapshots
- Event and command payloads
- Safe audit detail
- Provider-specific notification metadata
- Simulator diagnostic metadata
- Versioned workflow participant results

Core searchable fields must remain typed columns.

Do not store only JSON for:

- Lifecycle state
- Booking interval
- EVSE reference
- Account ownership
- Session sequence
- Energy total
- Maintenance interval
- Notification state
- Privacy-request state

Every JSON document includes a schema version where its structure may evolve.

---

# 9. Account Service model

## 9.1 `application_account`

| Column | Notes |
|---|---|
| `entity_ref` | Account integration reference |
| `identity_subject` | Unique Identity Provider subject |
| `state` | Account lifecycle state |
| `booking_eligible` | Derived application eligibility |
| `preferred_locale` | `el` or `en` |
| `preferred_timezone` | Initially `Europe/Athens` |
| `version` | Concurrency |
| Timestamps | Standard |

Constraints:

- Unique `identity_subject`
- Valid account state
- Deleted accounts cannot become active through ordinary update

## 9.2 `driver_profile`

Contains:

- Account reference
- Display name
- Email-routing reference where needed
- Optional contact metadata
- Profile version
- Privacy classification
- Redaction/deletion time

Email credential authority remains in the Identity Provider.

## 9.3 `vehicle_profile`

Contains:

- Vehicle reference
- Account reference
- User-defined label
- Connector types
- Maximum AC power
- Maximum DC power
- Optional battery capacity
- Default-vehicle indicator
- Version and timestamps

Constraints:

- Vehicle belongs to one account.
- At most one default vehicle per account, enforced by a partial unique index.
- VIN and registration plate are not stored.

## 9.4 `notification_preference`

Unique per account and optional notification category.

Contains:

- Account reference
- Category
- Enabled flag
- Locale override
- Version

Mandatory notification categories are not represented as disableable preferences.

## 9.5 Privacy tables

- `privacy_request`
- `privacy_participant_status`
- `privacy_export_artifact`
- `processing_restriction`
- `account_deletion_workflow`
- `deletion_tombstone`

A privacy workflow’s participant status is unique by:

- Workflow reference
- Participant service
- Requested action

`deletion_tombstone` stores no deleted personal content.

---

# 10. Station Operations Service model

## 10.1 Organization tables

- `operator_application`
- `operator_organization`
- `organization_member`
- `organization_invitation`
- `ownership_transfer`

Important constraints:

- Unique active membership per account and organization.
- Exactly one active owner must be preserved by transactional service logic and locking.
- Invitation token secrets are not stored directly; only token references/hashes where owned by the service.
- Closed organizations cannot return to active.

## 10.2 `station`

Contains:

- Station and public references
- Organization reference
- Lifecycle state
- Display name
- Address fields
- Latitude/longitude
- Station timezone
- Access type
- Access instructions
- Publication version
- Version and timestamps

Constraints:

- Coordinates use bounded valid ranges.
- Only published stations participate in public projections.
- Deactivated stations retain history.

Geospatial storage may later use PostGIS. Until the technology ADR, the logical contract remains latitude and longitude.

## 10.3 Schedule tables

- `station_opening_period`
- `station_schedule_exception`
- `station_temporary_closure`

Opening periods store:

- Day of week
- Local start time
- Local end time
- Effective date range

Schedule exceptions store a local date and either:

- Closed all day
- Replacement opening intervals

DST conversion is performed by domain logic using the station timezone.

## 10.4 `evse`

Contains:

- EVSE and public references
- Station reference
- Public label
- Administrative state
- Maximum simultaneous vehicles, fixed to 1 in v1
- Configuration version
- Version and timestamps

## 10.5 `connector`

Contains:

- Connector reference
- EVSE reference
- Connector type
- Current type
- Maximum power
- Active flag
- Version

A connector is not independently allocatable.

## 10.6 Tariff model

Tables:

- `tariff`
- `tariff_version`
- `tariff_component`

A tariff version contains:

- Currency
- Tax rate
- Effective interval
- Publication state
- Version number

Components contain:

- Energy price
- Time price
- Session fee
- Idle fee
- Unit and amount

Activated versions are immutable.

## 10.7 Booking policy model

Tables:

- `booking_policy`
- `booking_policy_version`

Versioned fields include:

- Minimum/maximum duration
- Advance window
- Time increment
- Hold duration
- Check-in opening offset
- Grace period
- Turnaround buffer
- Near-term horizon override where permitted

Activated versions are immutable.

## 10.8 Operations tables

- `maintenance`
- `fault_report`
- `fault_incident`
- `fault_report_incident_link`
- `status_override`
- `simulator_assignment`

Time-based records use `tstzrange` or equivalent start/end columns with half-open semantics.

Maintenance completion stores an outcome such as:

- `COMPLETED`
- `ABORTED`

without using an invalid `ACTIVE → CANCELLED` lifecycle transition.

---

# 11. Booking and Session Service model

This database contains the principal strong-consistency boundary.

## 11.1 `booking`

Principal columns:

| Column | Purpose |
|---|---|
| `entity_ref` | Booking identity |
| `public_ref` | Driver-visible reference |
| `account_ref` | Booking owner |
| `organization_ref` | Operator scope projection |
| `station_ref` | Assigned station |
| `evse_ref` | Assigned EVSE |
| `required_connector_type` | Compatibility requirement |
| `state` | Booking lifecycle |
| `scheduled_start` | Charging interval start |
| `scheduled_end` | Charging interval end |
| `hold_expires_at` | Required for `HELD` |
| `grace_deadline` | Snapshotted deadline |
| `checked_in_at` | Nullable |
| `activated_at` | Nullable |
| `terminal_at` | Nullable |
| `outcome_reason` | Structured reason |
| `tariff_snapshot_ref` | Immutable snapshot |
| `policy_snapshot_ref` | Immutable snapshot |
| `version` | Aggregate version |

Constraints:

- `scheduled_start < scheduled_end`
- `HELD` requires `hold_expires_at`
- `CONFIRMED` or later requires snapshots
- Terminal state requires `terminal_at`
- `FULFILMENT_FAILED` is invalid after confirmed energy transfer
- Unique public reference

## 11.2 `capacity_claim`

Represents planned exclusive EVSE capacity.

Claim kinds:

- `BOOKING_HOLD`
- `BOOKING`
- `MAINTENANCE`
- `EMERGENCY_BLOCK`
- `OPERATOR_RESTRICTION`

Principal columns:

| Column | Purpose |
|---|---|
| `entity_ref` | Claim identity |
| `evse_ref` | Exclusivity key |
| `claim_kind` | Capacity source |
| `source_ref` | Booking/workflow source |
| `effective_interval` | Half-open `tstzrange` |
| `state` | `ACTIVE` or `RELEASED` |
| `source_version` | Source workflow version |
| `released_at` | Nullable |
| `release_reason` | Structured reason |

Candidate database invariant:

- No two active capacity claims may overlap for one EVSE.

PostgreSQL candidate:

```sql
EXCLUDE USING gist (
    evse_ref WITH =,
    effective_interval WITH &&
)
WHERE (state = 'ACTIVE')
```

This requires an appropriate equality operator class, such as through `btree_gist`, when using GiST with scalar EVSE identifiers. PostgreSQL documents this pattern for combining a scalar equality key with a range-overlap exclusion. ([postgresql.org](https://www.postgresql.org/docs/current/rangetypes.html?utm_source=openai))

The final constraint, isolation level and locking protocol are approved in ARC-006.

## 11.3 `operational_occupation`

Records actual or uncertain EVSE occupation that may overlap an already committed future allocation.

Contains:

- EVSE reference
- Session reference
- Occupation start
- Confirmed end, if known
- State:
  - `ACTIVE`
  - `UNCERTAIN`
  - `RELEASED`
- Last evidence time
- Reconciliation reference

This table is separate from planned capacity claims because an actual session overrun must remain recordable even when it conflicts with a later committed Booking.

New allocation must consider both:

- Active `capacity_claim` records
- Active/uncertain `operational_occupation` records

The atomic cross-table protocol is defined in ARC-006.

## 11.4 Driver overlap control

A separate `driver_booking_claim` may be used to prevent one driver from holding overlapping non-terminal bookings.

Contains:

- Account reference
- Booking reference
- Charging interval
- State

A PostgreSQL exclusion constraint is the preferred candidate.

Whether this remains a separate table or is derived from Booking is finalized in ARC-006.

## 11.5 Snapshot tables

### `tariff_snapshot`

Contains:

- Snapshot reference
- Source tariff/version
- Currency
- Tax
- Pricing components
- Calculation schema version
- Canonical JSON document
- Content hash
- Created time

### `policy_snapshot`

Contains:

- Snapshot reference
- Source policy/version
- Timing and cancellation rules
- Canonical JSON document
- Content hash
- Created time

Snapshots are immutable and may be shared by several bookings when their canonical content is identical, but deduplication is optional.

## 11.6 Check-in tables

### `booking_check_in`

Contains:

- Booking reference
- EVSE reference
- Method
- Checked-in time
- Actor
- Rejection/override metadata

### `start_authorization`

Contains:

- Authorization reference
- Booking reference
- Account reference
- EVSE reference
- Intended session reference
- Secret hash where tokenized
- State:
  - `ISSUED`
  - `CONSUMED`
  - `REVOKED`
  - `EXPIRED`
- Issued, expiry, consumption and revocation times

Constraints:

- At most one `ISSUED` authorization per Booking.
- One authorization belongs to one intended Session.
- Secret material is never stored in plaintext.
- `CONSUMED` cannot return to `ISSUED`.

## 11.7 `charging_session`

Contains:

- Session and public references
- Booking reference
- Account reference
- Station and EVSE references
- State
- Start-command reference
- Stop-command reference
- Device transaction reference
- Confirmed start/end
- Latest meter sequence
- Latest cumulative energy
- Current power
- Outcome and stop reason
- Uncertainty flag
- Version and timestamps

Constraints:

- One Session per Booking in v1.
- At most one non-terminal Session per EVSE.
- At most one non-terminal Session per driver.
- `CHARGING` requires confirmed start evidence.
- Terminal states require an outcome.
- `START_REJECTED` cannot contain accepted energy transfer.

Partial unique indexes are candidates for non-terminal Session restrictions.

## 11.8 `meter_sample`

Contains:

- Session reference
- Meter sequence
- Event reference
- Observed time
- Received time
- Cumulative energy Wh
- Instantaneous power W
- Optional voltage/current
- Validation state

Constraints:

- Unique `(session_ref, meter_sequence)`
- Unique device event reference
- Non-negative bounded values
- Accepted cumulative energy cannot be counted twice

The service may preserve invalid samples in a separate quarantine table rather than mix them with accepted meter data.

## 11.9 `session_summary`

One immutable summary per terminal Session.

Contains:

- Session reference
- Booking reference
- Start/end
- Duration
- Energy
- Estimated amount
- Currency
- Tariff snapshot
- Outcome
- Calculation version
- Finalized time
- Summary hash

## 11.10 Enforcement and reconciliation tables

- `account_eligibility_projection`
- `bookable_evse_projection`
- `device_operational_projection`
- `driver_restriction`
- `booking_fulfilment_risk`
- `reconciliation_workflow`

Every projection contains:

- Source reference
- Source aggregate version
- Received time
- Effective time
- Freshness state

Older source versions cannot replace newer values.

---

# 12. Device Integration Service model

## 12.1 Machine identity

Tables:

- `machine_identity`
- `machine_credential_reference`
- `simulator_assignment_projection`

Machine credentials are held by the selected identity/certificate infrastructure; the database stores references and lifecycle metadata, not private keys.

## 12.2 Connectivity

Tables:

- `device_connection`
- `station_heartbeat`
- `evse_reported_state`
- `device_inventory_snapshot`

Only current state and required operational history are retained long term.

High-volume heartbeat history may be aggregated or deleted according to retention policy.

## 12.3 Commands

### `device_command`

Contains:

- Command reference
- Type
- Station/EVSE target
- Booking/session references
- State
- Payload schema version
- Payload
- Created and expiry times
- Dispatch and outcome times
- Attempt count
- Correlation/workflow references

Command IDs are unique and reused on retries.

### `device_command_result`

Stores the durable prior outcome returned for duplicate commands.

## 12.4 Device message processing

Tables:

- `device_message_receipt`
- `device_station_sequence`
- `device_session_sequence`
- `device_event_quarantine`
- `device_reconciliation`

Raw payload retention is minimized.

Normalized accepted evidence is published to Booking and Session.

## 12.5 Simulator control

- `simulation_profile`
- `fault_injection`
- `simulation_scenario_run`

Scenario runs store the deterministic seed and configuration version.

---

# 13. Discovery and Insights Service model

All tables are projections.

## 13.1 Discovery

- `station_search_projection`
- `evse_search_projection`
- `connector_search_projection`
- `advisory_availability_projection`
- `projection_checkpoint`

Search projections contain:

- Source versions
- Projection update time
- Freshness
- Rebuild state

The service may initially use PostgreSQL queries. PostGIS or a separate search engine requires a technology ADR.

## 13.2 Analytics

- `operator_metric_bucket`
- `platform_metric_bucket`
- `session_fact_projection`
- `booking_fact_projection`
- `report_export`

Metrics store:

- Definition version
- Aggregation period
- Organization/station/EVSE scope
- Count or numeric measure
- Freshness
- Suppression status

User-level analytics profiles are prohibited.

## 13.3 Rebuild behaviour

Projection tables support deterministic upsert using:

- Source entity reference
- Source version
- Projection type

Privacy tombstones take precedence over replayed older events.

---

# 14. Notification Service model

## 14.1 `notification`

Contains:

- Notification reference
- Notification type/category
- Recipient account/organization reference
- Template key/version
- Locale
- Mandatory flag
- Trigger event reference
- Aggregate reference/version
- State
- Scheduled time
- Obsolescence time
- Deduplication key
- Version and timestamps

Unique constraint on logical notification deduplication key.

## 14.2 `notification_delivery_attempt`

Contains:

- Notification reference
- Attempt number
- Provider reference
- State
- Submitted time
- Outcome time
- Safe failure category

## 14.3 Other tables

- `notification_template`
- `reminder_schedule`
- `recipient_suppression`
- `provider_webhook_inbox`

Rendered content retention follows the privacy schedule.

Raw action tokens are never stored in these tables.

---

# 15. Governance and Support Service model

## 15.1 Support

- `support_case`
- `support_case_assignment`
- `support_case_transition`
- `temporary_access_grant`
- `masked_field_reveal`

Temporary grants include:

- Case reference
- Actor
- Scope
- Allowed actions
- Issued time
- Expiry
- Revocation time

## 15.2 Administration

- `administrative_investigation`
- `emergency_intervention`
- `break_glass_request`
- `break_glass_grant`
- `privileged_action_review`

## 15.3 Audit projection

- `central_audit_projection`
- `audit_projection_checkpoint`

The centralized projection is searchable but not the source of truth for local business audit facts.

---

# 16. Common integration tables

Each event-producing/consuming service owns its own copies of these patterns.

## 16.1 `outbox_message`

Principal columns:

- Message ID
- Message kind
- Type
- Schema version
- Aggregate reference/version
- Workflow reference
- Correlation and causation IDs
- Classification
- Payload
- Occurred time
- Available time
- Published time
- Attempt count
- Publication state
- Last safe error

Indexes:

- Unpublished messages by `available_at`
- Aggregate reference/version
- Workflow reference
- Published retention time

## 16.2 `inbox_message`

Principal columns:

- Consumer name
- Message ID
- Message type
- Received time
- Processing state
- Completed time
- Attempt count
- Last safe error

Primary uniqueness:

- `(consumer_name, message_id)`

Business updates and inbox completion commit together.

## 16.3 `idempotency_record`

Contains:

- Principal/service scope
- Operation
- Idempotency key
- Request fingerprint
- State
- HTTP/business outcome
- Response representation or result reference
- Created and expiry times

Unique scope:

- Principal
- Operation
- Idempotency key

Large response bodies should not be retained indefinitely.

## 16.4 `workflow_instance`

Where the service coordinates a workflow:

- Workflow reference
- Type
- State
- Aggregate reference
- Current step
- Deadline
- Version
- Failure/review reason
- Created/updated/completed times

Broker messages do not replace workflow persistence.

---

# 17. Audit persistence

Each authoritative service owns an append-only `audit_event` table.

Contains:

- Audit reference
- Actor type/reference
- Service identity
- Action
- Target type/reference
- Structured reason
- Outcome
- Before/after safe summaries
- Correlation ID
- Occurred time
- Classification

Controls:

- Runtime application may insert.
- Runtime application may not update or delete.
- Retention/deletion uses a separate authorized role.
- Secrets and unnecessary personal data are prohibited.
- An outbox audit projection record is created where central search is required.

---

# 18. Constraint naming

All constraints and indexes use explicit names.

Pattern examples:

- `pk_booking`
- `uq_booking_public_ref`
- `ck_booking_interval`
- `fk_meter_sample_session`
- `ex_capacity_claim_evse_interval`
- `ix_outbox_unpublished`
- `ux_session_active_evse`

Named constraints allow database violations to be mapped safely to stable domain problem codes.

Database error text is never returned directly to clients.

---

# 19. Indexing strategy

## 19.1 General rules

- Every primary key is indexed.
- Every public reference is uniquely indexed.
- Common external references are indexed.
- Lifecycle queries use targeted partial indexes.
- Cursor pagination has matching indexes.
- Indexes are justified by documented query patterns.
- Duplicate or unused indexes are monitored and removed through migration.
- Foreign keys within a service have supporting indexes where required.

## 19.2 Booking indexes

Candidate indexes:

- Upcoming bookings by account and start
- Bookings by EVSE and interval
- Holds by expiry
- Grace deadlines by state
- Active Sessions by EVSE
- Active Sessions by account
- Meter samples by Session and sequence
- Reconciliation workflows by state and deadline
- Capacity claims using GiST
- Active operational occupations by EVSE

## 19.3 Integration indexes

- Outbox pending by availability time
- Inbox uniqueness
- Idempotency expiry
- Workflow state/deadline
- Projection source version

---

# 20. Partitioning

Partitioning is not applied automatically to every large-looking table.

Initial partition candidates:

- `meter_sample`
- High-volume device message metadata
- Audit events
- Notification delivery history
- Analytics facts

Partitioning decision criteria:

- Measured row volume
- Retention deletion cost
- Query locality
- Index size
- Restore requirements
- Operational complexity

PostgreSQL declarative partitioning can improve selected workloads and retention operations, but poor partition-key or partition-count choices can increase planning and operational complexity. ([postgresql.org](https://www.postgresql.org/docs/18/ddl-partitioning.html?utm_source=openai))

Preferred candidate where justified:

- Monthly range partitions by accepted/occurred time
- Automated creation of future partitions
- Retention by detaching and dropping expired partitions
- Default partition only if monitored carefully

No release-critical uniqueness constraint may be weakened merely to enable partitioning.

---

# 21. Row-level security

Application authorization remains the primary control.

PostgreSQL row-level security may be evaluated as defense in depth for selected organization-scoped or privacy-restricted tables. It is not required for v1 because:

- Each service already has exclusive database ownership.
- Service logic requires richer role/resource/case rules.
- Incorrect policies can create hidden operational complexity.
- Background workers need carefully controlled access.

If adopted:

- Policies are migration-controlled.
- Tests cover every database role.
- Table owners do not perform ordinary runtime queries.
- RLS cannot replace API authorization.

PostgreSQL supports command- and role-specific row-security policies when row security is enabled on a table. ([postgresql.org](https://www.postgresql.org/docs/18/catalog-pg-policy.html?utm_source=openai))

---

# 22. Retention implementation

Every retention rule maps to:

- Owning service
- Table/data category
- Eligibility query
- Delete/redact/anonymize action
- Legal/security hold condition
- Batch size
- Evidence record
- Failure metric

Rules:

1. Jobs use database time.
2. Jobs process bounded batches.
3. Jobs are idempotent.
4. Active holds exclude protected rows.
5. Child/dependent records are handled explicitly.
6. Cascading deletes are used only where lifecycle semantics permit.
7. Personal fields may be redacted while operational facts remain.
8. Projection deletion follows privacy tombstones.
9. Export artifacts are deleted after expiry.
10. Restored data must reapply tombstones before ordinary use.

---

# 23. Deletion behaviour

## 23.1 Hard deletion

Permitted for:

- Expired temporary tokens/references
- Generated export artifacts
- Expired operational caches
- Unverified accounts after approved retention
- Non-authoritative projections that can be rebuilt

## 23.2 Redaction or anonymization

Preferred for retained:

- Booking history
- Session summaries
- Support cases
- Analytics facts
- Operational incidents

## 23.3 Deactivation

Used for:

- Stations
- EVSEs
- Operator organizations
- Reference data
- Tariffs/policies with historical references

A generic `deleted` flag is not added to every table. Each aggregate uses its approved lifecycle or privacy action.

---

# 24. Migration tool and ownership

Flyway is the preferred migration mechanism.

Each service repository/module owns:

```text
src/main/resources/db/migration/
  V001__initial_schema.sql
  V002__add_booking_indexes.sql
  V003__expand_session_outcome.sql
  R__reporting_views.sql
```

Rules:

- Versioned migrations are immutable after application.
- A correction uses a new migration.
- Repeatable migrations are limited to replaceable objects.
- Production migrations are reviewed.
- Migration checksums are verified in CI.
- No service modifies another service’s migration history.
- Reference data changes use versioned migrations or controlled administrative APIs according to ownership.

---

# 25. Migration execution

Preferred deployment sequence:

1. Build and test migration artifact.
2. Back up or verify recovery point.
3. Acquire service-specific migration lock.
4. Run migrations through one dedicated migration job.
5. Validate schema history and required constraints.
6. Deploy compatible application version.
7. Run post-deployment checks.
8. Record deployment evidence.

Ordinary service replicas do not all attempt production migrations during startup.

Runtime startup:

- Verifies compatible schema version.
- Fails readiness if schema is too old or unsupported.
- Does not perform uncontrolled DDL.

---

# 26. Expand–migrate–contract strategy

Breaking database changes use multiple releases.

## 26.1 Expand

- Add new nullable column/table/index.
- Preserve old fields and behaviour.
- Deploy code that can tolerate both versions.
- Avoid destructive changes.

## 26.2 Migrate

- Backfill in bounded resumable batches.
- Record progress.
- Dual-read or dual-write only where explicitly designed.
- Verify counts, hashes and invariants.
- Stop safely on failure.

## 26.3 Contract

After all running application versions use the new model:

- Add final not-null/check constraints.
- Remove old reads/writes.
- Remove obsolete columns/indexes in a later deployment.
- Update retention and restore tooling.

Renaming a column is treated as:

1. Add replacement.
2. Populate replacement.
3. Deploy compatible readers/writers.
4. Stop using old column.
5. Remove old column later.

---

# 27. Migration safety rules

1. Never edit an applied versioned migration.
2. Never combine a destructive migration with the first code version requiring it.
3. Avoid long table locks during normal availability windows.
4. Large indexes use an online/concurrent strategy where supported.
5. Constraint validation may be staged when safe.
6. Large data backfills are not performed in one unbounded transaction.
7. Data transformations are restartable.
8. Migration progress is observable.
9. Failed migrations stop deployment.
10. Forward correction is preferred over automatic down migration.
11. Rollback means deploying compatible code or restoring through an approved recovery procedure.
12. Production schema drift is detected and investigated.

---

# 28. Backward compatibility matrix

| Change | Strategy |
|---|---|
| Add optional column | Expand migration |
| Add required column | Add nullable/default, backfill, enforce later |
| Rename column | Add-copy-switch-remove |
| Change enum/state | Expand check, deploy code, contract old value |
| Split table | Add target, backfill, dual-read/write temporarily |
| Merge tables | Add destination, migrate, switch, remove later |
| Add index | Online/concurrent where supported |
| Remove index | Verify unused, remove in separate migration |
| Change JSON schema | Version field and compatible readers |
| Change identifier | Dual reference and explicit migration |
| Move ownership between services | Dedicated data-ownership migration plan |
| Partition existing table | Shadow/attach strategy with load testing |

---

# 29. Service-boundary data migrations

Moving authoritative data between services is exceptional.

Required process:

1. Approve a boundary ADR.
2. Define old and new authority.
3. Introduce destination schema.
4. Copy historical data with integrity checks.
5. Stream ongoing changes through events or controlled dual-write.
6. Compare source/destination state.
7. Switch API and message ownership.
8. Prevent new writes at the old owner.
9. Retain rollback window.
10. Remove old authority only after verification.
11. Update privacy, audit, backup and retention ownership.
12. Publish new event ownership versions if necessary.

A shared database table is not used as an intermediate shortcut.

---

# 30. Reference and seed data

Reference data categories include:

- Connector types
- Fault reason categories
- Cancellation reasons
- Access types
- Supported locales
- Metric-definition versions

Rules:

- Stable reference values have stable codes.
- Values are deprecated, not deleted, while referenced.
- Initial mandatory values may be seeded by migration.
- Business-managed values use authorized administration APIs.
- Environment-specific secrets and URLs are never seeded by database migrations.
- Test fixtures remain separate from production migrations.

---

# 31. Backup and restoration considerations

The final backup plan is defined later, but the data model must support:

- Point-in-time recovery
- Consistent service-database restoration
- Schema history restoration
- Outbox recovery
- Inbox deduplication recovery
- Workflow resumption
- Reconciliation after restore
- Privacy-tombstone replay

After restoration:

1. Service remains unavailable for ordinary traffic.
2. Schema compatibility is verified.
3. Deletion tombstones are reapplied.
4. Expired holds and deadlines are re-evaluated.
5. Outbox publishing resumes.
6. Inbox records prevent duplicate effects.
7. Device state becomes unknown until fresh evidence.
8. Projections catch up or rebuild.
9. Incomplete workflows resume or enter review.

Cross-service restoration points need not be perfectly simultaneous, but reconciliation must detect version gaps and stale projections.

---

# 32. Security requirements

- Database transport encryption is required outside isolated local development.
- Credentials are stored in secrets management.
- Each service receives least privilege.
- Runtime roles cannot execute DDL.
- Sensitive columns are excluded from ordinary diagnostic queries.
- Backups are encrypted and access-controlled.
- Query parameters are bound; SQL concatenation is prohibited.
- Personal-data extracts are audited.
- Database logs must not expose bind values containing secrets or sensitive personal data.
- Public references are not authorization controls.
- Start Authorization secrets are hashed.
- Private machine keys are never stored in ordinary service tables.
- Quarantine payload access is restricted.
- Migration execution is an audited deployment action.

---

# 33. ORM and repository rules

If Spring Data/JPA is used:

- Domain aggregates do not map across service boundaries.
- Lazy loading is not relied on across transactions.
- Explicit queries support lists and projections.
- N+1 queries are tested.
- Database constraints remain authoritative for uniqueness/exclusion.
- State transitions use aggregate version checks.
- Bulk lifecycle updates are avoided unless they preserve audit/outbox behaviour.
- Native SQL is acceptable for range, exclusion and concurrency operations.
- Shared persistence entities across services are prohibited.

Database features must not be hidden merely to preserve ORM portability.

---

# 34. Data model testing

Required tests:

## Schema

- Fresh database migration
- Upgrade from every supported release
- Migration checksum validation
- Constraint-name verification
- Schema compatibility with application

## Constraints

- Public-reference uniqueness
- Capacity-claim overlap rejection
- Adjacent half-open intervals
- Partial active-state uniqueness
- One Session per Booking
- One active Session per EVSE/account
- Meter sequence uniqueness
- Authorization single-use state
- Snapshot immutability

## Migrations

- Expand/migrate/contract rehearsal
- Interrupted backfill restart
- Concurrent application during compatible migration
- Large-index creation
- Failed migration recovery
- Drift detection

## Privacy and recovery

- Retention boundary
- Hold exclusion
- Tombstone replay
- Backup restoration
- Projection rebuild
- Outbox/inbox replay after restore

---

# 35. Monitoring

Required database metrics:

- Connection pool utilization
- Transaction latency
- Lock waits and deadlocks
- Constraint violations
- Slow queries
- Table/index growth
- Unused/duplicate indexes
- Replication/backup lag
- Migration duration/failure
- Outbox age
- Inbox growth
- Idempotency-record growth
- Retention backlog
- Partition availability
- Autovacuum health
- Reconciliation backlog

Release-critical alerts include:

- Allocation constraint disabled/missing
- Outbox age above threshold
- Migration drift
- Backup failure
- Retention failure
- Privacy-tombstone failure
- Repeated deadlocks in booking allocation

---

# 36. Decisions proposed for approval

| ID | Decision |
|---|---|
| ARC-DATA-01 | Use one logical database boundary per business service. |
| ARC-DATA-02 | Permit logical databases to share one physical PostgreSQL cluster for cost efficiency. |
| ARC-DATA-03 | Prohibit cross-service foreign keys, joins and database access. |
| ARC-DATA-04 | Use UUID entity references and separate opaque public references. |
| ARC-DATA-05 | Prefer UUIDv7 generation without requiring PostgreSQL 18. |
| ARC-DATA-06 | Use `timestamptz` and half-open time intervals. |
| ARC-DATA-07 | Use text state columns with check constraints rather than PostgreSQL enum types initially. |
| ARC-DATA-08 | Use `jsonb` only for versioned snapshots, envelopes and variable metadata. |
| ARC-DATA-09 | Model planned capacity claims separately from actual/uncertain operational occupation. |
| ARC-DATA-10 | Use a datastore-enforced non-overlap constraint for active planned capacity claims. |
| ARC-DATA-11 | Finalize cross-table occupation/allocation concurrency in ARC-006. |
| ARC-DATA-12 | Store Booking, Allocation, Check-In, Session and accepted Meter data in one logical database. |
| ARC-DATA-13 | Use local Outbox, Inbox and Idempotency tables in every relevant service. |
| ARC-DATA-14 | Keep authoritative audit evidence local and append-only. |
| ARC-DATA-15 | Use Flyway as the preferred migration mechanism. |
| ARC-DATA-16 | Execute production migrations through dedicated migration jobs. |
| ARC-DATA-17 | Use expand–migrate–contract for breaking changes. |
| ARC-DATA-18 | Prefer forward correction over automatic down migrations. |
| ARC-DATA-19 | Partition only tables justified by measured volume or retention needs. |
| ARC-DATA-20 | Reapply deletion tombstones after restoration and projection rebuild. |
| ARC-DATA-21 | Prevent runtime service roles from executing DDL. |
| ARC-DATA-22 | Prohibit a shared persistence/domain entity library across services. |

---

# 37. Open questions

| ID | Question | Resolution phase |
|---|---|---|
| ARC-DATA-OQ-01 | Final PostgreSQL major version | Technology selection |
| ARC-DATA-OQ-02 | Separate databases versus schemas in the initial cloud environment | Cloud design |
| ARC-DATA-OQ-03 | Final UUIDv7 generation library/location | Technology selection |
| ARC-DATA-OQ-04 | Exact capacity-claim and occupation locking protocol | ARC-006 |
| ARC-DATA-OQ-05 | Exact driver overlap constraint | ARC-006 |
| ARC-DATA-OQ-06 | Transaction isolation for booking allocation | ARC-006 |
| ARC-DATA-OQ-07 | Whether PostgreSQL 18 `WITHOUT OVERLAPS` is preferable to exclusion constraints | ARC-006/technology |
| ARC-DATA-OQ-08 | Whether PostGIS is required | Frontend/technology |
| ARC-DATA-OQ-09 | Which tables are partitioned initially | Performance testing |
| ARC-DATA-OQ-10 | Final Inbox, Outbox and Idempotency retention | Privacy/operations |
| ARC-DATA-OQ-11 | Whether selective RLS is justified | Security architecture |
| ARC-DATA-OQ-12 | Final point-in-time recovery and backup topology | Cloud/operations |
| ARC-DATA-OQ-13 | Final money/tax precision | Domain/data review |
| ARC-DATA-OQ-14 | Whether immutable snapshots are deduplicated | Performance/data review |
| ARC-DATA-OQ-15 | Final local audit tamper-resistance controls | Security architecture |

---

# 38. Acceptance criteria

This data design is approved when:

1. Every authoritative entity maps to exactly one service database.
2. No table requires a cross-service foreign key.
3. Booking allocation data remains in one transactional boundary.
4. Planned claims and actual/uncertain occupation can both be represented.
5. Overlapping planned EVSE claims can be rejected by the datastore.
6. Session overruns can be recorded without deleting later bookings.
7. Every mutable aggregate has a version.
8. Public URLs need not expose entity UUIDs.
9. Start Authorization secrets are not stored in plaintext.
10. Duplicate meter samples cannot be accepted twice.
11. Outbox and Inbox processing can commit atomically with local effects.
12. Privacy workflows and tombstones have durable persistence.
13. Projections record source versions and freshness.
14. Migrations remain service-owned and backward compatible.
15. Runtime roles cannot alter schemas.
16. Restoration procedures can reapply privacy and lifecycle actions.
17. Retention can be executed idempotently and observably.
18. The model can support the definitive double-booking design without cross-service transactions.

---

# 39. Consequences

## Positive

- Clear authoritative ownership
- Strong allocation consistency
- Service-independent migrations
- Safe retries and event delivery
- Explicit handling of operational overruns
- Rebuildable projections
- Strong privacy restoration support
- Practical use of PostgreSQL constraints

## Negative

- Data duplication across service projections
- Additional UUID/public-reference complexity
- Outbox/Inbox storage overhead
- More migrations and database credentials
- Cross-service reporting requires projections
- Expand/migrate/contract deployments require discipline
- Operational occupation adds concurrency complexity

These costs are accepted to preserve service autonomy and domain correctness.

---

# 40. Next architecture artifact

The next document is:

**Definitive Double-Booking Prevention and Allocation Concurrency Design v1.0**

It must finalize:

- Capacity-claim table design
- Operational-occupation interaction
- PostgreSQL exclusion or temporal constraints
- Transaction isolation
- Lock acquisition order
- Automatic EVSE selection
- Hold expiry races
- Confirmation races
- Rescheduling and reassignment
- Maintenance block installation
- Session overrun behaviour
- Idempotency
- Deadlock handling
- Exact SQL transaction patterns
- Concurrency and property-based test strategy
