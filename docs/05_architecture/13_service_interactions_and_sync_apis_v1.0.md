Document ID: ARC-019
Title: Service Interactions and Synchronous APIs v1.0
Version: 1.0
Status: IN_REVIEW
Owner: Architecture Lead
Last reviewed: 2026-07-12
Depends on: ARC-001–017
Authoritative for: Service Interactions And Sync Apis
Refines: ARC-002, ARC-003
Does not supersede: Service topology and data ownership in ARC-001
Release applicability: W1 | W2 | W3 | Cross-cutting

---



# Service Interactions and Synchronous APIs v1.0

**Status:** Draft (In Review)

## 1. Interaction model

- HTTPS REST/JSON for immediate queries, validations, and commands requiring an immediate acceptance result.
- RabbitMQ for domain events, charger commands, telemetry, privacy jobs, and delayed processing.
- The API Gateway/BFF handles routing and presentation aggregation but owns no business workflow.
- Every write is owned by one domain service and committed in that service’s database.
- No cross-service database access, foreign keys, or distributed transactions.
- Discovery and Insights Service must never participate in authoritative write decisions.
- Synchronous chains should normally stop at: `Client → BFF → owning service → one dependency`.
- Domain events describe committed facts and must not be used as uncommitted remote procedure calls.

## 2. Sources of truth

| Information | Authority |
|---|---|
| Credentials, MFA, authentication sessions | Keycloak |
| Driver profile, vehicles, consent | Account Service |
| Organizations, stations, EVSE configuration, tariffs | Station Operations Service |
| Reservations, allocations, check-in, capacity restrictions | Booking module (Booking and Session Service) |
| Charging sessions, meter records, energy/cost summaries | Charging module (Booking and Session Service) |
| Device identity, live status, charger commands | Device Integration Service |
| Email delivery | Notification Service |
| Search and analytics | Discovery and Insights Service |
| Support, privacy workflows, central audit index | Platform Governance and Support Service |

## 3. Primary dependency matrix

| Caller | Callee | Purpose | Dependency |
|---|---|---|---|
| Booking | Station Operations | Reservation context, tariff/policy (advisory preflight validation only) | Optional |
| Booking | Device Integration Service | Fresh near-term EVSE status | Hard near-term; optional future |
| Charging | Booking | Consume start authorization | Hard |
| Network | Booking | Capacity freeze/block for maintenance or closure | Hard |
| Network | Device Integration Service | Provision simulator/device | Hard during provisioning |
| Governance | Domain services | Support views and privacy operations | Workflow dependent |
| Account/Governance | Keycloak | Identity lifecycle | Hard for identity completion |
| All domain services | Governance | Audit events | Asynchronous |
| All relevant services | Query/Notification | Projections and messages | Asynchronous |
| Charging/Network/Booking | Device Integration Service | Charger actions | Asynchronous commands |

Device Integration Service does not synchronously mutate bookings or sessions.

## 4. API conventions

### Namespaces

- Public/BFF-facing: `/api/v1/...`
- Service-only: `/internal/v1/...`
- Operational endpoints: `/actuator/...`

### Authentication

