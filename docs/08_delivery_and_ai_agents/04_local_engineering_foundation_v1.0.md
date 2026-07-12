# Local Engineering Foundation and Developer Runway v1.0

## Document metadata

- **Document ID:** ENG-001
- **Version:** 1.0
- **Status:** APPROVED
- **Owner:** Delivery / Platform Architect
- **Authoritative for:** M1 repository structure, local development environment, coding standards, developer commands and foundation acceptance criteria
- **Refines:** ARC-009, ARC-014, ARC-015
- **Depends on:** ARC-005, ARC-018, GOV-004
- **Does not supersede:** service boundaries, database ownership, domain lifecycles or production deployment architecture

---

## 1. Purpose and classification

This document defines the engineering foundation required before implementing business features.

It is **implementation planning**, not a new product or domain-architecture decision.

The foundation must provide:

1. A reproducible monorepo structure.
2. A documented and enforceable coding standard.
3. A local dependency environment.
4. Repeatable database migration and reset commands.
5. Contract-validation commands.
6. Structured local logging and tracing.
7. Pull-request validation through CI.
8. Evidence that a clean checkout can be prepared without undocumented manual steps.

No Booking, Station Operations, Charging Session or other business logic is implemented by this document.

---

## 2. Foundation completion target

The foundation is complete when a developer can perform:

```text
clone repository
→ run developer checks
→ start local dependencies
→ validate contracts
→ run migrations
→ run tests
→ inspect logs and health
```

The process must work without:

- global Maven or npm packages;
- developer-specific absolute paths;
- shared external databases;
- production credentials;
- manual database edits;
- direct cross-service database access;
- undocumented IDE configuration.

---

## 3. Baseline decisions

| Topic | Decision | Status |
|---|---|---|
| Repository model | One Git monorepo | APPROVED |
| Java baseline | Java 25 LTS | APPROVED |
| Java build | Maven Wrapper, Maven 3.9.16 | APPROVED |
| Backend framework | Spring Boot 4.1 / Spring MVC | APPROVED |
| Backend persistence | Spring JDBC / JdbcClient | APPROVED |
| Frontend | Angular 21.2 / Node 24 LTS | APPROVED |
| Database | PostgreSQL 18 | APPROVED |
| Migrations | Flyway, SQL-first | APPROVED |
| Broker | RabbitMQ 4.3 | APPROVED |
| Identity | Keycloak 26.6 | APPROVED, subject to implementation proof |
| REST contracts | OpenAPI 3.0.3 | APPROVED |
| Messaging contracts | AsyncAPI 2.6.0 | APPROVED |
| Message schemas | JSON Schema 2020-12 | APPROVED |
| Local orchestration | Docker Compose | APPROVED |
| Local mail catcher | Mailpit-compatible SMTP service | PROVISIONAL |
| Local observability | Prometheus/Grafana/Loki/Tempo profile | PROVISIONAL |
| Application execution | IDE/process mode first; container mode subsequently | APPROVED |
| Kubernetes locally | Not required | DEFERRED |
| Production secrets | Never used locally | DEFERRED |

The exact version and digest of every container image must be recorded in the implementation commit. Mutable `latest` image tags are prohibited.

---

## 4. Canonical repository structure

Create the following structure:

```text
/
├── README.md
├── pom.xml
├── mvnw
├── mvnw.cmd
├── package.json
├── package-lock.json
├── .nvmrc
├── .editorconfig
├── .gitignore
├── .gitattributes
│
├── apps/
│   ├── web/
│   └── bff/
│
├── services/
│   ├── account-service/
│   ├── station-operations-service/
│   ├── booking-session-service/
│   ├── device-integration-service/
│   ├── discovery-insights-service/
│   ├── notification-service/
│   └── governance-support-service/
│
├── simulator/
│   └── charger-simulator/
│
├── libraries/
│   ├── correlation/
│   ├── secure-logging/
│   ├── event-envelope/
│   └── test-support/
│
├── contracts/
│   ├── openapi/
│   ├── asyncapi/
│   ├── schemas/
│   │   ├── common/
│   │   ├── events/
│   │   ├── commands/
│   │   └── telemetry/
│   ├── registries/
│   ├── examples/
│   └── compatibility/
│
├── infra/
│   ├── local/
│   │   ├── compose.yaml
│   │   ├── compose.observability.yaml
│   │   ├── compose.apps.yaml
│   │   ├── .env.example
│   │   ├── postgres/
│   │   ├── rabbitmq/
│   │   ├── keycloak/
│   │   └── observability/
│   ├── tofu/
│   ├── bootstrap/
│   └── clusters/
│
├── scripts/
│   ├── dev/
│   ├── db/
│   ├── contracts/
│   ├── docs/
│   └── ci/
│
├── tests/
│   ├── e2e/
│   ├── concurrency/
│   ├── performance/
│   ├── resilience/
│   ├── security/
│   ├── accessibility/
│   └── recovery/
│
├── docs/
├── release-manifests/
└── .github/
    ├── workflows/
    ├── actions/
    ├── CODEOWNERS
    ├── dependabot.yml
    └── pull_request_template.md
```

