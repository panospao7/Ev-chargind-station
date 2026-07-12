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
| Driver profile, vehicles, consent, privacy request coordination | Account Service |
| Organizations, stations, EVSE configuration, tariffs | Station Operations Service |
| Reservations, allocations, check-in, capacity restrictions | Booking module (Booking and Session Service) |
| Charging sessions, meter records, energy/cost summaries | Charging module (Booking and Session Service) |
| Device identity, live status, charger commands | Device Integration Service |
| Email delivery | Notification Service |
| Search and analytics | Discovery and Insights Service |
| Support cases, audit review, oversight, escalation | Platform Governance and Support Service |

## 3. Primary dependency matrix

| Caller | Callee | Purpose | Dependency |
|---|---|---|---|
| Booking module (Booking and Session Service) | Station Operations Service | Advisory preflight reservation context and tariff/policy (preflight only; allocation uses local projections) | Optional (Release applicability: W1) |
| Booking module (Booking and Session Service) | Device Integration Service | Advisory near-term EVSE status (preflight only; allocation uses local projections) | Optional (Release applicability: W1) |
| Charging module (Booking and Session Service) | Booking module (Booking and Session Service) | Consume start authorization (direct internal method or local domain event call; no remote broker lock) | Hard (Release applicability: W1) |
| Station Operations Service | Booking module (Booking and Session Service) | Initiate capacity restriction (FREEZE) for maintenance/closure | Hard (Release applicability: W1) |
| Station Operations Service | Device Integration Service | Provision simulator/device | Hard during provisioning (Release applicability: W1) |
| Platform Governance and Support Service | Domain services | Support cases and audit oversight queries | Workflow dependent (Release applicability: W2) |
| Account Service / Platform Governance | Keycloak | User identity lifecycle | Hard for identity completion (Release applicability: W1) |
| All services | Platform Governance and Support Service | Publish audit events (asynchronous outbox) | Asynchronous (Release applicability: W1) |
| All services | Discovery and Insights / Notification | Projections and messages (asynchronous outbox) | Asynchronous (Release applicability: W1) |
| Booking and Session Service / Station Operations | Device Integration Service | Dispatch charger commands (asynchronous commands via RabbitMQ outbox only) | Asynchronous (Release applicability: W1) |

Device Integration Service does not synchronously mutate bookings or sessions.

## 4. API conventions

### Namespaces

- Public/BFF-facing: `/api/v1/...`
- Service-only: `/internal/v1/...`
- Operational endpoints: `/actuator/...`

### Authentication

- Browser-to-BFF: secure opaque session cookies plus CSRF protection. Opaque bearer tokens are not forwarded or returned to the browser.
- BFF-to-Service: target-audience tokens obtained via Keycloak Token Exchange.
- Service-to-service human delegation: a platform-specific signed JSON Web Signature (JWS) carried alongside the service token (carrying cryptographically signed actor and organization context), not a raw RFC 8693 token-exchange assertion.
- Service identity tokens are used for service-owned background calls.
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

