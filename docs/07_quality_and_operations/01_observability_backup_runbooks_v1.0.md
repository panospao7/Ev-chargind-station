Document ID: ARC-012  
Title: Observability, Backup, Disaster Recovery and Operational Runbook Strategy  
Version: 1.0  
Status: IN_REVIEW  
Owner: Cloud / Operations Architect  
Last reviewed: 2026-07-11  
Depends on: ARC-001–011, REQ-002, PRV-001, DOM-002  
Authoritative for: Service objectives, telemetry, alerting, backup, restoration, disaster recovery, incident handling and operational runbooks  

# Observability, Backup, Disaster Recovery and Operational Runbook Strategy v1.0

## 1. Purpose

This document defines:

- Service Level Indicators and Objectives
- Metrics, logs and distributed traces
- Alert severity and routing
- Operational dashboards
- Telemetry retention and privacy
- PostgreSQL, K3s and configuration backups
- Recovery Point and Recovery Time Objectives
- Privacy-tombstone restoration
- Node, database, broker and identity runbooks
- Certificate and secret rotation
- Quarantine and replay procedures
- Incident response
- Capacity and cost operations
- Disaster-recovery testing

---

## 2. Operational principles

1. Observe user-visible symptoms before infrastructure causes.
2. Alert only when a human action may be required.
3. Every alert links to a dashboard and runbook.
4. Telemetry is not an authoritative business record.
5. Logs and traces contain no secrets or unnecessary personal data.
6. Core business operations remain valid when observability is unavailable.
7. Backup success is insufficient without tested restoration.
8. Recovery never bypasses privacy tombstones or lifecycle invariants.
9. RabbitMQ is reconstructed from topology and authoritative service state after total loss.
10. Device state becomes `UNKNOWN` after restoration until fresh evidence arrives.
11. Incident response preserves evidence and prioritizes safe containment.
12. Recovery procedures are executable by the project owner without undocumented knowledge.

