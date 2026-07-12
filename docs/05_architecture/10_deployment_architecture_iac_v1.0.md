Document ID: ARC-011  
Title: Deployment Architecture and Infrastructure as Code Strategy  
Version: 1.0  
Status: IN_REVIEW  
Owner: Cloud / Platform Architect  
Last reviewed: 2026-07-11  
Depends on: ARC-001–010  
Authoritative for: Container orchestration, cluster topology, ingress, stateful platform deployment, GitOps, infrastructure provisioning, secrets delivery, scaling, upgrades and disaster-recovery topology  

# Deployment Architecture and Infrastructure as Code Strategy v1.0

## 1. Purpose

This document defines:

- Container orchestrator and cluster topology
- Node, control-plane and failure-domain layout
- Public and administrative network paths
- Namespace and tenancy structure
- PostgreSQL, RabbitMQ and Keycloak deployment
- Persistent storage
- Certificate and machine-identity infrastructure
- Secret delivery
- Workload scheduling and scaling
- Infrastructure as Code
- GitOps reconciliation
- Schema-migration deployment
- Environment promotion
- Cluster upgrades
- Disaster-recovery architecture

Detailed monitoring thresholds, backup retention, restoration procedures and operational runbooks remain authoritative in ARC-012.

---

# 2. Amendment to ARC-010

## 2.1 Primary-node placement

ARC-010 proposed splitting the three primary nodes between Nuremberg and Falkenstein.

This is superseded.