HTTP methods retain their standard semantics; automatic retries of non-idempotent operations require an application-level idempotency mechanism. ([rfc-editor.org](https://www.rfc-editor.org/rfc/rfc9110.html))

### Concurrency

- Mutable resources expose a numeric `version` and ETag.
- Updates require `If-Match` or `expectedVersion`.
- Version mismatch returns `412 VERSION_CONFLICT`.
- Booking interval conflicts return `409 EVSE_ALLOCATION_CONFLICT`.
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

This follows RFC 9457’s machine-readable problem-details model. ([rfc-editor.org](https://www.rfc-editor.org/rfc/rfc9457.html))

### Tracing

Use W3C `traceparent` and `tracestate` across HTTP and messaging. Do not place PII or credentials in trace baggage. ([opentelemetry.io](https://opentelemetry.io/docs/concepts/context-propagation/))

## 5. Required internal APIs

### Station Operations Service

#### Reservation context (Release applicability: W1)

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

#### Replacement candidates (Release applicability: W1)

`GET /internal/v1/stations/{stationId}/replacement-candidates`

Returns compatible candidates for the full interval, but Booking performs the final allocation.

#### Operator access decision (Release applicability: W1)

`POST /internal/v1/operator-access-decisions`

Checks current organization membership, role, organization status, and requested action. Used for sensitive operator writes when cached membership is insufficient.

#### Station Operations snapshot (Release applicability: W1)

`GET /internal/v1/snapshots/station-operations?cursor=...`

Used only to rebuild search/reporting projections.

### Booking module (Booking and Session Service)

#### Capacity impact preview (Release applicability: W1)

`GET /internal/v1/capacity-impacts`

Inputs include scope, interval, and restriction type. Returns affected booking IDs and states without driver PII.

#### Create capacity restriction (Release applicability: W1)

`POST /internal/v1/capacity-restrictions`

Restriction types:

- `MAINTENANCE`
- `STATION_CLOSURE`
- `EVSE_DEACTIVATION`
- `ORGANIZATION_SUSPENSION`
- `EMERGENCY`

Initial phase `FREEZE` blocks new holds, confirmations, and rescheduling while allowing existing allocations to be resolved.

#### Finalize capacity restriction (Release applicability: W1)

`POST /internal/v1/capacity-restrictions/{id}:finalize`

Permitted only when conflicting bookings/sessions have been resolved. Converts the freeze into a hard capacity block.

#### Release restriction (Release applicability: W1)

`POST /internal/v1/capacity-restrictions/{id}:release`

Idempotently releases an aborted or completed restriction, transitioning the auditable record to `RELEASED` rather than executing physical deletion.

#### Consume start authorization (Release applicability: W1)
*This is an internal module contract between Charging and Booking modules in W1. The HTTP endpoint shape is documented here only as a future extraction contract; the initial runtime path is executed via direct internal class/method invocation.*

`POST /internal/v1/start-authorizations:consume`

Inputs:

- Opaque authorization token
- Session ID
- Driver ID
- EVSE ID

The operation atomically binds the authorization to one persisted charging session and returns a booking, EVSE, connector, tariff, and deadline snapshot. Tokens never appear in URLs or logs.

#### Support view (Release applicability: W2)

`GET /internal/v1/bookings/{id}/support-view`

Returns masked, case-appropriate information to Platform Governance and Support Service.

### Device Integration Service

#### Live EVSE status (Release applicability: W1)

`GET /internal/v1/evses/{evseId}/status`

Returns:

- Reported state
- Derived state
- Freshness
- Last heartbeat/status times
- Active transaction reference
- Current fault summary

#### Command status (Release applicability: W1)

`GET /internal/v1/commands/{commandId}`

Used for reconciliation, not routine polling.

#### Device provisioning
**Release applicability:** W1

`POST /internal/v1/devices`

Creates a device assignment for Station Operations-owned station/EVSE references.

#### Credential rotation/revocation
**Release applicability:** W1

- `POST /internal/v1/devices/{id}:rotate-credentials`
- `POST /internal/v1/devices/{id}:revoke`

Credentials are returned or delivered only through a one-time secure provisioning process.

### Charging module (Booking and Session Service)
*This is an internal module interface of Booking and Session Service.*

- `GET /internal/v1/sessions/{id}/support-view` (Release applicability: W2)
- `GET /internal/v1/bookings/{bookingId}/session-control-state` (Release applicability: W1)
- `POST /internal/v1/sessions/{id}:reconcile` (Release applicability: W1)
- `GET /internal/v1/snapshots/sessions?cursor=...` (Release applicability: W1)

Booking should normally rely on charging events rather than synchronously querying the Charging module.

### Account Service

- `GET /internal/v1/users/{id}/lifecycle-status` (Release applicability: W1)
- `GET /internal/v1/users/{id}/support-view` (Release applicability: W2)
- `GET /internal/v1/snapshots/accounts?cursor=...` (Release applicability: W1)

Notification contact data is distributed through a restricted private event stream rather than general domain events.

### Platform Governance and Support Service

Support clients call Platform Governance and Support Service, which validates case assignment and obtains masked data from owning services. Support users do not receive direct unrestricted access to every service.

## 6. Failure policy

- Optional remote-preflight failure does not reject allocation. Preflight is advisory; Booking-local projections are authoritative for allocation. Missing, stale, incomplete or gap-detected Booking-local enforcement data causes the authoritative transaction to fail closed.
- If live status is required and Device Integration Service is unavailable, near-term booking/check-in fails as `STATUS_UNKNOWN`.
- Search projection failure never changes authoritative state.
- Timeouts return an uncertain or retryable response; they never invent success.
- Mutating retries require the original idempotency key.
- No downstream service may call back into the caller during the same request chain.
