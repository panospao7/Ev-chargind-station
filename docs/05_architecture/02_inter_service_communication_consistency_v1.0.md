Document ID: ARC-002  
Title: Inter-Service Communication and Consistency Matrix  
Version: 1.0  
Status: IN_REVIEW  
Owner: Backend / Distributed Systems Architect  
Last reviewed: 2026-07-12  
Depends on: ARC-001, GOV-001, GOV-003, REQ-001, DOM-002, PLT-001  
Authoritative for: Inter-service communication styles, consistency expectations, failure handling, retries, and service dependencies  

# Inter-Service Communication and Consistency Matrix v1.0

## 1. Purpose

This document defines:

- Synchronous and asynchronous service interactions
- Authoritative sources and local projections
- Consistency expectations
- Commands, events, and queries
- Timeout and retry rules
- Idempotency and ordering requirements
- Failure and fallback behaviour
- Long-running workflow coordination
- Service availability dependencies
- Reconciliation responsibilities

It does not define final URL paths, message payloads, queues, exchanges, or schemas. Those belong to the REST and event-contract phases.

---

## 2. Architectural participants

### Business services

- Account Service
- Station Operations Service
- Booking and Session Service
- Device Integration Service
- Discovery and Insights Service
- Notification Service
- Platform Governance and Support Service

### Platform and external components

- Angular Web Client
- API Gateway/BFF
- Identity Provider
- Message Broker
- Charger Simulator Runtime
- Email Provider
- Secure export/object storage

---

## 3. Communication principles

1. Every business entity has one authoritative owner.
2. Services cannot read or write another service’s database.
3. No cross-service database transaction is permitted.
4. Core state changes commit locally before integration events are published. Projections are authoritative for final allocation; no remote calls occur inside the transaction.
5. Every required integration event uses a Transactional Outbox.
6. Broker delivery is at least once.
7. Consumers must be idempotent.
8. Global ordering is not assumed.
9. Ordering-sensitive events carry aggregate versions or sequence numbers.
10. Commands request actions; events describe completed facts.
11. Queries must not cause business state changes.
12. Search, notifications, analytics, and audit aggregation are eventually consistent.
13. Allocation remains strongly consistent inside Booking and Session.
14. A synchronous dependency cannot participate inside an open allocation database transaction.
15. Failures must never weaken authorization or allocation correctness.
16. Missing critical information produces a conservative rejection or `UNKNOWN`.
17. Business success does not wait for email, analytics, search, or audit-projection updates.
18. Timeout does not prove that a remote side effect failed.
19. Long-running multi-service work uses persisted workflows.
20. Correlation and causation identifiers propagate across every interaction.

---

## 4. Communication styles

## 4.1 External synchronous request

Used between:

- Web Client and API Gateway/BFF
- API Gateway/BFF and business services
- Simulator control UI and appropriate services

Primary style: versioned HTTPS REST.

Use when the user requires an immediate response.

## 4.2 Internal synchronous query

Used when a service requires current information that:

- Is not appropriate to replicate
- Is not part of a local correctness-critical transaction
- Can fail without corrupting local state

Examples:

- Notification Service resolving a current email destination
- Governance Service retrieving case-scoped booking details
- Reconciliation requesting authoritative configuration

## 4.3 Internal synchronous command

Used sparingly when the caller needs an immediate accepted/rejected business result.

Requirements:

- Stable idempotency key
- Explicit timeout
- No ambiguous transparent retry
- Owning service commits independently
- Caller does not hold an open database transaction

Examples:

- Operator cancellation delegated to Booking and Session
- Case-scoped support intervention
- Immediate emergency restriction

## 4.4 Asynchronous integration event

Used to propagate a completed fact to zero or more consumers.

Examples:

- `BookingConfirmed`
- `StationPublished`
- `EvseOperationalStateChanged`
- `AccountDeletionCompleted`

Events are not addressed to a particular consumer.

## 4.5 Asynchronous command