### 4.1 Repository ownership rules

1. Each service owns its implementation and migrations.
2. Services cannot import another service's implementation.
3. Shared libraries contain technical primitives only.
4. Shared libraries must not contain:
   - domain aggregates;
   - Booking rules;
   - persistence entities;
   - authorization policy;
   - lifecycle decisions.
5. Cross-service integration uses approved REST or messaging contracts.
6. Generated contract output is never manually edited.
7. Infrastructure cannot depend on application implementation classes.
8. Browser code cannot import internal service contracts.
9. Test support cannot become production business logic.
10. Documentation must identify the authoritative source for every duplicated concept.

---

## 5. Coding standards

### 5.1 Java

All Java modules must use:

- Java 25;
- Maven Wrapper;
- Spring MVC;
- Spring JDBC / JdbcClient;
- UTC-first timestamps;
- explicit transaction boundaries;
- records for immutable transport types;
- sealed types where domain variants benefit;
- no preview language features;
- no JPA/Hibernate as the primary persistence model;
- no WebFlux/R2DBC in business services;
- explicit validation and error mapping;
- constructor injection;
- no field injection;
- no unchecked cross-service data access.

Critical allocation code must use explicit SQL and named transactions. SQL involved in locking, interval conflict detection or exclusion constraints requires focused tests against real PostgreSQL.

### 5.2 TypeScript and Angular

Use:

- strict TypeScript mode;
- Angular strict templates;
- Angular Signals and RxJS;
- feature-scoped stores;
- typed reactive forms;
- `angular-eslint`;
- Prettier;
- Angular Material/CDK accessibility primitives;
- generated API clients from approved OpenAPI contracts.

Do not introduce NgRx or another global state framework without a separate decision.

Generated clients must not be manually modified.

### 5.3 Formatting and static analysis

The repository must run formatting and analysis automatically.

Required checks:

- Java formatting;
- Java static analysis;
- ArchUnit boundary tests;
- JaCoCo reports;
- TypeScript lint;
- TypeScript formatting;
- Angular template diagnostics;
- accessibility linting;
- dependency vulnerability scanning;
- secret scanning;
- YAML and contract validation;
- Markdown link and metadata checks.

One formatter configuration is authoritative. Formatting changes must not be debated during ordinary code review.

### 5.4 Naming

Use:

- lower-case kebab-case directory names;
- Java package names matching the service;
- `PascalCase` Java types;
- `camelCase` Java methods and JSON properties;
- explicit `Ref` suffix for identifiers;
- `Instant`/`timestamptz` for instants;
- `LocalDate`/local time only when a station-local business rule requires it.

Do not use ambiguous names such as `data`, `status2`, `misc`, `common-domain` or `shared-model`.

---

## 6. Local Docker Compose environment

### 6.1 Compose files

Create:

```text
infra/local/compose.yaml
infra/local/compose.observability.yaml
infra/local/compose.apps.yaml
infra/local/.env.example
```

`compose.yaml` is the default core environment.

`compose.observability.yaml` is optional.

`compose.apps.yaml` is used after application images exist.

### 6.2 Core profile

The core profile contains:

| Service | Purpose | Host binding |
|---|---|---:|
| PostgreSQL | Local logical service databases | `127.0.0.1:5432` |
| RabbitMQ | Local broker and management UI | `127.0.0.1:5672`, `15672` |
| Keycloak | Local identity provider | `127.0.0.1:8180` |
| Mail catcher | Local SMTP and message inspection | `127.0.0.1:1025`, `8025` |

