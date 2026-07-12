Document ID: ARC-006  
Title: Definitive Double-Booking Prevention and Allocation Concurrency Design  
Version: 1.0  
Status: APPROVED  
Owner: Backend / Data Architect  
Last reviewed: 2026-07-12  
Depends on: ARC-001, ARC-002, ARC-003, ARC-004, ARC-005, DOM-002  
Authoritative for: EVSE allocation concurrency, datastore constraints, locking, transaction isolation, hold races, rescheduling, reassignment, maintenance blocks and operational occupation  

# Definitive Double-Booking Prevention and Allocation Concurrency Design v1.0

## 1. Purpose

This document finalizes:

- The authoritative allocation representation
- PostgreSQL exclusion constraints
- Per-driver and per-EVSE serialization
- Transaction isolation
- Lock acquisition order
- Hold-expiry treatment
- Exact and automatic EVSE allocation
- Confirmation, cancellation and no-show races
- Atomic rescheduling and reassignment
- Maintenance and emergency capacity blocks
- Charging-session occupation and overruns
- Deadlock and retry handling
- Concurrency verification

The design preserves these release-critical invariants:

1. One EVSE cannot have overlapping effective planned allocations.
2. Expired holds cannot block new allocation.
3. One driver cannot hold overlapping non-terminal bookings.
4. Failed rescheduling cannot remove the original allocation.
5. Actual or uncertain occupation blocks affected new allocations.
6. Existing future bookings are retained when a session overruns, but become operationally at risk.
7. Allocation correctness never depends on a remote service or eventually consistent search projection.

---

## 2. Final design summary

The Booking and Session Service uses four complementary controls:

1. **`evse_allocation_guard` row lock**  
   Serializes all allocation and occupation changes for one EVSE.

2. **`driver_schedule_guard` row lock**  
   Serializes booking-schedule changes for one driver.

3. **PostgreSQL exclusion constraints**  
   Reject overlapping committed booking and operational-block intervals.

4. **`operational_occupation` records**  
   Represent actual or uncertain physical use independently of planned allocations.

The transaction isolation level is `READ COMMITTED`, combined with explicit row locks, mandatory revalidation after locking and datastore constraints.