Used for directed, durable work where immediate completion is unnecessary or impossible.

Examples:

- `StartCharging`
- `InstallCapacityBlock`
- `CollectPrivacyExportContribution`
- `AnonymizeAccountData`

The recipient emits a result event.

## 4.6 Persistent device channel

The Charger Simulator communicates with Device Integration through authenticated, versioned JSON WebSockets.

Transport receipt, command acceptance, and simulated physical completion remain separate facts.

---

## 5. Consistency classes

| Class | Name | Meaning |
|---|---|---|
| `C0` | Local strong consistency | One authoritative service and one local transaction |
| `C1` | Synchronous current read | Current remote response required, without distributed commit |
| `C2` | Bounded enforcement projection | Local replica used for correctness; gaps cause fail-closed behaviour |
| `C3` | Eventual projection | Delay is acceptable and freshness is displayed |
| `C4` | Coordinated workflow | Persisted multi-service workflow with acknowledgements and reconciliation |
| `C5` | External uncertain outcome | External/device action may remain uncertain after timeout |

Only `C0` may be used for allocation and lifecycle atomicity.

---

## 6. Booking enforcement projections

The Booking and Session Service maintains local, versioned data required to avoid synchronous dependencies during allocation.

## 6.1 Account eligibility projection

Contains only:

- Account ID
- Booking eligibility
- Suspension/restriction status
- Projection version
- Last update time

It excludes profile and credential data.

If eligibility is missing or cannot be trusted, creation of new bookings fails closed.

Existing booking reads and eligible cancellations do not require Account Service availability.

## 6.2 Bookable infrastructure projection

Contains:

- Station and EVSE public/internal references
- Operator organization reference
- Publication and administrative eligibility
- Connector capabilities
- Maximum power
- Opening hours and exceptions
- Tariff versions
- Booking policy versions
- Configuration versions

It does not replace Station Operations authority.

## 6.3 Device operational projection

Contains:

- EVSE device-reported state
- Status freshness
- Last accepted heartbeat/status time
- Blocking operational confidence
- Source sequence/version

Missing or stale near-term information prevents a positive authoritative booking or check-in decision.

## 6.4 Booking restriction records

Booking and Session owns local enforcement records for:

- Driver restrictions
- Station restrictions
- EVSE capacity blocks
- Maintenance intervals
- Emergency operational blocks

The source business record remains owned by Account or Station Operations.

A source-side restriction is not considered fully applied until Booking acknowledges installation where immediate enforcement is required.

---

# 7. Client and gateway communication matrix

| Caller | Recipient | Purpose | Style | Consistency | Failure behaviour |
|---|---|---|---|---|---|
| Web Client | Identity Provider | Sign-in, verification, recovery, MFA | OIDC Authorization Code + PKCE | External identity authority | No insecure bypass |
| Web Client | API Gateway/BFF | Public and authenticated application requests | HTTPS REST | Depends on owning service | Typed unavailable/error response |
| API Gateway | Account | Profile, vehicles, privacy requests | Synchronous REST | `C0` at Account | No cached mutation result |
| API Gateway | Station Operations | Operator and infrastructure management | Synchronous REST | `C0` at Station Operations | Return dependency unavailable |
| API Gateway | Booking and Session | Driver booking/session operations | Synchronous REST | `C0` at Booking | Core result returned directly |
| API Gateway | Discovery and Insights | Search and analytics | Synchronous REST | `C3` | Show unavailable/stale state |
| API Gateway | Governance and Support | Cases and platform administration | Synchronous REST | `C0` at Governance for cases | No direct fallback to business databases |
| API Gateway | Identity Provider metadata | JWT validation keys | Cached JWKS retrieval | Bounded cache | Existing valid key cache may be used according to security policy |

The Gateway performs coarse authentication and routing. Final authorization remains in each business service.

---

# 8. Core synchronous service matrix