All host ports bind to loopback only.

No local infrastructure service is exposed to the public network.

### 6.3 PostgreSQL local topology

One PostgreSQL container may host multiple logical databases:

```text
account_db
station_operations_db
booking_session_db
device_integration_db
discovery_insights_db
notification_db
governance_support_db
bff_session_db
keycloak_db
```

Each logical database has:

- an owner role;
- a migrator role;
- a runtime role;
- an optional restricted operations role.

The local PostgreSQL container is shared infrastructure, not shared ownership.

The initialization script may create databases and roles. It must not insert business aggregates into another service's database.

### 6.4 RabbitMQ local topology

Use:

- one local virtual host;
- durable exchanges;
- publisher confirms;
- mandatory publishing;
- manual consumer acknowledgements;
- local retry/dead-letter definitions;
- critical queues configured consistently with the approved contract model.

The local single-node broker must be labelled as a development environment. It does not demonstrate broker high availability.

### 6.5 Keycloak local setup

Provide:

- an importable development realm;
- separate local clients for BFF, services and test tooling;
- test driver, operator and administrator identities;
- MFA-capable test administrator;
- deterministic realm bootstrap;
- local-only credentials supplied through ignored environment files.

No production realm, signing key or production client secret may be committed.

### 6.6 Mail catcher

Use a local-only Mailpit-compatible service.

Required behavior:

- SMTP endpoint for Notification Service;
- browser UI for inspecting messages;
- message reset during test reset;
- no external delivery;
- no production recipient addresses.

The exact image version and digest are recorded in the implementation setup.

### 6.7 Simulator profile

The simulator profile adds:

- Charger Simulator;
- local durable SQLite storage;
- secure WebSocket connection to Device Integration;
- deterministic device identities;
- repeatable scenario selection;
- resettable command and transaction history.

The simulator does not use a platform business database and does not directly write Booking data.

### 6.8 Observability profile

The optional observability profile adds:

- OpenTelemetry Collector;
- Prometheus;
- Grafana;
- Loki;
- Tempo.

The default application logging path remains structured stdout, so the application remains usable without the optional profile.

---

## 7. Local environment profiles

| Profile | Components | Intended use |
|---|---|---|
| `core` | PostgreSQL, RabbitMQ, Keycloak, mail catcher | Daily backend development |
| `simulator` | Core plus charger simulator | Charging-session development |
| `observability` | Core plus telemetry stack | Trace/log/metric development |
| `apps` | Core plus built application containers | Deployment smoke tests |
| `full` | Simulator, observability and apps | Demonstration and E2E tests |

Application services may initially run from the IDE against the `core` profile.

---

## 8. Canonical developer commands

Use a thin root `Makefile` backed by scripts under `scripts/`.

### 8.1 Environment

```text
make doctor
make infra-up
make infra-up PROFILE=simulator
make infra-up PROFILE=full
make infra-down
make infra-ps
make infra-reset CONFIRM=1
make health
```

`infra-reset` is destructive and must require explicit confirmation.

### 8.2 Database

```text
make db-init
make db-migrate SERVICE=all
make db-migrate SERVICE=booking-session
make db-info SERVICE=booking-session
make db-validate
make db-reset SERVICE=booking-session CONFIRM=1
make seed-apply
make seed-reset CONFIRM=1
```

Rules:

- `db-init` creates local databases and roles only.
- `db-migrate` invokes the service-owned Flyway migration path.
- Runtime services do not receive DDL privileges.
- Application startup does not silently perform uncontrolled migrations.
- `seed-apply` uses service-owned bootstrap APIs or runners.
- `seed-reset` never performs cross-service SQL writes.
- Migration scripts are immutable after application to a shared environment.
- Destructive local reset is unavailable without `CONFIRM=1`.

### 8.3 Contracts

```text
make contracts-install
make contracts-lint
make contracts-validate
make contracts-bundle
make contracts-generate
make contracts-examples
make contracts-compatibility
make contracts-test
```

Rules:

