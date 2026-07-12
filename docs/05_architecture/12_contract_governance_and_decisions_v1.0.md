Document ID: ARC-018
Title: Contract Governance and Decision Register v1.0
Version: 1.0
Status: APPROVED
Owner: Architecture Lead
Last reviewed: 2026-07-12
Depends on: ARC-001–017
Authoritative for: Contract Governance And Decisions
Refines: ARC-002, ARC-003, ARC-004
Does not supersede: Service topology and data ownership in ARC-001
Release applicability: W1 | W2 | W3 | Cross-cutting

---



# Contract Governance and Decision Register v1.0

## 1. Contract artifacts

Each service will maintain:

- OpenAPI 3.0.3 document for synchronous APIs
- AsyncAPI 2.6.0 document for events, commands, channels, and bindings
- JSON Schemas for every event/command payload
- Problem-code registry
- State-transition catalogue
- Example requests and responses
- Ownership and authorization matrix

OpenAPI provides the machine-readable HTTP contract, while AsyncAPI describes message-based interfaces, channels, operations, and reusable messages. ([asyncapi.com](https://www.asyncapi.com/docs/concepts/asyncapi-document/adding-messages))

Recommended repository layout:

- `contracts/openapi/`
- `contracts/asyncapi/`
- `contracts/schemas/events/`
- `contracts/schemas/commands/`
- `contracts/problems/`
- `contracts/examples/`
- `architecture/decisions/`

## 2. Compatibility rules

### HTTP APIs

- Additive fields are allowed.
- Existing fields cannot change meaning or type.
- New required request fields require a new API version.
- Enum consumers must tolerate documented future values or map them to `UNKNOWN`.
- Removed endpoints require deprecation and migration periods.

### Events

- Event type remains unchanged for backward-compatible additions.
- Breaking changes create a new event-type version.
- Producers support old and new versions during migration.
- Consumers ignore unknown optional fields.
- Replay uses the original event ID.
- Event schemas cannot depend on service-internal Java classes.

## 3. CI quality gates

- OpenAPI and AsyncAPI validation
- Breaking-change detection
- Event-schema compatibility checks
- Consumer-driven contract tests
- Provider verification
- Authorization tests per endpoint
- Idempotency and concurrency tests
- Duplicate and out-of-order event tests
- Saga crash/restart tests at every step
- Dead-letter replay tests
- Sensitive-data scanning
- Trace/correlation propagation tests

No service contract is considered complete until it maps back to a functional requirement and automated acceptance test.

## 4. Approved architectural decisions

1. REST/JSON for synchronous communication.
2. RabbitMQ for asynchronous events and charger commands.
3. CloudEvents-compatible event envelope.
4. Domain-owned saga orchestration; no generic Saga Service initially.
5. Capacity `FREEZE → BLOCKED → RELEASED` workflow for maintenance, closure, and suspension.
6. No reliance on broker event ordering.
7. Internal charging substates `AUTHORIZING` and `FINALIZING`.
8. Device reservation is a synchronized mirror; Booking remains authoritative.
9. Restricted identity/contact event stream for Notification.
10. Discovery and Insights Service is excluded from all authoritative write paths.
11. Charger command acceptance is distinct from physical/simulated execution.
12. Uncertain command outcomes remain visible until reconciled.
13. Device reservation synchronization horizon is fixed to the approved 60 minutes (Release applicability: W2).

## 5. Remaining implementation-level decisions

- RabbitMQ queue sizing, retry timing, and event retention (Release applicability: W1 | Cross-cutting)
- Exact meter-summary quality classifications (Release applicability: W1 | Cross-cutting)
- Object-storage provider for privacy exports (Release applicability: W3)
- Event-schema registry tooling (Release applicability: W1 | Cross-cutting)

These do not block the architectural model.

## 6. Completion assessment

With these documents approved, service interaction planning is approximately **complete at the logical-design level**.

Outstanding artifacts pending implementation readiness:

1. Machine-readable OpenAPI 3.0.3 contract files (Release applicability: W1 | Cross-cutting)
2. Machine-readable AsyncAPI 2.6.0 contract files (Release applicability: W1 | Cross-cutting)
3. Standalone JSON Schema 2020-12 files for events/commands (Release applicability: W1 | Cross-cutting)
4. CI contract validation and breaking-change detection pipeline (Release applicability: W1 | Cross-cutting)
5. Generated executable contract tests (Release applicability: W1 | Cross-cutting)

Database model and table ownership is already defined in ARC-005. Security architecture and deployment topology are covered in their respective documents.

## 7. Shared Problem-Code Registry (Release applicability: W1 | Cross-cutting)
All microservices standardise on the following RFC 9457 error problem codes:

| Problem Code | HTTP Status | Retryable | Parameters | Applicable Operations | Description |
|---|---|---|---|---|---|
| `VERSION_CONFLICT` | 412 Precondition Failed | Yes | `entityType`, `entityId`, `expectedVersion`, `actualVersion` | Any mutating write with `If-Match` | Entity version mismatch during optimistic concurrency checks. |
| `EVSE_ALLOCATION_CONFLICT` | 409 Conflict | No | `evseId`, `requestedInterval` (no `conflictingBookingId` in public edge responses) | Create booking/hold, reschedule | The target EVSE is already allocated for the requested interval. |
| `BOOKING_HOLD_EXPIRED` | 410 Gone | No | `bookingId`, `holdExpiredAt` | Confirm booking | The temporary hold period ended before the confirmation was received. |
| `EVSE_STALE_TELEMETRY` | 503 Service Unavailable | Yes | `evseId`, `lastHeartbeatAgeSeconds` | Near-term booking/hold, check-in | Near-term booking/hold blocked because EVSE telemetry is stale. Distinguish from `EVSE_OFFLINE` (no heartbeat at all). |
| `EVSE_OFFLINE` | 503 Service Unavailable | No | `evseId`, `lastHeartbeatAgeSeconds` | Near-term booking/hold, check-in | EVSE has no recent heartbeat; treated as unreachable. |
| `MAINTENANCE_CONFLICT` | 409 Conflict | No | `evseId`, `effectiveInterval` | Allocation, hold, reschedule, check-in | Requested EVSE or interval is covered by an unreleased capacity restriction (FREEZE/BLOCKED). |
| `INVALID_CREDENTIALS` | 401 Unauthorized | No | None | Auth, S2S delegation, device provisioning | Authentication or token signature verification failed. |
| `ALLOCATION_BUSY` | 409 Conflict | Yes | `evseId`, `retryAfterSeconds` | Start charging, reserve EVSE | Transient contention only; the EVSE is locked by another concurrent process. Actual physical occupation returns `EVSE_ALLOCATION_CONFLICT`. |
| `STATUS_UNKNOWN` | 503 Service Unavailable | Yes | `evseId` | Check-in, start charging | Current device status is unknown due to active communication loss. |
| `IDEMPOTENCY_KEY_REUSED` | 409 Conflict | No | `idempotencyKey` | Any mutating write | Mutating request retried with same key but different request body. |
| `NO_COMPATIBLE_EVSE` | 422 Unprocessable Entity | No | `stationId`, `connectorType`, `minPower` | Availability check, hold creation | No EVSE at the station matches the specified connector/power constraints. |
| `DEPENDENCY_UNAVAILABLE` | 503 Service Unavailable | Yes | `dependencyName` | Any remote preflight | Remote preflight/lookups failed or timed out during non-locking phases. |
| `MAINTENANCE_STATE_CONFLICT` | 409 Conflict | No | `maintenanceRef`, `expectedState`, `actualState` | Maintenance lifecycle | Maintenance record in unexpected state for the requested operation. |
| `CAPACITY_RESTRICTION_STATE_CONFLICT` | 409 Conflict | No | `restrictionRef`, `expectedState`, `actualState` | Capacity restriction lifecycle | Capacity restriction in unexpected state for the requested operation. |
| `SAFETY_EVIDENCE_REQUIRED` | 422 Unprocessable Entity | No | `maintenanceRef` | Failed maintenance release | Release of a failed maintenance restriction requires verified safety evidence. |
| `MAINTENANCE_IMPACT_UNRESOLVED` | 409 Conflict | No | `maintenanceRef`, `affectedBookingCount` | Maintenance submission | Maintenance cannot proceed until overlapping bookings/sessions are resolved. |
| `START_AUTHORIZATION_EXPIRED` | 409 Conflict | No | `authorizationRef` | Start charging | The start authorization has expired before it could be consumed. |
| `START_ATTEMPT_LIMIT_REACHED` | 409 Conflict | No | `bookingRef`, `maxAttempts` | Start charging | The maximum number of start attempts has been exhausted. |
| `START_RETRY_NOT_ELIGIBLE` | 409 Conflict | No | `bookingRef`, `reason` | Start charging | A retry is not eligible under current conditions. |
| `SESSION_OUTCOME_UNRESOLVED` | 409 Conflict | No | `sessionRef` | Start charging | The previous session outcome is still unresolved. |

## 8. Schema Dialect Policy (Release applicability: W1 | Cross-cutting)
To avoid tooling incompatibilities during code generation and automated contract testing:
- **Synchronous HTTP APIs:** OpenAPI 3.0.3 documents must use OpenAPI Schema Objects only. This is the schema dialect defined by the OpenAPI 3.0.3 specification; do not use standalone JSON Schema 2020-12 features inside OpenAPI documents.
- **Asynchronous Messaging:** AsyncAPI 2.6.0 documents and standalone JSON Schema files for RabbitMQ events/commands must use JSON Schema Draft 2020-12 dialect (`$schema: "https://json-schema.org/draft/2020-12/schema"`).
- **Bundling & Code-Gen:** Standalone event/command schemas must be bundled dynamically using node-based tooling to resolve `$ref` anchors before publication.
- **CI Validation:** The CI build pipeline must run validation tests against OpenAPI and JSON Schema dialects using `spectral` and `ajv-cli` to guarantee machine-readability and strict conformance.