| Caller | Recipient | Interaction | Purpose | Consistency | Retry rule | Failure rule |
|---|---|---|---|---|---|---|
| Account | Identity Provider | Disable/revoke/delete identity | Account suspension or deletion | `C4`/external | Idempotent bounded retry | Workflow remains incomplete |
| Notification | Account | Resolve driver destination, locale, preferences | Render current notification | `C1` | Retry safe query | Delivery remains queued |
| Notification | Station Operations | Resolve operator recipients | Operator notification | `C1` | Retry safe query | Delivery remains queued |
| Governance | Account | Case-scoped account view/action | Support or admin workflow | `C1` or command | Query retry only; command requires idempotency | Case remains open |
| Governance | Station Operations | Case-scoped organization/infrastructure view/action | Moderation/support | `C1` or command | Same as above | No direct state mutation |
| Governance | Booking and Session | View/cancel/reassign/intervene | Support or emergency workflow | `C1` or `C0` at recipient | Idempotent commands only | Intervention remains unresolved |
| Station Operations | Booking and Session | Operator booking query/command | Owned-station support | `C1` or `C0` at recipient | Commands use idempotency key | No direct Booking write |
| Booking and Session | Station Operations | Authoritative repair lookup | Resolve projection gap/version mismatch | `C1`, outside allocation transaction | One bounded query retry | New allocation fails closed |
| Device Integration | Station Operations | Assignment/inventory repair lookup | Validate simulator assignment | `C1` | Safe query retry | Device action quarantined |
| BFF | Multiple query services | Page composition | Combined UI response | Independent reads | Parallel bounded requests | Partial response only when safe |

## 8.1 Synchronous chain limit

A normal user request should contain no more than:

- Gateway/BFF
- One authoritative business service
- At most one necessary internal dependency

Longer synchronous chains require an explicit architecture review.

## 8.2 Booking hot-path rule

Booking creation, rescheduling, and reassignment must not make remote calls while holding the allocation transaction.

Required information comes from local enforcement projections.

If local inputs are incomplete:

- Do not assume availability.
- Do not use stale positive authorization.
- Return a typed conflict, dependency, or unknown-status result.

---

# 9. Asynchronous event propagation matrix

| Producer | Event category | Primary consumers | Consistency | Consumer purpose |
|---|---|---|---|---|
| Account | Account activated/restricted/deleted | Booking, Notification, Governance, Discovery where needed | `C2`/`C3` | Booking eligibility, communication, privacy cleanup |
| Account | Profile/locale/preference changed | Notification | `C3` | Optional projection invalidation |
| Station Operations | Organization lifecycle changed | Booking, Discovery, Governance | `C2`/`C3` | Booking restrictions, public visibility |
| Station Operations | Station/EVSE/Connector changed | Booking, Discovery, Device Integration | `C2`/`C3` | Bookable configuration, search, inventory validation |
| Station Operations | Tariff/Policy version published | Booking, Discovery | `C2`/`C3` | Snapshot source and public display |
| Station Operations | Maintenance/Fault changed | Booking, Discovery, Notification, Governance | `C2`/`C3` | Capacity restrictions, warnings, operations |
| Booking and Session | Booking lifecycle changed | Discovery, Notification, Governance, Station Operations | `C3` | Availability projection, email, operator views |
| Booking and Session | Allocation changed | Discovery | `C3` | Advisory bookable counts |
| Booking and Session | Check-in/session lifecycle changed | Device Integration, Notification, Discovery, Governance | `C3` | Device workflow, status, alerts |
| Booking and Session | Session summary finalized | Discovery/Insights, Notification | `C3` | Analytics and optional summary |
| Device Integration | Connectivity/freshness changed | Booking, Discovery, Station Operations | `C2`/`C3` | Near-term enforcement, public status, operations |
| Device Integration | EVSE reported state changed | Booking, Discovery, Station Operations | `C2`/`C3` | Availability inputs and monitoring |
| Device Integration | Transaction/meter evidence accepted | Booking and Session | `C2` | Session authority processing |
| Device Integration | Command result/uncertainty changed | Booking and Session | `C2`/`C5` | Start/stop reconciliation |
| All authoritative services | Auditable business/security facts | Governance audit projection | `C3` | Central audit search |
| All relevant services | Notification-triggering facts | Notification | `C3` | Email evaluation and dispatch |
| All personal-data owners | Privacy participant result | Account coordinator | `C4` | Export/deletion completion |