- tools are declared in `package.json`;
- `package-lock.json` is committed;
- CI uses `npm ci`;
- global npm installation is prohibited;
- OpenAPI, AsyncAPI and JSON Schema sources are validated;
- generated clients and interfaces are reproducible;
- schema references are resolved;
- compatibility checks compare the target branch with the base branch;
- generated output is written to ignored build directories unless explicitly needed as evidence.

### 8.4 Build and test

```text
make backend-build
make backend-test
make frontend-install
make frontend-test
make integration-test
make concurrency-test
make e2e-test
make verify
```

`make verify` runs every fast local check and must be the minimum pre-push command.

### 8.5 Logging and diagnostics

```text
make logs
make logs SERVICE=booking-session
make logs-follow SERVICE=booking-session
make traces
make metrics
make health
make rabbit-status
make keycloak-status
```

The command interface must work whether services run in containers or from the IDE.

---

## 9. Logging and telemetry policy

Every service emits structured JSON logs to stdout.

Required fields:

- timestamp;
- level;
- service;
- environment;
- logger;
- message code;
- trace ID;
- span ID;
- correlation ID;
- causation ID where applicable;
- operation;
- aggregate type;
- aggregate reference in safe form;
- outcome;
- duration;
- exception classification.

Never log:

- access tokens;
- refresh tokens;
- client secrets;
- passwords;
- authorization secrets;
- raw QR secrets;
- private keys;
- complete email addresses;
- unnecessary vehicle or identity data;
- full message payloads containing personal data.

Logs must use stable technical references or approved pseudonymous references where possible.

Business audit evidence is not replaced by ordinary application logs.

Critical workflows must create spans for:

- BFF request;
- Booking transaction;
- database lock wait;
- outbox write;
- message publication;
- consumer handling;
- device command;
- device result;
- session reconciliation.

---

## 10. CI foundation

### 10.1 Required pull-request stages

1. Repository metadata and file validation.
2. Formatting and linting.
3. Contract validation and generation.
4. Java compilation and tests.
5. Frontend compilation and tests.
6. PostgreSQL integration tests.
7. RabbitMQ integration tests.
8. Keycloak security tests where affected.
9. Architecture-boundary checks.
10. Secret and dependency scanning.
11. Container and infrastructure validation where affected.
12. Final `ci/required` aggregation job.

A skipped required job must fail the aggregate check unless the path classifier explicitly proves it is not applicable.

### 10.2 CI security rules

- Workflow permissions default to none.
- Jobs grant only required permissions.
- External Actions are pinned to full commit SHAs.
- Pull-request workflows receive no deployment secrets.
- Fork code is never executed through privileged workflow contexts.
- Persistent self-hosted runners are prohibited for untrusted pull requests.
- Global package installation is prohibited.
- Release artifacts are built only from trusted branches/tags.
- Images are published by digest.
- Secret scanning runs before artifact publication.

### 10.3 Local/CI parity

Every required CI check must have a local equivalent.

| CI check | Local command |
|---|---|
| Java build | `make backend-build` |
| Java tests | `make backend-test` |
| Frontend checks | `make frontend-test` |
| Contract validation | `make contracts-validate` |
| Contract generation | `make contracts-generate` |
| Migration validation | `make db-validate` |
| Integration tests | `make integration-test` |
| Full fast gate | `make verify` |

---

## 11. Required foundation artifacts

Create these files during ENG-001:

```text
pom.xml
mvnw
mvnw.cmd
package.json
package-lock.json
.nvmrc
.editorconfig
.gitignore
.gitattributes
Makefile
.env.example
CODEOWNERS
pull_request_template.md
infra/local/compose.yaml
infra/local/compose.observability.yaml
infra/local/compose.apps.yaml
infra/local/postgres/init/
infra/local/rabbitmq/
infra/local/keycloak/
scripts/dev/
scripts/db/
scripts/contracts/
scripts/ci/
```

Add service and application directories as empty buildable modules before implementing business features.

---

## 12. Foundation implementation tasks

