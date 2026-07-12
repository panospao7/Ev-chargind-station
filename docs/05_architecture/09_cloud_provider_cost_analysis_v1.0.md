Document ID: ARC-010  
Title: Cloud Provider and Cost Analysis  
Version: 1.0  
Status: IN_REVIEW  
Owner: Cloud / Operations Architect  
Last reviewed: 2026-07-12  
Depends on: ARC-001–009, REQ-002, PRV-001  
Authoritative for: Cloud-provider selection, hosting region, external providers, deployment cost baseline and cost controls  

# Cloud Provider and Cost Analysis v1.0

## 1. Purpose

This document:

- Compares realistic cloud deployment options.
- Estimates idle and reference-environment costs.
- Evaluates EU data location, reliability and operational complexity.
- Selects the preferred cloud provider and external services.
- Establishes monthly budgets and cost controls.
- Identifies risks deferred to deployment architecture.

The exact Kubernetes topology, resource limits, backup jobs and Infrastructure as Code modules are defined in ARC-011.

---

## 2. Pricing basis

Estimates use publicly listed prices available on **July 11, 2026**.

Unless stated otherwise:

- Prices exclude VAT and taxes.
- No reserved-use discounts are assumed.
- A month is approximately 730 hours.
- Network overage, domain registration and exceptional support are excluded.
- Estimates are planning ranges, not binding quotations.
- Production usage must be checked in the provider calculator before purchase.
- EUR and USD values are not converted because exchange rates vary.
- Free plans must not be treated as permanent service guarantees.

---

## 3. Platform workload

The deployment must host:

### Custom applications

- API Gateway/BFF
- Account Service
- Station Operations Service
- Booking and Session Service
- Device Integration Service
- Discovery and Insights Service
- Notification Service
- Platform Governance and Support Service
- Angular static assets

### Stateful platform components

- PostgreSQL 18
- RabbitMQ 4.3
- Keycloak 26.6
- BFF session database
- Object storage for exports and backups
- Metrics, logs and traces

### Workload characteristics

- Seven business microservices
- Long-lived simulator WebSocket connections
- Correctness-critical PostgreSQL transactions
- RabbitMQ quorum workloads
- Background workers
- Public map/search traffic
- Low ordinary traffic with occasional reference-load testing
- Greece-first users
- EU personal-data preference
- Individual-project budget

---

## 4. Evaluation criteria

| Criterion | Weight |
|---|---:|
| Monthly cost | 25% |
| Operational feasibility | 20% |
| EU data location | 15% |
| PostgreSQL/RabbitMQ suitability | 15% |
| Reliability and recovery | 10% |
| WebSocket support | 5% |
| Portability | 5% |
| Portfolio/evaluation value | 5% |

---

# 5. Option A — Hetzner Cloud

## 5.1 Proposed topology

Recommended production-like profile:

- Three ARM cloud nodes
- Two nodes in Nuremberg (`nbg1`)
- One node in Falkenstein (`fsn1`)
- One private `eu-central` network
- One Hetzner Load Balancer
- S3-compatible Hetzner Object Storage
- Node backups
- Self-managed PostgreSQL, RabbitMQ and Keycloak
- Cluster orchestration finalized in ARC-011

