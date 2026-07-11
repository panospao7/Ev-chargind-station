Document ID: UC-DR-008
Title: DR-17-20 - Charging Session Lifecycle v1.0
Version: 1.0
Status: APPROVED
Owner: DA/BA
Last reviewed: 2026-07-11
Supersedes: None
Depends on: UC-DR-007, SIM-001
Authoritative for: Session Telemetry, Meter Events, and Stop Commands

---

## DR-17–20 — Charging Session Lifecycle v1.0

### Session states

- `STARTING` — start command submitted
- `CHARGING` — energy transfer in progress
- `SUSPENDED` — temporarily paused, with a reason
- `STOPPING` — stop requested but not finalized
- `COMPLETED` — ended normally
- `INTERRUPTED` — ended because of fault, disconnection, or emergency
- `START_REJECTED` — charging never started

The booking remains separate but linked:

- Session starts → booking becomes `ACTIVE`
- Session completes normally → booking becomes `COMPLETED`
- Failure before any charging → booking may become `FULFILMENT_FAILED`
- Failure after charging began → booking becomes `COMPLETED` with an interrupted outcome

### DR-17 — Start session

1. Driver presses **Start charging**.
2. System validates the booking, EVSE, start window, authorization, and operational status.
3. A session is created idempotently in `STARTING`.
4. The simulator receives the start command.
5. Acceptance changes the session to `CHARGING` and booking to `ACTIVE`.
6. Rejection changes the session to START_REJECTED and the booking to FULFILMENT_FAILED, with no same-booking retry in v1.

Only one session may exist for the booking, driver, and EVSE.

### DR-18 — Monitor session

Display:

- Current state
- Duration
- Energy delivered
- Charging power
- Estimated cost
- Last status-update time
- Suspension or fault reason

Meter events must include sequence identifiers. Duplicate and out-of-order events are handled safely. Stale information is shown as `UNKNOWN`, not as live data.

### DR-19 — Stop session

A session may stop because of:

- Driver request
- Vehicle fully charged
- Reserved time ending
- Operator emergency action
- EVSE fault or disconnection
- Simulator rule

The driver receives a warning before automatic stopping. Stop requests are idempotent. A timeout leaves the session in `STOPPING` until reconciliation determines the actual result.

### DR-20 — Complete session

The system records:

- Start and end times
- Final energy
- Duration
- Final estimated cost
- Tariff snapshot
- Stop reason
- Completion/interruption outcome

Explicitly stopping is final; restarting requires a new booking. Capacity is released after the configured turnaround buffer.

### Core acceptance criteria

- Repeated start requests create only one session.
- An authorization cannot be reused on another EVSE.
- Unauthorized drivers cannot control sessions.
- Meter-event duplication never inflates energy or cost.
- Concurrent stop commands produce one final outcome.
- Disconnections do not falsely report successful completion.
- Final summaries are reproducible from stored session data.

Next, we should define the **Availability Calculation Model**, since search, booking, maintenance, sessions, stale statuses, and buffers all depend on one authoritative definition of “available.”