K3s embedded-etcd guidance requires at least three server nodes, an odd server count, private connectivity between them and recommends that embedded-etcd servers remain in the same location. ([docs.k3s.io](https://docs.k3s.io/datastore/ha-embedded?utm_source=openai))

Therefore:

- All three primary nodes reside in `nbg1`.
- They use a Hetzner spread placement group.
- They communicate over one private network.
- Falkenstein is used for backup storage and cold disaster recovery.
- Multi-location active operation requires a future multi-cluster architecture.

This prioritizes control-plane correctness over unsupported location distribution.

## 2.2 Server backups

Routine Hetzner server backups are removed from the reference architecture.

Reasons:

- Nodes are replaceable through Infrastructure as Code.
- Server backups do not include attached Hetzner Volumes.
- K3s etcd, PostgreSQL and application data have dedicated backup mechanisms. ([docs.hetzner.com](https://docs.hetzner.com/cloud/volumes/overview/?utm_source=openai))

The ARC-010 cost estimate must be recalculated to:

- Remove server-backup charges.
- Add persistent-volume charges.
- Add any cold-disaster-recovery test resources.

---

# 3. Selected architecture

## 3.1 Orchestrator

Use **K3s**, pinned initially to `v1.36.1+k3s1`.

K3s packages Kubernetes 1.36.1, etcd 3.6.7, containerd 2.2.3 and Flannel 0.28.4 in that release. ([github.com](https://github.com/k3s-io/k3s/releases?utm_source=openai))

K3s is selected because it provides:

- Kubernetes-compatible APIs
- Embedded highly available etcd
- Lower control-plane overhead
- ARM64 support
- Containerd
- Integrated CoreDNS and metrics-server
- Standard Helm, operator and CSI support
- S3-compatible etcd snapshot support

## 3.2 Cluster profile

The persistent reference environment contains:

- Three K3s server/worker nodes
- Three embedded-etcd members
- One public Hetzner Load Balancer
- One private Hetzner network
- Hetzner Cloud Controller Manager
- Hetzner CSI Driver
- Flux controllers
- Traefik ingress
- cert-manager
- CloudNativePG
- RabbitMQ Operators
- Keycloak Operator
- Application workloads
- Observability workloads

All three nodes run control-plane and application workloads.

Dedicated worker nodes are not justified initially.

## 3.3 Node profile

Each node:

- Uses `CAX31` ARM64 compute initially
- Uses the same validated Ubuntu LTS image ID
- Has no public IPv4 where practical
- Has private IPv4
- Has public IPv6 only where operationally required
- Uses SSH key authentication
- Disables password and root SSH login
- Runs containerd through K3s
- Is reproducible from OpenTofu and bootstrap configuration

Nodes are treated as replaceable infrastructure.

Manual long-lived configuration drift is prohibited.

---

# 4. K3s configuration

## 4.1 Embedded etcd

Use:

- Three server nodes
- Embedded etcd
- One initial `cluster-init`
- Two server joins
- Private node addresses
- Encrypted Kubernetes Secrets
- Scheduled etcd snapshots
- S3-compatible remote snapshot storage

An odd number of server nodes preserves etcd quorum; three nodes tolerate one unavailable member. ([docs.k3s.io](https://docs.k3s.io/datastore/ha-embedded?utm_source=openai))

## 4.2 Disabled packaged components

Disable the K3s packaged:

- Traefik installation
- ServiceLB
- Local-path storage as the default production StorageClass

Traefik is installed through Flux so that:

- Its version is pinned.
- Configuration is reviewed.
- Rollback is Git-controlled.
- The Hetzner Load Balancer integration is explicit.

Local-path storage remains available only for disposable data.

## 4.3 Retained components

Retain:

- Flannel networking
- K3s network-policy controller
- CoreDNS
- metrics-server
- Embedded etcd
- Containerd

A service mesh is not included in v1.

Service authentication uses short-lived OAuth tokens, and internal TLS uses managed certificates.

## 4.4 Cloud integration

K3s is configured for an external cloud provider.

Install:

- Hetzner Cloud Controller Manager, at least version `1.30.1`
- Hetzner CSI Driver, initially `2.21.1`

Hetzner warns that older controller-manager releases will fail after removal of a deprecated API field, so versions must be pinned above the affected line. ([github.com](https://github.com/hetznercloud/hcloud-cloud-controller-manager/blob/main/README.md?utm_source=openai))

The selected CSI version supports Kubernetes 1.36. ([github.com](https://github.com/hetznercloud/csi-driver/releases?utm_source=openai))

---

# 5. Network topology

## 5.1 Private network

All nodes attach to one private network.

Private traffic includes:

- Kubernetes API
- etcd
- Pod and Service traffic
- Database connections
- RabbitMQ connections
- Internal HTTP APIs
- Metrics and telemetry
- Storage attachment control

K3s server and etcd ports are not exposed publicly.

## 5.2 Public entry point

One Hetzner Load Balancer is the only ordinary public infrastructure entry point.

It forwards:

- HTTPS application traffic
- OIDC authentication traffic
- Secure simulator WebSockets

Targets use node private addresses.

The Hetzner Cloud Controller Manager manages target membership and health integration. It supports Kubernetes LoadBalancer Services and Hetzner-specific load-balancer configuration. ([github.com](https://github.com/hetznercloud/hcloud-cloud-controller-manager/blob/main/README.md?utm_source=openai))

## 5.3 Public hostnames

Initial hostname model:

| Hostname purpose | Target |
|---|---|
| Application | BFF and Angular assets |
| Identity | Keycloak public authentication |
| Device | Device Integration secure WebSocket |
| Status, if added | Public-safe status page |

The Keycloak administration hostname is not publicly exposed.

## 5.4 Kubernetes administration

The Kubernetes API is reachable only through:

- Private network access
- Administrative WireGuard connection
- Emergency SSH tunnel from an allowlisted source

No public Kubernetes API Load Balancer is created.

Administrative kubeconfigs:

- Are short-lived where practical
- Are stored outside the repository
- Have role-specific permissions
- Are revoked after emergency use

## 5.5 Node firewall

Inbound node traffic permits only:

- Private cluster traffic
- Traffic from the Hetzner Load Balancer
- Administrative VPN traffic
- Restricted emergency SSH
- Required K3s overlay and etcd communication

Flannel and etcd ports must not be exposed to the public internet. ([docs.k3s.io](https://docs.k3s.io/zh/installation/requirements?os=pi&utm_source=openai))

---

# 6. Ingress architecture

## 6.1 Traefik

Deploy three Traefik replicas, one per node where possible.

Responsibilities:

- Public TLS termination
- Host and path routing
- WebSocket upgrade
- Security headers
- Request-size limits
- Forwarded-client-address handling
- HTTP-to-HTTPS redirection
- Ingress access logging
- Rate-limit integration where appropriate

The Hetzner Load Balancer operates primarily as a Layer 4 entry point.

## 6.2 Routing

Routes include:

- `/` and static assets → BFF
- `/api/v1/**` → BFF
- Identity hostname → Keycloak
- Device hostname → Device Integration
- ACME challenge paths where required

Business services are never exposed directly through public ingress.

## 6.3 Management endpoints

Actuator, database, RabbitMQ management, Grafana administration and Keycloak administration are:

- Cluster-internal, or
- VPN-only

They are not public ingress routes.

---

# 7. TLS and certificate management

## 7.1 Public certificates

Use:

- cert-manager
- Let’s Encrypt
- DNS-01 validation
- Cloudflare DNS API token restricted to the required zone

cert-manager supports ACME DNS-01 and Cloudflare as a provider. It also supports delegating the ACME challenge subdomain to reduce root-zone credential exposure. ([cert-manager.io](https://cert-manager.io/docs/configuration/acme/dns01/?utm_source=openai))

Use separate:

- Staging ACME issuer
- Production ACME issuer

Certificate issuance must be tested against staging before production.

## 7.2 Internal workload certificates

Use a private intermediate CA managed through cert-manager.

The root CA:

- Remains offline
- Is not stored in Kubernetes
- Signs the online intermediate
- Is backed up through an offline protected process

Internal certificates cover:

- Internal HTTPS Services
- PostgreSQL clients and servers
- RabbitMQ clients and servers
- Keycloak internal TLS
- Operator webhook endpoints

## 7.3 Simulator certificate authority

Use `step-ca` for simulator machine certificates.

`step-ca` supports an offline root, an online intermediate, automated X.509 issuance, mTLS certificates and JWK-provisioner authentication. ([smallstep.com](https://smallstep.com/docs/step-ca/?utm_source=openai))

Deployment:

- Two `step-ca` replicas
- Shared logical PostgreSQL database
- One online intermediate
- Offline root
- JWK enrollment provisioner
- Short-lived machine certificates
- Certificate templates restricting machine SANs
- Device Integration checks Machine Identity state independently

Enrollment:

1. Simulator generates its private key locally.
2. Simulator creates a CSR.
3. Device Integration validates a one-time enrollment request.
4. Device Integration issues a short-lived JWK enrollment token.
5. Simulator submits token and CSR to `step-ca`.
6. Simulator receives the certificate chain.
7. The private key never leaves the simulator.

Machine suspension remains effective even before certificate expiry because Device Integration checks the current Machine Identity assignment and state.

---

# 8. Namespace model

## 8.1 System namespaces

- `kube-system`
- `flux-system`
- `ingress-system`
- `cert-manager`
- `cloudnative-pg`
- `rabbitmq-system`
- `keycloak-system`
- `pki-system`
- `observability`

## 8.2 Application namespaces

- `ev-edge`
- `ev-account`
- `ev-station-operations`
- `ev-booking-session`
- `ev-device-integration`
- `ev-discovery-insights`
- `ev-notification`
- `ev-governance-support`
- `ev-simulator-lab`

## 8.3 Namespace rules

Each namespace receives:

- Default-deny ingress NetworkPolicy
- Default-deny egress NetworkPolicy
- ResourceQuota
- LimitRange
- Dedicated ServiceAccounts
- Pod Security Admission labels
- Explicit DNS egress
- Explicit telemetry egress
- Explicit database/broker access where required

Applications do not use the `default` namespace.

---

# 9. Workload security

Every application Pod uses:

- Non-root user
- Read-only root filesystem where possible
- `allowPrivilegeEscalation: false`
- `RuntimeDefault` seccomp
- All Linux capabilities dropped
- Explicit resource requests and limits
- Dedicated ServiceAccount
- Disabled service-account-token automount unless needed
- Immutable image digest
- Restricted writable temporary directories
- Liveness, readiness and startup probes
- Graceful shutdown period

Privileged containers and host mounts are prohibited for application workloads.

Controllers requiring elevated privileges undergo separate review.

---

# 10. Application deployment topology

## 10.1 Core replicas

Initial replica counts:

| Component | Replicas |
|---|---:|
| BFF | 2 |
| Account Service | 2 |
| Station Operations Service | 2 |
| Booking and Session Service | 2 |
| Device Integration Service | 2 |
| Discovery and Insights Service | 2 |
| Notification Service | 1 |
| Governance and Support Service | 1 |
| Traefik | 3 |
| Keycloak | 2 |
| step-ca | 2 |

Notification and Governance can temporarily stop without invalidating committed Booking operations.

## 10.2 Scheduling

Core replicas use:

- Required or preferred anti-affinity
- Topology spread across `kubernetes.io/hostname`
- PodDisruptionBudgets
- Rolling updates with at least one available replica
- Priority classes

Priority order:

1. Cluster infrastructure
2. PostgreSQL and RabbitMQ
3. Keycloak and BFF
4. Booking and Device Integration
5. Other business services
6. Analytics, notifications and simulator workloads

## 10.3 Device Integration connection ownership

WebSocket connections terminate on one Device Integration replica.

Device commands are:

1. Persisted durably.
2. Associated with current connection ownership.
3. Dispatched by the replica owning the connection.
4. Reconciled after ownership or node changes.

A node or Pod loss causes simulator reconnection and replay rather than requiring sticky Load Balancer sessions.

---

# 11. PostgreSQL architecture

## 11.1 Operator

Use CloudNativePG, pinned to the selected supported release line.

CloudNativePG provides automated failover using Kubernetes leases and supports controlled switchover-based rolling updates. ([cloudnative-pg.io](https://cloudnative-pg.io/docs/1.29/rolling_update/?utm_source=openai))

## 11.2 Physical cluster

Deploy one three-instance physical PostgreSQL cluster.

It hosts separate logical databases and roles for:

- Account
- Station Operations
- Booking and Session
- Device Integration
- Discovery and Insights
- Notification
- Governance and Support
- BFF sessions
- Keycloak
- step-ca

This preserves logical service ownership while controlling cost.

Cross-database queries and grants remain prohibited.

## 11.3 Replication

Use quorum-based synchronous replication requiring one standby acknowledgement.

CloudNativePG supports `ANY 1` quorum synchronous replication for a three-instance cluster. ([cloudnative-pg.io](https://cloudnative-pg.io/docs/1.27/replication/?utm_source=openai))

Consequences:

- A committed transaction survives loss of the primary when at least one synchronized standby remains.
- Write latency increases.
- Loss of both standbys blocks writes rather than silently weakening durability.
- Reference-load testing must validate the latency target.

## 11.4 Storage

Each PostgreSQL instance uses:

- One Hetzner CSI Volume
- Initially 80 GiB
- `ReadWriteOnce`
- Retain reclaim policy
- Encrypted database transport
- PostgreSQL checksums

Hetzner Volumes use network block storage with redundant storage of blocks across physical systems, but provider snapshots or backups are not supplied for Volumes. External database backups therefore remain mandatory. ([docs.hetzner.com](https://docs.hetzner.com/cloud/volumes/overview/?utm_source=openai))

## 11.5 Connections

Applications connect directly to the CloudNativePG read/write Service using bounded HikariCP pools.

PgBouncer is not deployed initially.

CloudNativePG supports PgBouncer through a Pooler resource, but it is added only if measured connection pressure justifies it. ([cloudnative-pg.io](https://cloudnative-pg.io/docs/devel/connection_pooling/?utm_source=openai))

## 11.6 Backups

Use the CloudNativePG Barman Cloud Plugin for:

- Continuous WAL archiving
- Online base backups
- Point-in-time recovery
- Restoration into a new PostgreSQL cluster

The plugin supports online backups, WAL archiving and PITR through object storage. ([cloudnative-pg.io](https://cloudnative-pg.io/plugin-barman-cloud/docs/intro/?utm_source=openai))

Initial policy:

- Continuous WAL archive
- Daily base backup
- Provisional 35-day recovery window
- Backup success required before risky upgrades
- Scheduled restoration tests
- Separate object prefix for each restored cluster

Exact retention remains subject to ARC-012 and privacy approval.

---

# 12. RabbitMQ architecture

## 12.1 Operators

Use:

- RabbitMQ Cluster Operator
- RabbitMQ Messaging Topology Operator

The Cluster Operator manages RabbitMQ lifecycle and day-two operations, while the Topology Operator manages exchanges, queues, users and policies. ([rabbitmq.com](https://www.rabbitmq.com/kubernetes/operator/operator-overview?utm_source=openai))

## 12.2 Cluster

Deploy:

- Three RabbitMQ replicas
- One replica per node
- One 20 GiB CSI Volume per replica
- TLS for client and peer traffic
- Separate virtual host for the platform environment
- One user identity per service
- Quorum queues for release-critical commands and events

Quorum queues require majority availability, and the Operator exposes quorum-critical status for safe maintenance decisions. ([rabbitmq.com](https://www.rabbitmq.com/kubernetes/operator/quorum-status?utm_source=openai))

## 12.3 Broker recovery

RabbitMQ message files are not the primary disaster-recovery record.

Authoritative recovery sources are:

- Service outboxes
- Service inboxes
- Workflow tables
- Database authority
- Git-controlled topology definitions

After total broker loss:

1. Recreate RabbitMQ.
2. Reapply exchanges, queues, policies and users.
3. Resume outbox publication.
4. Reconcile incomplete workflows.
5. Audit quarantine and duplicate processing.

This avoids depending on filesystem-level message restoration.

---

# 13. Keycloak architecture

## 13.1 Deployment

Use the official Keycloak Operator matching the approved Keycloak version.

Deploy:

- Two Keycloak replicas
- External PostgreSQL logical database
- Strict public hostname
- Separate administrative hostname
- TLS
- Topology spread
- Explicit resource requests
- Startup, readiness and liveness probes
- OpenTelemetry tracing

The Keycloak Operator supports external PostgreSQL configuration, resource settings, probes, topology spread and NetworkPolicy integration. ([keycloak.org](https://www.keycloak.org/operator/advanced-configuration))

## 13.2 Operator installation

Install the operator through pinned Kustomize resources under Flux.

Automatic unreviewed operator upgrades are prohibited.

Keycloak warns that operator upgrades can implicitly upgrade server versions and database schemas, and recommends controlled approval and pre-upgrade backups. ([keycloak.org](https://www.keycloak.org/operator/installation))

## 13.3 Realm configuration

Manage:

- Realm structure
- Roles
- Clients
- Scopes
- Authentication flows
- Token settings
- Required actions

through reviewed declarative configuration and idempotent administration Jobs.

Client private keys and secrets remain SOPS-encrypted.

Bootstrap administrator credentials:

- Are temporary
- Are rotated immediately
- Are removed after initial configuration
- Are unavailable to ordinary workloads

---

# 14. Object-storage layout

Use separate Hetzner Object Storage buckets or credential-isolated prefixes for:

- OpenTofu state
- K3s etcd snapshots
- PostgreSQL backups and WAL
- Privacy export artifacts
- Organization report exports
- Disaster-recovery artifacts

Rules:

- Separate access credentials per purpose
- Server-side encryption
- TLS
- Bucket versioning where supported
- Lifecycle expiration
- No public access
- Export artifacts separated from backups
- Backup credentials cannot delete privacy exports
- Application credentials cannot read OpenTofu state
- Production backups stored outside the primary node location

---

# 15. K3s etcd backup

Configure:

- Scheduled etcd snapshots
- S3-compatible remote upload
- Local short-term snapshots
- Remote retention
- Pre-upgrade snapshot
- Pre-control-plane-maintenance snapshot

K3s supports S3-compatible etcd snapshot configuration and retention controls. ([docs.k3s.io](https://docs.k3s.io/cli/server?utm_source=openai))

GitOps manifests remain the desired-state source, but etcd snapshots accelerate restoration of:

- Custom resources
- Secret metadata
- Operator state
- Workflow-related Kubernetes objects

Application database backups remain separate.

---

# 16. Secret delivery

## 16.1 Selected mechanism

Use:

- SOPS-encrypted YAML
- `age` encryption
- Flux Kustomize decryption
- K3s secret encryption at rest
- Separate age key per environment
- Offline recovery copy

Flux supports SOPS secret decryption during Kubernetes reconciliation. ([fluxcd.io](https://fluxcd.io/flux/components/kustomize/?utm_source=openai))

## 16.2 Bootstrap key

The Flux age private key is:

- Injected during protected cluster bootstrap
- Stored only in `flux-system`
- Restricted to Flux
- Backed up offline
- Rotatable
- Never committed to Git

## 16.3 Secret scope

Use one Secret per application purpose.

Do not create shared global secrets for:

- Databases
- RabbitMQ
- Service OAuth identities
- External providers
- Object storage

## 16.4 OpenTofu and secrets

OpenTofu must not generate or manage ordinary application secrets.

This prevents secrets from entering infrastructure state.

Only infrastructure identifiers and restricted infrastructure credentials needed for provisioning may appear in state.

---

# 17. OpenTofu strategy

## 17.1 Scope

OpenTofu owns:

- Hetzner project resources
- Private network
- Subnet
- Firewalls
- Placement group
- Server nodes
- SSH keys/references
- Public Load Balancer
- Object-storage buckets
- Cloudflare DNS records
- Backup lifecycle configuration where supported
- Resource labels

OpenTofu does not own:

- Business-service Deployments
- Kubernetes application Secrets
- Database schema migrations
- RabbitMQ exchanges/queues
- Keycloak realm data

## 17.2 Remote state

Store OpenTofu state in a dedicated S3-compatible bucket.

Use:

- Remote state
- Object versioning
- Server-side encryption
- OpenTofu state and plan encryption
- Environment-supplied credentials
- CI serialization
- Native lock file if provider compatibility is validated

OpenTofu recommends remote state for sensitive infrastructure state, supports S3-compatible storage, locking and state encryption. ([opentofu.org](https://opentofu.org/docs/language/settings/backends/s3/?utm_source=openai))

Credentials are never hardcoded into backend configuration because backend settings can be written into local metadata and plan files. ([opentofu.org](https://opentofu.org/docs/language/settings/backends/configuration/?utm_source=openai))

## 17.3 State separation

Use separate state roots for:

- Bootstrap state storage
- Shared cloud foundation
- Reference cluster
- DNS/external services
- Disaster-recovery environment

A failure or replacement in one root should not require unlocking unrelated infrastructure.

---

# 18. GitOps strategy

## 18.1 Flux

Use Flux as the continuous-reconciliation system.

Flux bootstrap installs its controllers and configures the cluster to synchronize from Git. Bootstrap is idempotent, and later cluster changes can be performed through Git reconciliation. ([fluxcd.io](https://fluxcd.io/flux/installation/?utm_source=openai))

Enabled components:

- Source Controller
- Kustomize Controller
- Helm Controller
- Notification Controller

Image automation controllers are not enabled initially.

## 18.2 Deployment ownership

Use:

- HelmRelease for third-party controllers and operators
- Kustomize for first-party services
- SOPS for Secrets
- Immutable image digests
- Flux health checks
- Dependency ordering
- Automatic drift correction

Flux Kustomizations support health checks, dependency ordering and controlled reconciliation. ([fluxcd.io](https://fluxcd.io/flux/components/kustomize/kustomizations/?utm_source=openai))

## 18.3 Reconciliation order

1. Namespaces and RBAC
2. Cloud Controller Manager
3. CSI Driver
4. Ingress and cert-manager
5. Operators and CRDs
6. Internal PKI
7. PostgreSQL
8. RabbitMQ
9. Keycloak and step-ca
10. Database migration Jobs
11. Business services
12. Public ingress routes
13. Observability
14. Simulator lab

Each stage waits for required health conditions.

---

# 19. Repository structure

```text
infra/
├── tofu/
│   ├── modules/
│   │   ├── network/
│   │   ├── firewall/
│   │   ├── compute-node/
│   │   ├── load-balancer/
│   │   ├── object-storage/
│   │   └── dns/
│   └── environments/
│       ├── reference/
│       └── disaster-recovery/
├── bootstrap/
│   ├── cloud-init/
│   ├── k3s/
│   └── flux/
└── clusters/
    └── reference/
        ├── flux-system/
        ├── infrastructure/
        │   ├── controllers/
        │   └── configuration/
        ├── databases/
        ├── messaging/
        ├── identity/
        ├── apps/
        │   ├── base/
        │   └── overlays/
        ├── migrations/
        ├── ingress/
        ├── observability/
        └── secrets/
```

Rules:

- Modules have one infrastructure concern.
- Environment roots contain no copied module implementation.
- Third-party chart versions are pinned.
- Container images use digests.
- Generated state and decrypted Secrets are ignored.
- Every directory contains ownership documentation.
- Cluster manifests can be validated without contacting production.

---

# 20. Bootstrap sequence

## Phase 1 — State foundation

1. Create the state bucket through a one-time controlled bootstrap.
2. Enable versioning and encryption.
3. Store the recovery procedure offline.
4. Initialize OpenTofu remote state.

## Phase 2 — Cloud foundation

OpenTofu creates:

- Network
- Firewalls
- Placement group
- Nodes
- Load Balancer
- Object-storage buckets
- DNS prerequisites

Cloud-init performs only non-secret host hardening.

## Phase 3 — K3s

A protected bootstrap workflow:

1. Connects through restricted SSH.
2. Installs the pinned K3s binary.
3. Initializes the first server.
4. Joins the second and third servers.
5. Validates etcd quorum.
6. Enables secret encryption.
7. Configures remote etcd snapshots.

Cluster join tokens are supplied from protected CI secrets and do not enter OpenTofu state.

## Phase 4 — Cloud integration

Install:

- Hetzner Cloud Controller Manager
- Required cloud credential Secret
- Flux bootstrap

Flux then assumes management of the controller and all later components.

## Phase 5 — Platform

Flux installs infrastructure and applications in dependency order.

## Phase 6 — Acceptance

Run:

- Cluster conformance checks
- NetworkPolicy tests
- Storage tests
- Database failover
- RabbitMQ quorum checks
- Keycloak login
- Device WebSocket connection
- Backup verification
- Application smoke tests

---

# 21. Database migration deployment

Each service provides one immutable migration artifact.

For a release:

1. CI builds and tests the image.
2. GitOps manifests add a versioned migration Job.
3. Flux applies the migration layer.
4. The Job uses the service-specific migration role.
5. Flux waits for Job success.
6. Application Deployment updates only after migration success.
7. Failed migration blocks the application rollout.

Rules:

- Only one migration Job runs per logical database at a time.
- Runtime Pods have no DDL rights.
- Applied Flyway migrations are immutable.
- Expand–migrate–contract spans multiple releases.
- Migration Jobs are retained long enough for audit evidence.
- Contract migrations require confirmation that old application versions are absent.

Keycloak database migrations follow the controlled Keycloak Operator upgrade process and require a successful PostgreSQL backup first.

---

# 22. Scaling

## 22.1 Horizontal scaling

Initially permit:

| Service | Minimum | Maximum |
|---|---:|---:|
| BFF | 2 | 4 |
| Booking and Session | 2 | 4 |
| Device Integration | 2 | 4 |
| Account | 2 | 3 |
| Station Operations | 2 | 3 |
| Discovery and Insights | 2 | 4 |
| Notification | 1 | 3 |
| Governance and Support | 1 | 2 |

Use Horizontal Pod Autoscaling after baseline load measurements.

CPU is the initial scaling signal.

Custom queue-depth, request-rate or WebSocket metrics may be added later.

## 22.2 Vertical scaling

Resource requests are updated only through Git.

Vertical Pod Autoscaler mutation is not enabled initially.

Recommendation mode may be evaluated after sufficient telemetry exists.

## 22.3 Node scaling

No automatic node autoscaler is used initially.

Node resizing or additional nodes require:

- OpenTofu plan
- Cost review
- Capacity review
- RabbitMQ and PostgreSQL placement review

Three nodes are the minimum supported persistent topology.

---

# 23. Initial resource plan

Planning requests, subject to load testing:

| Component | CPU request | Memory request |
|---|---:|---:|
| Ordinary Spring service replica | 250m | 512 Mi |
| Booking and Session replica | 500m | 768 Mi |
| Device Integration replica | 350m | 512 Mi |
| BFF replica | 250m | 512 Mi |
| Keycloak replica | 750m | 1.5 Gi |
| PostgreSQL instance | 1 CPU | 2.5 Gi |
| RabbitMQ instance | 500m | 1.5 Gi |
| Traefik replica | 100m | 128 Mi |
| step-ca replica | 100m | 128 Mi |

Limits prevent one workload from exhausting a node but must not cause repeated JVM termination.

Resource profiles are verified at reference and double load.

---

# 24. Environment model

## 24.1 Local

Docker Compose and local application processes.

No production credentials or data.

## 24.2 CI

Ephemeral containers and test clusters.

Used for:

- Contract tests
- PostgreSQL tests
- RabbitMQ tests
- Security tests
- Migration tests

## 24.3 Reference

Persistent three-node production-like cluster.

Contains only synthetic or approved demonstration data until production readiness.

## 24.4 Preview/integration

Created temporarily through OpenTofu.

Must be deleted automatically after the approved lifetime.

## 24.5 Actual production

Before onboarding real users, provision:

- Separate cluster
- Separate cloud project
- Separate object-storage credentials
- Separate Identity Provider realm
- Separate email and map credentials
- Separate OpenTofu state

Namespaces within the reference cluster are not sufficient isolation for actual production.

---

# 25. Release and promotion

## 25.1 Application release

1. Merge reviewed source change.
2. CI builds ARM64 and AMD64 images.
3. Run tests and scans.
4. Generate SBOM.
5. Sign image.
6. Push to GHCR.
7. Create deployment-manifest pull request using the image digest.
8. Run manifest and policy validation.
9. Approve environment promotion.
10. Merge.
11. Flux reconciles.
12. Health checks validate rollout.

## 25.2 Rollback

Application rollback:

- Revert to the previous image digest.
- Preserve compatible database schema.
- Do not run automatic down migrations.

If a new database schema is incompatible with the prior application, use forward correction rather than unsafe rollback.

## 25.3 Direct changes

Ordinary `kubectl apply` to Flux-managed resources is prohibited.

Emergency direct changes require:

- Break-glass authorization
- Audit
- Time limit
- Immediate Git backport or explicit revert

Flux will otherwise restore the declared state.

---

# 26. Upgrade policy

## 26.1 K3s

- Upgrade one Kubernetes minor version at a time.
- Validate compatibility of every operator.
- Take etcd and PostgreSQL backups first.
- Drain and upgrade one node at a time.
- Check etcd health before continuing.
- Avoid downgrade assumptions.

K3s 1.36 includes etcd 3.6 data-layout changes that complicate downgrade to earlier versions, so restoration procedures must be prepared before upgrading. ([docs.k3s.io](https://docs.k3s.io/zh/blog/2026/05/27/K3s-1.36-release?utm_source=openai))

## 26.2 Operators

Upgrade separately:

1. cert-manager
2. Cloud Controller Manager
3. CSI Driver
4. Flux
5. CloudNativePG
6. RabbitMQ Operators
7. Keycloak Operator

No bulk operator upgrade is allowed.

## 26.3 PostgreSQL

Patch updates use controlled CloudNativePG rolling updates.

Major upgrades require a dedicated migration plan and restore-tested backup.

## 26.4 RabbitMQ

Before node restart or rolling upgrade:

- Confirm quorum health.
- Confirm no member is quorum-critical.
- Upgrade one Pod at a time.

## 26.5 Keycloak

- Back up database.
- Test against a non-production realm.
- Update Keycloak resources.
- Validate login/token exchange.
- Update Operator only after server compatibility is confirmed.

---

# 27. Disaster recovery

## 27.1 Single-node loss

Expected automatic behaviour:

- etcd retains quorum.
- Stateless Pods reschedule.
- CloudNativePG promotes or continues with remaining members.
- RabbitMQ quorum queues remain available.
- Simulator clients reconnect.
- Hetzner CSI Volumes are reattached where possible.

Replacement node is created through OpenTofu.

## 27.2 Two-node loss

The cluster is unavailable.

Recovery requires:

- Restoring nodes
- Restoring etcd quorum, or
- Rebuilding the cluster and restoring state

No unsafe single-member continuation is attempted without the documented etcd recovery procedure.

## 27.3 Location loss

Cold recovery target: `fsn1`.

Procedure:

1. Provision a replacement cluster through OpenTofu.
2. Bootstrap K3s and Flux.
3. Restore PostgreSQL into a new CloudNativePG cluster.
4. Restore Keycloak and step-ca dependencies.
5. Recreate RabbitMQ topology.
6. Resume outbox publication.
7. Reapply privacy tombstones.
8. Validate security and application invariants.
9. Switch DNS.
10. Reconnect simulators.

## 27.4 Recovery objectives

Targets remain:

- RPO no greater than five minutes
- RTO no greater than 60 minutes

Synchronous local replication supports node-loss durability.

Continuous WAL archive supports disaster recovery.

The 60-minute location-recovery target must be demonstrated. If cold restoration cannot meet it, one of these is required:

- Warm standby database
- Pre-provisioned disaster-recovery cluster
- Managed PostgreSQL
- Relaxed approved RTO

---

# 28. NetworkPolicy model

Default policy:

- Deny all ingress.
- Deny all egress.
- Permit DNS.
- Add purpose-specific rules.

Examples:

- BFF → business APIs and Keycloak
- Booking → PostgreSQL and RabbitMQ
- Device Integration → PostgreSQL, RabbitMQ and step-ca
- Notification → PostgreSQL, RabbitMQ and Brevo
- Discovery → PostgreSQL, RabbitMQ and Geoapify
- Keycloak → PostgreSQL and email provider
- Prometheus → approved metrics endpoints
- Business services → OpenTelemetry Collector
- No application → Kubernetes API unless explicitly required

Operator namespaces receive the narrow Kubernetes permissions required for their controllers.

---

# 29. Infrastructure validation

## 29.1 OpenTofu

CI runs:

- Formatting
- Validation
- Provider lock verification
- Linting
- Security scanning
- Plan generation
- Cost estimation
- Policy checks

Apply requires protected-environment approval.

## 29.2 Kubernetes manifests

CI runs:

- Kustomize build
- Helm rendering
- Schema validation
- Server-side dry run against a test cluster
- Policy validation
- Secret scanning
- Image-digest validation
- NetworkPolicy checks
- Deprecated-API detection

## 29.3 Cluster acceptance

Required tests:

- One node terminated
- PostgreSQL primary terminated
- RabbitMQ Pod terminated
- Keycloak Pod terminated
- BFF Pod terminated
- Device Integration Pod terminated
- Load Balancer health failover
- CSI volume reattachment
- DNS and certificate renewal
- Flux drift correction
- Secret rotation
- etcd restore
- PostgreSQL PITR
- RabbitMQ reconstruction from outboxes
- Complete location-recovery drill

---

# 30. Decisions proposed for approval

| ID | Decision |
|---|---|
| ARC-DEP-01 | Use K3s with three embedded-etcd server/worker nodes. |
| ARC-DEP-02 | Place all primary K3s server nodes in Nuremberg. |
| ARC-DEP-03 | Use Falkenstein for backup storage and cold disaster recovery. |
| ARC-DEP-04 | Supersede ARC-010’s split active-node placement. |
| ARC-DEP-05 | Disable packaged Traefik and ServiceLB and manage Traefik through Flux. |
| ARC-DEP-06 | Use Hetzner Cloud Controller Manager and CSI Driver. |
| ARC-DEP-07 | Use one public Load Balancer with private node targets. |
| ARC-DEP-08 | Keep the Kubernetes API private and VPN-restricted. |
| ARC-DEP-09 | Use one namespace per business service. |
| ARC-DEP-10 | Apply default-deny NetworkPolicies. |
| ARC-DEP-11 | Use CloudNativePG with three PostgreSQL instances. |
| ARC-DEP-12 | Host separate logical service databases in one physical PostgreSQL cluster initially. |
| ARC-DEP-13 | Use quorum synchronous replication with one standby acknowledgement. |
| ARC-DEP-14 | Use RabbitMQ Operators and a three-node RabbitMQ cluster. |
| ARC-DEP-15 | Use quorum queues for release-critical messages. |
| ARC-DEP-16 | Rebuild RabbitMQ from Git topology and service outboxes after total loss. |
| ARC-DEP-17 | Use the official Keycloak Operator with two Keycloak replicas. |
| ARC-DEP-18 | Use cert-manager for public and internal workload certificates. |
| ARC-DEP-19 | Use step-ca for simulator machine-certificate issuance. |
| ARC-DEP-20 | Use Hetzner CSI Volumes for stateful production-like workloads. |
| ARC-DEP-21 | Do not rely on Hetzner server backups for application recovery. |
| ARC-DEP-22 | Use OpenTofu for cloud infrastructure. |
| ARC-DEP-23 | Use remote encrypted and versioned OpenTofu state. |
| ARC-DEP-24 | Use Flux for Kubernetes GitOps reconciliation. |
| ARC-DEP-25 | Use HelmRelease for third-party components and Kustomize for first-party applications. |
| ARC-DEP-26 | Use SOPS and age for Git-managed Secrets. |
| ARC-DEP-27 | Run schema migrations as dedicated versioned Jobs before application rollout. |
| ARC-DEP-28 | Pin all images, charts and providers. |
| ARC-DEP-29 | Require actual production to use a separate cluster and cloud project. |
| ARC-DEP-30 | Require tested node-loss and location-loss recovery before readiness approval. |

---

# 31. Open questions

| ID | Question | Resolution phase |
|---|---|---|
| ARC-DEP-OQ-01 | Final Hetzner Volume sizes | Load and retention testing |
| ARC-DEP-OQ-02 | Final operator/chart versions | Implementation lock file |
| ARC-DEP-OQ-03 | OpenTofu S3 native lock compatibility with Hetzner Object Storage | IaC proof of concept |
| ARC-DEP-OQ-04 | Final WireGuard administration implementation | ARC-012 |
| ARC-DEP-OQ-05 | Exact application resource requests and limits | Load testing |
| ARC-DEP-OQ-06 | Final Horizontal Pod Autoscaler thresholds | Load testing |
| ARC-DEP-OQ-07 | Whether PgBouncer becomes necessary | Database load testing |
| ARC-DEP-OQ-08 | Whether synchronous PostgreSQL replication meets latency targets | Allocation/load testing |
| ARC-DEP-OQ-09 | Whether cold recovery meets the 60-minute RTO | Disaster-recovery drill |
| ARC-DEP-OQ-10 | Final internal CA rotation mechanism | ARC-012 |
| ARC-DEP-OQ-11 | Final privacy/export bucket lifecycle | Privacy and ARC-012 |
| ARC-DEP-OQ-12 | Whether public Cloudflare proxying is later enabled | Security/performance review |
| ARC-DEP-OQ-13 | Final image-signature admission enforcement | CI/CD strategy |
| ARC-DEP-OQ-14 | Whether observability data uses CSI storage or disposable local storage | ARC-012 |

---

# 32. Acceptance criteria

This architecture is approved when:

1. Three K3s servers can form and retain etcd quorum.
2. No control-plane port is publicly exposed.
3. One-node loss preserves core platform operation.
4. PostgreSQL can fail over without violating committed Booking durability.
5. RabbitMQ retains quorum after one node loss.
6. Keycloak remains available after one Pod loss.
7. Simulators reconnect safely after Device Integration loss.
8. Public traffic reaches only Traefik, BFF, Keycloak authentication and Device Integration.
9. Every business service has a dedicated namespace and ServiceAccount.
10. Default-deny NetworkPolicies are enforced.
11. Stateful data uses declared persistent storage.
12. PostgreSQL supports PITR from object storage.
13. RabbitMQ can be reconstructed from Git and outboxes.
14. Public and machine certificates renew automatically.
15. OpenTofu can rebuild cloud infrastructure.
16. Flux can rebuild Kubernetes desired state.
17. Secrets remain encrypted in Git and Kubernetes storage.
18. Database migrations run once before application deployment.
19. Production image references use digests.
20. A location-loss recovery drill validates or revises the RTO.
21. ARC-010 cost estimates are updated for the final storage architecture.
22. No unresolved deployment decision prevents ARC-012 operational planning.

---

# 33. Consequences

## Positive

- Low-cost production-like Kubernetes architecture
- Three-member control-plane quorum
- Automated PostgreSQL and RabbitMQ operations
- Git-reconstructable infrastructure and workloads
- Strong namespace and network isolation
- Automated public and machine certificates
- No dependency on Redis or a service mesh
- Tested path from node failure to full disaster recovery
- Provider portability through Kubernetes and open protocols

## Negative

- All active nodes share one primary location.
- Stateful operators increase Kubernetes complexity.
- One physical PostgreSQL cluster is a shared failure boundary.
- Synchronous replication may increase Booking latency.
- K3s, PostgreSQL, RabbitMQ, Keycloak and PKI remain self-managed.
- SOPS bootstrap keys require careful recovery handling.
- Cold location recovery may not meet the RTO without further investment.
- Persistent Volumes increase the ARC-010 cost estimate.
- Three nodes leave limited spare capacity during node loss.

These trade-offs are accepted for the reference environment, subject to proof through failover, load and recovery testing.

---

# 34. Next architecture artifact

The next document is:

**Observability, Backup, Disaster Recovery and Operational Runbook Strategy v1.0**

It must finalize:

- Service-level indicators and objectives
- Metrics, logs and traces
- Alert classification
- Dashboard catalogue
- Telemetry retention
- PostgreSQL backup schedule
- etcd backup schedule
- Privacy tombstone restoration
- Restore procedures
- Node-loss runbook
- PostgreSQL failover runbook
- RabbitMQ recovery runbook
- Keycloak recovery runbook
- Broker quarantine handling
- Certificate rotation
- Secret rotation
- Incident response
- Cost and capacity operations
