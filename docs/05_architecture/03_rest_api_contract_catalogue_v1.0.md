Document ID: ARC-003  
Title: REST API Contract Catalogue  
Version: 1.0  
Status: APPROVED  
Owner: Backend / API Architect  
Last reviewed: 2026-07-12  
Depends on: ARC-001, ARC-002, ARC-019, REQ-001, DOM-001, DOM-002  
Authoritative for: REST API conventions, operations, ownership, synchronous semantics, errors, concurrency and idempotency  

# REST API Contract Catalogue v1.0

## 1. Purpose

This document defines:

- Public and internal REST API boundaries
- Resource and command operations
- Authentication and authorization expectations
- Request/response conventions
- Idempotency and concurrency controls
- Pagination, filtering and sorting
- Error representation
- HTTP status semantics
- Caching and data-freshness rules
- Requirement and service traceability

It is a contract catalogue, not the final OpenAPI description. Exact schemas will be produced from this catalogue before implementation.

---

## 2. Scope

Included:

- Browser-facing APIs through the API Gateway/BFF
- Synchronous service-to-service APIs
- Immediate business commands
- Workflow-status queries
- Administrative and support APIs

Excluded:

- OIDC endpoints owned by the Identity Provider
- Simulator WebSocket protocol
- RabbitMQ events and asynchronous commands
- Email-provider APIs
- Cloud-management APIs
- Observability ingestion endpoints
- Final deployment hostnames

---

## 3. API contract standard