Events must contain the minimum information required by consumers.

---

# 10. Device communication matrix

| Caller | Recipient | Interaction | Style | Consistency | Failure behaviour |
|---|---|---|---|---|---|
| Simulator | Device Integration | Boot, heartbeat, status, transaction, meter, fault | Secure WebSocket event | `C5` transport with durable receipt | Queue offline where permitted |
| Device Integration | Simulator | Start, stop, state request, reservation mirror | Secure WebSocket command | `C5` | Timeout becomes uncertain |
| Booking and Session | Device Integration | Request start/stop/state reconciliation | Async command | `C4`/`C5` | Session remains `STARTING`/`STOPPING` |
| Device Integration | Booking and Session | Normalized command result and physical evidence | Async event | `C2` | Duplicate-safe processing |
| Station Operations | Device Integration | Simulator control request | Synchronous command or durable async command | Recipient-local commit | No direct protocol bypass |
| Device Integration | Station Operations | Fault/inventory mismatch fact | Async event | `C3` | Operational review created |

## 10.1 Start sequence

1. Booking consumes Start Authorization in a local transaction.
2. Session becomes `STARTING`.
3. Outbox records the start-device command.
4. Device Integration receives and dispatches it.
5. Simulator may accept, reject, delay, or become unavailable.
6. Command acceptance does not change Session to `CHARGING`.
7. Only accepted `TransactionStarted` evidence changes:
   - Session to `CHARGING`
   - Booking to `ACTIVE`
8. Timeout leaves the Session uncertain in `STARTING`.

## 10.2 Stop sequence

1. Booking records stop intent and Session becomes `STOPPING`.
2. Device command is dispatched asynchronously.
3. Stop-command acceptance does not finalize the Session.
4. `TransactionEnded` or equivalent reconciled evidence determines:
   - `COMPLETED`
   - `INTERRUPTED`
5. Capacity remains blocked until termination and release time are established.

---

# 11. Coordinated workflow matrix

| Workflow | Coordinator | Participants | Consistency | Completion condition |
|---|---|---|---|---|
| Account suspension | Account or Governance workflow | Identity Provider, Booking, Notification | `C4` | Identity disabled and Booking restriction acknowledged |
| Organization suspension | Station Operations/Governance | Booking, Discovery, Notification | `C4` | New-booking restriction acknowledged |
| Station emergency closure | Station Operations | Booking, Device Integration, Notification | `C4` | Booking restriction installed; impacted obligations tracked |
| Maintenance scheduling | Station Operations | Booking, Notification | `C4` | Impact resolved and capacity block installed |
| Maintenance activation | Station Operations | Booking, Device Integration | `C4` | Required block confirmed and no unresolved prohibited session |
| Booking reassignment | Booking and Session | Station Operations, Notification | `C0` locally plus `C3` propagation | Replacement allocation commits |
| Device start/stop | Booking and Session | Device Integration, Simulator | `C5` | Physical outcome proven or manual resolution recorded |
| Privacy export | Account | Every personal-data owner, secure storage, Notification | `C4` | All mandatory contributions assembled |
| Account deletion | Account | Identity Provider and every personal-data owner | `C4` | All mandatory participants acknowledge or approved exception exists |
| Emergency intervention | Governance | Relevant authoritative owner | `C4` | Owner records result and review evidence |
| Break-glass access | Governance | Relevant owners and audit projection | `C4` | Access expires/revokes and review completes |

Workflow state must be persisted by the coordinator. Broker messages alone are not workflow state.

---

# 12. Restriction installation pattern

Immediate restrictions require acknowledgement by the service enforcing the rule.