- User requests use short-lived access tokens.
- Internal background calls use service-account tokens with service-specific audiences.
- Preferred BFF model: exchange the user token for a target-service token while preserving the user subject and authorized context.
- Keycloak supports service accounts and audience-specific token exchange, but exact configuration remains an implementation decision. ([keycloak.org](https://www.keycloak.org/securing-apps/token-exchange?utm_source=openai))
- Correlation headers, organization IDs, and actor headers are never accepted as authorization proof.
- Every service independently checks roles, ownership, organization status, and resource state.

### Idempotency

`Idempotency-Key` is mandatory for:

- Hold creation and confirmation
- Rescheduling and cancellation
- Check-in
- Session start and stop
- Reassignment
- Capacity restrictions
- Emergency interventions

The record is scoped by actor, operation, and key. It stores the request-body hash and final response. Reusing a key with different content returns `409 IDEMPOTENCY_KEY_REUSED`.

HTTP methods retain their standard semantics; automatic retries of non-idempotent operations require an application-level idempotency mechanism. ([rfc-editor.org](https://www.rfc-editor.org/rfc/rfc9110.html?utm_source=openai))

### Concurrency

- Mutable resources expose a numeric `version` and ETag.
- Updates require `If-Match` or `expectedVersion`.
- Version mismatch returns `412 VERSION_MISMATCH`.
- Booking interval conflicts return `409 EVSE_INTERVAL_CONFLICT`.
- The server clock is authoritative.

### Errors

Errors use `application/problem+json` with:

- `type`
- `title`
- `status`
- `detail`
- `instance`
- `code`
- `correlationId`
- `retryable`
- Optional `violations`, `currentVersion`, and `alternatives`

This follows RFC 9457’s machine-readable problem-details model. ([rfc-editor.org](https://www.rfc-editor.org/rfc/rfc9457.html?utm_source=openai))

### Tracing

Use W3C `traceparent` and `tracestate` across HTTP and messaging. Do not place PII or credentials in trace baggage. ([opentelemetry.io](https://opentelemetry.io/docs/concepts/context-propagation/?utm_source=openai))

## 5. Required internal APIs

### Station Operations Service

#### Reservation context

`GET /internal/v1/stations/{stationId}/reservation-context`

Inputs:

- Start/end UTC
- Connector types
- Minimum power
- Optional EVSE
- Driver/vehicle requirements

Returns:

- Station and organization status
- Opening-hours result
- Compatible EVSE candidates
- Connector/power snapshots
- Tariff and booking-policy snapshots
- Configuration revision
- Operational warnings

It does not determine booking availability.

#### Replacement candidates

`GET /internal/v1/stations/{stationId}/replacement-candidates`

Returns compatible candidates for the full interval, but Booking performs the final allocation.

#### Operator access decision

`POST /internal/v1/operator-access-decisions`

Checks current organization membership, role, organization status, and requested action. Used for sensitive operator writes when cached membership is insufficient.

#### Network snapshot

`GET /internal/v1/snapshots/network?cursor=...`

Used only to rebuild search/reporting projections.

### Booking module (Booking and Session Service)

#### Capacity impact preview

`GET /internal/v1/capacity-impacts`

Inputs include scope, interval, and restriction type. Returns affected booking IDs and states without driver PII.

#### Create capacity restriction

`POST /internal/v1/capacity-restrictions`

Restriction types:

- `MAINTENANCE`
- `STATION_CLOSURE`
- `EVSE_DEACTIVATION`
- `ORGANIZATION_SUSPENSION`
- `EMERGENCY`

Initial phase `FREEZE` blocks new holds, confirmations, and rescheduling while allowing existing allocations to be resolved.

#### Finalize capacity restriction

`POST /internal/v1/capacity-restrictions/{id}:finalize`

Permitted only when conflicting bookings/sessions have been resolved. Converts the freeze into a hard capacity block.

#### Release restriction

`DELETE /internal/v1/capacity-restrictions/{id}`

Idempotently releases an aborted or completed restriction.

#### Consume start authorization

`POST /internal/v1/start-authorizations:consume`

Inputs:

- Opaque authorization token
- Session ID
- Driver ID
- EVSE ID

The operation atomically binds the authorization to one persisted charging session and returns a booking, EVSE, connector, tariff, and deadline snapshot. Tokens never appear in URLs or logs.

#### Support view

`GET /internal/v1/bookings/{id}/support-view`

Returns masked, case-appropriate information to Governance.

### Device Integration Service

#### Live EVSE status

`GET /internal/v1/evses/{evseId}/status`

Returns:

- Reported state
- Derived state
- Freshness
- Last heartbeat/status times
- Active transaction reference
- Current fault summary

#### Command status

`GET /internal/v1/commands/{commandId}`

Used for reconciliation, not routine polling.

#### Device provisioning

`POST /internal/v1/devices`

Creates a device assignment for Network-owned station/EVSE references.

#### Credential rotation/revocation

- `POST /internal/v1/devices/{id}:rotate-credentials`
- `POST /internal/v1/devices/{id}:revoke`

Credentials are returned or delivered only through a one-time secure provisioning process.

### Charging module (Booking and Session Service)

- `GET /internal/v1/sessions/{id}/support-view`
- `GET /internal/v1/bookings/{bookingId}/session-control-state`
- `POST /internal/v1/sessions/{id}:reconcile`
- `GET /internal/v1/snapshots/sessions?cursor=...`

Booking should normally rely on charging events rather than synchronously querying Charging.

### Account Service

- `GET /internal/v1/users/{id}/lifecycle-status`
- `GET /internal/v1/users/{id}/support-view`
- `GET /internal/v1/snapshots/accounts?cursor=...`

Notification contact data is distributed through a restricted private event stream rather than general domain events.

### Governance Service

Support clients call Governance, which validates case assignment and obtains masked data from owning services. Support users do not receive direct unrestricted access to every service.

## 6. Failure policy

- If Network validation fails, new booking writes fail safely.
- If live status is required and Device Integration Service is unavailable, near-term booking/check-in fails as `STATUS_UNKNOWN`.
- Search projection failure never changes authoritative state.
- Timeouts return an uncertain or retryable response; they never invent success.
- Mutating retries require the original idempotency key.
- No downstream service may call back into the caller during the same request chain.