| Task | Deliverable | Dependency |
|---|---|---|
| ENG-001-01 | Repository skeleton and root build files | None |
| ENG-001-02 | Toolchain and formatting configuration | ENG-001-01 |
| ENG-001-03 | Core Docker Compose profile | ENG-001-01 |
| ENG-001-04 | PostgreSQL databases, roles and Flyway harness | ENG-001-03 |
| ENG-001-05 | RabbitMQ local topology and health checks | ENG-001-03 |
| ENG-001-06 | Keycloak realm/client/test-user bootstrap | ENG-001-03 |
| ENG-001-07 | Mail catcher integration | ENG-001-03 |
| ENG-001-08 | Structured logging and telemetry baseline | ENG-001-01 |
| ENG-001-09 | Contract command suite | ENG-001-01 |
| ENG-001-10 | Pull-request CI and required-check aggregation | ENG-001-02, ENG-001-09 |
| ENG-001-11 | Simulator profile | ENG-001-03 |
| ENG-001-12 | Clean-checkout proof and developer guide | ENG-001-01 through ENG-001-11 |

---

## 13. Foundation definition of done

ENG-001 is complete only when:

1. A clean checkout passes `make doctor`.
2. `make infra-up` starts all core dependencies.
3. Every core dependency has a health check.
4. Local databases and roles are created reproducibly.
5. A fresh migration succeeds.
6. An upgrade migration succeeds.
7. Runtime roles cannot execute DDL.
8. Seed/reset is deterministic and service-owned.
9. Contract validation runs without globally installed tools.
10. Generated contract output is reproducible.
11. Logs contain correlation and trace identifiers.
12. Sensitive values are absent from logs and telemetry.
13. The simulator starts through its documented profile.
14. CI executes the same essential checks as local commands.
15. A harmless pull request passes the required gate.
16. A deliberately invalid contract is rejected.
17. A deliberately invalid migration is rejected.
18. A secret-scanning test is demonstrated.
19. The developer guide contains no undocumented manual step.
20. Evidence is attached to ENG-001 and ARC-015 M1.

---

## 14. Status and unresolved decisions

### OPEN

- Whether Windows-native PowerShell wrappers are required in addition to Make.
- Exact formatter implementation/version after Java 25 compatibility check.
- Exact container image digests.
- Final local TLS profile.
- Final observability dashboard set.

### PROVISIONAL

- Mailpit-compatible local mail catcher.
- Polling for the first frontend slice.
- Running applications from the IDE while infrastructure runs in Compose.
- Single-node local RabbitMQ.
- Single-container PostgreSQL with separate logical databases.

### DEFERRED

- K3s and Flux local deployment.
- Production secret manager.
- Managed PostgreSQL/RabbitMQ.
- Production email provider.
- Production map/geocoding provider.
- Multi-region infrastructure.
- Production admission enforcement.
- Full disaster-recovery automation.

An OPEN item blocks ENG-001 only if it affects reproducibility, security, contract meaning, database ownership or booking correctness.

---

## 15. Required documentation patches

### ARC-014

1. Replace `contracts/json-schema` with `contracts/schemas`.
2. Reference ENG-001 for exact local commands and M1 acceptance.
3. Mark the repository structure as planned until ENG-001 artifacts exist.
4. Keep ARC-014 authoritative for strategic CI/CD and release governance.

### ARC-009

1. Reference ENG-001 for Docker Compose profiles.
2. State that local observability is optional by profile.
3. State that Mailpit is local-only and provisional.
4. Keep technology versions authoritative.

### ARC-015

1. Add ENG-001 as the implementation of EPIC-01.
2. Add ENG-001 acceptance evidence to M1 exit criteria.
3. Do not mark M1 complete until the definition of done passes.

### GOV-001

Add:

- `DEC-ENG-01` — use the ENG-001 monorepo structure;
- `DEC-ENG-02` — use Docker Compose for local dependencies;
- `DEC-ENG-03` — use Makefile/scripts as the canonical command interface;
- `DEC-ENG-04` — use service-owned migrations and bootstrap;
- `DEC-ENG-05` — use structured stdout logging;
- `DEC-ENG-06` — require local/CI command parity.

### README

Add:

- prerequisites;
- quick start;
- local profiles;
- command reference;
- reset warning;
- contract commands;
- troubleshooting;
- link to ENG-001.

---

## 16. Foundation rule

After ENG-001 is approved:

> No business feature implementation begins by inventing local infrastructure, repository conventions, migration behavior, logging conventions or contract commands. Feature work must use this foundation.

ENG-001 may be amended only when implementation evidence reveals a real correctness, security or reproducibility defect.