OpenAPI is the machine-readable contract format. OpenAPI 3.0.3 is selected as the API specification version, as confirmed by ARC-018 §1 and §8. Java and TypeScript tooling compatibility has been validated for 3.0.3. ([spec.openapis.org](https://spec.openapis.org/oas/v3.0.3.html))

Every API operation must define:

- Stable `operationId`
- Summary and business semantics
- Owning service
- Actor and permission requirements
- Path, query, header and body schemas
- Success responses
- Problem responses
- Idempotency requirements
- Concurrency requirements
- Rate-limit category
- Data classification
- Requirement and invariant references

Generated OpenAPI must be linted and compared for breaking changes in CI.

---

## 4. API surfaces

## 4.1 Edge API

Base path:

`/api/v1`

Used by:

- Angular web client
- Authorized browser-based administrative interfaces

Exposed only through the API Gateway/BFF.

## 4.2 Internal API

Base path:

`/internal/v1`

Used only by authenticated platform services.

Internal APIs are not reachable from the public internet and still require authentication, authorization and input validation.

## 4.3 Operational API

Base path:

`/actuator` or a final equivalent

Used for:

- Liveness
- Readiness
- Metrics scraping where applicable

Operational endpoints must be network-restricted and must not expose business data, secrets or environment configuration.

---

## 5. Versioning

- Major versions appear in the URI: `/api/v1`.
- Backward-compatible additions do not change the major version.
- Breaking changes require `/api/v2` or an explicitly managed migration.
- Removing or changing enum meanings is breaking.
- Adding optional response fields is backward compatible.
- Consumers must ignore unknown response fields.
- Deprecation must include replacement, announcement and removal target.
- Internal APIs follow the same versioning discipline.
- API version and OpenAPI document version are separate.

Versioning applies to semantics, not merely JSON shape.

---

## 6. Resource naming

- Paths use lowercase plural nouns.
- Multiword path segments use kebab case.
- Public references appear in URLs instead of internal database IDs.
- URLs do not expose implementation or service names.
- Actions that are genuine domain commands may use verb subresources.

Examples:

- `/api/v1/stations`
- `/api/v1/bookings/{bookingRef}`
- `/api/v1/bookings/{bookingRef}/check-in`
- `/api/v1/operator-organizations/{organizationRef}/members`

Avoid:

- `/getStations`
- `/booking-service/bookings`
- `/stations/delete`
- `/api/v1/booking?id=123`

---

## 7. Representation conventions

## 7.1 Media types

Success payload:

`application/json`

Problem payload:

`application/problem+json`

JSON field names use `lowerCamelCase`.

## 7.2 Timestamps

Timestamps use RFC 3339-compatible strings and include an offset. Persisted instants and ordinary API responses use UTC with `Z`; station-local display conversion remains a client/presentation concern. RFC 3339 defines an unambiguous internet timestamp representation. ([rfc-editor.org](https://www.rfc-editor.org/info/rfc3339/))

Examples of semantic field names:

- `createdAt`
- `scheduledStart`
- `scheduledEnd`
- `evaluatedAt`
- `expiresAt`

Date-only values use `YYYY-MM-DD`.

Durations are represented as integer minutes where booking-policy increments apply. Electrical values use explicit unit-bearing fields such as:

- `energyWh`
- `powerW`
- `voltageV`
- `currentA`

Money uses:

- `currency: "EUR"`
- Decimal string values such as `estimatedAmount: "12.40"`

Binary floating-point values must not represent money.

## 7.3 Identifiers

- Public references are opaque strings.
- Clients must not infer type, creation time or sequence from an identifier.
- Internal IDs never appear in public contracts.
- References are case-sensitive unless their schema explicitly states otherwise.

## 7.4 Enumerations

- Enum values use uppercase `SNAKE_CASE`.
- Unknown future enum values must not crash clients.
- Existing enum meanings cannot be changed silently.
- User-facing localized text is not used as program logic.

---

## 8. Request headers

| Header | Requirement | Purpose |
|---|---|---|
| `Authorization` | Protected APIs | Bearer access token |
| `Idempotency-Key` | Retryable commands | Identifies one logical operation |
| `If-Match` | Versioned updates | Prevents lost updates |
| `Accept-Language` | Optional | Preferred `el` or `en` response text |
| `traceparent` | Propagated/generated | Distributed trace context |
| `tracestate` | Optional | Vendor trace context |
| `X-Correlation-ID` | Optional externally; always returned | User/support correlation |
| `X-Case-Access-Grant` | Internal/provisional | Scoped support authorization |
| `X-Workflow-ID` | Internal workflows | Long-running workflow correlation |

W3C Trace Context defines `traceparent` and `tracestate` for interoperable distributed trace propagation. ([w3.org](https://www.w3.org/TR/trace-context/))

Clients cannot choose trusted actor, organization, role or account headers. Those values are derived from validated identity and authorization context.

---

## 9. Response headers

| Header | Use |
|---|---|
| `Location` | URI of newly created resource or workflow |
| `ETag` | Current resource representation/version |
| `Retry-After` | Rate limiting or temporary unavailability where known |
| `X-Correlation-ID` | Correlation reference returned to caller |
| `Cache-Control` | Explicit caching policy |
| `Deprecation` | Provisional pending final standards/tooling decision |
| `Sunset` | Provisional for scheduled API retirement |

A successful resource creation normally returns `201 Created` with `Location`. A `202 Accepted` response means processing has started but is not complete. ([rfc-editor.org](https://www.rfc-editor.org/rfc/inline-errata/rfc7231.html))

---

## 10. Authentication and actor context

## 10.1 Public operations

No account required:

- Station discovery
- Station details
- Advisory availability
- Public reference-data lookup

## 10.2 Driver operations

Require:

- Valid access token
- Active verified account where state changes are requested
- Resource ownership

## 10.3 Operator operations

Require:

- Valid access token
- MFA assurance
- Canonical operator role
- Current organization membership
- Resource ownership

## 10.4 Platform operations

Require:

- MFA assurance
- Platform role
- Case scope or explicit administrative permission
- Recent reauthentication for high-risk actions
- Reason for exceptional actions

## 10.5 Internal service operations

Require:

- Authenticated service identity
- Intended audience
- Allowed caller-service scope
- Originating actor context where acting on behalf of a human
- Independent authorization by the recipient

The exact service-identity and actor-context format remains pending the security architecture.

---

## 11. Authorization response policy

- `401 Unauthorized`: authentication missing or invalid.
- `403 Forbidden`: authenticated actor lacks permission where revealing resource existence is safe.
- `404 Not Found`: resource absent or object-level authorization requires existence masking.
- Public responses must not reveal another driver’s booking, session or case.
- Authorization is evaluated on every request by the authoritative owner.
- The Gateway’s authorization is not final.

---

## 12. Problem response model

All REST errors use RFC 9457 Problem Details. RFC 9457 defines `application/problem+json` for machine-readable HTTP API errors and supersedes older API error detail conventions. ([rfc-editor.org](https://www.rfc-editor.org/rfc/rfc9457.html))

Required fields:

| Field | Meaning |
|---|---|
| `type` | Stable problem-type URI |
| `title` | Stable short summary |
| `status` | HTTP status |
| `detail` | Safe occurrence-specific explanation |
| `instance` | Safe occurrence reference |
| `code` | Stable platform reason code |
| `correlationId` | Support correlation reference |
| `timestamp` | UTC occurrence time |
| `retryable` | Whether retry may be appropriate |

Optional extensions:

- `fieldErrors`
- `currentVersion`
- `allowedActions`
- `retryAfterSeconds`
- `availabilityReasons`
- `workflowRef`
- `conflictingResourceRef`

Problem details must not expose:

- Stack traces
- SQL
- Internal network names
- Secrets
- Tokens
- Another user’s information
- Sensitive device diagnostics

---

## 13. Standard status-code policy

| Status | Use |
|---|---|
| `200 OK` | Successful query or command returning representation |
| `201 Created` | Resource durably created |
| `202 Accepted` | Long-running workflow accepted |
| `204 No Content` | Successful command with no representation |
| `400 Bad Request` | Malformed syntax or invalid parameter format |
| `401 Unauthorized` | Missing/invalid authentication |
| `403 Forbidden` | Authenticated but unauthorized |
| `404 Not Found` | Absent or existence-masked resource |
| `409 Conflict` | Domain-state, allocation or idempotency conflict |
| `410 Gone` | Expired one-time resource where safe |
| `412 Precondition Failed` | `If-Match` version mismatch |
| `415 Unsupported Media Type` | Unsupported content type |
| `422 Unprocessable Content` | Structurally valid request violating semantic validation |
| `428 Precondition Required` | Mandatory `If-Match` absent |
| `429 Too Many Requests` | Rate limit exceeded |
| `500 Internal Server Error` | Unexpected internal failure |
| `502 Bad Gateway` | Invalid/upstream gateway response |
| `503 Service Unavailable` | Temporary dependency or service unavailability |
| `504 Gateway Timeout` | Gateway did not receive a timely response |

HTTP defines `409` for conflict with current resource state, `412` for failed preconditions and `422` for semantically unprocessable content. `If-Match` supports lost-update prevention. ([rfc-editor.org](https://www.rfc-editor.org/rfc/rfc9110.html))

Rate-limited requests use `429` and should include `Retry-After` where a useful retry time is known. ([rfc-editor.org](https://www.rfc-editor.org/info/rfc6585/))

---

## 14. Stable problem codes

Initial shared codes:

### General

- `VALIDATION_FAILED`
- `MALFORMED_REQUEST`
- `AUTHENTICATION_REQUIRED`
- `ACCESS_DENIED`
- `RESOURCE_NOT_FOUND`
- `VERSION_CONFLICT`
- `PRECONDITION_REQUIRED`
- `RATE_LIMITED`
- `DEPENDENCY_UNAVAILABLE`
- `OPERATION_TIMEOUT`
- `INTERNAL_ERROR`

### Idempotency

- `IDEMPOTENCY_KEY_REQUIRED`
- `IDEMPOTENCY_KEY_REUSED`
- `IDEMPOTENCY_RESULT_PENDING`

### Account

- `ACCOUNT_NOT_ACTIVE`
- `EMAIL_NOT_VERIFIED`
- `ACCOUNT_RESTRICTED`
- `ACTIVE_OBLIGATION_EXISTS`

### Availability and booking

- `INVALID_INTERVAL`
- `OUTSIDE_ADVANCE_WINDOW`
- `OUTSIDE_OPENING_HOURS`
- `NO_COMPATIBLE_EVSE`
- `EVSE_ALLOCATION_CONFLICT`
- `STATUS_STALE`
- `STATUS_UNKNOWN`
- `MAINTENANCE_CONFLICT`
- `BOOKING_STATE_CONFLICT`
- `BOOKING_HOLD_EXPIRED`
- `CHECK_IN_WINDOW_CLOSED`
- `WRONG_EVSE`
- `START_AUTHORIZATION_INVALID`
- `START_AUTHORIZATION_CONSUMED`

### Charging

- `SESSION_STATE_CONFLICT`
- `START_REJECTED`
- `COMMAND_OUTCOME_UNCERTAIN`
- `SESSION_OUTCOME_UNRESOLVED`

### Operator and support

- `ORGANIZATION_SCOPE_VIOLATION`
- `CASE_SCOPE_REQUIRED`
- `PRIVILEGED_REASON_REQUIRED`
- `BREAK_GLASS_REQUIRED`

Service-specific catalogues may add codes but cannot redefine shared meanings.

---

## 15. Idempotency

## 15.1 Required operations

`Idempotency-Key` is mandatory for:

- Booking hold creation
- Booking confirmation
- Rescheduling
- Cancellation
- Check-in
- Check-in abandonment
- Charging start and stop
- Reassignment
- Maintenance scheduling
- Fault submission
- Status overrides
- Invitations and ownership transfer
- Suspension and emergency intervention
- Privacy export/deletion requests
- Manual notification resend

## 15.2 Semantics

The idempotency scope is:

- Authenticated principal or service
- Operation
- Target resource where applicable
- Idempotency key

Rules:

1. The first request stores a request fingerprint and durable outcome.
2. Exact retries return the prior semantic result.
3. Reusing the key with a different payload returns `409 IDEMPOTENCY_KEY_REUSED`.
4. An operation still processing may return `202` or `409 IDEMPOTENCY_RESULT_PENDING`.
5. Idempotency does not bypass optimistic concurrency checks.
6. Gateway retries must preserve the original key.
7. Keys and stored results are retained for an operation-specific replay window.

Proposed minimum retention:

- Ordinary commands: 24 hours
- Booking/session commands: 7 days
- Privacy and administrative workflows: workflow lifetime plus audit window

Final periods are resolved during data design.

---

## 16. Optimistic concurrency

Mutable administrative resources return `ETag`.

Updates and destructive lifecycle commands require:

`If-Match: "<version>"`

Applies to:

- Profile
- Vehicle
- Notification preferences
- Organization profile
- Membership role
- Station
- EVSE
- Connector
- Tariff draft
- Booking-policy draft
- Maintenance draft
- Fault Incident
- Support Case

Results:

- Missing required precondition: `428`
- Stale version: `412 VERSION_CONFLICT`
- Domain-state conflict despite matching version: `409`

Booking allocation additionally uses datastore-enforced transactional concurrency and does not rely only on ETags.

---

## 17. Pagination

List endpoints use cursor pagination by default.

Query parameters:

- `limit`
- `cursor`
- `sort`

Response metadata:

- `items`
- `nextCursor`
- `hasMore`

Rules:

- Default limit: 20
- Maximum ordinary limit: 100
- Stable tie-breaker is always included in sorting.
- Cursors are opaque and cannot be constructed by clients.
- Authorization filters apply before pagination.
- Offset pagination may be used only for small immutable reference lists.
- Total counts are optional and omitted when expensive or privacy-sensitive.

---

## 18. Filtering and sorting

- Filters use query parameters.
- Repeated parameters represent multi-value filters.
- Date ranges use `from` and `to`.
- Geographic bounds use `north`, `south`, `east`, `west`.
- Unsupported filters return `400`.
- Sort fields are allowlisted.
- Default sort is documented per endpoint.
- Free-form query syntax is not supported in v1.

---

## 19. Sparse fields and expansion

General-purpose client-selected field expansion is excluded from v1.

Rationale:

- It complicates authorization.
- It can expose personal data accidentally.
- It creates unpredictable queries.
- It weakens contract stability.

Purpose-specific summary and detail representations are used instead.

---

## 20. Caching

### Public discovery

May use short-lived caches with:

- `Cache-Control`
- `ETag`
- Projection freshness fields

Cached discovery remains advisory.

### Authenticated personal resources

Use:

`Cache-Control: private, no-store`

unless a security review approves otherwise.

### State-changing responses

Must not be shared-cacheable.

### Internal enforcement data

REST caching must never replace the versioned local enforcement projections defined in ARC-002.

---

# 21. Account Service — edge operations

Base: `/api/v1/me`

| Method and path | Operation ID | Purpose | Auth | Idempotency |
|---|---|---|---|---|
| `GET /me` | `getMyProfile` | Read driver profile/account state | Driver | No |
| `PATCH /me` | `updateMyProfile` | Update profile fields | Driver | `If-Match` |
| `GET /me/vehicles` | `listMyVehicles` | List saved vehicles | Driver | No |
| `POST /me/vehicles` | `createMyVehicle` | Create saved vehicle | Driver | Required |
| `GET /me/vehicles/{vehicleRef}` | `getMyVehicle` | Read vehicle | Driver owner | No |
| `PATCH /me/vehicles/{vehicleRef}` | `updateMyVehicle` | Update vehicle | Driver owner | `If-Match` |
| `DELETE /me/vehicles/{vehicleRef}` | `deleteMyVehicle` | Delete unused profile vehicle | Driver owner | Required + `If-Match` |
| `GET /me/notification-preferences` | `getMyNotificationPreferences` | Read optional preferences | Driver | No |
| `PUT /me/notification-preferences` | `replaceMyNotificationPreferences` | Replace optional preferences | Driver | `If-Match` |
| `GET /me/login-sessions` | `listMyLoginSessions` | Identity-provider session view | Driver | No |
| `DELETE /me/login-sessions/{sessionRef}` | `revokeMyLoginSession` | Revoke one identity session | Driver | Required |
| `POST /me/login-sessions/revoke-all` | `revokeAllMyLoginSessions` | Revoke all identity sessions | Driver | Required |
| `POST /me/privacy-exports` | `requestMyPrivacyExport` | Start access/portability export | Recent auth | Required |
| `GET /me/privacy-exports` | `listMyPrivacyExports` | List export workflows | Driver | No |
| `GET /me/privacy-exports/{exportRef}` | `getMyPrivacyExport` | Read export status | Driver owner | No |
| `POST /me/privacy-exports/{exportRef}/download-authorizations` | `authorizePrivacyExportDownload` | Create short-lived download | Recent auth | Required |
| `POST /me/deletion-requests` | `requestMyAccountDeletion` | Start deletion validation | Recent auth | Required |
| `GET /me/deletion-requests/current` | `getMyDeletionRequest` | Read deletion status | Driver | No |
| `POST /me/deletion-requests/{requestRef}/cancel` | `cancelMyDeletionRequest` | Cancel during allowed window | Recent auth | Required |

Registration, verification, sign-in, recovery, MFA and token operations remain Identity Provider endpoints.

---

# 22. Discovery and Insights — public operations

Base: `/api/v1`

| Method and path | Operation ID | Purpose |
|---|---|---|
| `GET /stations` | `searchStations` | Search map/list projection |
| `GET /stations/{stationRef}` | `getStationDetails` | Public station details |
| `GET /stations/{stationRef}/availability` | `getStationAvailability` | Advisory interval availability |
| `GET /stations/{stationRef}/evses/{evseRef}` | `getPublicEvseDetails` | Public EVSE/connector details |
| `GET /reference-data/connector-types` | `listConnectorTypes` | Public connector reference data |
| `GET /reference-data/amenities` | `listAmenities` | Public amenity reference data |
| `GET /operators/{operatorRef}/public-profile` | `getOperatorPublicProfile` | Public operator information |

## 22.1 Station search parameters

Supported:

- Geographic bounds
- Text location query
- Requested `startAt`
- `durationMinutes`
- Connector types
- Minimum power
- Availability result
- Price range
- Operator
- Access type
- Amenities
- Cursor, limit and sort

Response includes:

- `evaluatedAt`
- `projectionUpdatedAt`
- `statusFreshness`
- Requested interval
- Compatible EVSE count
- Advisory bookable EVSE count
- Safe reason codes

Public search must not expose another driver’s allocations.

---

# 23. Booking and Session — driver operations

Base: `/api/v1/bookings`

| Method and path | Operation ID | Success | Purpose |
|---|---|---|---|
| `POST /bookings/holds` | `createBookingHold` | `201` | Atomically hold one EVSE |
| `POST /bookings/{bookingRef}/confirm` | `confirmBooking` | `200` | Confirm unexpired hold |
| `GET /bookings/{bookingRef}` | `getMyBooking` | `200` | Read authoritative booking |
| `GET /bookings/upcoming` | `listMyUpcomingBookings` | `200` | Upcoming bookings |
| `GET /bookings/history` | `listMyBookingHistory` | `200` | Booking/session history |
| `POST /bookings/{bookingRef}/reschedule` | `rescheduleBooking` | `200` | Atomic reschedule |
| `POST /bookings/{bookingRef}/cancel` | `cancelMyBooking` | `200` | Cancel eligible booking |
| `POST /bookings/{bookingRef}/check-in` | `checkInToBooking` | `200` | Check in at assigned EVSE |
| `POST /bookings/{bookingRef}/abandon-check-in` | `abandonBookingCheckIn` | `200` | Revoke authorization and abandon |
| `POST /bookings/{bookingRef}/charging-session/start` | `startChargingSession` | `202` | Commit `STARTING`, queue device command |
| `GET /bookings/{bookingRef}/charging-session` | `getMyChargingSession` | `200` | Current authoritative session |
| `POST /bookings/{bookingRef}/charging-session/stop` | `stopChargingSession` | `202` | Commit `STOPPING`, queue stop |
| `GET /bookings/{bookingRef}/charging-session/summary` | `getMySessionSummary` | `200` | Final summary |
| `POST /bookings/{bookingRef}/fault-reports` | `reportBookingFault` | `201` | Submit linked fault report |

## 23.1 Create hold request

Contains:

- Station reference
- Scheduled start
- Duration
- Required connector type
- Optional minimum power
- Optional vehicle reference
- Assignment mode: `AUTOMATIC` or `EXACT_EVSE`
- Optional exact EVSE reference

Success includes:

- Booking reference
- State `HELD`
- Assigned EVSE
- Hold expiration
- Charging and allocation intervals
- Estimated tariff snapshot preview
- Allowed actions
- `ETag`

Common failures:

- `409 EVSE_ALLOCATION_CONFLICT`
- `409 NO_COMPATIBLE_EVSE`
- `409 ACCOUNT_NOT_ACTIVE`
- `422 INVALID_INTERVAL`
- `503 DEPENDENCY_UNAVAILABLE`
- `503 STATUS_UNKNOWN`

Exactly one concurrent conflicting hold may succeed.

## 23.2 Start and stop semantics

`202 Accepted` means the intent was durably recorded and device processing began asynchronously.

It does not mean:

- Charging started
- Charging stopped
- The simulator accepted the command
- Physical completion occurred

The returned representation includes:

- Session reference
- Current state
- Command/workflow reference
- Status URI
- `retryAfterSeconds` where useful

---

# 24. Station Operations — operator organization operations

Base: `/api/v1/operator`

| Method and path | Operation ID | Actor |
|---|---|---|
| `POST /operator-applications` | `createOperatorApplication` | Verified account |
| `GET /operator-applications/{applicationRef}` | `getMyOperatorApplication` | Applicant |
| `POST /operator-applications/{applicationRef}/submit` | `submitOperatorApplication` | Applicant |
| `POST /operator-applications/{applicationRef}/withdraw` | `withdrawOperatorApplication` | Applicant |
| `GET /organizations/{organizationRef}` | `getOperatorOrganization` | Operator member |
| `PATCH /organizations/{organizationRef}` | `updateOperatorOrganization` | Owner/Manager |
| `GET /organizations/{organizationRef}/members` | `listOrganizationMembers` | Owner/Manager |
| `POST /organizations/{organizationRef}/invitations` | `inviteOrganizationMember` | Owner/Manager |
| `POST /organizations/{organizationRef}/invitations/{invitationRef}/revoke` | `revokeOrganizationInvitation` | Owner/Manager |
| `PATCH /organizations/{organizationRef}/members/{memberRef}` | `changeOrganizationMemberRole` | Authorized role |
| `DELETE /organizations/{organizationRef}/members/{memberRef}` | `removeOrganizationMember` | Authorized role |
| `POST /organizations/{organizationRef}/ownership-transfers` | `requestOwnershipTransfer` | Owner |
| `POST /ownership-transfers/{transferRef}/accept` | `acceptOwnershipTransfer` | Target |
| `POST /ownership-transfers/{transferRef}/cancel` | `cancelOwnershipTransfer` | Current owner |
| `GET /organizations/{organizationRef}/audit-events` | `listOrganizationAuditEvents` | Owner/permitted Manager |

Invitations and ownership transfers use one-time Identity Provider action links where applicable.

---

# 25. Station Operations — infrastructure operations

Base: `/api/v1/operator/organizations/{organizationRef}`

| Method and path | Operation ID | Notes |
|---|---|---|
| `GET /stations` | `listOwnedStations` | Operator-scoped |
| `POST /stations` | `createStation` | Creates `DRAFT` |
| `GET /stations/{stationRef}` | `getOwnedStation` | Detail |
| `PATCH /stations/{stationRef}` | `updateStation` | `If-Match` |
| `POST /stations/{stationRef}/publish` | `publishStation` | Validates invariants |
| `POST /stations/{stationRef}/temporary-closures` | `temporarilyCloseStation` | Workflow may return `202` |
| `POST /stations/{stationRef}/reopen` | `reopenStation` | Validated |
| `POST /stations/{stationRef}/deactivate` | `deactivateStation` | Obligations must be resolved |
| `POST /stations/{stationRef}/reactivate-as-draft` | `reactivateStationAsDraft` | Full revalidation required |
| `GET /stations/{stationRef}/evses` | `listStationEvses` | Operator detail |
| `POST /stations/{stationRef}/evses` | `createEvse` | Initially disabled |
| `GET /stations/{stationRef}/evses/{evseRef}` | `getOwnedEvse` | Detail |
| `PATCH /stations/{stationRef}/evses/{evseRef}` | `updateEvse` | `If-Match` |
| `POST /stations/{stationRef}/evses/{evseRef}/activate` | `activateEvse` | Connector required |
| `POST /stations/{stationRef}/evses/{evseRef}/disable` | `disableEvse` | May coordinate restriction |
| `POST /stations/{stationRef}/evses/{evseRef}/deactivate` | `deactivateEvse` | Terminal in v1 |
| `POST /stations/{stationRef}/evses/{evseRef}/connectors` | `createConnector` | Operator Manager |
| `PATCH /stations/{stationRef}/evses/{evseRef}/connectors/{connectorRef}` | `updateConnector` | `If-Match` |
| `POST /stations/{stationRef}/tariffs` | `createTariffVersion` | Draft/versioned |
| `GET /stations/{stationRef}/tariffs` | `listStationTariffs` | Version history |
| `POST /stations/{stationRef}/tariffs/{tariffRef}/activate` | `activateTariffVersion` | Prevent ambiguous overlap |
| `POST /stations/{stationRef}/booking-policies` | `createBookingPolicyVersion` | Versioned |
| `POST /stations/{stationRef}/booking-policies/{policyRef}/activate` | `activateBookingPolicyVersion` | Existing snapshots unchanged |

Hard-delete APIs are excluded for infrastructure with history.

---

# 26. Station Operations — operational workflows

Base: `/api/v1/operator/organizations/{organizationRef}`

| Method and path | Operation ID | Success |
|---|---|---|
| `GET /operations/evses` | `listOperationalEvseStatus` | `200` |
| `GET /stations/{stationRef}/booking-impact` | `previewStationBookingImpact` | `200` |
| `POST /maintenance-impact-previews` | `previewMaintenanceImpact` | `200` |
| `POST /maintenances` | `scheduleMaintenance` | `201` or `202` |
| `GET /maintenances/{maintenanceRef}` | `getMaintenance` | `200` |
| `PATCH /maintenances/{maintenanceRef}` | `updateScheduledMaintenance` | `200` |
| `POST /maintenances/{maintenanceRef}/cancel` | `cancelScheduledMaintenance` | `200` |
| `POST /maintenances/{maintenanceRef}/complete` | `completeMaintenance` | `200` |
| `GET /fault-incidents` | `listFaultIncidents` | `200` |
| `POST /fault-incidents` | `createFaultIncident` | `201` |
| `GET /fault-incidents/{faultRef}` | `getFaultIncident` | `200` |
| `POST /fault-incidents/{faultRef}/acknowledge` | `acknowledgeFaultIncident` | `200` |
| `POST /fault-incidents/{faultRef}/begin-work` | `beginFaultRepair` | `200` |
| `POST /fault-incidents/{faultRef}/resolve` | `resolveFaultIncident` | `200` |
| `POST /fault-incidents/{faultRef}/reopen` | `reopenFaultIncident` | `200` |
| `POST /status-overrides` | `createStatusOverride` | `201` or `202` |
| `POST /status-overrides/{overrideRef}/revoke` | `revokeStatusOverride` | `200` |
| `GET /bookings` | `listOwnedStationBookings` | `200` |
| `POST /bookings/{bookingRef}/reassignment-requests` | `requestBookingReassignment` | `202` |
| `POST /bookings/{bookingRef}/cancellation-requests` | `requestOperatorBookingCancellation` | `202` |
| `GET /charging-sessions/active` | `listOwnedActiveSessions` | `200` |

Booking state changes are executed by Booking and Session, even when initiated from the operator API.

---

# 27. Device Integration — operator control operations

Base: `/api/v1/operator/organizations/{organizationRef}/simulators`

| Method and path | Operation ID |
|---|---|
| `GET /assignments` | `listSimulatorAssignments` |
| `POST /assignments` | `createSimulatorAssignment` |
| `GET /assignments/{assignmentRef}` | `getSimulatorAssignment` |
| `POST /assignments/{assignmentRef}/enrollment-credentials` | `issueSimulatorEnrollmentCredential` |
| `POST /assignments/{assignmentRef}/suspend` | `suspendSimulatorIdentity` |
| `POST /assignments/{assignmentRef}/revoke` | `revokeSimulatorIdentity` |
| `GET /stations/{stationRef}/status` | `getSimulatorStationStatus` |
| `POST /stations/{stationRef}/simulation-profile` | `setSimulationProfile` |
| `POST /stations/{stationRef}/fault-injections` | `injectSimulatorFault` |
| `POST /stations/{stationRef}/fault-injections/{injectionRef}/clear` | `clearSimulatorFault` |
| `POST /stations/{stationRef}/disconnect` | `disconnectSimulator` |
| `POST /stations/{stationRef}/reconnect` | `reconnectSimulator` |
| `POST /stations/{stationRef}/status-report-requests` | `requestSimulatorStatusReport` |
| `POST /stations/{stationRef}/resets` | `resetSimulator` |
| `GET /stations/{stationRef}/commands` | `listSimulatorCommands` |
| `GET /stations/{stationRef}/events` | `listSanitizedSimulatorEvents` |

These operations cannot forge driver authorization or directly change Booking state.

---

# 28. Discovery and Insights — operator analytics

Base: `/api/v1/operator/organizations/{organizationRef}/analytics`

| Method and path | Operation ID |
|---|---|
| `GET /utilization` | `getOrganizationUtilizationAnalytics` |
| `GET /energy` | `getOrganizationEnergyAnalytics` |
| `GET /sessions` | `getOrganizationSessionAnalytics` |
| `GET /cancellations` | `getOrganizationCancellationAnalytics` |
| `GET /failures` | `getOrganizationFailureAnalytics` |
| `POST /report-exports` | `createOrganizationReportExport` |
| `GET /report-exports/{exportRef}` | `getOrganizationReportExport` |

Responses include:

- Metric-definition version
- Projection freshness
- Aggregation interval
- Suppression indicator
- Applied filters

Driver-level bulk data is prohibited.

---

# 29. Platform Governance and Support operations

Base: `/api/v1/platform`

## 29.1 Support cases

| Method and path | Operation ID |
|---|---|
| `POST /support-cases` | `createSupportCase` |
| `GET /support-cases` | `listSupportCases` |
| `GET /support-cases/{caseRef}` | `getSupportCase` |
| `PATCH /support-cases/{caseRef}` | `updateSupportCase` |
| `POST /support-cases/{caseRef}/assignments` | `assignSupportCase` |
| `POST /support-cases/{caseRef}/wait-for-user` | `waitForSupportUser` |
| `POST /support-cases/{caseRef}/wait-for-operator` | `waitForSupportOperator` |
| `POST /support-cases/{caseRef}/resolve` | `resolveSupportCase` |
| `POST /support-cases/{caseRef}/close` | `closeSupportCase` |
| `POST /support-cases/{caseRef}/reopen` | `reopenSupportCase` |
| `POST /support-cases/{caseRef}/access-grants` | `createCaseAccessGrant` |
| `POST /support-cases/{caseRef}/reveals` | `revealMaskedCaseField` |

## 29.2 Administration

| Method and path | Operation ID |
|---|---|
| `GET /operator-applications` | `listPendingOperatorApplications` |
| `GET /operator-applications/{applicationRef}` | `reviewOperatorApplicationDetails` |
| `POST /operator-applications/{applicationRef}/request-clarification` | `requestOperatorClarification` |
| `POST /operator-applications/{applicationRef}/approve` | `approveOperatorApplication` |
| `POST /operator-applications/{applicationRef}/reject` | `rejectOperatorApplication` |
| `POST /accounts/{accountRef}/suspensions` | `suspendAccount` |
| `POST /accounts/{accountRef}/reactivations` | `reactivateAccount` |
| `POST /organizations/{organizationRef}/suspensions` | `suspendOrganization` |
| `POST /organizations/{organizationRef}/reactivations` | `reactivateOrganization` |
| `POST /stations/{stationRef}/moderation-actions` | `moderateStation` |
| `GET /reference-data` | `listPlatformReferenceData` |
| `POST /reference-data/{category}` | `createReferenceDataVersion` |
| `POST /reference-data/{category}/{valueRef}/deprecate` | `deprecateReferenceValue` |

## 29.3 Emergency and audit

| Method and path | Operation ID |
|---|---|
| `POST /emergency-interventions` | `createEmergencyIntervention` |
| `GET /emergency-interventions/{interventionRef}` | `getEmergencyIntervention` |
| `POST /break-glass-requests` | `requestBreakGlassAccess` |
| `POST /break-glass-requests/{requestRef}/approve` | `approveBreakGlassAccess` |
| `POST /break-glass-access/{grantRef}/revoke` | `revokeBreakGlassAccess` |
| `GET /audit-events` | `searchCentralAuditProjection` |
| `GET /security-events` | `searchSecurityEvents` |

Every exceptional command requires:

- Structured reason
- Recent authentication
- MFA
- Narrow resource scope
- Idempotency key
- Immutable audit evidence

---

# 30. Internal Account APIs

Base: `/internal/v1/accounts`

| Method and path | Caller | Purpose |
|---|---|---|
| `GET /{accountRef}/notification-routing` | Notification | Email destination, locale, allowed preferences |
| `GET /{accountRef}/support-view` | Governance | Case-scoped masked account view |
| `GET /{accountRef}/privacy-export-contribution` | Account workflow internal only | Not exposed cross-service; use async workflow |
| `POST /{accountRef}/restriction-requests` | Governance | Request account restriction workflow |
| `GET /restrictions/{workflowRef}` | Governance | Restriction status |
| `GET /{accountRef}/booking-eligibility` | Booking repair only | Resolve projection gap outside transaction |

Ordinary booking creation must not call `booking-eligibility` synchronously.

---

# 31. Internal Station Operations APIs

Base: `/internal/v1/station-operations`

| Method and path | Caller | Purpose |
|---|---|---|
| `GET /bookable-configurations/{evseRef}` | Booking repair | Retrieve authoritative versioned configuration |
| `GET /stations/{stationRef}/notification-routing` | Notification | Resolve operator recipients |
| `GET /stations/{stationRef}/support-view` | Governance | Case-scoped infrastructure view |
| `GET /simulator-assignments/{stationRef}` | Device Integration | Validate assignment/inventory |
| `GET /tariffs/{tariffRef}/versions/{version}` | Booking repair | Retrieve immutable version |
| `GET /booking-policies/{policyRef}/versions/{version}` | Booking repair | Retrieve immutable version |
| `GET /maintenances/{maintenanceRef}` | Booking reconciliation | Repair workflow state |

These calls occur outside allocation transactions.

---

# 32. Internal Booking and Session APIs

Base: `/internal/v1/booking-operations`

| Method and path | Caller | Purpose |
|---|---|---|
| `POST /impact-previews` | Station Operations | Non-binding maintenance/closure preview |
| `POST /capacity-blocks` | Station Operations workflow | Install restriction atomically |
| `GET /capacity-blocks/{blockRef}` | Station Operations | Read installation state |
| `POST /capacity-blocks/{blockRef}/release` | Station Operations workflow | Release coordinated block |
| `GET /bookings/{bookingRef}/operator-view` | Station Operations | Owned-station minimized view |
| `GET /bookings/{bookingRef}/support-view` | Governance | Case-scoped masked view |
| `POST /bookings/{bookingRef}/operator-cancellations` | Station Operations/Governance | Authoritative cancellation |
| `POST /bookings/{bookingRef}/reassignments` | Station Operations/Governance | Authoritative reassignment |
| `POST /sessions/{sessionRef}/emergency-stops` | Governance | Emergency stop intent |
| `POST /driver-restrictions` | Account/Governance workflow | Install booking restriction |
| `POST /driver-restrictions/{restrictionRef}/release` | Account workflow | Remove restriction |
| `GET /privacy-obligations/{accountRef}` | Account | Active booking/session blocker check |

All state-changing internal operations require idempotency and caller authorization.

---

# 33. Internal Device Integration APIs

Base: `/internal/v1/device-integration`

Most device actions use asynchronous commands. REST is limited to query and reconciliation support.

| Method and path | Caller | Purpose |
|---|---|---|
| `GET /evses/{evseRef}/current-state` | Booking reconciliation | Current accepted device state |
| `GET /sessions/{sessionRef}/device-state` | Booking reconciliation | Device transaction evidence |
| `GET /commands/{commandRef}` | Booking | Command status |
| `POST /commands/{commandRef}/reconciliation-requests` | Booking | Request reconciliation |
| `GET /stations/{stationRef}/connection-state` | Station Operations | Operational view |

REST must not be used to synchronously wait for physical device completion.

---

# 34. Internal Governance APIs

Base: `/internal/v1/governance`

| Method and path | Caller | Purpose |
|---|---|---|
| `GET /case-access-grants/{grantRef}` | Authoritative services | Validate grant when signed-local validation is unavailable |
| `POST /audit-events` | Restricted legacy/fallback only | Submit audit fact if event route unavailable |
| `GET /emergency-interventions/{workflowRef}` | Authoritative services | Validate intervention scope |

Preferred audit propagation remains asynchronous. The exact support-grant mechanism is resolved by the security architecture.

---

# 35. Workflow resources

Long-running REST commands return a workflow representation containing:

- `workflowRef`
- `workflowType`
- `status`
- `submittedAt`
- `updatedAt`
- `completedAt`
- `currentStep`
- `resultRef`
- `failureCode`
- `retryable`
- `links`

Canonical workflow states:

- `REQUESTED`
- `IN_PROGRESS`
- `WAITING`
- `COMPLETED`
- `FAILED`
- `REQUIRES_REVIEW`
- `CANCELLED`

These are transport/workflow summary states and do not replace domain-specific lifecycle states.

---

# 36. Search and list consistency

Every non-authoritative response includes:

- `dataFreshness`
- `projectionUpdatedAt`
- `sourceVersion` where practical
- `rebuilding` indicator where relevant

Canonical freshness:

- `LIVE`
- `STALE`
- `UNKNOWN`

Search APIs must never claim that a displayed result reserves capacity.

---

# 37. Validation

Validation occurs in layers:

1. JSON/media-type validation
2. OpenAPI schema validation
3. Field-level constraints
4. Authorization
5. Domain invariants
6. Current-state/concurrency checks
7. Dependency/enforcement-projection checks

Rules:

- Unknown request fields are rejected for state-changing operations.
- Unknown response fields must be tolerated by clients.
- Strings are length-limited.
- Free text is sanitized and treated as untrusted.
- Geographic, money and electrical values use bounded ranges.
- Time intervals are validated using database time for authoritative operations.
- Validation errors identify safe field paths.

---

# 38. Rate-limit classes

| Class | Examples | Relative policy |
|---|---|---|
| `PUBLIC_SEARCH` | Station search/details | Moderate per IP/client |
| `AUTH_SECURITY` | Recovery, verification resend | Strict |
| `BOOKING_WRITE` | Holds, confirmation, reschedule | Strict per account/IP |
| `CHECK_IN_START` | Check-in/start | Strict per booking/account/EVSE |
| `OPERATOR_WRITE` | Infrastructure changes | Moderate |
| `PRIVILEGED` | Suspension/emergency/reveal | Very strict |
| `EXPORT` | Privacy/report exports | Low frequency |
| `INTERNAL_QUERY` | Service reads | Service quota/circuit breaker |
| `INTERNAL_COMMAND` | Restriction/intervention | Service and workflow quota |

Exact thresholds remain part of the security and performance design.

---

# 39. API security requirements

- TLS is mandatory outside isolated local development.
- Access tokens are never accepted in query parameters.
- Tokens, cookies and authorization headers are never logged.
- CORS uses an explicit allowlist.
- Browser-to-BFF communication uses secure, opaque HTTP-only session cookies plus CSRF token protection.
- Service-to-service and BFF-to-service communication uses standard JWT bearer access tokens.
- Browser mutations require the selected CSRF-safe token/session architecture.
- Request bodies have strict size limits.
- Object-level authorization is mandatory.
- Mass assignment is prevented through explicit request schemas.
- Sensitive fields use response allowlists.
- Internal APIs reject public/user tokens lacking service audience.
- Error detail is minimized.
- OpenAPI documentation for internal APIs is access-controlled in deployed environments.
- File upload endpoints are excluded from v1.
- Redirect destinations are allowlisted.
- Public references are not authorization controls.

---

# 40. Contract testing requirements

Every operation requires:

1. OpenAPI schema validation
2. Provider contract test
3. Consumer contract test where another service depends on it
4. Authorization test
5. Problem-response test
6. Idempotency test for commands
7. Concurrency/precondition test where mutable
8. Data-minimization test
9. Backward-compatibility check
10. Requirement and invariant traceability

Critical REST scenarios:

- Duplicate booking hold
- Concurrent exact-EVSE booking
- Confirmation after expiry
- Reschedule version race
- Cancel versus start race
- Check-in versus no-show
- Duplicate charging start
- Operator cross-organization access
- Support access without case grant
- Stale `If-Match`
- Reused idempotency key with changed payload
- Projection unavailable during booking
- Maintenance preview mistaken for commitment
- Timeout after accepted asynchronous command

---

# 41. Requirement ownership summary

| API area | Principal requirements |
|---|---|
| Account | FR-IAM-01, FR-IAM-04/05, FR-NOT-02, FR-PRV-01–04 |
| Discovery | FR-DIS-01–03, FR-AVL-01/02 advisory |
| Booking | FR-AVL-03, FR-BKG-01–07, FR-HIS-01 |
| Charging | FR-CHG-01–04 |
| Station Operations | FR-FLT-01, FR-OPS-01–04 |
| Operator Analytics | FR-OPS-05 |
| Governance/Support | FR-ADM-01/02, FR-SUP-01/02, FR-AUD-02 |
| Simulator Control | FR-SIM-01–03 |
| Internal Workflow APIs | FR-PLT-04/05 |
| Errors, idempotency, versions | FR-PLT-01–06, NFR reliability/security |

---

# 42. Approved REST decisions

| ID | Decision |
|---|---|
| ARC-REST-01 | Use versioned JSON REST APIs for browser and selected synchronous internal communication. |
| ARC-REST-02 | Use `/api/v1` for edge APIs and `/internal/v1` for service APIs. |
| ARC-REST-03 | Describe contracts in OpenAPI 3.0.3, as confirmed by ARC-018 §1 and §8. |
| ARC-REST-04 | Use RFC 9457 Problem Details for all REST errors. |
| ARC-REST-05 | Use opaque public references rather than database IDs. |
| ARC-REST-06 | Use cursor pagination by default. |
| ARC-REST-07 | Use `ETag` and `If-Match` for mutable resource concurrency. |
| ARC-REST-08 | Require `Idempotency-Key` for retryable state-changing commands. |
| ARC-REST-09 | Use domain command subresources for lifecycle transitions rather than generic state patches. |
| ARC-REST-10 | Return `202 Accepted` for durable commands awaiting asynchronous device/workflow completion. |
| ARC-REST-11 | Keep identity credential endpoints in the Identity Provider. |
| ARC-REST-12 | Prohibit remote calls inside Booking allocation transactions. |
| ARC-REST-13 | Keep device physical completion outside REST request lifetimes. |
| ARC-REST-14 | Use purpose-specific representations rather than general field expansion. |
| ARC-REST-15 | Use conservative `404` existence masking for protected user resources. |
| ARC-REST-16 | Require typed stable problem codes in addition to HTTP status. |
| ARC-REST-17 | Require service and originating-actor authorization for internal APIs. |
| ARC-REST-18 | Treat public search and analytics responses as freshness-labelled projections. |
| ARC-REST-19 | Prohibit hard-delete infrastructure APIs where history exists. |
| ARC-REST-20 | Require OpenAPI compatibility checks and contract tests in CI. |

---

# 43. Open questions

| ID | Question | Resolution phase |
|---|---|---|
| ARC-REST-OQ-01 | Confirm OpenAPI 3.0.3 tooling compatibility | Final technology selection |
| ARC-REST-OQ-02 | Exact service authentication and actor-context headers/tokens | RESOLVED (BFF session cookie for browser; JWT bearer tokens for S2S; JWS delegated assertion for human S2S) |
| ARC-REST-OQ-03 | Final support access-grant mechanism | Security architecture |
| ARC-REST-OQ-04 | Final idempotency retention periods | Database/privacy design |
| ARC-REST-OQ-05 | Whether internal APIs use separate hostnames or network routes | Deployment design |
| ARC-REST-OQ-06 | Final geospatial search parameter representation | Frontend/data design |
| ARC-REST-OQ-07 | Final cache durations for public projections | Performance testing |
| ARC-REST-OQ-08 | Final rate-limit thresholds | Security testing |
| ARC-REST-OQ-09 | Whether API Gateway also performs substantial BFF composition | RESOLVED (BFF composition used for browser endpoints; Gateway handles coarse routing) |
| ARC-REST-OQ-10 | Exact API deprecation headers and policy | API governance |
| ARC-REST-OQ-11 | Whether notification recipient resolution remains synchronous | Performance/security review |
| ARC-REST-OQ-12 | Exact privacy export download mechanism | Cloud/security design |

---

# 44. Acceptance criteria

The REST catalogue is approved when:

1. Every synchronous interaction from ARC-002 maps to an operation or explicit exclusion.
2. Every operation has one authoritative owner.
3. Public and internal APIs are clearly separated.
4. Booking allocation remains free of remote transactional dependencies.
5. State-changing retries are idempotent.
6. Mutable resources use concurrency preconditions.
7. Long-running work returns workflow or session status.
8. Device command acceptance is not represented as physical completion.
9. Errors use stable problem codes without leaking sensitive data.
10. Actor, organization, resource and case scopes are enforceable.
11. Public identifiers are not treated as authorization.
12. Search responses expose freshness and advisory status.
13. Privacy and support APIs minimize personal data.
14. No API permits direct foreign-service data mutation.
15. Every operation maps to requirements and future tests.
16. The catalogue can be converted into machine-readable OpenAPI contracts without unresolved semantic ambiguity.

---

# 45. Consequences

## Positive

- Clear synchronous service boundaries
- Consistent client error handling
- Explicit lifecycle commands
- Safer retries and concurrency
- Strong traceability
- Independent contract testing
- Reduced accidental data exposure

## Negative

- Large contract surface
- Idempotency storage overhead
- Additional workflow/status resources
- More authorization scenarios
- ETag and client retry complexity
- Need for strict OpenAPI governance

These costs are accepted because the APIs form long-lived boundaries between independently deployable services.

---

# 46. Next architecture artifact

The next document is:

**Event and Command Contract Catalogue v1.0**

It must define:

- Event and command envelope
- RabbitMQ topology assumptions
- Producers and consumers
- Payload minimization
- Aggregate versions and sequence numbers
- Idempotency and inbox rules
- Retry and dead-letter policy
- Command result semantics
- Workflow messages
- Device-normalized events
- Schema compatibility and evolution
- Event-to-requirement traceability

### REST Contract Implementation Roadmap
To convert this catalogue into machine-readable OpenAPI contracts:
- **OpenAPI Schema Generation:** Public and internal endpoints will be formally described in OpenAPI 3.0 specification files.
- **Standardized Error Codes:** Every failure returns a RFC 9457 problem details object containing a stable error code (e.g. `BOOKING_HOLD_EXPIRED`, `EVSE_STALE_TELEMETRY`).
- **Idempotency Key Behavior:** Booking-changing POST operations validate the `Idempotency-Key` header, returning cached responses for duplicates.
- **Optimistic Concurrency:** HTTP ETags and `If-Match` headers are evaluated to verify configuration state consistency.
