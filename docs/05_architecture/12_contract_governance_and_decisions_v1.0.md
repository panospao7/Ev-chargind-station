Document ID: ARC-018
Title: Contract Governance and Decision Register v1.0
Version: 1.0
Status: IN_REVIEW
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

OpenAPI provides the machine-readable HTTP contract, while AsyncAPI describes message-based interfaces, channels, operations, and reusable messages. ([asyncapi.com](https://www.asyncapi.com/docs/concepts/asyncapi-document/adding-messages?utm_source=openai))

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

## 4. Decisions recommended for approval

1. REST/JSON for synchronous communication.
2. RabbitMQ for asynchronous events and charger commands.
3. CloudEvents-compatible event envelope.
4. Domain-owned saga orchestration; no generic Saga Service initially.
5. Capacity `FREEZE → BLOCK` workflow for maintenance, closure, and suspension.
6. No reliance on broker event ordering.
7. Internal charging substates `AUTHORIZING` and `FINALIZING`.
8. Device reservation is a synchronized mirror; Booking remains authoritative.
9. Restricted identity/contact event stream for Notification.
10. Discovery and Insights Service is excluded from all authoritative write paths.
11. Charger command acceptance is distinct from physical/simulated execution.
12. Uncertain command outcomes remain visible until reconciled.

## 5. Remaining implementation-level decisions

- Device reservation synchronization horizon (Release applicability: W2); recommended starting value: 60 minutes
- RabbitMQ queue sizing, retry timing, and event retention (Release applicability: W1 | Cross-cutting)
- Exact meter-summary quality classifications (Release applicability: W1 | Cross-cutting)
- Object-storage provider for privacy exports (Release applicability: W3)
- Event-schema registry tooling (Release applicability: W1 | Cross-cutting)

These do not block the architectural model.

## 6. Completion assessment

With these documents approved, service interaction planning is approximately **complete at the logical-design level**.

The next planning phase should be:

1. Database model and table ownership per service (Release applicability: W1 | Cross-cutting)
2. Detailed OpenAPI contracts for Booking and Session Service, and Station Operations Service (Release applicability: W1 | Cross-cutting)
3. AsyncAPI/event-schema definitions (Release applicability: W1 | Cross-cutting)
4. Security architecture and threat model (Release applicability: W1 | Cross-cutting)
5. Deployment topology and cloud design (Release applicability: W1 | Cross-cutting)

## 4. Shared Problem-Code Registry
All microservices standardise on the following RFC 9457 error problem codes:
- `VERSION_CONFLICT` (Wave 1): Entity version mismatch during write locks.
- `EVSE_ALLOCATION_CONFLICT` (Wave 1): Target EVSE already allocated for the interval.
- `BOOKING_HOLD_EXPIRED` (Wave 1): Hold period ended before confirmation.
- `EVSE_STALE_TELEMETRY` (Wave 1): Near-term booking blocked because EVSE is offline/stale.
- `INVALID_CREDENTIALS` (Wave 1): Credentials verification failed.