## 12.1 Driver restriction

1. Account/Governance creates a restriction workflow.
2. Directed command requests Booking to install a driver restriction.
3. Booking commits the restriction and outbox acknowledgement.
4. Coordinator records acknowledgement.
5. Identity sessions are revoked or disabled.
6. Suspension is reported complete.

This ensures a suspension is not reported complete while Booking may still accept new bookings.

## 12.2 Infrastructure restriction

Used for:

- Organization suspension
- Station closure
- EVSE disablement
- Maintenance
- Emergency operational block

Booking installs the effective capacity or eligibility block before the source workflow is considered complete.

## 12.3 Restriction removal

Removal is also coordinated.

Booking must not remove a restriction merely because a delayed source event appears to show an older active state. Source versions prevent stale reversal.

---

# 13. Maintenance workflow consistency

## 13.1 Preview

Station Operations may synchronously request a non-binding impact preview from Booking.

The preview:

- Does not reserve or block capacity.
- May become stale immediately.
- Must not be treated as approval.

## 13.2 Scheduling

1. Station Operations creates a workflow ID.
2. Existing booking impact is resolved.
3. Station Operations commands Booking to install the maintenance capacity block.
4. Booking atomically verifies conflicts and installs the block.
5. Booking emits acknowledgement.
6. Station Operations transitions Maintenance to `SCHEDULED`.

If Station Operations fails after block installation:

- The block remains linked to the workflow.
- Reconciliation determines whether to finish scheduling or safely remove the orphan block.
- No silent expiration occurs while safety is uncertain.

## 13.3 Activation

Maintenance activates only after:

- The block remains installed.
- Required booking resolutions are complete.
- Active sessions do not prohibit normal activation.
- The scheduled start is reached.

## 13.4 Completion

Maintenance completion removes the maintenance restriction through a coordinated command.

Near-term Booking eligibility remains blocked until Device Integration supplies fresh acceptable status.

---

# 14. Notification communication

## 14.1 Triggering

Authoritative services publish completed facts after commit.

They do not call the email provider directly.

## 14.2 Recipient resolution

Notification resolves the latest destination and preferences through an authorized Account or Station Operations query.

Events should normally contain:

- Recipient reference
- Notification type
- Aggregate reference/version
- Required template data that is immutable
- Correlation information

They should not contain broad user profiles or raw identity-action tokens.

## 14.3 Identity messages

Verification, recovery, and identity-owned action links remain handled by the Identity Provider.

Raw action tokens never pass through the broker.

## 14.4 Provider failure

- Business state remains committed.
- Notification retries transient failures.
- Permanent failures are recorded.
- User-visible application warnings are produced where action depends on email.

---

# 15. Governance and support communication

## 15.1 Case-scoped reads

Governance requests data from authoritative owners using:

- Platform Support actor identity
- Case reference
- Temporary access-grant reference
- Requested fields/action
- Correlation ID

The receiving service independently validates the permitted scope.

## 15.2 Case-scoped writes

Governance never modifies foreign data directly.

It sends an authenticated, idempotent command to the authoritative owner.

Examples:

- Cancel booking
- Reassign booking
- Suspend account
- Restrict organization
- Stop session in an emergency

## 15.3 Temporary access grants

The security architecture must select one of:

- Short-lived signed support-access token
- Authoritative grant lookup
- Replicated grant projection

Preferred direction: short-lived signed grants verifiable locally, with immediate revocation events for exceptional cases.

This remains provisional until the security architecture.

---

# 16. Privacy workflow communication

## 16.1 Export

Account sends one command per participant using a common workflow ID.

Participants:

1. Collect their authoritative data.
2. Redact unrelated or protected data.
3. Produce a contribution or secure storage reference.
4. Emit completion/failure status.

Large exports must not be transported directly through the message broker.

## 16.2 Deletion

Each participant receives an idempotent deletion/anonymization command.

Participants report:

- Completed
- Blocked
- Failed temporarily
- Requires review
- Not applicable