PostgreSQL row locks prevent concurrent transactions from modifying or locking the same selected rows until the transaction ends. PostgreSQL recommends acquiring multiple locks in a consistent order to avoid deadlocks. ([postgresql.org](https://www.postgresql.org/docs/17/explicit-locking.html))

---

## 3. Why Allocation and occupation remain separate

### Planned capacity

A `capacity_claim` represents an exclusive planned interval for:

- Confirmed Booking
- Booking Hold
- Maintenance
- Emergency block
- Operator restriction

Confirmed claims must never overlap for one EVSE.

### Physical occupation

An `operational_occupation` represents:

- A Session that may have started
- Confirmed active charging
- Session overrun
- Uncertain start or stop outcome
- Post-session turnaround time

Physical occupation may overlap an already committed future Booking when a Session overruns.

Therefore, one cross-table exclusion constraint cannot represent both concepts:

- Rejecting the overlap would prevent recording the real overrun.
- Silently deleting the future Booking would violate Booking durability.
- Ignoring the occupation would allow unsafe new allocations.

The EVSE guard serializes cross-table decisions while the exclusion constraint provides an independent planned-capacity safety net.

---

# 4. Core tables

## 4.1 `evse_allocation_guard`

One row exists for every EVSE known to the Booking authority.

```sql
CREATE TABLE evse_allocation_guard (
    evse_ref uuid PRIMARY KEY,
    source_configuration_version bigint NOT NULL,
    allocation_enabled boolean NOT NULL DEFAULT false,
    last_allocated_at timestamptz,
    updated_at timestamptz NOT NULL
);
```

Rules:

- The row is created from the infrastructure enforcement projection.
- A missing guard means the EVSE is not bookable.
- Allocation code never creates the row lazily.
- Every allocation or occupation mutation locks this row.
- `last_allocated_at` supports fair automatic assignment.

---

## 4.2 `driver_schedule_guard`

One row exists for every account eligible to create Bookings.

```sql
CREATE TABLE driver_schedule_guard (
    account_ref uuid PRIMARY KEY,
    booking_eligible boolean NOT NULL,
    source_account_version bigint NOT NULL,
    updated_at timestamptz NOT NULL
);
```

Rules:

- A missing row causes fail-closed Booking rejection.
- Every operation changing a driver’s planned schedule locks this row.
- Suspension events update `booking_eligible`.
- Existing Booking cancellation remains permitted when eligibility is false.

---

## 4.3 `capacity_claim`

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE capacity_claim (
    claim_ref uuid PRIMARY KEY,
    evse_ref uuid NOT NULL
        REFERENCES evse_allocation_guard(evse_ref),
    claim_kind varchar(32) NOT NULL,
    source_ref uuid NOT NULL,
    booking_ref uuid,
    effective_interval tstzrange NOT NULL,
    state varchar(16) NOT NULL,
    hold_expires_at timestamptz,
    source_version bigint NOT NULL,
    released_at timestamptz,
    release_reason varchar(64),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,

    CONSTRAINT ck_capacity_claim_kind CHECK (
        claim_kind IN (
            'BOOKING_HOLD',
            'BOOKING_ALLOCATION',
            'MAINTENANCE_BLOCK',
            'EMERGENCY_BLOCK',
            'OPERATOR_RESTRICTION'
        )
    ),
    /* Domain-to-storage mapping: BOOKING_ALLOCATION = confirmed booking allocation,
       MAINTENANCE_BLOCK = scheduled maintenance block. These domain names are
       used in public contracts and events; only the storage names are abbreviated. */
    CONSTRAINT ck_capacity_claim_state CHECK (
        state IN ('ACTIVE', 'RELEASED')
    ),
    CONSTRAINT ck_capacity_claim_interval CHECK (
        NOT isempty(effective_interval)
        AND lower_inc(effective_interval)
        AND NOT upper_inc(effective_interval)
        AND NOT lower_inf(effective_interval)
        AND NOT upper_inf(effective_interval)
    ),
    CONSTRAINT ck_hold_expiry CHECK (
        (claim_kind = 'BOOKING_HOLD' AND hold_expires_at IS NOT NULL)
        OR
        (claim_kind <> 'BOOKING_HOLD' AND hold_expires_at IS NULL)
    )
);
```

PostgreSQL range types support half-open timestamp intervals, and exclusion constraints can prevent overlapping ranges. The `btree_gist` extension supplies GiST equality support for scalar types including UUID, allowing EVSE equality and interval overlap to be combined. ([postgresql.org](https://www.postgresql.org/docs/current/ddl-constraints.html))

---

## 4.4 Planned-capacity exclusion constraint

```sql
ALTER TABLE capacity_claim
ADD CONSTRAINT ex_capacity_claim_evse_interval
EXCLUDE USING gist (
    evse_ref WITH =,
    effective_interval WITH &&
)
WHERE (
    state = 'ACTIVE'
    AND claim_kind <> 'BOOKING_HOLD'
);
```

The constraint covers:

- Confirmed Bookings
- Maintenance
- Emergency blocks
- Operator restrictions

Booking Holds are intentionally excluded because their validity depends on database time. PostgreSQL partial predicates cannot safely express “unexpired at the current instant” as a permanent constraint.

Unexpired Holds are protected through the EVSE guard and transactional conflict query.

Adding an exclusion constraint creates the supporting exclusion index. ([postgresql.org](https://www.postgresql.org/docs/current/ddl-constraints.html))

---

## 4.5 Claim source uniqueness

```sql
CREATE UNIQUE INDEX ux_active_capacity_claim_source
ON capacity_claim (claim_kind, source_ref, evse_ref)
WHERE state = 'ACTIVE';
```

This prevents duplicate active claims for the same source workflow.

---

## 4.6 `capacity_restriction`

Capacity restrictions track the maintenance freeze/block/release lifecycle independently from the interval-based `capacity_claim`. A restriction begins as a `FREEZE` (planned/imminent), transitions to `BLOCKED` (active), and ends as `RELEASED`.

During `FREEZE`, no `capacity_claim` is inserted. Enforcement uses a query-based model: every allocation transaction queries overlapping `FREEZE`/`BLOCKED` `capacity_restriction` rows while holding the EVSE guard lock. The `capacity_claim` is inserted only at `BLOCKED` time, after overlapping obligations are resolved.

```sql
CREATE TABLE capacity_restriction (
    restriction_ref uuid PRIMARY KEY,
    evse_ref uuid NOT NULL
        REFERENCES evse_allocation_guard(evse_ref),
    restriction_kind varchar(32) NOT NULL,
    scope_type varchar(32),
    scope_ref uuid,
    effective_interval tstzrange NOT NULL,
    source_ref uuid NOT NULL,
    source_workflow_version bigint NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    state varchar(16) NOT NULL,
    reason text,
    authorized_by varchar(128),
    authorized_at timestamptz,
    frozen_at timestamptz NOT NULL,
    blocked_at timestamptz,
    released_at timestamptz,
    version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,

    CONSTRAINT ck_restriction_kind CHECK (
        restriction_kind IN ('MAINTENANCE', 'EMERGENCY', 'OPERATOR_RESTRICTION', 'ADMINISTRATIVE_CLOSURE', 'ORGANIZATION_BLOCK')
    ),
    CONSTRAINT ck_restriction_state CHECK (
        state IN ('FREEZE', 'BLOCKED', 'RELEASED')
    ),
    CONSTRAINT ck_restriction_state_consistency CHECK (
        (state = 'FREEZE' AND blocked_at IS NULL AND released_at IS NULL)
        OR (state = 'BLOCKED' AND blocked_at IS NOT NULL AND released_at IS NULL)
        OR (state = 'RELEASED' AND released_at IS NOT NULL)
    ),
    CONSTRAINT ck_emergency_authorization CHECK (
        (restriction_kind = 'EMERGENCY' AND authorized_by IS NOT NULL AND authorized_at IS NOT NULL)
        OR (restriction_kind <> 'EMERGENCY')
    ),
    CONSTRAINT uq_restriction_idempotency UNIQUE (evse_ref, idempotency_key),
    CONSTRAINT ck_restriction_interval CHECK (
        NOT isempty(effective_interval)
        AND lower_inc(effective_interval)
        AND NOT upper_inc(effective_interval)
    )
);
```

**State-field consistency** (`ck_restriction_state_consistency`): validates that the timestamp columns are consistent with the current state value. It does not enforce state *transitions* (SQL CHECK cannot reference previous row values). Transition enforcement is provided by:

- Aggregate/service transition logic that rejects forbidden transitions (e.g. `BLOCKED → FREEZE`).
- Optimistic version checks (`version` incremented on every update; stale versions are rejected).
- Automated forbidden-transition tests that prove each disallowed transition is rejected.

**Scope fields** (`scope_type`, `scope_ref`): support station-wide (`STATION`) or organization-wide (`ORGANIZATION`) restrictions that fan out to EVSE-level enforcement. Each fan-out creates one `capacity_restriction` row per EVSE.

**Restriction-type mapping to ARC-019 taxonomy:**

| ARC-019 type | ARC-006 restriction_kind | Notes |
|---|---|---|
| Maintenance | `MAINTENANCE` | Scheduled maintenance block |
| Station closure | `ADMINISTRATIVE_CLOSURE` | Station-wide; fans out to one restriction per EVSE |
| EVSE deactivation | `ADMINISTRATIVE_CLOSURE` | Single-EVSE administrative closure |
| Organization suspension | `ORGANIZATION_BLOCK` | Organization-wide; blocks all EVSE access for that operator |
| Emergency | `EMERGENCY` | Emergency block (requires authorized audit record) |
| Operator restriction | `OPERATOR_RESTRICTION` | General operator-initiated restriction |

**Freeze enforcement model:** During `FREEZE`, no `capacity_claim` is inserted. Enforcement uses a query-based model: every allocation transaction MUST query overlapping `FREEZE`/`BLOCKED` `capacity_restriction` rows while holding the EVSE guard lock. If an overlapping unreleased restriction exists, the allocation is rejected with `MAINTENANCE_CONFLICT`. The `capacity_claim` is inserted only at `BLOCKED` time (Phase C).

Permitted states:

- `FREEZE` — Restriction is planned or imminent; allocation queries reject overlapping intervals. No capacity_claim is inserted at this phase.
- `BLOCKED` — Restriction is active; maintenance/emergency may proceed.
- `RELEASED` — Restriction lifted; capacity_claim released; EVSE available for allocation.

Permitted transitions:

- `FREEZE` → `BLOCKED` — Maintenance window starts or emergency goes active.
- `FREEZE` → `RELEASED` — Restriction cancelled before taking effect.
- `BLOCKED` → `RELEASED` — Maintenance/emergency completed.

The `capacity_restriction` aggregate is write-only authoritative for the freeze/block protocol; the `capacity_claim` table is the interval-based enforcement mechanism.

---

## 4.7 `driver_schedule_claim`

```sql
CREATE TABLE driver_schedule_claim (
    claim_ref uuid PRIMARY KEY,
    account_ref uuid NOT NULL
        REFERENCES driver_schedule_guard(account_ref),
    booking_ref uuid NOT NULL,
    claim_kind varchar(16) NOT NULL,
    charging_interval tstzrange NOT NULL,
    state varchar(16) NOT NULL,
    hold_expires_at timestamptz,
    created_at timestamptz NOT NULL,
    released_at timestamptz,

    CONSTRAINT ck_driver_claim_kind CHECK (
        claim_kind IN ('BOOKING_HOLD', 'BOOKING')
    ),
    CONSTRAINT ck_driver_claim_state CHECK (
        state IN ('ACTIVE', 'RELEASED')
    )
);
```

Confirmed driver schedules receive an exclusion constraint:

```sql
ALTER TABLE driver_schedule_claim
ADD CONSTRAINT ex_driver_schedule_overlap
EXCLUDE USING gist (
    account_ref WITH =,
    charging_interval WITH &&
)
WHERE (
    state = 'ACTIVE'
    AND claim_kind = 'BOOKING'
);
```

Unexpired driver Holds are serialized and checked under `driver_schedule_guard`.

---

## 4.8 `operational_occupation`

```sql
CREATE TABLE operational_occupation (
    occupation_ref uuid PRIMARY KEY,
    evse_ref uuid NOT NULL
        REFERENCES evse_allocation_guard(evse_ref),
    session_ref uuid NOT NULL,
    attempt_ref uuid NOT NULL,
    booking_ref uuid NOT NULL,
    account_ref uuid NOT NULL,
    state varchar(16) NOT NULL,
    blocking_interval tstzrange NOT NULL,
    last_evidence_at timestamptz,
    released_at timestamptz,
    reconciliation_ref uuid,
    version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,

    CONSTRAINT ck_occupation_state CHECK (
        state IN ('ACTIVE', 'UNCERTAIN', 'RELEASED')
    ),
    CONSTRAINT ck_occupation_interval CHECK (
        NOT isempty(blocking_interval)
        AND lower_inc(blocking_interval)
        AND NOT upper_inc(blocking_interval)
        AND NOT lower_inf(blocking_interval)
    ),
    CONSTRAINT fk_occupation_attempt FOREIGN KEY (attempt_ref) REFERENCES session_attempt(attempt_ref)
);
```

At most one unresolved occupation may exist per EVSE:

```sql
CREATE UNIQUE INDEX ux_unreleased_occupation_evse
ON operational_occupation (evse_ref)
WHERE state IN ('ACTIVE', 'UNCERTAIN');
```

An unbounded upper interval is allowed only when physical release is uncertain.

## 4.9 `session_attempt`

```sql
CREATE TABLE session_attempt (
    attempt_ref uuid PRIMARY KEY,
    session_ref uuid NOT NULL,
    booking_ref uuid NOT NULL,
    attempt_number int NOT NULL,
    authorization_id uuid,
    authorization_consumed_at timestamptz,
    command_ref uuid,
    state varchar(32) NOT NULL,
    outcome varchar(32),
    version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,

    CONSTRAINT uq_session_attempt UNIQUE (session_ref, attempt_number),
    CONSTRAINT ck_attempt_state CHECK (
        state IN ('AUTHORIZING', 'STARTING', 'DEVICE_ACCEPTED', 'TIMED_OUT',
                  'RECONCILING', 'TRANSACTION_STARTED', 'ATTEMPT_REJECTED',
                  'UNRESOLVED_REQUIRES_ACTION')
    )
);
```

- `session_ref` — the ChargingSession this attempt belongs to.
- `booking_ref` — the Booking being fulfilled.
- `attempt_number` — monotonically increasing per session.
- `authorization_id` — the start authorization consumed for this attempt.
- `authorization_consumed_at` — when consumption committed.
- `command_ref` — the outbox command reference.
- `state` — from the SessionAttempt lifecycle (DOM-002 §1.2).
- `outcome` — terminal outcome classification.
- The unique constraint `(session_ref, attempt_number)` ensures ordering.

---

# 5. Effective intervals

## 5.1 Booking charging interval

The driver’s planned usage:

```text
[scheduledStart, scheduledEnd)
```

## 5.2 Booking allocation interval

The planned exclusive interval:

```text
[scheduledStart, scheduledEnd + turnaroundBuffer)
```

The post-booking turnaround buffer is taken from the Booking’s immutable Policy Snapshot.

## 5.3 Driver schedule interval

Driver overlap prevention uses the charging interval, not the post-booking EVSE buffer.

## 5.4 Operational blocking interval

At start intent:

```text
[scheduledStart, plannedAllocationEnd)
```

After confirmed activity observed at time `t`:

```text
upper = max(existingUpper, t + turnaroundBuffer)
```

After definitive end at `actualEnd`:

```text
[actualStart, actualEnd + turnaroundBuffer)
```

If the physical outcome becomes uncertain, the upper bound may become unbounded until reconciliation.

---

# 6. Isolation level

## 6.1 Selected isolation

Allocation transactions use:

```sql
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
```

`READ COMMITTED` is PostgreSQL’s default isolation level. Each statement observes data committed before that statement begins. ([postgresql.org](https://www.postgresql.org/docs/16/transaction-iso.html))

Correctness does not depend on an unlocked multi-statement snapshot. Instead:

1. Guard rows are locked.
2. Relevant records are re-read after locking.
3. Conflicts are checked.
4. Claims are inserted or changed.
5. Exclusion and unique constraints provide final enforcement.
6. The transaction commits atomically.

## 6.2 Why not `SERIALIZABLE` by default

`SERIALIZABLE` provides stronger general anomaly prevention but requires full-transaction retries when serialization failures occur. The explicit guards make the allocation decision serial per driver and EVSE without imposing serializable overhead on every operation. ([postgresql.org](https://www.postgresql.org/docs/16/transaction-iso.html))

`SERIALIZABLE` may be enabled in selected verification tests to compare outcomes, but it is not the production correctness mechanism.

---

# 7. Global lock order

Every transaction follows this order:

1. Idempotency record
2. `driver_schedule_guard` rows, ordered by `account_ref`
3. `evse_allocation_guard` rows, ordered by `evse_ref`
4. Booking rows, ordered by `booking_ref`
5. Charging Session rows, ordered by `session_ref`
6. Capacity and driver claim rows
7. Operational occupation rows

Rules:

- No transaction may lock a Booking before its required driver and EVSE guards.
- Multiple driver or EVSE guards are locked in ascending UUID order.
- The strongest required row lock is acquired first.
- No remote call occurs while locks are held.
- No broker or device response is awaited inside the transaction.
- Transactions remain short.

Consistent lock ordering is PostgreSQL’s primary recommended deadlock-avoidance technique. ([postgresql.org](https://www.postgresql.org/docs/17/explicit-locking.html))

---

# 8. Database-time rule

Every transaction obtains one database instant:

```sql
SELECT clock_timestamp() AS db_now;
```

That value is reused throughout the transaction for:

- Hold validity
- Check-in deadlines
- Authorization expiry
- No-show eligibility
- Release timestamps
- Occupation extension

No client-supplied time determines validity.

---

# 9. Idempotency transaction

Before business locks:

1. Insert the operation’s idempotency record.
2. If the key already exists, lock the existing record.
3. Compare request fingerprints.
4. Return the stored outcome for an exact retry.
5. Reject changed payload reuse.
6. Continue only for the first valid execution.

The idempotency result commits in the same transaction as:

- Booking state
- Claims
- Audit evidence
- Outbox records

A database failure cannot commit the Booking without its retry result.

---

# 10. Conflict predicate

A requested EVSE interval conflicts when any of these is true:

1. An active non-Hold `capacity_claim` overlaps.
2. An active unexpired Hold overlaps.
3. An active or uncertain `operational_occupation` overlaps.
4. An unreleased `capacity_restriction` in `FREEZE` or `BLOCKED` state overlaps.
5. The EVSE guard is disabled.
6. Required enforcement projections fail validation.

Conceptually:

```sql
SELECT EXISTS (
    SELECT 1
    FROM capacity_claim
    WHERE evse_ref = :evseRef
      AND state = 'ACTIVE'
      AND effective_interval && :requestedInterval
      AND (
          claim_kind <> 'BOOKING_HOLD'
          OR hold_expires_at > :dbNow
      )
)
OR EXISTS (
    SELECT 1
    FROM operational_occupation
    WHERE evse_ref = :evseRef
      AND state IN ('ACTIVE', 'UNCERTAIN')
      AND blocking_interval && :requestedInterval
)
OR EXISTS (
    SELECT 1
    FROM capacity_restriction
    WHERE evse_ref = :evseRef
      AND state IN ('FREEZE', 'BLOCKED')
      AND effective_interval && :requestedInterval
);
```

The query executes only after locking the EVSE guard.

---

# 11. Lazy expired-Hold release

Expired Holds are ignored immediately, even if the lifecycle worker has not processed them.

While holding the EVSE guard, allocation may mark overlapping expired Hold claims `RELEASED`.

It does not need to transition the corresponding Booking in the same transaction.

Consequences:

- The old Hold stops blocking immediately.
- Later confirmation fails because database time is past `hold_expires_at`.
- The expiration worker eventually transitions the Booking to `EXPIRED`.
- Duplicate release and expiration processing remain idempotent.

While holding a driver guard, the same lazy release applies to expired `driver_schedule_claim` rows.

This prevents delayed cleanup from extending Hold validity.

---

# 12. Exact EVSE Hold creation

## 12.1 Transaction

1. Begin transaction.
2. Claim idempotency record.
3. Obtain `db_now`.
4. Lock the driver guard.
5. Validate account booking eligibility.
6. Lock the requested EVSE guard.
7. Re-read bookable infrastructure and device enforcement projections.
8. Lazily release expired EVSE and driver Hold claims.
9. Validate compatibility, policy, opening hours and freshness.
10. Check capacity claims, operational occupation, and unreleased capacity restrictions (FREEZE/BLOCKED).
11. Insert Booking in `HELD`.
12. Insert `BOOKING_HOLD` capacity claim.
13. Insert driver Hold claim.
14. Update `last_allocated_at`.
15. Insert audit and outbox records.
16. Complete idempotency result.
17. Commit.

## 12.2 Concurrent outcome

When multiple transactions request the same EVSE and interval:

- One locks the EVSE guard first.
- It inserts the Hold and commits.
- The next transaction revalidates and sees the unexpired Hold.
- The next transaction receives `EVSE_ALLOCATION_CONFLICT`.

Exactly one succeeds.

---

# 13. Automatic EVSE assignment

## 13.1 Candidate construction

Candidates come only from Booking-local enforcement projections.

They must already satisfy:

- Station
- Administrative eligibility
- Connector type
- Minimum power
- Opening hours
- Policy
- Maintenance and fault restrictions
- Near-term status requirements

## 13.2 Fair candidate order

Default order:

1. Lowest operational risk
2. Oldest `last_allocated_at`
3. Lowest deterministic EVSE reference

The order is stable and testable.

## 13.3 Lock acquisition

Candidate selection may use:

```sql
SELECT g.evse_ref
FROM evse_allocation_guard g
JOIN bookable_evse_projection p
  ON p.evse_ref = g.evse_ref
WHERE p.station_ref = :stationRef
  AND g.allocation_enabled = true
  AND ...
ORDER BY
  p.operational_risk ASC,
  g.last_allocated_at ASC NULLS FIRST,
  g.evse_ref ASC
FOR UPDATE OF g SKIP LOCKED
LIMIT 1;
```

`SKIP LOCKED` can return an inconsistent view and is not suitable as a general availability decision. PostgreSQL documents it primarily as a contention-avoidance mechanism for queue-like access. ([postgresql.org](https://www.postgresql.org/docs/current/sql-select.html))

Therefore:

- It is used only to avoid waiting on contended candidates.
- Every selected candidate is fully revalidated after locking.
- No result is declared unavailable solely because candidates were skipped.
- If no candidate is obtained, the operation retries after bounded jitter.
- Exhausted contention returns `ALLOCATION_BUSY`, not `NO_COMPATIBLE_EVSE`.

## 13.4 Candidate retry

A failed candidate attempt rolls back completely.

The service retries the whole transaction with another candidate, preserving:

- The same external idempotency key
- A new internal attempt number
- A bounded candidate-attempt count

Recommended initial maximum: five candidate attempts.

---

# 14. Hold confirmation

## 14.1 Transaction

1. Begin transaction.
2. Claim idempotency record.
3. Read Booking references required for lock planning.
4. Lock driver guard.
5. Lock assigned EVSE guard.
6. Lock Booking.
7. Obtain `db_now`.
8. Revalidate Booking state and references.
9. If already `CONFIRMED`, return stored result.
10. Require `state = HELD`.
11. Require `db_now < hold_expires_at`.
12. Require active Hold capacity and driver claims.
13. Require no overlapping unreleased capacity restriction (FREEZE/BLOCKED).
14. Persist immutable Tariff and Policy Snapshots.
15. Change capacity claim kind from `BOOKING_HOLD` to `BOOKING_ALLOCATION`.
16. Change driver claim kind from `BOOKING_HOLD` to `BOOKING`.
17. Transition Booking to `CONFIRMED`.
18. Write audit, outbox and idempotency outcome.
19. Commit.

Converting the Hold activates the exclusion constraints.

Any unexpected exclusion violation aborts the whole transaction and maps to `EVSE_ALLOCATION_CONFLICT`.

## 14.2 Confirmation versus expiry

- If confirmation locks first and the Hold is valid, confirmation commits.
- If expiration or another allocation first releases the expired claim, confirmation fails.
- The database-time comparison is final.
- A cleanup delay cannot rescue an expired Hold.

---

# 15. Hold-expiration worker

Workers select candidates using:

```sql
SELECT booking_ref
FROM booking
WHERE state = 'HELD'
  AND hold_expires_at <= clock_timestamp()
ORDER BY hold_expires_at, booking_ref
FOR UPDATE SKIP LOCKED
LIMIT :batchSize;
```

For each Booking:

1. Plan required driver and EVSE references.
2. Start a per-item transaction.
3. Lock driver guard.
4. Lock EVSE guard.
5. Lock Booking.
6. Re-read state and deadline using database time.
7. If still expired and `HELD`, transition to `EXPIRED`.
8. Release active capacity and driver claims.
9. Record audit and outbox.
10. Commit.

`SKIP LOCKED` is appropriate here because workers are consuming queue-like lifecycle work, and skipped records remain eligible for another pass. ([postgresql.org](https://www.postgresql.org/docs/current/sql-select.html))

---

# 16. Cancellation

## 16.1 Unused Booking

For `HELD` or `CONFIRMED` without check-in or physical use:

1. Lock driver guard.
2. Lock EVSE guard.
3. Lock Booking.
4. Validate cancellation.
5. Transition Booking to `CANCELLED`.
6. Release complete capacity and driver claims immediately.
7. Revoke any unconsumed Start Authorization.
8. Write audit/outbox/idempotency result.
9. Commit.

No turnaround buffer is retained for unused capacity.

## 16.2 Checked-in Booking

Driver cancellation first performs check-in abandonment.

Authorized operational cancellation may transition directly from `CHECKED_IN` while atomically revoking Start Authorization.

## 16.3 Active Session

An `ACTIVE` Booking cannot be cancelled.

The Session must use the stop/interruption workflow.

---

# 17. No-show processing

No-show and charging start use the same lock order:

1. Driver guard
2. EVSE guard
3. Booking
4. Session/authorization

The no-show worker requires:

- Grace deadline passed
- Booking remains eligible for no-show
- No accepted physical start evidence
- No unresolved start command
- No equipment/platform failure
- No active operational-resolution workflow

If start processing commits first, no-show fails.

If no-show commits first, subsequent ordinary start fails.

Equipment failure routes to `FULFILMENT_FAILED`, never `NO_SHOW`.

---

# 18. Atomic rescheduling

## 18.1 Candidate planning

The proposed replacement EVSE is selected before acquiring transactional EVSE locks.

The candidate is advisory until locked and revalidated.

## 18.2 Lock set

The transaction locks:

1. Driver guard
2. Old and new EVSE guards in ascending UUID order
3. Booking
4. Existing claim rows

If the Booking’s current EVSE/version differs from the pre-read value, the transaction rolls back and restarts.

## 18.3 Transaction

1. Validate current Booking state and version.
2. Validate replacement interval and EVSE.
3. Lazily release relevant expired Holds.
4. Check replacement EVSE claims, occupation, and unreleased capacity restrictions (FREEZE/BLOCKED).
5. Check driver schedule conflicts, excluding the current Booking.
6. Mark old capacity and driver claims `RELEASED`.
7. Insert new active `BOOKING_ALLOCATION` claims.
8. Update Booking assignment and schedule.
9. Increment Booking version.
10. Write audit and outbox.
11. Commit.

The old claims are released before insertion only inside the same transaction.

Other transactions cannot observe the release before commit.

If insertion or any later step fails, rollback restores the original active claims automatically.

## 18.4 Same-EVSE rescheduling

The same transaction and lock order apply.

Releasing the old claim before inserting the replacement allows the exclusion constraint to validate the new interval.

---

# 19. Reassignment

Reassignment uses the rescheduling transaction pattern but normally preserves:

- Scheduled start
- Scheduled end
- Required connector type
- Tariff rules, unless an approved replacement snapshot applies

The old EVSE is released only when the replacement claim commits.

Any Start Authorization bound to the old EVSE is revoked atomically.

A failed reassignment preserves the original assignment.

---

# 20. Maintenance and emergency block installation

All maintenance and emergency blocks follow a unified FREEZE → BLOCKED → RELEASED lifecycle through the `capacity_restriction` aggregate. The `capacity_claim` table provides interval-based enforcement.

## 20.1 Lock acquisition

1. All affected EVSE guards are locked in ascending UUID order.
2. Driver guards are NOT locked unless the workflow modifies driver Bookings (e.g., emergency reassignment).

## 20.2 Normal maintenance — freeze-to-release algorithm

*Key constraint:* The exclusion constraint (`ex_capacity_claim_evse_interval`) prevents overlapping active claims for confirmed bookings and maintenance blocks. Therefore a `MAINTENANCE_BLOCK` claim cannot be inserted until existing overlapping claims have been released. The algorithm separates restriction metadata from interval enforcement across two phases.

### Phase A — Freeze (DRAFT → FREEZE transition)

Triggered when Station Operations requests a capacity freeze (before maintenance is SCHEDULED).

1. Validate source workflow identity and version against the Station Operations maintenance record.
2. Lock all target EVSE guards in ascending UUID order.
3. Create one `capacity_restriction` row per target EVSE in `FREEZE` state, each with:
   - `restriction_kind = 'MAINTENANCE'`
   - `effective_interval` matching the planned maintenance window
   - `idempotency_key` from the source workflow
4. Do NOT insert a `capacity_claim` at this point. The `FREEZE` restriction alone does not enforce interval exclusion — it is a metadata record that signals intent.
5. Return affected obligation summary to Station Operations (existing overlapping Holds, Bookings, unresolved sessions detected by querying active `capacity_claim` rows that overlap `effective_interval`).
6. Commit under the EVSE guard lock.
7. Publish `CapacityFreezeCommitted` event.

*Freeze enforcement:* During `FREEZE`, every allocation transaction MUST query overlapping `FREEZE`/`BLOCKED` `capacity_restriction` rows while holding the EVSE guard lock. If an overlapping unreleased restriction exists, the allocation is rejected with `MAINTENANCE_CONFLICT`. This query is necessary because the exclusion constraint alone cannot express "block new claims while allowing existing claims to remain."

### Phase B — Obligation resolution

Before transitioning FREEZE → BLOCKED, Station Operations must resolve affected obligations:

- Overlapping Holds: wait for expiry or proactively cancel and notify the driver.
- Confirmed Bookings: reschedule to a non-overlapping slot or cancel with operator/driver consent. Each resolved Booking releases its `capacity_claim`.
- Unresolved sessions: await resolution or escalate through the emergency path.

Phase B may involve multiple human-mediated steps and is not a single transaction. Each resolution releases the overlapping Booking's capacity claim, making room for the eventual `MAINTENANCE_BLOCK` claim.

### Phase C — Block (FREEZE → BLOCKED transition)

Triggered when Station Operations confirms obligations are resolved.

1. Lock target EVSE guards (same order as Phase A).
2. Verify each `capacity_restriction` is in `FREEZE` state with matching version.
3. For each EVSE, verify that no new overlapping claims have appeared since the freeze. Reject if new conflicts exist.
4. Insert one `capacity_claim` per target EVSE with `claim_kind = 'MAINTENANCE_BLOCK'` and `effective_interval` matching the restriction interval. This claim now passes the exclusion constraint because overlapping claims were released in Phase B.
5. Transition each `capacity_restriction` to `BLOCKED` (set `blocked_at = now()`).
6. Commit.
7. Publish `CapacityBlockCommitted` event.
8. Station Operations transitions the maintenance record to SCHEDULED.

### Phase D — Activation (BLOCKED → maintenance ACTIVE)

When maintenance window opens:

1. Station Operations transitions maintenance to ACTIVE.
2. The capacity_claim and capacity_restriction remain in BLOCKED.

### Phase E — Release (BLOCKED → RELEASED transition)

Triggered when Station Operations reports maintenance complete or cancelled.

1. Lock target EVSE guards (same order as Phase A).
2. Verify each `capacity_restriction` is in `BLOCKED` or `FREEZE` state with matching version.
3. Verify no active session currently occupies the EVSE (check `operational_occupation` for unreleased rows).
4. Transition each `capacity_restriction` to `RELEASED` (set `released_at = now()`).
5. Release the corresponding `capacity_claim` rows (`state = 'RELEASED'`, `released_at = now()`).
6. Commit.
7. Publish `CapacityRestrictionReleased` event.

## 20.3 Emergency block

Emergency activation must commit an immediate restriction before the maintenance state changes. The emergency path compresses Phases A–C into a single transaction, but must respect the exclusion constraint: `EMERGENCY_BLOCK` claims cannot be inserted while overlapping Booking claims exist.

Inside one guarded transaction:

1. Lock target EVSE guards in ascending UUID order.
2. Create `capacity_restriction` rows in `FREEZE` state with `restriction_kind = 'EMERGENCY'`, `authorized_by` and `authorized_at` populated from the emergency authorization record.
3. Identify all overlapping Bookings/sessions by querying active `capacity_claim` rows that overlap `effective_interval`.
4. For each overlapping Booking, handle according to its lifecycle state:
   - **HELD or CONFIRMED:** Cancel the Booking. Release its `capacity_claim` and `driver_schedule_claim`. Emit `BookingCancelled` with emergency reason.
   - **CHECKED_IN (no active session):** Cancel the Booking. Release claims. Emit `BookingCancelled`.
   - **ACTIVE (session in CHARGING):** Do NOT cancel the Booking. The `operational_occupation` remains. Mark the session for emergency stop (session-level `emergency_stop_requested` flag). The session will complete or interrupt naturally; only after the session ends and the occupation releases can the block be fully active. The `capacity_restriction` covers the planned interval; the `EMERGENCY_BLOCK` claim is inserted for the portion of the interval that does not overlap the active session.
5. For Bookings that were cancelled/released (steps 4a–4b), the overlapping `capacity_claim` rows are now released. For the remaining active-session overlap, the `EMERGENCY_BLOCK` claim is inserted for the non-overlapping part. (The active session's `operational_occupation` remains the blocking authority for its own interval.)
6. Insert `EMERGENCY_BLOCK` capacity_claim rows for the effective interval (minus any active-session overlap that cannot be released).
7. Transition each `capacity_restriction` to `BLOCKED` (set `blocked_at = now()`).
8. Commit.
9. Publish `CapacityRestrictionCreated` domain event (restriction_kind=EMERGENCY) with affected Booking/session summary.

Emergency intervention cannot silently delete existing claims. An ACTIVE session's Booking is never silently cancelled — the session must terminate naturally or through an emergency stop command.

## 20.4 Race outcome

Maintenance/emergency and Booking allocation serialize on the EVSE guard:

- **Hold or Booking acquired first:** the FREEZE phase detects overlapping `capacity_claim` rows via the overlap query and reports them as affected obligations. The exclusion constraint protects existing claims during Phase B/C.
- **FREEZE restriction acquired first:** every allocation transaction queries overlapping `FREEZE`/`BLOCKED` `capacity_restriction` rows while holding the EVSE guard lock. If an overlapping unreleased restriction exists, the allocation is rejected with `MAINTENANCE_CONFLICT`. This query is the enforcement mechanism during the FREEZE phase.
- **BLOCKED restriction + capacity_claim acquired first:** the exclusion constraint (`ex_capacity_claim_evse_interval`) directly rejects any new overlapping `BOOKING_ALLOCATION` or `BOOKING_HOLD` claim with `MAINTENANCE_CONFLICT` or `EVSE_ALLOCATION_CONFLICT`.
- No both-success outcome is possible for conflicting intervals.

---

# 21. Charging start intent

When start is requested:

1. Lock driver guard.
2. Lock EVSE guard.
3. Lock Booking.
4. Lock or create Charging Session (reuse existing if in start-pending state).
5. Create SessionAttempt in `AUTHORIZING` → `STARTING`.
5a. Create `session_attempt` row with `attempt_number = previous + 1`, `state = AUTHORIZING`.
5b. If this is a retry (`previous attempt = ATTEMPT_REJECTED` and retry policy permits), create a new authorization: new reference, new secret/hash, same Booking/driver/EVSE, expiry = original grace deadline, next attempt number.
6. Validate and consume Start Authorization (consumption commits with this transaction — DOM-002 §1.2a).
7. Insert `operational_occupation` in `UNCERTAIN`.
8. Use the Booking allocation interval as its initial finite blocking interval.
9. Write the `StartChargingAtEVSE` command to the Outbox.
10. Commit.

The record is `UNCERTAIN` because the physical command may execute before acknowledgement is received.

A definitive rejection releases the occupation and terminates this SessionAttempt (`ATTEMPT_REJECTED`). Booking stays `CHECKED_IN`; a new authorization and new SessionAttempt may be created for retry.

A timeout or disconnection may make its upper bound unbounded until reconciliation.

---

# 22. Confirmed physical start

On accepted `DeviceTransactionStarted` evidence:

1. Lock driver guard.
2. Lock EVSE guard.
3. Lock Booking.
4. Lock SessionAttempt, Session and occupation.
5. Deduplicate device evidence.
6. Require SessionAttempt `STARTING` or `RECONCILING`.
7. Transition SessionAttempt to `TRANSACTION_STARTED`.
8. Transition ChargingSession to `CHARGING`.
9. Transition Booking to `ACTIVE`.
10. Change occupation state to `ACTIVE`.
11. Extend blocking upper bound to at least:
    - Planned allocation end, or
    - Evidence time plus turnaround buffer
12. Write audit and outbox.
13. Commit.

Only physical transaction-start evidence performs these transitions.

---

# 23. Occupation extension and overrun

When accepted evidence proves the EVSE remains occupied near or beyond the current upper bound:

1. Lock the EVSE guard.
2. Lock occupation and Session.
3. Extend the blocking interval to at least:
   - Evidence time plus turnaround buffer
4. Query active future capacity claims now overlapped.
5. Create idempotent Fulfilment Risk records.
6. Publish affected-Booking events.
7. Commit.

Existing future capacity claims remain durable.

The extension does not fail because occupation is intentionally outside the planned-capacity exclusion constraint.

After commit:

- New overlapping allocations fail.
- Existing affected Bookings enter reassignment or operational-resolution workflows.

---

# 24. Device uncertainty

The occupation becomes or remains `UNCERTAIN` when:

- Start command outcome is unknown
- Stop command outcome is unknown
- Device disconnects during an open Session
- Sequence gaps prevent safe reconstruction
- Physical transaction state conflicts with platform state

Where no defensible release limit exists, the blocking interval becomes upper-unbounded.

No new future allocation may overlap an unbounded uncertain occupation.

Only reconciled physical evidence or authorized operational resolution may restore a finite end.

---

# 25. Session end and release

On definitive `DeviceTransactionEnded` evidence:

1. Lock driver guard.
2. Lock EVSE guard.
3. Lock Booking.
4. Lock Session and occupation.
5. Deduplicate evidence.
6. Set final Session outcome.
7. Set occupation upper bound to actual end plus turnaround buffer.
8. Release the planned Booking capacity claim.
9. Release the driver schedule claim.
10. Transition Booking to `COMPLETED`.
11. Finalize Session summary where data is complete.
12. Record affected later Bookings if overrun occurred.
13. Write audit and outbox.
14. Commit.

The occupation remains unreleased until its finite upper bound passes.

A release worker then transitions it to `RELEASED`.

New Bookings beginning after the known occupation upper bound may be accepted immediately.

---

# 26. Operational occupation versus new allocation

After locking the EVSE guard, new allocation checks:

```sql
SELECT 1
FROM operational_occupation
WHERE evse_ref = :evseRef
  AND state IN ('ACTIVE', 'UNCERTAIN')
  AND blocking_interval && :requestedInterval
LIMIT 1;
```

If found:

- Near-term result: `EVSE_ALLOCATION_CONFLICT`
- Advisory public result: `UNAVAILABLE` or `UNKNOWN`, according to confidence
- Exact reason remains privacy-safe

An occupation does not automatically conflict with every future interval unless its upper bound is unbounded.

---

# 27. Lock waiting policy

## 27.1 Exact EVSE operations

Use a bounded lock timeout.

Proposed initial value:

```sql
SET LOCAL lock_timeout = '750ms';
```

A timeout returns:

- `ALLOCATION_BUSY`, or
- A retryable internal conflict

It must not return `UNAVAILABLE`.

## 27.2 Automatic assignment

Use `SKIP LOCKED` only as a contention optimization, followed by bounded whole-transaction retries.

## 27.3 Administrative bulk operations

Maintenance and station-wide restrictions may use a longer bounded timeout because they acquire multiple ordered EVSE locks.

No transaction waits indefinitely.

---

# 28. Database error mapping

| SQLSTATE | Meaning | Handling |
|---|---|---|
| `23P01` | Exclusion violation | `EVSE_ALLOCATION_CONFLICT`; automatic assignment may try another candidate |
| `23505` | Unique violation | Idempotency/duplicate/invariant-specific mapping |
| `40P01` | Deadlock detected | Retry whole transaction |
| `40001` | Serialization failure | Retry whole transaction |
| `55P03` | Lock not available | Retry or return `ALLOCATION_BUSY` |
| `57014` | Statement cancelled/timeout | Retry only where operation remains safe |

PostgreSQL identifies serialization failures as `40001`, deadlocks as `40P01`, and exclusion violations as `23P01`; retries must restart the whole transaction where applicable. ([postgresql.org](https://www.postgresql.org/docs/17/mvcc-serialization-failure-handling.html))

Database error text is never exposed to clients.

---

# 29. Retry policy

Automatic transaction retries apply only to:

- `40P01`
- `40001`
- Selected `55P03`
- Automatic-assignment `23P01` with another candidate

Initial policy:

- Maximum three transaction retries
- Exponential backoff with jitter
- Same idempotency key
- Entire transaction restarted
- Fresh database time obtained
- Projections and aggregate versions re-read

Do not automatically retry:

- Stale client `If-Match`
- Expired Hold
- Invalid lifecycle transition
- Exact-EVSE allocation conflict
- Authorization failure
- Missing critical projection

PostgreSQL does not automatically retry failed transactions because only the application can safely repeat the complete business logic. ([postgresql.org](https://www.postgresql.org/docs/17/mvcc-serialization-failure-handling.html))

---

# 30. Constraint violation as final safety net

Application prechecks provide useful domain errors, but they are not authoritative by themselves.

The final planned-capacity decision is:

```text
Guard lock + revalidation + successful datastore constraint enforcement + commit
```

A successful precheck followed by an exclusion violation is treated as an ordinary concurrent conflict, not an internal corruption.

Repeated unexpected violations after guard acquisition trigger an alert because they may indicate:

- Missing guard usage
- Direct database write
- Incorrect lock order
- Projection error
- Defective migration
- Unsupported administrative path

---

# 31. Deadlock prevention

Mandatory controls:

1. Global lock order
2. Sorted UUID locking
3. Short transactions
4. No network calls while locked
5. Bounded lock timeout
6. Whole-transaction deadlock retry
7. Deadlock metrics and structured diagnostics
8. Concurrency tests covering every lock combination

Important lock combinations:

- Confirmation versus expiry
- Cancellation versus start
- No-show versus start
- Reschedule versus cancellation
- Reassignment versus check-in
- Maintenance versus Hold creation
- Maintenance versus reschedule
- Occupation extension versus new allocation
- Session end versus maintenance
- Suspension versus Booking creation

---

# 32. Crash and recovery behaviour

## Crash before commit

No state, claim, outbox or idempotency result is visible.

The client may retry with the same key.

## Crash after commit before response

Retry returns the committed idempotency result.

## Crash after claim commit before event publication

Outbox publication resumes.

## Crash during Hold cleanup

Expired Holds remain logically invalid because allocation checks database time.

## Crash during rescheduling

Atomic rollback leaves either:

- Original allocation, or
- Fully committed replacement

No intermediate release is visible.

## Database restoration

Before traffic resumes:

1. Validate constraints and extensions.
2. Reapply privacy tombstones.
3. Process expired Holds.
4. Re-evaluate open occupations.
5. Set unresolved device state to unknown.
6. Resume Outbox publication.
7. Reconcile incomplete workflows.

---

# 33. Migration requirements

The following objects are release-blocking:

- `btree_gist`
- Guard tables
- Capacity claim table
- Driver claim table
- Operational occupation table
- Named exclusion constraints
- Required partial unique indexes
- Outbox, inbox and idempotency tables

Deployment validation must confirm:

- Extension installed
- Constraints valid
- Constraints not disabled
- Correct operator classes
- Correct half-open range construction
- No overlapping pre-existing rows
- Application runtime lacks constraint-altering privileges

Any migration changing allocation constraints requires:

1. Offline or shadow validation
2. Concurrency regression tests
3. Rollback/forward-correction plan
4. Architecture approval
5. Requirement and invariant impact review

---

# 34. Exact test strategy

## 34.1 Database integration tests

Run against the selected real PostgreSQL major version.

Required cases:

- Adjacent intervals succeed
- One-millisecond overlap fails
- Buffer overlap fails
- Different EVSE overlap succeeds
- Released claim does not block
- Unexpired Hold blocks
- Expired Hold does not block
- Confirming an expired Hold fails
- Maintenance conflicts with Booking
- Occupation overlaps existing future Booking without insert failure
- New claim overlapping occupation fails
- Unbounded uncertainty blocks applicable future intervals

## 34.2 Concurrency tests

For each scenario, execute at least 1,000 repeated races:

- 100 simultaneous exact-EVSE Holds
- Confirmation versus expiration
- Two reschedules to the same EVSE
- Reschedule versus cancellation
- Check-in/start versus no-show
- Maintenance versus Booking creation
- Reassignment contention
- Session overrun versus new Hold
- Stop completion versus new allocation
- Duplicate idempotency requests

Expected result:

- Exactly one valid winner where operations conflict
- No invalid overlapping claim state
- No lost original allocation
- No duplicate lifecycle effect

## 34.3 Property-based tests

Generate random:

- Intervals
- Buffers
- EVSEs
- Drivers
- Hold expiration times
- Lifecycle actions
- Device events
- Maintenance blocks
- Transaction interleavings

After every generated history, assert:

1. Confirmed planned claims never overlap per EVSE.
2. Confirmed driver schedules never overlap.
3. Expired Holds do not affect new allocation.
4. At most one unresolved occupation exists per EVSE.
5. Every active Booking has an appropriate claim.
6. Every terminal unused Booking has no active claim.
7. Failed rescheduling preserves prior claims.
8. Duplicate operations have one effect.

## 34.4 Fault injection

Inject:

- Database connection loss before and after commit
- Deadlock
- Lock timeout
- Exclusion violation
- Worker crash
- Outbox publisher failure
- Delayed expiration worker
- Device timeout
- Projection version gap

## 34.5 Linearizability review

Capture operation histories containing:

- Invocation
- Response
- Transaction commit time
- Booking version
- Claim references
- Idempotency key

Verify that successful conflicting allocation operations can be ordered as one serial history respecting commit outcomes.

---

# 35. Operational metrics

Required metrics:

- Allocation transaction latency
- EVSE guard wait time
- Driver guard wait time
- Exact and automatic assignment success rates
- Candidate attempts per automatic assignment
- Exclusion violations
- Deadlocks
- Lock timeouts
- Transaction retries
- Lazily released expired Holds
- Expiration-worker delay
- Active and uncertain occupations
- Overrun-affected Bookings
- Reconciliation age
- Orphan claims
- Bookings without expected claims
- Claims without valid sources

Alerts:

- Any overlapping confirmed claim found by integrity scan
- Missing exclusion constraint
- Repeated violation after EVSE guard acquisition
- Uncertain occupation older than threshold
- Expired Hold backlog
- Deadlock rate above threshold
- Capacity claim/source mismatch
- Disabled `btree_gist` extension

---

# 36. Integrity reconciliation

A recurring integrity worker checks:

1. Every active non-terminal Booking has expected claims.
2. Every active Booking claim references a valid Booking.
3. Every `HELD` claim has an expiry.
4. Every expired Hold claim is releasable.
5. No unresolved occupation lacks a Session.
6. No EVSE has multiple unresolved occupations.
7. Confirmed exclusion constraints remain valid.
8. Fulfilment-risk records exist for occupation overlaps.
9. Released claims have release metadata.
10. Projection versions do not regress.

The worker does not silently repair release-critical corruption.

It quarantines affected EVSEs from new allocation and creates an operational incident.

---

# 37. Security requirements

- Only Booking and Session runtime roles may modify allocation tables.
- Runtime roles cannot alter constraints or extensions.
- Internal support/admin APIs cannot bypass allocation procedures.
- Public references do not grant allocation authority.
- SQL is parameterized.
- Constraint details are sanitized.
- Idempotency records exclude authorization secrets.
- Start Authorization secrets are hashed.
- Allocation audit records include actor, reason and correlation ID.
- Manual data repair requires privileged, audited migration tooling.
- Direct production allocation-table editing is prohibited.

---

# 38. Decisions proposed for approval

| ID | Decision |
|---|---|
| ARC-ALLOC-01 | Use `READ COMMITTED` with explicit guard locks and datastore constraints. |
| ARC-ALLOC-02 | Use one `evse_allocation_guard` row per EVSE. |
| ARC-ALLOC-03 | Use one `driver_schedule_guard` row per booking-eligible account. |
| ARC-ALLOC-04 | Lock driver guards before EVSE guards. |
| ARC-ALLOC-05 | Lock multiple resources in ascending UUID order. |
| ARC-ALLOC-06 | Use `tstzrange` with half-open allocation intervals. |
| ARC-ALLOC-07 | Use a GiST exclusion constraint with `btree_gist` for confirmed planned claims. |
| ARC-ALLOC-08 | Exclude time-expiring Holds from the static exclusion constraint. |
| ARC-ALLOC-09 | Serialize and validate Holds under driver and EVSE guard locks. |
| ARC-ALLOC-10 | Ignore and lazily release expired Holds using database time. |
| ARC-ALLOC-11 | Use a separate driver-schedule claim and constraint. |
| ARC-ALLOC-12 | Keep operational occupation separate from planned capacity claims. |
| ARC-ALLOC-13 | Permit occupation to overlap existing future claims and mark them at risk. |
| ARC-ALLOC-14 | Block new claims that overlap active or uncertain occupation. |
| ARC-ALLOC-15 | Represent unresolved physical uncertainty with an optionally unbounded interval. |
| ARC-ALLOC-16 | Use `SKIP LOCKED` only as an automatic-assignment contention optimization. |
| ARC-ALLOC-17 | Never report unavailable solely because candidates are lock-contended. |
| ARC-ALLOC-18 | Release and replace claims within one transaction for rescheduling. |
| ARC-ALLOC-19 | Preserve the original allocation automatically through transaction rollback. |
| ARC-ALLOC-20 | Install maintenance blocks under the same EVSE guard protocol. |
| ARC-ALLOC-21 | Require physical start evidence before Booking activation. |
| ARC-ALLOC-22 | Keep capacity blocked until definitive end plus turnaround buffer. |
| ARC-ALLOC-23 | Retry complete transactions after deadlock or serialization failure. |
| ARC-ALLOC-24 | Require real-PostgreSQL concurrency and property-based tests. |
| ARC-ALLOC-25 | Quarantine an EVSE if integrity reconciliation detects critical corruption. |

---

# 39. Resolved open questions

| Previous question | Resolution |
|---|---|
| ARC-DATA-OQ-04 — Claim/occupation locking | EVSE guard serializes cross-table changes |
| ARC-DATA-OQ-05 — Driver overlap | Driver guard plus driver schedule claims |
| ARC-DATA-OQ-06 — Isolation level | `READ COMMITTED` plus explicit locks |
| ARC-DATA-OQ-07 — Temporal constraint choice | GiST exclusion constraint; no PostgreSQL 18 dependency |
| ARC-COM-OQ-09 — Enforcement lag impact | Missing or unsafe projection fails closed |
| OQ-QA-02 — Concurrent allocation testing | Repeated real-database and property-based race tests |

---

# 40. Remaining open questions

| ID | Question | Resolution phase |
|---|---|---|
| ARC-ALLOC-OQ-01 | Final lock timeout values | Performance testing |
| ARC-ALLOC-OQ-02 | Final automatic-assignment attempt count | Performance testing |
| ARC-ALLOC-OQ-03 | Maximum EVSEs in one maintenance-block transaction | Load/operations testing |
| ARC-ALLOC-OQ-04 | Exact occupation-extension frequency | Device/performance testing |
| ARC-ALLOC-OQ-05 | Maximum unresolved-occupation duration before escalation | Operations/security |
| ARC-ALLOC-OQ-06 | Whether early Session completion releases capacity before scheduled end | Product/domain confirmation |
| ARC-ALLOC-OQ-07 | Final PostgreSQL major version | Technology selection |
| ARC-ALLOC-OQ-08 | Exact Java transaction-retry implementation | Technology selection |
| ARC-ALLOC-OQ-09 | Final candidate fairness scoring | Product/performance testing |
| ARC-ALLOC-OQ-10 | Whether emergency restriction uses claims, an eligibility flag, or both | RESOLVED_BY restriction algorithm (§20.3) |

---

# 41. Acceptance criteria

This design is approved when:

1. Exactly one conflicting Hold can commit per EVSE.
2. Confirmed planned claims cannot overlap at the datastore level.
3. Expired Holds are non-blocking without waiting for cleanup.
4. Driver overlap is prevented under concurrency.
5. Rescheduling is atomic.
6. Failed rescheduling preserves the original claims.
7. Reassignment cannot release the original before replacement commits.
8. Maintenance and Booking races produce one valid result.
9. Start and no-show races produce one valid result.
10. Physical occupation can be recorded despite an existing future Booking.
11. Session overrun prevents new affected allocations.
12. Existing affected future Bookings are retained and marked at risk.
13. Uncertain physical outcomes remain blocking.
14. No allocation transaction makes a remote call.
15. Idempotent retries return one semantic outcome.
16. Deadlock and serialization retries restart complete transactions.
17. Lock contention is not misreported as unavailability.
18. All locks follow the documented order.
19. Database constraints are verified during deployment.
20. Automated race tests prove all release-critical invariants.

---

# 42. Consequences

## Positive

- Definitive prevention of planned double booking
- Expired Holds cannot extend capacity accidentally
- Atomic rescheduling and reassignment
- Honest treatment of physical overruns
- No distributed transaction
- Parallelism across unrelated drivers and EVSEs
- Database-backed correctness
- Deterministic retry behaviour

## Negative

- Holds require both locking logic and constraint-aware handling
- Driver and EVSE guard rows add persistence complexity
- Operational occupation introduces cross-table reasoning
- Automatic assignment requires bounded contention retries
- Every relevant code path must obey lock order
- PostgreSQL-specific constraints reduce database portability
- Extensive real-database concurrency testing is mandatory

These costs are accepted because allocation correctness is the platform’s primary transactional invariant.

---

# 43. Next architecture artifact

The next document is:

**Security Architecture and Threat Model v1.0**

It must define:

- Trust boundaries
- Identity Provider integration
- Browser authentication
- Service identities
- Actor-context propagation
- Operator tenancy
- Support case grants
- Break-glass controls
- Simulator certificate identity
- API and broker authorization
- Secret management
- Threat scenarios
- OWASP ASVS mapping
- Abuse prevention
- Audit and incident controls