Prometheus recommends alerting primarily on symptoms associated with user impact and avoiding alerts for conditions where no meaningful action exists. Alertmanager provides grouping, deduplication, inhibition and silencing to reduce alert floods. ([prometheus.io](https://prometheus.io/docs/alerting/latest/alertmanager/))

---

# 3. Reliability objectives

## 3.1 Service categories

### Tier 0 — Correctness-critical

- Booking and Session Service
- PostgreSQL
- BFF
- Identity Provider
- Device Integration for active Session workflows

### Tier 1 — Operationally important

- Account Service
- Station Operations Service
- RabbitMQ
- Keycloak
- Certificate infrastructure

### Tier 2 — Degradable

- Discovery and Insights
- Notification
- Governance and Support
- Central audit projection

### Tier 3 — Supporting

- Grafana
- Loki
- Tempo
- Report exports
- Simulator control interface

Failure of a lower-tier component must not corrupt a higher-tier component.

---

## 3.2 Availability SLOs

Measured monthly:

| Capability | SLI | Objective |
|---|---|---:|
| Core authenticated API | Valid non-5xx responses / eligible requests | 99.5% |
| Booking reads | Successful authoritative reads / eligible reads | 99.5% |
| Booking mutations | Correct committed or domain-rejected outcomes / eligible commands | 99.5% |
| Cancellation | Correct cancellation outcome / eligible cancellation request | 99.7% |
| Public discovery | Successful responses / eligible requests | 99.0% |
| Identity login | Successful completed login flows / eligible attempts | 99.5% |
| Device command intake | Durably accepted intents / valid commands | 99.5% |
| Notification dispatch | Provider submission within five minutes / eligible messages | 99.0% |
| Operator management | Successful responses / eligible requests | 99.0% |

The following are excluded from availability calculations:

- Invalid authentication
- Authorization denials
- Validation failures
- Allocation conflicts
- Expired Holds
- Definitive device rejection
- Planned announced maintenance
- Client cancellation or disconnection

An exclusion cannot hide a platform-generated failure.

---

## 3.3 Latency SLOs

| Operation | Objective |
|---|---:|
| Public search and Station detail | p95 ≤ 1.5 seconds |
| Advisory availability | p95 ≤ 1 second |
| Booking Hold creation | p95 ≤ 2 seconds |
| Booking confirmation | p95 ≤ 2 seconds |
| Reschedule/cancellation | p95 ≤ 2 seconds |
| Check-in | p95 ≤ 2 seconds |
| Authoritative Booking read | p95 ≤ 1 second |
| Operator configuration reads | p95 ≤ 1.5 seconds |
| Operator mutations | p95 ≤ 2 seconds |
| Internal synchronous query | p95 ≤ 750 milliseconds |

Latency is measured at the BFF and owning service.

Device physical completion is excluded from HTTP latency because it is asynchronous.

---

## 3.4 Freshness and propagation SLOs

| Signal | Objective |
|---|---:|
| Accepted device event reflected in authoritative Session | 99% within 10 seconds |
| Enforcement projection event applied | 99% within 10 seconds |
| Public discovery projection applied | 99% within 30 seconds |
| Central audit projection applied | 99% within 60 seconds |
| Essential notification queued | 99% within 30 seconds |
| Provider submission after queueing | 99% within five minutes |
| Operational dashboard status | 99% within 30 seconds |

Missing enforcement information fails closed and does not become a false positive availability result.

---

## 3.5 Durability and recovery objectives

| Scope | RPO | RTO |
|---|---:|---:|
| Single PostgreSQL instance failure | 0 committed transactions | 5 minutes |
| Single K3s node failure | 0 committed database transactions | 15 minutes |
| Complete PostgreSQL cluster loss | ≤ 5 minutes | ≤ 60 minutes |
| Complete RabbitMQ loss | Authoritative state retained in databases | ≤ 30 minutes |
| Complete K3s cluster loss | ≤ 5 minutes for database state | ≤ 60 minutes |
| Observability data | Up to 15 minutes acceptable | 60 minutes |
| Search/analytics projections | Rebuildable | 4 hours |
| Notification queue | Rebuild/reconcile from events and records | 2 hours |

The 60-minute location-loss RTO remains conditional until demonstrated through a timed recovery drill.

---

## 3.6 Error budgets

The monthly error budget for a 99.5% objective is approximately 3 hours 39 minutes.

Budget consumption is reviewed:

- Weekly during active development
- After every SEV-1 or SEV-2 incident
- Before major feature releases
- Before infrastructure upgrades

When more than 50% of a monthly budget is consumed:

- Reliability work receives priority.
- Risky releases require explicit approval.
- Existing incidents and recurring causes are reviewed.

When 100% is consumed:

- Nonessential feature deployment pauses.
- Only corrective, security and reliability changes proceed without an exception.

---

# 4. Observability architecture

## 4.1 Components

| Component | Deployment |
|---|---|
| Prometheus Operator stack | Kubernetes-managed |
| Prometheus | One persistent replica initially |
| Alertmanager | Two replicas |
| Grafana | One replica |
| Loki | Single-binary deployment |
| Tempo | Monolithic deployment |
| OpenTelemetry Collector agents | One DaemonSet Pod per node |
| OpenTelemetry Collector gateway | Two replicas |
| kube-state-metrics | One replica |
| Node Exporter | One per node |
| Blackbox Exporter | One replica |
| RabbitMQ Prometheus plugin | One endpoint per broker node |
| Keycloak metrics/health | Management interface only |

Alertmanager replicas operate as a cluster, and Prometheus sends alerts directly to all replicas rather than through a Load Balancer. Alertmanager’s HA design may produce duplicate notifications during partitions rather than risk suppressing a critical notification. ([prometheus.io](https://prometheus.io/docs/alerting/latest/high_availability/))

## 4.2 Resource posture

Observability is not in the Booking critical path.

Initial storage:

| Component | Persistent storage |
|---|---:|
| Prometheus | 40 GiB CSI Volume |
| Alertmanager | 1 GiB per replica |
| Grafana | 2 GiB |
| Loki working data/compactor | 10 GiB |
| Tempo WAL | 10 GiB |
| OTel Collector | No durable business storage |

Loki and Tempo use object storage for retained telemetry.

Prometheus remains local to avoid adding a separate long-term metrics backend.

---

# 5. Metrics strategy

## 5.1 Collection

Prometheus scrapes:

- Spring Boot Actuator
- K3s and Kubernetes control-plane metrics
- Node Exporter
- kube-state-metrics
- CloudNativePG
- RabbitMQ
- Keycloak
- Traefik
- cert-manager
- Flux
- K3s etcd
- Blackbox Exporter
- Observability components

Scrape intervals:

| Target | Interval |
|---|---:|
| Booking, BFF, PostgreSQL, RabbitMQ | 15 seconds |
| Other business services | 30 seconds |
| Kubernetes and node metrics | 30 seconds |
| Low-priority projections | 60 seconds |

## 5.2 Metric naming

Application metrics use:

```text
ev_<domain>_<measurement>_<unit>
```

Examples:

- `ev_booking_hold_created_total`
- `ev_booking_allocation_conflict_total`
- `ev_allocation_guard_wait_seconds`
- `ev_device_event_lag_seconds`
- `ev_outbox_oldest_pending_seconds`
- `ev_privacy_workflow_age_seconds`

Prometheus recording rules follow the recommended `level:metric:operations` convention and are syntax-checked using `promtool`. ([prometheus.io](https://prometheus.io/docs/practices/rules/))

## 5.3 Label policy

Permitted low-cardinality labels:

- Service
- Environment
- Operation
- Outcome category
- HTTP method
- Stable route template
- Queue
- Message type
- Workflow type
- Lifecycle state
- Region
- Node

Prohibited labels:

- Account reference
- Booking reference
- Session reference
- EVSE reference where fleet cardinality is high
- Email
- IP address
- Correlation ID
- Trace ID
- Free-text error
- Device event ID

High-cardinality context belongs in restricted logs and traces.

## 5.4 Business-correctness metrics

Mandatory:

- Overlapping-claim integrity failures
- Booking without expected claim
- Claim without valid source
- Duplicate meter event
- Authorization reuse attempt
- Illegal lifecycle transition
- Device sequence gap
- Projection version gap
- Premature privacy-completion prevention
- Audit/outbox atomicity failure
- Uncertain occupation age

Any detected overlapping confirmed allocation generates a SEV-1 alert and quarantines the EVSE.

---

# 6. Logging strategy

## 6.1 Format

Applications write structured JSON to stdout.

Required fields:

- Timestamp
- Severity
- Service
- Environment
- Event name
- Safe message
- Operation ID
- Correlation ID
- Trace ID and span ID where available
- Workflow ID
- Safe resource type
- Outcome
- Error category

Prohibited:

- Tokens
- Cookies
- Passwords
- Private keys
- Start Authorization secrets
- Enrollment credentials
- Privacy-export content
- Full request or response bodies
- SQL parameters containing personal data
- Raw broker payloads

## 6.2 Pipeline

1. OTel Collector DaemonSet reads container logs.
2. Kubernetes metadata is added.
3. Sensitive attributes are dropped or redacted.
4. Records are batched.
5. Logs are sent through OTLP HTTP to Loki.
6. Loki stores searchable metadata and retained chunks.

Loki supports native OTLP ingestion from the OpenTelemetry Collector; its native OTLP endpoint is preferred over the older Loki-specific exporter. ([grafana.com](https://grafana.com/docs/loki/latest/send-data/otel/))

## 6.3 Log levels

- `ERROR`: Operation failed unexpectedly or integrity is threatened.
- `WARN`: Degradation, uncertainty or recoverable abnormal condition.
- `INFO`: Significant lifecycle and operational facts.
- `DEBUG`: Temporary diagnostic detail.
- `TRACE`: Disabled in persistent environments unless time-limited.

Business audit evidence remains in service-owned audit tables, not application logs.

---

# 7. Distributed tracing

## 7.1 Collection

- Java services use the OpenTelemetry Java Agent.
- Angular/BFF propagation uses W3C Trace Context.
- RabbitMQ messages carry `traceparent`.
- OTel gateway exports traces to Tempo over OTLP.
- Tempo runs in monolithic mode without Kafka.

Tempo’s monolithic mode can ingest directly without Kafka, and OTLP is the recommended trace-forwarding protocol. ([grafana.com](https://grafana.com/docs/tempo/latest/set-up-for-tracing/instrument-send/set-up-collector/otel-collector/))

## 7.2 Sampling

Initial rule-based head sampling:

| Trace category | Sampling |
|---|---:|
| Booking Hold/confirm/reschedule/cancel | 100% |
| Check-in/start/stop/reconciliation | 100% |
| Privacy and break-glass workflows | 100% |
| Allocation/database errors | 100% |
| Ordinary authenticated reads | 20% |
| Public discovery | 10% |
| Health and metrics endpoints | 0% |
| Load-test traffic | Configurable, normally 1–5% |

Tail sampling is deferred because it adds stateful Collector routing complexity.

## 7.3 Trace attributes

Allowed:

- Operation ID
- Service
- Workflow type
- Message type
- Lifecycle state
- Safe outcome code
- Database operation category
- Queue
- HTTP route template

Personal or resource references are included only where necessary, protected and low volume.

---

# 8. Telemetry retention

| Data | Retention |
|---|---:|
| Prometheus metrics | 30 days |
| Ordinary application logs | 14 days |
| Debug logs | 3 days |
| Ingress access logs | 7 days |
| Security-operational logs | 30 days |
| Distributed traces | 7 days |
| Load-test telemetry | 7 days |
| Alert history | 90 days where available |
| Grafana dashboards/rules | Indefinite in Git |

Loki retention is implemented through its Compactor. Retention must be explicitly enabled; the Compactor should be stateful with persistent marker storage, and object-storage lifecycle rules must not delete index or control objects indiscriminately. ([grafana.com](https://grafana.com/docs/loki/latest/operations/storage/retention/))

Telemetry retention does not override longer authoritative audit-retention requirements.

---

# 9. Dashboard catalogue

## DASH-01 — Platform SLO overview

- Availability
- Latency
- Error-budget consumption
- Device propagation delay
- Notification delay
- Active incidents
- Backup state

## DASH-02 — BFF and identity

- Login success/failure
- Session creation and expiry
- Token exchange failures
- CSRF failures
- Keycloak latency
- Keycloak database pool
- MFA/step-up failures

Keycloak provides dedicated started, live and ready health endpoints and can expose metrics through its management interface. ([keycloak.org](https://www.keycloak.org/operator/advanced-configuration))

## DASH-03 — Booking and allocation

- Hold creation
- Confirmation
- Allocation conflicts
- Guard-lock waits
- Exclusion violations
- Deadlocks/retries
- Hold-expiry delay
- Rescheduling outcomes
- Integrity checks
- Uncertain occupation

## DASH-04 — Charging and device integration

- Connected simulators
- Stale devices
- Device events per second
- Event lag
- Sequence gaps
- Commands by state
- Timeouts
- Reconciliation age
- Active/uncertain Sessions

## DASH-05 — PostgreSQL

- Instance role and health
- Replication lag
- Synchronous standbys
- Transaction latency
- Locks/deadlocks
- Connection pools
- Disk/WAL growth
- Backup/WAL archive state
- Restore-test status

## DASH-06 — RabbitMQ

- Cluster and quorum health
- Queue depth and age
- Ready/unacknowledged messages
- Consumer count
- Publish/confirm rates
- Redelivery/retry
- Quarantine depth
- Disk and memory alarms
- Raft commit latency

RabbitMQ recommends Prometheus and Grafana for long-term monitoring, and the Kubernetes Operator exposes quorum-health status. ([rabbitmq.com](https://www.rabbitmq.com/docs/4.2/monitoring))

## DASH-07 — Async workflows

- Outbox age
- Inbox duplicates
- Projection lag
- Retry count
- Quarantine
- Incomplete restrictions
- Privacy participants
- Maintenance-block workflows

## DASH-08 — Notifications

- Requested/queued/dispatched
- Provider acceptance
- Temporary/permanent failure
- Bounce/suppression
- Oldest queued message
- Essential-delivery failures

## DASH-09 — Kubernetes and infrastructure

- Node health
- CPU/memory
- Disk and inode use
- Pod restarts
- Pending Pods
- PVC state
- Network errors
- Flux readiness
- Certificate expiry
- K3s etcd health

## DASH-10 — Backup and disaster recovery

- Latest base backup
- WAL archive delay
- K3s snapshot age
- OpenTofu state backup
- Restore-test results
- Privacy-ledger status
- RPO/RTO drill history

## DASH-11 — Security operations

- Authentication failures
- Authorization denials
- Cross-tenant attempts
- Support reveals
- Break-glass use
- Invalid certificates
- Machine-identity failures
- Message replay
- Secret/certificate age

## DASH-12 — Cost and capacity

- Node utilization
- PVC growth
- Object-storage growth
- Telemetry volume
- Email quota
- Map credits
- Monthly cost estimate
- Capacity forecast

---

# 10. Alert classification

## 10.1 Severity

| Severity | Meaning | Response |
|---|---|---|
| `SEV-1` | Active data-integrity, security or broad core outage | Immediate |
| `SEV-2` | Major degradation or loss of redundancy | Within 30 minutes |
| `SEV-3` | Limited degradation requiring planned action | Within one business day |
| `SEV-4` | Informational or maintenance issue | Backlog/review |

## 10.2 Routing

Reference environment:

- SEV-1: Email plus urgent webhook
- SEV-2: Email and operational channel
- SEV-3: Email digest or issue
- SEV-4: Dashboard only

Before real users are onboarded, an on-call-capable urgent notification channel must be selected and tested.

## 10.3 Alert rules

Every alert includes:

- Severity
- Service/component
- Environment
- Summary
- User impact
- Start time
- Dashboard
- Runbook
- Safe diagnostic labels

Alertmanager groups related alerts and inhibits derivative service alerts during a known cluster- or database-level incident.

---

# 11. Mandatory alerts

## SEV-1

- Confirmed overlapping EVSE allocations
- Booking database unavailable
- PostgreSQL quorum/durability lost
- Privacy deletion falsely reported complete
- Audit/outbox atomicity invariant failure
- Unauthorized cross-tenant access confirmed
- Private key or production secret exposure
- Data corruption detected
- Complete identity outage
- Complete core API outage
- Backup restoration impossible

## SEV-2

- One K3s node unavailable
- PostgreSQL failover
- RabbitMQ node unavailable
- RabbitMQ queue without quorum
- Keycloak replica loss
- Booking error-budget rapid consumption
- Outbox backlog above release threshold
- Device-event lag above objective
- Uncertain occupation above maximum age
- Latest PostgreSQL base backup failed
- WAL archive delay above five minutes
- Certificate approaching expiry
- Object-storage failure
- Flux reconciliation blocked

## SEV-3

- Elevated API latency
- Search projection stale
- Notification failure increase
- Disk above 75%
- Expired-Hold cleanup delay
- Repeated deadlocks
- Quarantine message present
- Restore drill overdue
- Cost above warning threshold

---

# 12. PostgreSQL backup strategy

## 12.1 Mechanism

Use the CloudNativePG Barman Cloud Plugin for:

- Online physical base backups
- Continuous WAL archiving
- Point-in-time recovery
- Full-cluster recovery into a new cluster

CloudNativePG’s plugin supports online backups, WAL archiving and PITR through object storage. Recovery bootstraps a new cluster rather than modifying the failed cluster in place. ([cloudnative-pg.io](https://cloudnative-pg.io/plugin-barman-cloud/docs/intro/))

## 12.2 Schedule

- Continuous WAL archiving
- Daily base backup at 02:00 UTC
- Additional backup before risky database/operator upgrades
- Additional backup before destructive contract migration
- Recovery window: provisional 35 days
- Daily backup validation
- Weekly automated restore smoke test
- Monthly full application restore test

CloudNativePG uses a default five-minute `archive_timeout`, supporting a deterministic five-minute RPO at low write volume when WAL archiving is healthy. ([cloudnative-pg.io](https://cloudnative-pg.io/docs/1.29/wal_archiving/))

## 12.3 Backup source

Prefer a healthy standby to reduce primary load.

Backup must not proceed from an unhealthy or materially lagging standby.

## 12.4 Encryption and access

- Dedicated write credential
- Dedicated restore credential
- TLS
- Object-store server-side encryption
- Separate backup prefix per cluster
- No application-service access
- Deletion restricted to retention automation and break-glass operator

## 12.5 Success criteria

A backup is successful only when:

- Backup completed
- WAL before and after backup is archived
- Object-store objects are readable
- Metadata is valid
- Backup is within retention
- Monitoring observed completion

Plugin-specific status and metrics must be used because older centralized CloudNativePG backup status fields are deprecated under the plugin model. ([cloudnative-pg.io](https://cloudnative-pg.io/docs/1.26/installation_upgrade/))

---

# 13. K3s and cluster-state backup

## 13.1 Etcd snapshots

Schedule:

- Every six hours
- Before K3s upgrade
- Before control-plane maintenance
- Before major operator upgrades

Retention:

- Local: 3 days
- Remote object storage: 14 days
- Monthly recovery-test snapshot: 90 days

## 13.2 Required recovery material

Back up separately:

- K3s server token
- K3s configuration
- Cluster CA material where required
- Flux age decryption key
- Administrative recovery instructions

K3s requires the original server token when restoring an embedded-etcd snapshot because it is used to encrypt confidential datastore content. ([docs.k3s.io](https://docs.k3s.io/datastore/backup-restore))

## 13.3 Desired state

Git remains authoritative for:

- Workload manifests
- Operators
- Dashboards
- Alert rules
- RabbitMQ topology
- Certificate resources
- Network policies

Etcd snapshots accelerate recovery but do not replace Git.

---

# 14. Additional backup inventory

| Asset | Backup strategy |
|---|---|
| OpenTofu state | Encrypted versioned object storage |
| SOPS age key | Two encrypted offline copies |
| Git repository | GitHub plus independent encrypted mirror |
| RabbitMQ topology | Git-managed definitions and policies |
| Keycloak realm configuration | Declarative configuration in Git |
| Keycloak runtime data | PostgreSQL physical backup |
| step-ca database | PostgreSQL physical backup |
| step-ca intermediate key | Encrypted Secret plus offline protected copy |
| Offline root CA | Two geographically separated offline copies |
| Grafana dashboards | Git provisioning |
| Alert rules | Git |
| Loki/Tempo configuration | Git |
| Prometheus history | Not considered authoritative |
| Privacy exports | Expiring artifacts, not backup |
| Report exports | Expiring artifacts, not backup |

---

# 15. Privacy recovery ledger

## 15.1 Purpose

Database restoration may return the platform to a point before a completed privacy action.

A separate Privacy Recovery Ledger ensures completed deletion and restriction actions are reapplied.

## 15.2 Record

Each ledger record contains only:

- Tombstone reference
- Pseudonymous account reference
- Action type
- Completion time
- Participant completion versions
- Workflow reference
- Record hash
- Schema version

It contains no deleted personal content.

## 15.3 Durability

- One object per completed privacy action
- Unique immutable object key
- Versioning enabled
- Account runtime may create but not delete records
- Separate recovery-reader credential
- Daily inventory verification

If object locking is unavailable, write-once behaviour is enforced through unique keys, credential separation, versioning and deletion denial.

## 15.4 Completion rule

The Recovery Ledger is a mandatory privacy-workflow participant.

Account deletion is not reported `COMPLETED` until its ledger record is durably stored.

## 15.5 Restoration

Before ordinary traffic:

1. Restore PostgreSQL.
2. Retrieve every ledger entry newer than or absent from the restored tombstone table.
3. Reapply deletion, anonymization and restriction actions idempotently.
4. Rebuild affected projections.
5. Validate participant acknowledgements.
6. Record recovery evidence.

---

# 16. RabbitMQ recovery policy

RabbitMQ topology is restored from Git using the Messaging Topology Operator.

The platform does not depend on filesystem-level message backup.

RabbitMQ notes that live message-store copying is discouraged and that disk restoration requires matching node names, especially for quorum queues. Definitions can be exported/imported independently. ([rabbitmq.com](https://www.rabbitmq.com/docs/next/backup))

Authoritative recovery sources:

- Service outboxes
- Service inboxes
- Workflow tables
- Local business state
- Git-controlled broker topology

Three-member quorum queues tolerate one node loss, but loss of a majority can make a queue permanently unavailable. ([rabbitmq.com](https://www.rabbitmq.com/docs/quorum-queues))

---

# 17. Disaster-recovery sequence

## 17.1 Declaration

1. Declare SEV-1.
2. Assign Incident Commander.
3. Stop unsafe writes where required.
4. Preserve evidence.
5. Record recovery target time.
6. Notify stakeholders.

## 17.2 Provision infrastructure

1. Create the disaster-recovery OpenTofu environment in Falkenstein.
2. Bootstrap K3s.
3. Install Flux.
4. Restore the SOPS key.
5. Reconcile infrastructure operators.
6. Validate private networking and DNS readiness.

## 17.3 Restore stateful foundations

1. Restore PostgreSQL into a new CloudNativePG cluster.
2. Verify consistency and selected PITR target.
3. Reapply Privacy Recovery Ledger records.
4. Reconcile service database roles.
5. Start Keycloak and validate realms.
6. Restore step-ca configuration and intermediate.
7. Recreate RabbitMQ topology.
8. Start business services with public ingress disabled.

CloudNativePG requires distinct archive identities for restored clusters to avoid overwriting an existing WAL archive. ([cloudnative-pg.io](https://cloudnative-pg.io/docs/1.26/recovery/))

## 17.4 Reconcile application state

1. Resume outbox publishers.
2. Process duplicate-safe inbox deliveries.
3. Reconcile incomplete workflows.
4. Expire invalid Holds.
5. Set device state to `UNKNOWN`.
6. Rebuild Discovery and Analytics.
7. Validate audit projections.
8. Require simulators to reconnect.

## 17.5 Acceptance

Verify:

- Authentication
- Booking read/cancel
- Allocation constraints
- New Hold/confirm
- Device connection
- RabbitMQ processing
- Privacy tombstones
- Audit writes
- Email
- Certificates
- Backup of the restored cluster

## 17.6 Cutover

1. Enable public ingress.
2. Switch DNS.
3. Monitor error rate and latency.
4. Keep the old environment isolated.
5. Record actual RPO and RTO.
6. Close recovery only after data-integrity review.

---

# 18. Runbook template

Every runbook must include:

- Runbook ID
- Trigger
- Severity
- Preconditions
- Safety warnings
- Required access
- Diagnosis
- Containment
- Recovery
- Validation
- Rollback
- Evidence to retain
- Escalation
- Related dashboards
- Last successful drill

Commands are maintained in executable, versioned runbook files rather than copied into incident chat.

---

# 19. RUN-NODE-01 — K3s node loss

## Trigger

- Node `NotReady`
- Node unreachable
- Multiple critical Pods unavailable

## Immediate actions

1. Confirm whether the failure is node, network or control-plane related.
2. Check remaining etcd quorum.
3. Check PostgreSQL and RabbitMQ quorum.
4. Verify core API health.
5. Do not restart another node.

## Recovery

1. If transient, permit Kubernetes to recover.
2. If permanent, remove the failed node through the approved K3s procedure.
3. Recreate the node with OpenTofu.
4. Join it to K3s.
5. Verify etcd membership.
6. Allow operators to rebuild database and broker replicas.
7. Verify Volume attachment.
8. Rebalance workloads only after stateful health is restored.

## Validation

- Three etcd members
- Three PostgreSQL instances
- Three RabbitMQ members
- Core replicas spread across nodes
- No orphaned Volume
- No unresolved simulator-command loss

---

# 20. RUN-PG-01 — PostgreSQL automatic failover

## Trigger

- Primary unavailable
- CloudNativePG promotion
- Write Service target changed

## Actions

1. Confirm CloudNativePG is performing failover.
2. Do not manually promote another instance during automatic recovery.
3. Check synchronous standby availability.
4. Check application connection recovery.
5. Check Booking write latency and errors.
6. Confirm WAL archiving resumes.
7. Replace or recover the failed instance.
8. Restore three-instance topology.

## Validation

- One writable primary
- At least one synchronized standby
- No split brain
- Allocation constraints present
- Outbox publishing healthy
- Backup/WAL archive healthy

---

# 21. RUN-PG-02 — PostgreSQL PITR

## Preconditions

- Approved recovery target
- Confirmed backup and WAL availability
- Public writes stopped
- Incident evidence preserved

## Procedure

1. Create a new CloudNativePG recovery cluster.
2. Select the latest safe backup before the target.
3. Replay WAL to the selected time.
4. Do not permit application writes during recovery.
5. Verify databases, roles and extensions.
6. Verify Booking allocation constraints.
7. Reapply Privacy Recovery Ledger.
8. Reconcile workflows and projections.
9. Run integrity and smoke tests.
10. Cut applications over to the new cluster.
11. Start new WAL archive under a distinct cluster identity.
12. Take a new base backup.

PITR across PostgreSQL major-version boundaries is not supported; a new base backup must be created after a major upgrade. ([cloudnative-pg.io](https://cloudnative-pg.io/documentation/current/postgres_upgrades/))

---

# 22. RUN-RMQ-01 — RabbitMQ node loss

## Actions

1. Check Operator `quorumStatus`.
2. Identify queues whose members are affected.
3. Confirm a majority remains.
4. Do not stop another broker.
5. Permit leader election and client reconnection.
6. Restore or replace the failed Pod/Volume.
7. Verify member synchronization.
8. Confirm queue consumers return.
9. Confirm outbox age decreases.

RabbitMQ pauses delivery briefly during quorum-queue leader election and resumes after a follower is elected. ([rabbitmq.com](https://www.rabbitmq.com/docs/reliability))

---

# 23. RUN-RMQ-02 — Complete RabbitMQ loss

1. Stop asynchronous consumers if partial topology exists.
2. Recreate the RabbitMQ cluster.
3. Apply users, vhosts, permissions, exchanges, queues and policies from Git.
4. Validate quorum queues.
5. Start consumers.
6. Resume outbox publishers.
7. Monitor duplicates and version gaps.
8. Reconcile workflows.
9. Inspect quarantine.
10. Verify no critical command remains only in an unavailable broker.

Do not attempt an improvised live message-directory restoration.

---

# 24. RUN-MSG-01 — Quarantine handling

## Classification

- Invalid schema
- Unsupported version
- Unauthorized producer
- Impossible lifecycle state
- Missing dependency
- Repeated transient failure
- Consumer defect
- Data corruption

## Procedure

1. Pause automated replay for the affected message family.
2. Preserve original envelope and failure.
3. Identify authoritative source state.
4. Determine whether the original message remains valid.
5. Correct code/configuration first.
6. Test replay in a non-production environment.
7. Obtain privileged approval.
8. Replay with the original message ID.
9. Verify idempotent result.
10. Record audit evidence.

If payload correction is required:

- Do not alter and replay the original.
- Create a replacement message with a new ID.
- Reference the invalid original.
- Record justification.

---

# 25. RUN-KC-01 — Keycloak outage

## Pod loss

1. Confirm the remaining replica is ready.
2. Check database connectivity.
3. Check cluster/cache health.
4. Permit the Operator to recreate the Pod.
5. Validate login and token exchange.

## Database-related outage

1. Restore PostgreSQL first.
2. Start Keycloak with public traffic restricted.
3. Validate realm and client configuration.
4. Reconcile declarative realm configuration.
5. Rotate uncertain bootstrap/admin sessions.
6. Validate BFF login, MFA, logout and service tokens.
7. Re-enable public traffic.

Keycloak recommends two or more instances for clustered production availability and provides readiness endpoints for routing traffic only to initialized instances. ([keycloak.org](https://www.keycloak.org/server/configuration-production))

---

# 26. RUN-CERT-01 — Public certificate renewal failure

1. Check Certificate, Order and Challenge state.
2. Check Cloudflare DNS credentials.
3. Check ACME quota/rate-limit response.
4. Verify system time and DNS propagation.
5. Use staging issuer for troubleshooting.
6. Renew through cert-manager after correcting the cause.
7. Confirm Traefik serves the new chain.
8. Confirm expiry monitoring clears.

Do not delete certificate Secrets as the ordinary renewal method.

cert-manager automatically renews Certificate resources, and manual reissuance should use `cmctl renew`; private-key rotation should use `rotationPolicy: Always`. ([cert-manager.io](https://cert-manager.io/v1.14-docs/usage/certificate/))

---

# 27. RUN-CERT-02 — Internal CA rotation

cert-manager CA Issuers do not automatically rotate their CA certificate, and replacing the CA Secret does not automatically reissue existing leaf certificates. ([cert-manager.io](https://cert-manager.io/docs/configuration/ca/))

Procedure:

1. Generate the replacement intermediate from the offline root.
2. Distribute a trust bundle containing old and new intermediates.
3. Verify all workloads trust both.
4. Replace the issuing intermediate.
5. Trigger leaf-certificate reissuance.
6. Roll/reload certificate consumers.
7. Verify new leaf chains.
8. Wait through the approved overlap window.
9. Remove the old intermediate from trust bundles.
10. Preserve rotation evidence.

Root rotation requires a separate architecture review and a longer dual-trust migration.

---

# 28. RUN-CERT-03 — Simulator certificate compromise

1. Suspend the Machine Identity immediately.
2. Revoke the certificate where active revocation is supported.
3. Reject future connections by Machine Identity state.
4. Investigate device events and commands since suspected compromise.
5. Rotate the machine key.
6. Require fresh enrollment.
7. Reconcile affected Sessions.
8. Record security incident evidence.

step-ca supports certificate revocation, while short-lived certificates and automated renewal reduce exposure. ([smallstep.com](https://smallstep.com/docs/step-cli/reference/ca/revoke/))

---

# 29. RUN-SEC-01 — Secret rotation

## Standard rotation

1. Create a new credential/key.
2. Add it to SOPS-encrypted configuration.
3. Permit old and new credentials during overlap where supported.
4. Reconcile Flux.
5. Roll affected workloads.
6. Verify new credential use.
7. Revoke the old credential.
8. Update recovery copies.
9. Record rotation evidence.

## Emergency rotation

1. Revoke the compromised credential first where safe.
2. Isolate the affected workload.
3. Issue replacement.
4. Redeploy.
5. Investigate usage and lateral movement.
6. Rotate dependent credentials.
7. Preserve evidence.

Credential-specific runbooks are required for:

- PostgreSQL
- RabbitMQ
- Keycloak clients
- Brevo
- Geoapify
- Cloudflare
- Object storage
- GHCR
- OpenTofu
- SOPS age
- step-ca provisioners

---

# 30. RUN-BACKUP-01 — Backup failure

1. Identify base-backup, WAL, snapshot or object-store failure.
2. Confirm the latest known recoverable point.
3. Check credentials, storage quota and network.
4. Avoid destructive upgrades while recovery confidence is reduced.
5. Retry after correction.
6. Validate resulting objects.
7. Trigger a restore test if the failure crossed the RPO threshold.
8. Escalate to SEV-2 when no valid recovery point remains within objective.

---

# 31. RUN-PRV-01 — Privacy-tombstone restoration

1. Block ordinary traffic.
2. Restore the database.
3. Read the latest Privacy Recovery Ledger.
4. Compare ledger and database tombstones.
5. Reapply missing actions idempotently.
6. Delete/redact rebuilt personal projections.
7. Invalidate affected exports.
8. Verify all mandatory participants.
9. Record recovery evidence.
10. Enable traffic only after privacy validation passes.

---

# 32. RUN-INC-01 — Incident response

## Roles

- Incident Commander
- Operations Lead
- Security/Privacy Lead
- Communications Lead
- Scribe

One person may hold multiple roles, but responsibilities remain explicit.

## Lifecycle

1. Detect
2. Declare
3. Classify
4. Contain
5. Preserve evidence
6. Recover
7. Validate
8. Communicate
9. Monitor
10. Close
11. Review

## Required timeline

| Severity | Initial acknowledgement | Status updates |
|---|---:|---:|
| SEV-1 | 5 minutes | Every 30 minutes |
| SEV-2 | 15 minutes | Every 60 minutes |
| SEV-3 | One business day | As material |

## Post-incident review

Required for:

- Every SEV-1
- Security/privacy incident
- Data-integrity incident
- Repeated SEV-2
- Failed recovery drill

Review includes:

- Impact
- Timeline
- Detection
- Root and contributing causes
- What worked
- What failed
- Corrective actions
- Owners and deadlines
- Requirement/architecture changes

Post-incident reviews are blameless but actions are accountable.

---

# 33. Capacity management

## 33.1 Weekly review

- Node CPU/memory
- PostgreSQL storage and connections
- RabbitMQ disk/queue growth
- PVC consumption
- Object-storage growth
- Device connections
- API traffic
- Telemetry ingestion
- Error budget

## 33.2 Capacity thresholds

| Resource | Warning | Critical |
|---|---:|---:|
| Node CPU sustained | 65% | 80% |
| Node memory sustained | 70% | 85% |
| PVC usage | 70% | 85% |
| PostgreSQL connections | 70% | 85% |
| RabbitMQ disk free | 30% remaining | 15% remaining |
| Object-storage budget | 75% | 90% |
| Queue age | SLO threshold | 2× SLO |
| Outbox age | SLO threshold | 2× SLO |

Sustained means at least 15 minutes unless an operation-specific threshold states otherwise.

## 33.3 Scaling sequence

1. Remove leaks or abnormal workload.
2. Tune requests and connection pools.
3. Scale stateless replicas.
4. Increase persistent storage.
5. Add node capacity.
6. Upgrade to dedicated CPU if shared-CPU variance is confirmed.
7. Introduce PgBouncer only if connection pressure is measured.
8. Introduce additional infrastructure only through ADR.

---

# 34. Cost operations

Monthly review includes:

- Hetzner compute
- Load Balancer
- Volumes
- Object storage
- Brevo
- Geoapify
- Registry/CI
- Domain
- Unexpected resources

Alerts:

- 50% budget
- 75%
- 90%
- 100%
- Unlabelled billable resource
- Unexpected resource-size change
- Backup/log growth anomaly
- Map or email quota risk

Cost-saving actions must never:

- Remove required redundancy without approval
- Shorten required backup retention
- Disable security monitoring
- Reduce privacy controls
- Weaken allocation correctness

---

# 35. Operational access

Operational systems are available only through administrative VPN or protected access.

Access principles:

- Named individual identities
- MFA
- Least privilege
- No shared administrator account
- Time-limited elevated access
- Audit
- Separate application and infrastructure roles
- No production credentials on unmanaged devices

Grafana access uses Keycloak SSO.

Direct Loki, Tempo and Prometheus interfaces are not publicly exposed.

---

# 36. Backup and recovery testing

## Daily

- Check base-backup result
- Check WAL archive delay
- Check K3s snapshot age
- Check object-storage access
- Check privacy-ledger write

## Weekly

- Restore PostgreSQL into an isolated namespace
- Run schema/integrity smoke tests
- Verify latest K3s snapshot metadata
- Verify OpenTofu state recovery access

## Monthly

- Full application restore
- Privacy-tombstone replay
- RabbitMQ reconstruction
- Keycloak login validation
- Certificate and secret recovery
- Timed recovery measurement

## Quarterly

- Node-loss drill
- PostgreSQL failover drill
- RabbitMQ node-loss drill
- Secret-rotation drill
- Machine-certificate compromise drill
- Incident-response tabletop exercise

## Twice yearly

- Full Falkenstein location-loss recovery
- Offline root/intermediate recovery validation
- OpenTofu/Flux clean-room rebuild

Tests use synthetic data unless approved privacy-safe fixtures exist.

---

# 37. Recovery evidence

Every drill records:

- Scenario
- Start and finish time
- Participants
- Backup identifiers
- Recovery target
- Actual RPO
- Actual RTO
- Data-integrity results
- Privacy validation
- Failed steps
- Manual interventions
- Corrective actions
- Runbook version

A backup or runbook cannot be marked verified without successful execution evidence.

---

# 38. Decisions proposed for approval

| ID | Decision |
|---|---|
| ARC-OPS-01 | Use Prometheus, Alertmanager, Grafana, Loki, Tempo and OpenTelemetry Collector. |
| ARC-OPS-02 | Deploy one Prometheus and two Alertmanager replicas initially. |
| ARC-OPS-03 | Use Loki single-binary and Tempo monolithic modes initially. |
| ARC-OPS-04 | Use native OTLP ingestion for Loki and Tempo. |
| ARC-OPS-05 | Keep metrics in Prometheus for 30 days, logs for up to 30 days and traces for 7 days. |
| ARC-OPS-06 | Treat telemetry as non-authoritative and privacy-minimized. |
| ARC-OPS-07 | Use symptom-based alerts with Alertmanager grouping and inhibition. |
| ARC-OPS-08 | Adopt SEV-1 through SEV-4 incident classifications. |
| ARC-OPS-09 | Use continuous PostgreSQL WAL archiving and daily base backups. |
| ARC-OPS-10 | Use a provisional 35-day PostgreSQL recovery window. |
| ARC-OPS-11 | Perform weekly restore smoke tests and monthly full restore tests. |
| ARC-OPS-12 | Take K3s etcd snapshots every six hours and before risky control-plane changes. |
| ARC-OPS-13 | Back up the K3s server token separately. |
| ARC-OPS-14 | Reconstruct RabbitMQ from Git topology, outboxes and workflows after total loss. |
| ARC-OPS-15 | Do not rely on live RabbitMQ message-directory backups. |
| ARC-OPS-16 | Make the Privacy Recovery Ledger a mandatory deletion-workflow participant. |
| ARC-OPS-17 | Reapply privacy tombstones before restoring ordinary traffic. |
| ARC-OPS-18 | Restore PostgreSQL into a new cluster rather than in place. |
| ARC-OPS-19 | Set device operational confidence to `UNKNOWN` after restoration. |
| ARC-OPS-20 | Require timed node-loss, failover and location-loss drills. |
| ARC-OPS-21 | Require every actionable alert to link to a runbook and dashboard. |
| ARC-OPS-22 | Use cert-manager automatic leaf renewal and planned manual CA rotation. |
| ARC-OPS-23 | Use short-lived renewable simulator certificates plus immediate Machine Identity suspension. |
| ARC-OPS-24 | Require dual-credential overlap for ordinary secret rotation where supported. |
| ARC-OPS-25 | Pause feature delivery when the reliability error budget is exhausted. |

---

# 39. Open questions

| ID | Question | Resolution phase |
|---|---|---|
| ARC-OPS-OQ-01 | Final urgent alert provider for real users | Implementation readiness |
| ARC-OPS-OQ-02 | Final Prometheus storage size after load testing | Performance testing |
| ARC-OPS-OQ-03 | Final telemetry sampling and retention | Privacy/load testing |
| ARC-OPS-OQ-04 | Whether Prometheus needs a second replica | Resilience testing |
| ARC-OPS-OQ-05 | Whether Grafana/Loki/Tempo require dedicated CSI Volumes | Deployment proof of concept |
| ARC-OPS-OQ-06 | Final off-provider backup destination | Disaster-recovery test |
| ARC-OPS-OQ-07 | Whether cold recovery meets the 60-minute RTO | Timed DR drill |
| ARC-OPS-OQ-08 | Maximum acceptable uncertain-occupation age | Product/operations review |
| ARC-OPS-OQ-09 | Final CA and leaf-certificate lifetimes | Security proof of concept |
| ARC-OPS-OQ-10 | Final recovery-ledger immutability mechanism | Cloud/privacy proof of concept |
| ARC-OPS-OQ-11 | Final security-log retention | Privacy/security review |
| ARC-OPS-OQ-12 | Final vulnerability remediation deadlines | CI/CD strategy |

---

# 40. Acceptance criteria

This strategy is approved when:

1. Every NFR reliability target has a measurable SLI.
2. Every SLO has a dashboard and alerting policy.
3. Metrics avoid prohibited high-cardinality labels.
4. Logs and traces exclude secrets and unnecessary personal data.
5. Every SEV-1 and SEV-2 alert has a runbook.
6. PostgreSQL can restore to a selected point in time.
7. WAL archive health supports the five-minute RPO.
8. K3s snapshots and the server token are recoverable.
9. RabbitMQ can be reconstructed without filesystem message backup.
10. Keycloak can recover from the shared PostgreSQL backup.
11. Privacy tombstones survive point-in-time restoration.
12. Device status becomes unknown after recovery.
13. Certificate and secret rotation are documented and tested.
14. Node and PostgreSQL failover drills succeed.
15. A full location-loss drill measures actual RTO.
16. Backup failure generates actionable alerts.
17. Capacity and cost thresholds are monitored.
18. Incident evidence and post-incident actions are retained.
19. Observability failure cannot stop core business operations.
20. Operational access is MFA-protected and audited.

---

# 41. Consequences

## Positive

- Measurable reliability objectives
- Actionable alerting
- Central metrics, logs and traces
- Tested PITR and cluster recovery
- Explicit privacy-safe restoration
- Broker reconstruction without fragile message backups
- Repeatable incident procedures
- Controlled certificate and secret rotation
- Visible capacity and cost risk

## Negative

- Observability consumes significant cluster resources.
- Restore testing adds operational work and storage cost.
- A single Prometheus instance can temporarily lose monitoring availability.
- Cold location recovery may exceed the target RTO.
- Privacy Recovery Ledger adds a mandatory workflow dependency.
- Manual CA rotation requires careful trust overlap.
- Frequent drills require ongoing maintenance discipline.

These costs are accepted because backups without tested restoration and alerts without runbooks do not provide operational readiness.

---

# 42. Next architecture artifact

The next document is:

**Complete Testing and Quality Assurance Strategy v1.0**

It must define:

- Test pyramid and ownership
- Unit, component, integration and contract tests
- PostgreSQL concurrency tests
- Event and command tests
- Security and privacy tests
- Frontend and accessibility tests
- Simulator and failure-injection tests
- Load, endurance and resilience tests
- Backup and disaster-recovery tests
- Test-data management
- Coverage and mutation-testing policy
- Quality gates
- Acceptance-evidence traceability