Account must not declare completion until every mandatory participant is resolved.

## 16.3 Rebuild and restoration

Deletion tombstones and restriction events must be applied during:

- Projection rebuilds
- Analytics rebuilds
- Backup restoration
- Search reindexing

---

# 17. Timeout policy

Exact values remain configurable and will be validated during performance testing.

## 17.1 Synchronous queries

Proposed initial policy:

- Connection timeout: 250–500 ms
- Overall internal query timeout: 750–1,000 ms
- At most one retry for safe idempotent reads
- Retry uses short jitter
- No retry after the caller’s response budget is exhausted

## 17.2 Synchronous commands

- Stable idempotency key required
- No blind retry after ambiguous timeout
- At most one controlled retry where the recipient can return the original result
- Caller queries operation status when outcome is uncertain

## 17.3 Asynchronous processing

- Exponential backoff with jitter
- Bounded retry attempts
- Operation-specific maximum age
- Dead-letter/quarantine after exhaustion
- Manual replay remains idempotent

## 17.4 Device commands

Command deadlines are business-specific.

Timeout means `TIMED_OUT` and then `RECONCILING`, never automatic rejection.

---

# 18. Circuit-breaker policy

Circuit breakers apply to remote synchronous dependencies.

When open:

- No positive authorization is inferred.
- No allocation is accepted using unknown critical data.
- Safe cached public details may be returned with freshness warnings.
- State-changing commands return a dependency-unavailable or pending-workflow result.
- Existing locally owned records remain manageable where possible.

Circuit breakers must not:

- Hide durable workflow state
- Discard commands
- Release uncertain capacity
- Convert `UNKNOWN` into `AVAILABLE`
- Bypass service authorization

---

# 19. Retry and idempotency matrix

| Interaction | Retry allowed | Idempotency mechanism |
|---|---|---|
| Safe REST query | Yes, bounded | Naturally read-only |
| Booking creation | Client retry allowed | Client idempotency key |
| Reschedule/cancel | Yes | Operation idempotency key plus aggregate version |
| Internal business command | Yes, controlled | Command ID and stored result |
| Integration event | Broker redelivery expected | Consumer inbox/event ID |
| Device command | Yes with same command ID | Simulator command-result history |
| Device event | Redelivery expected | Event ID and sequence |
| Notification dispatch | Provider-specific controlled retry | Notification identity/provider reference |
| Privacy participant command | Yes | Workflow ID plus participant action |
| Dead-letter replay | Yes | Original event/command ID |
| Projection rebuild | Yes | Source version and deterministic upsert |

---

# 20. Ordering rules

1. No global event order is assumed.
2. Aggregate events carry aggregate version.
3. Device events carry station and session sequence numbers.
4. Older versions cannot overwrite newer state.
5. A version gap causes:
   - Deferral
   - Authoritative source lookup
   - Replay request
   - Reconciliation
6. Independent aggregates may be processed concurrently.
7. Terminal facts cannot be reversed by delayed older events.
8. Privacy tombstones take precedence over rebuilt older personal projections.

---

# 21. Service availability dependency matrix

| Operation | Services required synchronously | May continue if unavailable |
|---|---|---|
| Public station search | Discovery and Insights | No search; other functions continue |
| View existing booking | Booking and Session | Independent of Station/Discovery |
| Create booking | Booking and Session | Uses local enforcement projections |
| Reschedule booking | Booking and Session | Uses local enforcement projections |
| Cancel booking | Booking and Session | Notification/Station may be unavailable |
| Check in | Booking and Session | Uses local device/configuration projections |
| Start charging | Booking and Session initially | Device command may remain pending/uncertain |
| Stop charging | Booking and Session initially | Device outcome reconciles asynchronously |
| Operator edit station | Station Operations | Booking remains available from prior projection |
| Simulator communication | Device Integration | Existing bookings remain durable |
| Send notification | Notification plus recipient lookup/provider | Source business operation remains committed |
| Support case management | Governance | Driver core remains available |
| Privacy export/deletion | Account coordinator plus async participants | Workflow remains incomplete and retries |
| Analytics | Discovery and Insights | Core operations continue |
| Central audit search | Governance projection | Local authoritative audit remains durable |