Hetzner private networks can span Falkenstein, Nuremberg and Helsinki, and Load Balancer targets do not need to be in the same location as long as they remain in the same network zone. ([docs.hetzner.com](https://docs.hetzner.com/networking/load-balancers/faq/?utm_source=openai))

## 5.2 Compute profile

Use three `CAX31` nodes:

- 8 ARM vCPUs each
- 16 GB RAM each
- 160 GB local SSD each
- 24 vCPUs, 48 GB RAM and 480 GB total local storage
- Current planning price: €20.99 per node/month

Hetzner’s June 15, 2026 pricing lists `CAX31` at €20.99 per month excluding VAT and IPv4. CAX plans use shared Ampere ARM resources, so performance can vary under host contention. ([docs.hetzner.com](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/?utm_source=openai))

All application images must therefore support `linux/arm64`. CI should optionally produce both `linux/amd64` and `linux/arm64` images.

## 5.3 Cost estimate

| Component | Monthly estimate |
|---|---:|
| Three `CAX31` nodes | €62.97 |
| Server backups at 20% | €12.59 |
| LB11 planning allowance | €7.50 |
| Object Storage | €4.99 |
| Private network/firewalls | €0 |
| Server public IPv4 | €0 if private/IPv6-only targets are used |
| **Infrastructure subtotal** | **Approximately €88.05** |

Hetzner charges server backups at 20% of the server price and provides seven backup slots. Object Storage starts at €4.99 per month excluding VAT and includes 1 TB of storage and 1 TB of egress. A Load Balancer supplies public IPv4 and IPv6 and can target servers through private addresses. ([docs.hetzner.com](https://docs.hetzner.com/cloud/billing/faq/?utm_source=openai))

Expected total after a domain and minor incidental usage:

> **Approximately €90–€105/month excluding VAT**

## 5.4 Dedicated-CPU upgrade profile

If shared CPU variance prevents the NFR targets, replace the nodes with three `CCX13` instances.

| Component | Monthly estimate |
|---|---:|
| Three `CCX13` nodes at €42.99 | €128.97 |
| Backups at 20% | €25.79 |
| Load Balancer | €7.50 allowance |
| Object Storage | €4.99 |
| **Subtotal** | **Approximately €167.25** |

Hetzner recommends dedicated-resource CCX plans for sustained production or CPU-intensive workloads. ([docs.hetzner.com](https://docs.hetzner.com/cloud/servers/faq/?utm_source=openai))

## 5.5 Availability

Hetzner publishes a 99.9% monthly availability SLA per Cloud Server. Load Balancers are designed for automatic hardware failover. ([docs.hetzner.com](https://docs.hetzner.com/general/company-and-policy/slas-cloud/?utm_source=openai))

The three-node design can tolerate an individual-node failure when:

- Workloads have at least two replicas where required.
- PostgreSQL has a cross-node standby.
- RabbitMQ uses three quorum members.
- Keycloak has multiple replicas.
- Persistent recovery does not rely on one node disk.
- Backups and WAL archives are stored outside the cluster.

It does not provide a managed database or a contractual platform-wide multi-AZ SLA.

## 5.6 Advantages

- Lowest predictable paid cost.
- Germany and Finland data locations.
- High compute and memory per euro.
- Private networking across EU locations.
- S3-compatible object storage.
- No proprietary application-service runtime.
- Strong portability.
- Suitable for demonstrating infrastructure ownership and Kubernetes operations.

## 5.7 Disadvantages

- PostgreSQL, RabbitMQ and Keycloak are self-managed.
- Shared ARM CPU performance may vary.
- Database failover and restoration are project responsibilities.
- No provider-managed secret manager.
- More operational effort than serverless platforms.
- The infrastructure must be patched and monitored by the project owner.

---

# 6. Option B — Google Cloud

## 6.1 Candidate topology

- Cloud Run for stateless HTTP services
- Cloud Run with minimum instances for BFF and Device Integration
- Cloud SQL for PostgreSQL
- Compute Engine VM or external managed service for RabbitMQ
- Self-hosted Keycloak
- Cloud Storage
- Secret Manager
- Managed logging and monitoring

Cloud Run supports scale to zero, managed TLS and WebSockets. WebSocket connections remain subject to a maximum 60-minute request timeout and clients must reconnect. ([cloud.google.com](https://cloud.google.com/run/pricing?utm_source=openai))

That timeout is compatible with the simulator protocol only because reconnect and durable event replay are already required.

## 6.2 Cost drivers

A non-HA Cloud SQL instance with:

- 2 vCPUs
- 8 GiB RAM
- 20 GiB storage

is approximately **$135/month** using listed compute, memory and storage rates. HA approximately doubles database compute and memory costs. ([cloud.google.com](https://cloud.google.com/sql/pricing?utm_source=openai))

One `e2-standard-2` RabbitMQ VM is approximately **$49/month** before disks and backups. ([cloud.google.com](https://cloud.google.com/products/compute/pricing/general-purpose?utm_source=openai))

Two always-running Cloud Run containers with 1 vCPU and 1 GiB each are approximately **$66/month before free-tier reductions**, while scale-to-zero services would add usage-dependent costs. ([cloud.google.com](https://cloud.google.com/run/pricing?utm_source=openai))

## 6.3 Planning range

| Profile | Estimated monthly cost |
|---|---:|
| Minimal, non-HA managed/hybrid | $250–$320 |
| HA database and stronger messaging | $380–$550 |
| Fully production-oriented observability/networking | $500+ |

These are architectural planning estimates. Final values require the Google Cloud Pricing Calculator.

## 6.4 Advantages

- Strong managed-container experience.
- Managed PostgreSQL, secrets and object storage.
- Scale-to-zero for suitable services.
- Mature IAM and observability.
- Several EU regions.
- Less host-level administration.

## 6.5 Disadvantages

- Higher always-on database and messaging cost.
- RabbitMQ is not a native first-class managed GCP service.
- Device WebSockets reconnect every 60 minutes at most.
- Network and observability charges are harder to predict.
- More provider-specific IAM and networking.
- Cloud Run is not ideal for every stateful or long-running component.

## 6.6 Assessment

Google Cloud is the preferred **managed-cloud alternative**, but not the initial deployment target because its steady-state cost is materially higher.

---

# 7. Option C — Microsoft Azure

## 7.1 Candidate topology

- Azure Container Apps
- Azure Database for PostgreSQL Flexible Server
- Self-hosted RabbitMQ
- Self-hosted Keycloak
- Blob Storage
- Key Vault
- Azure Monitor

Container Apps supports scale to zero and includes monthly free grants of 180,000 vCPU-seconds, 360,000 GiB-seconds and two million requests. ([azure.microsoft.com](https://azure.microsoft.com/en-us/pricing/details/container-apps/?msockid=25a8976d58726bcc2415818e59606a51&utm_source=openai))

## 7.2 Database cost

Published PostgreSQL Flexible Server examples include:

- B1ms, 1 vCore/2 GiB: $12.41/month
- B2s, 2 vCores/4 GiB: $49.64/month
- B2ms, 2 vCores/8 GiB: $99.28/month
- Storage: $0.115/GiB/month

The smallest instance is not considered sufficient for the full shared transactional workload. ([azure.microsoft.com](https://azure.microsoft.com/en-us/pricing/details/postgresql/flexible-server/?msockid=3b98de93a43a666f2474c846a5626728&utm_source=openai))

## 7.3 Planning range

| Profile | Estimated monthly cost |
|---|---:|
| Minimal non-HA | $120–$200 |
| Production-like | $220–$400 |
| Managed HA and full monitoring | $400+ |

## 7.4 Assessment

Azure is technically viable and less expensive than a fully managed AWS design, but it offers no decisive project advantage over Google Cloud or Hetzner.

---

# 8. Option D — Amazon Web Services

## 8.1 Candidate topology

- ECS/Fargate
- RDS for PostgreSQL
- Amazon MQ for RabbitMQ
- Self-hosted Keycloak
- S3
- Secrets Manager
- CloudWatch
- Application Load Balancer

Fargate charges continuously for allocated vCPU and memory while tasks run. RDS charges for provisioned database instances and storage. Amazon MQ charges for broker instances and storage. ([aws.amazon.com](https://aws.amazon.com/rds/postgresql/pricing/?utm_source=openai))

## 8.2 Planning range

| Profile | Estimated monthly cost |
|---|---:|
| Minimal single-instance services | $300–$450 |
| Production-like/HA | $500–$900+ |

This range is an architectural inference from the number of required always-on services. It must be replaced by an AWS Pricing Calculator estimate before any AWS deployment.

## 8.3 Assessment

AWS provides the broadest managed-service portfolio, including managed RabbitMQ, but its minimum steady-state cost and service complexity are disproportionate for this individual project.

**Decision:** Not selected.

---

# 9. Option E — Oracle Cloud Free Tier

OCI Always Free currently provides limited ARM compute, block storage, Object Storage, Vault secrets and a 10 Mbps Load Balancer allowance. Free-shape capacity may be unavailable in a selected home region. ([docs.oracle.com](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm?trk=public_post_comment-text&utm_source=openai))

The current free-only account documentation describes up to 2 OCPUs and 12 GB RAM for Ampere A1 resources, which is insufficient for a reliable implementation of all services, PostgreSQL, RabbitMQ, Keycloak and observability. ([docs.oracle.com](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm?trk=public_post_comment-text&utm_source=openai))

## Assessment

Suitable for:

- Proofs of concept
- Temporary demonstrations
- CI experiments
- Disaster-recovery exercises

Not suitable as the official reference deployment because:

- Capacity is not assured.
- Resources are too limited.
- The design would require unsafe component consolidation.
- Free-tier changes could invalidate the deployment.

---

# 10. Comparative decision matrix

Scores: 1 = weak, 5 = strong.

| Option | Cost | Operations | EU location | Managed services | Portability | Overall |
|---|---:|---:|---:|---:|---:|---:|
| Hetzner Cloud | 5 | 3 | 5 | 2 | 5 | **4.1** |
| Google Cloud | 3 | 5 | 5 | 5 | 3 | **4.0** |
| Azure | 3 | 4 | 5 | 4 | 3 | **3.7** |
| AWS | 2 | 4 | 5 | 5 | 3 | **3.5** |
| OCI Free Tier | 5 | 2 | 3 | 2 | 3 | **3.1** |

The close score between Hetzner and GCP reflects a trade-off:

- Hetzner optimizes cost and portability.
- GCP optimizes managed operations.

For this project, the budget and architectural-evaluation goals give Hetzner the advantage.

---

# 11. External service selection

## 11.1 Transactional email — Brevo

Selected:

- Brevo Free during development and demonstration
- Brevo Starter when the daily free limit becomes unsuitable

Brevo Free includes 300 email sends per day, while Starter begins at $9/month. Brevo states that its database hosting is located within the European Union, using France, Germany and Belgium. ([help.brevo.com](https://help.brevo.com/hc/en-us/articles/208589409-About-Brevo-s-pricing-plans?utm_source=openai))

Reasons:

- Transactional email support
- SMTP/API compatibility with Keycloak and Notification Service
- EU data-storage position
- Low initial cost
- Domain authentication support

Requirements:

- Dedicated sending subdomain
- SPF
- DKIM
- DMARC
- Signed webhook validation
- Provider abstraction inside Notification Service

## 11.2 Maps and geocoding — Geoapify

Selected initial provider:

- Geoapify Free for development and demonstration
- API 10 plan if production usage exceeds free limits

Current plans include:

- Free: 3,000 credits/day
- API 10: 10,000 credits/day for $59/month
- Geocoding: one credit/request
- Map tiles: 0.25 credits/tile

([geoapify.com](https://www.geoapify.com/pricing/?utm_source=openai))

The application must not depend on the public OpenStreetMap tile or Nominatim services for production. OSM’s public tile service is best-effort, and the public Nominatim service has an absolute maximum of one request per second. ([operations.osmfoundation.org](https://operations.osmfoundation.org/policies/tiles/?utm_source=openai))

Geoapify access must be replaceable through configuration.

## 11.3 DNS — Cloudflare DNS

Use Cloudflare’s free authoritative DNS plan.

Cloudflare states that DNS is available free on all plans and does not charge Free, Pro or Business users based on DNS query count. ([developers.cloudflare.com](https://developers.cloudflare.com/dns/faq/?utm_source=openai))

The initial architecture uses Cloudflare for DNS only. Proxy/CDN functionality requires a later security and mTLS compatibility review.

## 11.4 TLS certificates — Let’s Encrypt

Use Let’s Encrypt through automated ACME certificate management.

Let’s Encrypt provides free, automated TLS certificates. ([letsencrypt.org](https://letsencrypt.org/?locale=en_us&utm_source=openai))

## 11.5 Container registry — GitHub Container Registry

Use GHCR for OCI images.

GitHub currently states that Container Registry image storage and bandwidth are free, and public images may be pulled anonymously. ([docs.github.com](https://docs.github.com/en/enterprise-cloud%40latest/billing/concepts/product-billing/github-packages?apiVersion=2022-11-28&utm_source=openai))

Production images must still be:

- Signed
- Referenced by digest
- Scanned
- Protected from unauthorized publication

## 11.6 Object storage — Hetzner Object Storage

Use for:

- PostgreSQL base backups and WAL archives
- Privacy exports
- Generated report exports
- Disaster-recovery artifacts
- Selected encrypted operational archives

Do not use it for active PostgreSQL data files.

---

# 12. Secret-management selection

Hetzner does not provide the integrated managed-secret experience available in hyperscale clouds.

Initial selected approach:

- SOPS-encrypted secret manifests
- `age` encryption
- CI environment protections
- Kubernetes/cluster secrets encrypted at rest
- Offline recovery copy of the root decryption key
- Separate secrets per environment
- Automated rotation procedures

Prohibited:

- Plaintext secrets in Git
- Secrets in container images
- Shared development/production credentials
- Long-lived GitHub personal tokens on cluster nodes

A dedicated secret manager may be added later if operational requirements justify it.

---

# 13. Environment cost profiles

## 13.1 Local development

| Resource | Cost |
|---|---:|
| Docker Compose dependencies | $0 cloud cost |
| Mail catcher | $0 |
| Local observability | $0 |
| Local simulator | $0 |

## 13.2 Temporary integration environment

One `CAX31`, created only when needed:

- Approximately €20.99/month if continuously active
- Hourly billing when deleted after use
- No production data
- No HA claim

## 13.3 Demonstration environment

Two `CAX31` nodes, Load Balancer, backups and Object Storage:

> Approximately **€63/month excluding VAT**

This environment does not provide three-member RabbitMQ quorum or full node-failure tolerance.

## 13.4 Production-like reference environment

Three `CAX31` nodes:

> Approximately **€88–€105/month excluding VAT**

This is the approved initial budget profile.

## 13.5 Dedicated-performance environment

Three `CCX13` nodes:

> Approximately **€167–€185/month excluding VAT**

Use only if load testing demonstrates unacceptable shared-CPU variance.

---

# 14. Monthly budget

## 14.1 Initial limits

| Category | Warning | Hard review |
|---|---:|---:|
| Core Hetzner infrastructure | €100 | €130 |
| Email | €0 | €15 |
| Maps/geocoding | €0 | €60 |
| Domain/DNS/TLS | €3 averaged | €10 |
| CI/registry | €0 | €10 |
| Total | **€105** | **€200** |

The hard-review threshold is not an automatic outage threshold. It requires explicit approval before adding or resizing resources.

## 14.2 Cost alerts

Required alerts:

- 50% of monthly budget
- 75%
- 90%
- 100%
- Unexpected new billable resource
- Traffic quota threshold
- Object-storage growth
- Snapshot/backup growth
- Map-credit threshold
- Email quota threshold

---

# 15. Cost controls

1. Use private networking for node communication.
2. Avoid public IPv4 addresses on worker nodes.
3. Delete temporary environments after tests.
4. Set resource requests and limits.
5. Suspend nonessential staging workloads.
6. Retain only approved backups.
7. Apply log and trace retention.
8. Sample non-critical traces.
9. Keep raw meter telemetry retention short.
10. Prevent unbounded object-storage exports.
11. Set RabbitMQ queue-length and message-size limits.
12. Use database connection pooling.
13. Require labels for every billable resource.
14. Generate monthly cost reports.
15. Do not add Redis, a search engine or a schema registry without measured need.
16. Load-test before moving to dedicated CPU plans.

---

# 16. Data-location decision

Selected primary data locations:

- Nuremberg, Germany
- Falkenstein, Germany
- German Hetzner Object Storage location

A third node may remain in another German location to reduce single-location risk.

Helsinki is an approved EU disaster-recovery option but not the default, because keeping the primary cluster in Germany reduces cross-location operational complexity.

External provider data-location and processor agreements must be recorded in the privacy inventory.

---

# 17. Portability

The selected deployment remains portable because it uses:

- OCI containers
- Kubernetes-compatible manifests, if Kubernetes is selected
- PostgreSQL
- RabbitMQ
- S3-compatible storage
- OpenID Connect
- OpenAPI
- AsyncAPI
- OpenTelemetry
- SMTP/HTTP email abstraction
- Map provider abstraction

Provider-specific dependencies are limited to:

- Compute provisioning
- Load Balancer resources
- Private networking
- Object-storage endpoint
- DNS records
- Billing/monitoring integration

A future move to GCP, Azure or AWS should not require changing domain contracts.

---

# 18. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Shared ARM CPU contention | Load testing; upgrade to CCX dedicated CPU |
| ARM dependency incompatibility | Multi-architecture CI builds and startup tests |
| Self-managed PostgreSQL | Automated failover, WAL archiving, restore drills |
| Self-managed RabbitMQ | Three quorum members, queue monitoring, backups of definitions |
| Self-managed Keycloak | Multiple replicas, exported realm configuration, database backups |
| One provider controls all core infrastructure | Off-provider repository, encrypted backup export option |
| Location outage | Distribute nodes across Nuremberg/Falkenstein |
| Object-storage outage | Local short-term backup retention and retry |
| Cost increase | Monthly budget review and portability |
| Free external plan removal | Provider abstraction and paid-plan allowance |
| Insufficient operational expertise | Automated IaC, runbooks and failure exercises |

---

# 19. Decisions proposed for approval

| ID | Decision |
|---|---|
| ARC-CLD-01 | Select Hetzner Cloud as the initial deployment provider. |
| ARC-CLD-02 | Use the `eu-central` network zone and Germany as the primary data location. |
| ARC-CLD-03 | Use a three-node production-like baseline split across Nuremberg and Falkenstein. |
| ARC-CLD-04 | Use three `CAX31` ARM nodes initially. |
| ARC-CLD-05 | Upgrade to dedicated `CCX13` nodes only if load testing justifies it. |
| ARC-CLD-06 | Use one Hetzner Load Balancer as the public application entry point. |
| ARC-CLD-07 | Keep application nodes on private networking without public IPv4 where practical. |
| ARC-CLD-08 | Self-manage PostgreSQL, RabbitMQ and Keycloak. |
| ARC-CLD-09 | Use Hetzner Object Storage for backups and expiring export artifacts. |
| ARC-CLD-10 | Use Brevo for transactional email. |
| ARC-CLD-11 | Use Geoapify for initial map tiles and geocoding. |
| ARC-CLD-12 | Do not use public OSM tiles or Nominatim as production dependencies. |
| ARC-CLD-13 | Use Cloudflare for authoritative DNS only initially. |
| ARC-CLD-14 | Use Let’s Encrypt for public TLS certificates. |
| ARC-CLD-15 | Use GHCR as the container registry. |
| ARC-CLD-16 | Use SOPS and age for initial deployment-secret management. |
| ARC-CLD-17 | Set €105/month as the initial expected budget and €200 as the hard-review threshold. |
| ARC-CLD-18 | Keep Google Cloud as the preferred managed-cloud migration alternative. |
| ARC-CLD-19 | Do not use OCI Free Tier as the official reference deployment. |
| ARC-CLD-20 | Preserve provider portability through open protocols and containerized workloads. |

---

# 20. Open questions

| ID | Question | Resolution phase |
|---|---|---|
| ARC-CLD-OQ-01 | Final k3s/Kubernetes versus simpler orchestrator decision | ARC-011 |
| ARC-CLD-OQ-02 | Exact node placement and failure domains | ARC-011 |
| ARC-CLD-OQ-03 | PostgreSQL operator and replication mechanism | ARC-011 |
| ARC-CLD-OQ-04 | RabbitMQ cluster placement and storage | ARC-011 |
| ARC-CLD-OQ-05 | Keycloak HA topology | ARC-011 |
| ARC-CLD-OQ-06 | Exact backup encryption and off-provider-copy policy | ARC-011/012 |
| ARC-CLD-OQ-07 | Whether the BFF and static web assets share one deployment | ARC-011 |
| ARC-CLD-OQ-08 | Final Geoapify browser-key restrictions and proxy design | ARC-011 |
| ARC-CLD-OQ-09 | Domain registrar and final domain name | Delivery planning |
| ARC-CLD-OQ-10 | Whether dedicated CPU is necessary at reference load | Load testing |
| ARC-CLD-OQ-11 | Whether an external managed PostgreSQL service is worth the cost | Post-load operational review |
| ARC-CLD-OQ-12 | Off-provider disaster-recovery storage destination | ARC-012 |

---

# 21. Required validation

Before the production-like environment is accepted:

1. Provision all infrastructure from code.
2. Verify ARM compatibility for every image.
3. Run reference and double-load tests.
4. Measure shared-CPU variance.
5. Terminate one node during load.
6. Verify PostgreSQL failover.
7. Verify RabbitMQ quorum continuity.
8. Verify Keycloak session continuity.
9. Restore PostgreSQL from Object Storage.
10. Reapply privacy tombstones.
11. Verify Load Balancer failover.
12. Test simulator reconnect across node loss.
13. Confirm no node requires a public IPv4.
14. Validate backup encryption.
15. Confirm monthly cost against the budget.
16. Validate Brevo domain authentication.
17. Validate Geoapify quotas and attribution.
18. Document provider DPAs and processing locations.

---

# 22. Acceptance criteria

This analysis is approved when:

1. One primary provider is selected.
2. The primary region is within the EU.
3. The platform has a costed idle and production-like profile.
4. PostgreSQL, RabbitMQ, Keycloak and WebSockets are supported.
5. The expected monthly cost remains below the hard-review threshold.
6. External email, maps, DNS, TLS, registry and storage providers are selected.
7. Free plans have documented paid fallbacks.
8. Provider-specific dependencies remain replaceable.
9. Backups use storage outside the application nodes.
10. Shared-CPU risk has an explicit upgrade path.
11. OCI Free Tier is not treated as production capacity.
12. GCP remains a documented managed alternative.
13. Data-location decisions are traceable to privacy review.
14. Cost monitoring and alerts are mandatory.
15. The selected target can proceed to detailed deployment and IaC design.

---

# 23. Consequences

## Positive

- Production-like microservices environment for approximately €100/month
- EU and primarily German data hosting
- High resource capacity for the cost
- Strong provider portability
- S3-compatible backup storage
- No forced proprietary application runtime
- Clear managed-cloud migration alternative

## Negative

- Significant self-managed infrastructure
- Greater backup and failover responsibility
- Shared ARM CPU requires validation
- No native managed secret service
- Kubernetes or equivalent operations remain necessary
- Platform SLA depends heavily on project implementation

These trade-offs are accepted because the project prioritizes low cost, architectural learning and portability.

---

# 24. Next architecture artifact

The next document is:

**Deployment Architecture and Infrastructure as Code Strategy v1.0**

It must finalize:

- Container orchestrator
- Cluster and node topology
- Namespace structure
- Ingress and Load Balancer
- PostgreSQL deployment and failover
- RabbitMQ quorum topology
- Keycloak topology
- Storage classes
- Backup jobs
- Object-storage access
- Secret delivery
- Network policies
- Certificate management
- Horizontal and vertical scaling
- Resource requests and limits
- Terraform/OpenTofu modules
- Helm/Kustomize deployment structure
- Environment promotion and disaster recovery