---

# 22. Data sensitivity matrix

| Interaction | Sensitivity | Required controls |
|---|---|---|
| Identity tokens | Secret/security | TLS, no logging, audience validation |
| Account profile query | Personal | Service authorization, field minimization |
| Booking events | Personal/operational | Use references; avoid broad profile data |
| Device protocol | Operational/security | Machine authentication, no driver PII |
| Notification recipient lookup | Personal | Purpose-limited fields |
| Support case query | Personal/restricted | Case scope, masking, audit |
| Privacy contribution | Highly restricted | Encryption, workflow scope, expiring access |
| Audit event | Security/operational | Append-only evidence, no secrets |
| Search event | Public/internal | Public-field allowlist |
| Analytics event | Pseudonymous/aggregate | Minimize stable user identifiers |

---

# 23. Reconciliation matrix

| Condition | Owner | Reconciliation action |
|---|---|---|
| Booking projection version gap | Booking and Session | Fetch authoritative version or reject new allocation |
| Discovery projection gap | Discovery and Insights | Replay/rebuild; show stale/unknown |
| Device command timeout | Device Integration + Booking | Request state; await transaction evidence |
| Device event sequence gap | Device Integration | Replay queued events or request current state |
| Maintenance block without scheduled record | Station Operations + Booking | Complete scheduling or remove safely |
| Scheduled maintenance without block | Station Operations | Prevent activation and reinstall block |
| Account suspension participant missing | Account coordinator | Retry participant; do not report completion |
| Privacy participant missing | Account coordinator | Retry/escalate; do not complete workflow |
| Notification provider outcome unknown | Notification | Query provider or controlled resend |
| Audit projection gap | Governance | Replay from local service outboxes/audit sources |
| Search/analytics corruption | Discovery and Insights | Rebuild from authoritative events/source snapshots |
| Identity deletion failed | Account | Keep deletion workflow incomplete and authentication disabled where safe |

---

# 24. Prohibited communication patterns

1. Direct service-to-service database access.
2. Cross-service SQL joins.
3. Distributed database transactions.
4. Synchronous email sending from business operations.
5. Booking allocation based on Discovery data.
6. Device Integration directly changing Booking state.
7. Broker request/reply in latency-sensitive user paths.
8. Unbounded synchronous call chains.
9. Retrying non-idempotent commands with new operation IDs.
10. Treating HTTP timeout as business rejection.
11. Embedding user credentials or Start Authorization secrets in events.
12. Placing large privacy-export payloads on the broker.
13. Shared mutable domain models across services.
14. Fallback authorization based on stale positive cache entries.
15. Releasing capacity because a remote dependency timed out.

---

# 25. Communication decisions proposed for approval

| ID | Decision |
|---|---|
| ARC-COM-01 | Use REST for external request/response APIs and selected internal queries/commands. |
| ARC-COM-02 | Use asynchronous events for completed facts and projection updates. |
| ARC-COM-03 | Use directed asynchronous commands for device actions and long-running workflows. |
| ARC-COM-04 | Keep allocation transactions free of synchronous remote calls. |
| ARC-COM-05 | Maintain bounded enforcement projections inside Booking and Session. |
| ARC-COM-06 | Fail closed when critical enforcement data is missing, stale, or version-incomplete. |
| ARC-COM-07 | Coordinate immediate account and infrastructure restrictions with Booking acknowledgement. |
| ARC-COM-08 | Use versioned local capacity blocks for maintenance and emergency restrictions. |
| ARC-COM-09 | Keep Simulator communication isolated behind Device Integration. |
| ARC-COM-10 | Treat device timeout as an uncertain reconciled outcome. |
| ARC-COM-11 | Resolve current notification recipients through authorized service queries rather than broad PII events. |
| ARC-COM-12 | Use Account Service as privacy-workflow coordinator. |
| ARC-COM-13 | Use Governance as workflow owner, never as direct owner of foreign business state. |
| ARC-COM-14 | Keep local audit facts authoritative and central audit search eventually consistent. |
| ARC-COM-15 | Limit ordinary synchronous request chains to one business service and at most one internal dependency. |
| ARC-COM-16 | Require stable idempotency keys for every retryable state-changing command. |
| ARC-COM-17 | Use aggregate versions and device sequences instead of assuming global ordering. |
| ARC-COM-18 | Prohibit positive availability or authorization fallback when dependencies are uncertain. |

---

# 26. Open questions

| ID | Question | Deadline |
|---|---|---|
| ARC-COM-OQ-01 | Exact internal REST timeout and retry values | Performance/testing design |
| ARC-COM-OQ-02 | Service-to-service identity and signed actor-context design | Security architecture |
| ARC-COM-OQ-03 | Exact support-access grant format and revocation mechanism | Security architecture |
| ARC-COM-OQ-04 | RabbitMQ exchange, queue, routing, and ordering design | Event contracts |
| ARC-COM-OQ-05 | Projection snapshot/rebuild transport | Event/data design |
| ARC-COM-OQ-06 | Exact maintenance restriction workflow messages | Event contracts |
| ARC-COM-OQ-07 | Whether recipient resolution should use queries or a restricted contact projection at scale | Security/performance review |
| ARC-COM-OQ-08 | Secure storage design for privacy-export contributions | Cloud/privacy architecture |
| ARC-COM-OQ-09 | Maximum permitted enforcement-projection lag | NFR/test design |
| ARC-COM-OQ-10 | Strategy for reactivating restrictions after broker recovery | Event/reconciliation design |

---

# 27. Acceptance criteria

This communication design is approved when:

1. Every interaction identifies an authoritative owner.
2. Allocation requires only Booking-local transactional data.
3. Missing critical projections cannot produce a successful allocation.
4. No workflow requires a distributed database transaction.
5. Device command acceptance remains separate from physical evidence.
6. All asynchronous consumers are idempotent.
7. All retryable state changes have stable operation identifiers.
8. Timeout never silently becomes rejection or success.
9. Immediate restrictions require enforcement acknowledgement.
10. Search, analytics, notification, and audit-projection failure cannot reverse core state.
11. Existing Booking management remains possible during nonessential service outages.
12. Support and governance actions pass through authoritative owners.
13. Privacy workflows have explicit coordinator and participant behaviour.
14. Personal data in events is minimized.
15. Reconciliation exists for every uncertain or partially completed workflow.
16. Communication chains remain bounded.
17. Security architecture can authenticate both service and originating actor.
18. Every interaction can be mapped to future REST or event contracts.

---

# 28. Consequences

## Positive

- Booking correctness is isolated from remote-service availability.
- Core operations avoid fragile synchronous chains.
- Restriction workflows provide stronger suspension and maintenance semantics.
- Device uncertainty is represented honestly.
- Search, analytics, notifications, and audit views remain independently recoverable.
- Services retain clear ownership.

## Negative

- Booking stores several local projections.
- Restriction workflows introduce coordination complexity.
- Projection versioning and reconciliation require substantial testing.
- Some data is duplicated deliberately.
- Support and notification queries introduce controlled synchronous dependencies.
- Operational tooling must expose lag, gaps, dead letters, and incomplete workflows.

These costs are accepted because they preserve correctness without distributed transactions.

---

# 29. Next architecture artifact

The next planning artifact is:

**REST API Contract Catalogue v1.0**

Before its approval:

- Resolve any remaining secondary lifecycle errata.
- Define API-wide conventions.
- Define public versus internal APIs.
- Define authentication and actor-context propagation assumptions.
- Map every synchronous matrix entry to an API operation.
- Preserve idempotency, versioning, error, pagination, and authorization requirements.
