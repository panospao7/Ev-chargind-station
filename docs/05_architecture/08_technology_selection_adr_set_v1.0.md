Document ID: ARC-009  
Title: Final Technology Selection and Architecture Decision Record Set  
Version: 1.0  
Status: APPROVED  
Owner: Architecture Lead  
Last reviewed: 2026-07-11  
Depends on: ARC-001–008  
Authoritative for: Technology families, baseline versions, dependency policy and technology ADRs  

# Final Technology Selection and Architecture Decision Record Set v1.0

## 1. Purpose

This document selects the implementation technologies for:

- Frontend
- Backend and BFF
- Identity and security
- Databases and migrations
- Messaging
- API and event contracts
- Mapping and charts
- Testing
- Observability
- Containers and local development
- Dependency governance

Cloud vendors, managed-service plans, regions, pricing and production deployment topology remain subject to ARC-010.

---

## 2. Selection principles

Technologies are selected according to:

1. Compatibility with the approved architecture.
2. Active security support.
3. Operational simplicity for one developer.
4. Strong documentation and ecosystem maturity.
5. Support for automated testing.
6. Low local and cloud cost.
7. Avoidance of unnecessary infrastructure.
8. Explicit upgrade paths.
9. No dependence on preview releases.
10. Preference for open standards and replaceable integrations.

Version numbers below are baseline release lines. Patch releases may advance automatically after CI verification.

---

# 3. Approved stack summary

| Area | Selection | Baseline |
|---|---|---|
| Frontend framework | Angular | 21.2.x |
| Frontend runtime/tooling | Node.js | 24 LTS |
| Language | TypeScript | 5.9.x |
| Reactive library | RxJS | 7.8.x |
| UI components | Angular Material/CDK | 21.2.x |
| Unit/component tests | Vitest | 4.1.x |
| Browser E2E | Playwright | 1.61.x |
| Mapping | MapLibre GL JS | 5.24.x |
| Charts | Apache ECharts | 6.x |
| Backend language | Java | 25 LTS |
| Backend framework | Spring Boot | 4.1.x |
| Spring Cloud | Oakwood train | 2025.1.x |
| Build tool | Maven Wrapper | Maven 3.9.16 |
| BFF/gateway | Spring Cloud Gateway Server Web MVC | 5.0.x |
| Browser sessions | Spring Session JDBC | 4.1.x |
| Persistence access | Spring JDBC/JdbcClient | Spring-managed |
| Transaction database | PostgreSQL | 18.x |
| Migration tool | Flyway | 12.6.x |
| Identity Provider | Keycloak | 26.6.x |
| Message broker | RabbitMQ | 4.3.x |
| REST contract | OpenAPI | 3.0.3 |
| Event contract | AsyncAPI | 2.6.0 |
| Payload schema | JSON Schema | 2020-12 |
| API code generation | OpenAPI Generator | 7.22.x |
| Event envelope | CloudEvents | 1.0 |
| Telemetry standard | OpenTelemetry | OTLP |
| Metrics | Prometheus | 3.12.x |
| Dashboards | Grafana | 13.1.x |
| Logs | Grafana Loki | 3.6.x |
| Traces | Grafana Tempo | 3.0.x |
| Java containers | Cloud Native Buildpacks/Paketo | Pinned builder digest |
| Local orchestration | Docker Compose | Compose Specification |
| Simulator storage | SQLite | Current supported 3.x |
| Source repository | GitHub | Existing repository |

---

# 4. Frontend selection

## 4.1 Angular 21 instead of Angular 22

Angular 22 is the current Angular generation as of July 2026, but the stable OpenAPI Generator `typescript-angular` generator currently documents support through Angular 21. Angular 21 supports Node 24 and TypeScript 5.9. Therefore, v1 selects Angular 21.2.x, Node 24 LTS and TypeScript 5.9.x as the lower-risk compatible toolchain. ([angular.dev](https://angular.dev/reference/versions))

Upgrade to Angular 22 requires:

- OpenAPI Generator compatibility
- Angular Material compatibility
- TypeScript 6 migration
- Successful contract-client generation
- Full frontend regression testing

Angular 21 remains a deliberate compatibility choice, not an accidental outdated dependency.

## 4.2 Node.js

Use Node.js 24 LTS for:

- Angular CLI
- Frontend tests
- OpenAPI generation wrapper
- AsyncAPI tooling
- Documentation tooling

Node 26 is still in Current status and does not enter LTS until October 2026; production-oriented development should use an LTS release. ([nodejs.org](https://nodejs.org/en/about/previous-releases))

Use:

- `npm`
- Committed `package-lock.json`
- `npm ci` in CI
- Exact Node major through `.nvmrc` and `engines`
- No globally assumed CLI dependencies

## 4.3 Angular Material and CDK

Use Angular Material and Angular CDK on the same 21.2.x patch line as Angular.

Use Material for:

- Forms
- Dialogs
- Navigation
- Tables
- Menus
- Date controls
- Progress
- Accessible interaction primitives

Use CDK for:

- Overlay
- Focus management
- Accessibility
- Stepper foundations
- Layout utilities
- Custom application components

Angular Material/CDK follow Angular’s release policy and are maintained by the Angular team with accessibility and internationalization as explicit project goals. ([github.com](https://github.com/angular/components))

The application will use a custom visual theme rather than an unmodified Material appearance.

## 4.4 Frontend state

Use:

- Angular Signals
- RxJS
- Feature-scoped stores
- Typed reactive forms
- URL query state

Do not initially add:

- NgRx Store
- Redux
- Akita
- Elf
- A second global state framework

NgRx may be reconsidered only if measured cross-feature state complexity justifies it.

## 4.5 Frontend tests

Use:

- Vitest 4.1.x for unit and component tests
- Angular TestBed
- Angular Material component harnesses
- Playwright 1.61.x for E2E
- `@axe-core/playwright` for automated accessibility checks

Angular CLI uses Vitest as its default current testing setup, and Vitest 4.1 is the supported stable line selected here. Playwright 1.61 provides current cross-browser testing and WebAuthn test support. ([angular.dev](https://angular.dev/guide/testing))

Do not use Karma/Jasmine for new tests.

---

# 5. Mapping and charts

## 5.1 MapLibre GL JS

Use MapLibre GL JS 5.24.x.

Version 5.24 is the final stable v5 release line; version 6 was still in pre-release work during spring 2026. The application therefore avoids v6 until it reaches stable status and the CSP, browser and Angular integration are validated. ([maplibre.org](https://maplibre.org/maplibre-gl-js/docs/))

Use the CSP-compatible worker bundle if required by the final Content Security Policy.

The map-tile, geocoding and routing providers remain pending ARC-010 because:

- Pricing is provider-specific.
- Attribution requirements vary.
- Usage limits affect architecture.
- Hosting region and privacy terms require review.

## 5.2 Apache ECharts

Use Apache ECharts 6.x for operator and platform analytics.

Requirements:

- Canvas/SVG rendering chosen per chart
- Accessible tabular alternative
- No business information available only through a chart
- Design-system colours
- Reduced-motion support
- Lazy loading of chart code

ECharts 6 is the current major release and includes a revised theme and layout system. ([echarts.apache.org](https://echarts.apache.org/handbook/en/basics/release-note/v6-feature/))

---

# 6. Backend platform

## 6.1 Java 25 LTS

Use Java 25 LTS.

Java 25 is an LTS release; Java 21 remains supported, but using Java 25 avoids beginning the implementation on the previous LTS generation. ([oracle.com](https://www.oracle.com/ca-fr/java/technologies/java-se-support-roadmap.html))

Use an OpenJDK distribution such as Eclipse Temurin rather than depending on Oracle-specific runtime licensing.

Language policy:

- Records for immutable transport types
- Sealed types where domain variants benefit
- No preview language features
- UTC-first time handling
- Explicit nullability conventions
- Virtual threads only after load testing

## 6.2 Spring Boot 4.1

Use Spring Boot 4.1.x.

Spring Boot 4.1.0 is the current stable feature line and uses the Spring Framework 7 generation. ([spring.io](https://spring.io/projects/spring-boot/))

Primary starters:

- Spring Web MVC
- Spring Security
- OAuth2 Resource Server
- OAuth2 Client for BFF
- Validation
- JDBC
- AMQP
- Actuator
- Testcontainers support

Do not use WebFlux or R2DBC for business services.

Reasons:

- Allocation relies on JDBC transactions and explicit locks.
- Expected load does not require reactive persistence.
- Servlet-based code is easier to debug and operate.
- Mixing reactive and blocking persistence would add complexity.

## 6.3 Spring Cloud

Use Spring Cloud 2025.1.x, initially 2025.1.2 or its latest compatible service release.

The Oakwood release train supports Spring Boot 4.0 and 4.1 starting with 2025.1.2. ([spring.io](https://spring.io/projects/spring-cloud/))

Selected modules:

- Spring Cloud Gateway Server Web MVC
- Spring Cloud CircuitBreaker with Resilience4j where required
- Spring Cloud Contract only if later testing design proves it adds value

Do not use:

- Eureka
- Config Server
- OpenFeign by default
- Spring Cloud Bus
- Distributed configuration infrastructure

Service discovery and configuration will use the selected deployment platform.

---

# 7. BFF and browser sessions

## 7.1 BFF implementation

Use a dedicated Spring Boot BFF based on:

- Spring Cloud Gateway Server Web MVC
- Spring Security OAuth2 Client
- Spring Security
- Spring Session JDBC
- Spring Web MVC

Gateway Server Web MVC is a supported servlet-based gateway built on Spring Boot and WebMvc functional routing. ([docs.spring.io](https://docs.spring.io/spring-cloud-gateway/reference/))

The BFF owns:

- OIDC login redirects
- OAuth token storage
- Browser session
- CSRF
- Target-audience token exchange
- API routing
- Coarse rate limits
- Correlation headers
- Optional page-level response composition

It owns no business data.

## 7.2 Session storage

Use Spring Session JDBC backed by a dedicated PostgreSQL logical database.

Spring Session provides a JDBC-backed session repository with PostgreSQL-specific customization and expiry cleanup. ([docs.spring.io](https://docs.spring.io/spring-session/reference/configuration/jdbc.html))

This avoids introducing Redis before a measured requirement exists.

Redis may replace JDBC sessions only if testing demonstrates:

- Session-store database contention
- Insufficient session-expiry performance
- A scaling requirement not served adequately by PostgreSQL

Session attributes must use a controlled serialization format; arbitrary Java object serialization is prohibited.

---

# 8. Persistence

## 8.1 PostgreSQL 18

Use PostgreSQL 18 and remain on the latest available 18.x patch.

PostgreSQL 18 is the current production major release; PostgreSQL 19 is still beta as of July 2026. PostgreSQL 18.4 is the current documented patch line. ([postgresql.org](https://www.postgresql.org/docs/))

Required features include:

- `tstzrange`
- GiST indexes
- `btree_gist`
- Exclusion constraints
- Partial indexes
- `jsonb`
- UUID
- Transactional row locking
- Declarative partitioning where justified

Do not depend on PostgreSQL 19 beta features.

## 8.2 Persistence access

Use Spring JDBC:

- `JdbcClient`
- `JdbcTemplate`
- `NamedParameterJdbcTemplate`
- Explicit SQL
- Spring transaction management

`JdbcClient` provides a unified fluent API over JDBC query and update operations while retaining access to lower-level JDBC facilities for advanced operations. ([docs.spring.io](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html))

Do not use Hibernate/JPA as the principal persistence model.

Reasons:

- Allocation uses PostgreSQL-specific ranges and locks.
- Explicit SQL makes concurrency behaviour reviewable.
- Aggregate loading remains controlled.
- Hidden flushing and ORM state transitions are undesirable in critical transactions.

Small services may not independently adopt JPA without an architecture amendment.

## 8.3 Flyway

Use Flyway 12.6.x Community features through Maven.

Flyway 12.6.1 is the selected baseline patch. ([documentation.red-gate.com](https://documentation.red-gate.com/flyway/release-notes-and-older-versions/release-notes-for-flyway-engine))

Rules:

- SQL-first migrations
- Separate migration job
- Immutable versioned migrations
- No automatic production `clean`
- One history per logical service database
- Expand–migrate–contract
- Migration validation in CI

## 8.4 Redis

Redis is not part of the initial required stack.

Potential later uses:

- High-throughput session storage
- Distributed rate-limit counters
- Short-lived non-authoritative caches

No correctness rule may depend solely on Redis.

---

# 9. Identity Provider

Use Keycloak 26.6.x, initially 26.6.3 or a later security patch within that line.

Keycloak 26.6.3 includes security fixes released on June 4, 2026, and 26.6 introduced supported token exchange, workflows and federated client-authentication capabilities relevant to the architecture. ([keycloak.org](https://www.keycloak.org/2026/06/keycloak-2663-released))

Selected capabilities:

- OIDC Authorization Code with PKCE
- MFA
- WebAuthn/passkeys where supported
- Service accounts
- `private_key_jwt`
- Audience-limited tokens
- Standard token exchange
- Session revocation
- Email verification/recovery
- Separate realms per environment

Keycloak extensions and custom providers are avoided unless configuration alone cannot meet requirements.

The exact container image must be pinned by version and digest.

---

# 10. Messaging

## 10.1 RabbitMQ

Use RabbitMQ 4.3.x, initially 4.3.2 or its latest security patch.

RabbitMQ 4.3 is the current fully community-supported line as of July 2026. RabbitMQ 4.2 community support ends on July 31, 2026, making 4.3 the appropriate new-system baseline. ([rabbitmq.com](https://www.rabbitmq.com/release-information))

Use:

- AMQP 0-9-1
- Spring AMQP
- Publisher confirms
- Mandatory publishing
- Manual consumer acknowledgements
- Quorum queues for critical durable commands/events
- Broker policies for retries and dead-lettering
- Separate virtual hosts per environment

Durable classic queues may be used only for non-critical, rebuildable or disposable projection workloads after review.

Do not use RabbitMQ Streams in v1.

## 10.2 Kafka rejection

Apache Kafka is not selected because:

- Workload volume is moderate.
- Command routing and queues are primary needs.
- Long event-log retention is not foundational.
- Kafka adds operational cost.
- Projection rebuilds can use source snapshots plus retained events.

The design remains broker-abstracted at the contract level.

---

# 11. API and event tooling

## 11.1 OpenAPI

Use OpenAPI 3.0.3 as the checked-in REST contract standard.

OpenAPI 3.0.3 is current, but OpenAPI Generator documents only beta OpenAPI 3.1 support and no equivalent 3.2 compatibility assurance. OpenAPI 3.0.3 therefore provides the safer contract/tooling boundary. ([spec.openapis.org](https://spec.openapis.org/oas/))

Contract approach:

- API-first
- YAML source of truth
- One specification per API owner
- Shared schema fragments where stable
- Bundled and linted in CI
- Compatibility diff required

## 11.2 OpenAPI Generator

Use OpenAPI Generator 7.22.x, pinned exactly.

The selected version supports:

- Stable TypeScript Angular generation
- Angular through version 21
- Spring Boot 4 generation through `useSpringBoot4`
- Spring HTTP interfaces and server interfaces ([openapi-generator.tech](https://openapi-generator.tech/docs/generators/typescript-angular/))

Generate:

- Angular API clients
- Java API interfaces and DTOs
- Test fixtures where useful

Generated business implementations are prohibited.

## 11.3 springdoc-openapi

Do not make runtime code-first generation authoritative.

`springdoc-openapi` 3.x is compatible with Spring Boot 4, but it may be used only for:

- Non-production Swagger UI
- Runtime exposure of the approved static contract
- Additional documentation checks ([springdoc.org](https://springdoc.org/v4/index.html))

The checked-in OpenAPI file remains authoritative.

## 11.4 AsyncAPI

Use AsyncAPI 2.6.0 and JSON Schema 2020-12.

AsyncAPI 2.6.0 is the selected standard for messaging interface definitions due to its mature code generation and tooling ecosystems. ([asyncapi.com](https://www.asyncapi.com/docs/reference/specification/v2.6.0))

Use:

- AsyncAPI CLI, pinned in `package-lock.json`
- JSON Schema validation
- Generated human-readable documentation
- No generated domain handlers

## 11.5 CloudEvents

Use CloudEvents 1.0 structured JSON semantics for integration events.

Java event classes remain explicit project-owned records; adopting a CloudEvents SDK is optional.

---

# 12. Backend testing

Use the versions managed by Spring Boot 4.1 where possible.

Selected tools:

- JUnit Jupiter/JUnit 6 through `spring-boot-starter-test`
- AssertJ
- Mockito
- Awaitility
- Spring MockMvc
- Testcontainers
- PostgreSQL container
- RabbitMQ container
- Keycloak container/test environment
- Toxiproxy for network-failure tests
- jqwik for property-based tests
- ArchUnit for architecture constraints

Spring Boot 4.1’s test starter includes JUnit, AssertJ, Mockito, Hamcrest, JSONassert, JsonPath and Awaitility, and provides dedicated Testcontainers integration. ([docs.spring.io](https://docs.spring.io/spring-boot/appendix/auto-configuration-classes/spring-boot-testcontainers.html))

Rules:

- H2 must not replace PostgreSQL in allocation tests.
- Embedded fake brokers must not replace RabbitMQ contract tests.
- Critical concurrency tests run against the selected PostgreSQL major.
- Testcontainers image versions are pinned.

Spring Cloud Contract remains optional because OpenAPI and AsyncAPI are already the contract sources of truth. Adding another independently maintained contract DSL is not justified initially.

---

# 13. Observability stack

## 13.1 Application instrumentation

Use:

- Spring Boot Actuator
- Micrometer
- OpenTelemetry Java Agent
- Manual OpenTelemetry spans for critical workflows
- W3C Trace Context
- OTLP export
- Structured JSON logs to stdout

OpenTelemetry recommends the Java agent as the default Spring Boot instrumentation approach because it provides broader out-of-the-box instrumentation. ([opentelemetry.io](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/))

Selected baseline:

- OpenTelemetry Java Agent 2.29.x or latest compatible security patch
- OpenTelemetry Java API line compatible with 1.64.x
- OpenTelemetry Collector current stable image, pinned by digest

Avoid duplicate metrics:

- Micrometer exports application metrics to Prometheus.
- OpenTelemetry exports traces.
- OTel agent metric export is disabled unless a later design consolidates metrics under OTLP.

## 13.2 Local/self-managed observability

Use:

| Signal | Technology |
|---|---|
| Metrics | Prometheus 3.12.x |
| Dashboards/alerts | Grafana 13.1.x |
| Logs | Loki 3.6.x |
| Traces | Tempo 3.0.x |
| Collection | OpenTelemetry Collector |

Prometheus 3.12 was released in May 2026. Grafana 13.1 is the current Grafana generation, while Loki 3.6 and Tempo 3.0 provide selected stable baselines for local evaluation. ([github.com](https://github.com/prometheus/prometheus))

For an individual project:

- Run single-binary/local modes.
- Do not deploy the observability stack as independent microservice clusters.
- Allow ARC-010 to replace components with managed equivalents.

---

# 14. Containers and runtime

## 14.1 Java images

Build Java service images with:

- Spring Boot Maven plugin
- Cloud Native Buildpacks
- Paketo Java buildpack
- Java 25
- OCI image format
- Non-root runtime user
- Layered application image
- SBOM output
- Immutable image digest

Spring Boot’s Maven plugin builds OCI images through Cloud Native Buildpacks, and the generated images run as non-root users. ([docs.spring.io](https://docs.spring.io/spring-boot/maven-plugin/build-image.html))

Do not maintain handwritten Java Dockerfiles unless buildpack limitations are demonstrated.

## 14.2 Angular delivery

Initial preferred deployment:

- Angular compiled static assets
- Same-origin delivery through the edge/BFF deployment
- Immutable hashed assets
- Short cache for HTML shell
- Long cache for fingerprinted assets

A separate static-hosting/CDN deployment may replace this in ARC-010 without changing frontend contracts.

## 14.3 Local development

Use Docker Compose for:

- PostgreSQL databases
- RabbitMQ
- Keycloak
- Mail catcher
- Object-storage emulator if required
- Observability stack
- Optional service dependencies

Application services may run either:

- From the IDE against containers
- As OCI containers

Kubernetes is not required for local development.

---

# 15. Charger Simulator

Use:

- Java 25
- Spring Boot 4.1 non-web application profile
- JDK WebSocket client
- Jackson
- SQLite for durable local queue and command-result history
- Actuator management endpoint restricted to local/operator use
- Same Maven/buildpack baseline as backend services

The simulator does not need:

- A business-service PostgreSQL database
- RabbitMQ access
- Keycloak human-user authentication
- Spring WebFlux
- A public REST API

SQLite represents device-local durable storage and does not become a platform authoritative database.

---

# 16. Code quality

## Java

Use:

- Maven Enforcer
- Spotless
- Google Java Format or Palantir Java Format, selected once and pinned
- Checkstyle for architectural conventions
- ArchUnit
- JaCoCo
- OWASP Dependency-Check or equivalent
- SpotBugs where compatible with Java 25

## TypeScript

Use:

- ESLint
- `angular-eslint` matching Angular 21
- Prettier
- Angular template accessibility linting
- TypeScript strict mode
- Angular extended diagnostics
- Bundle budgets

Formatting is automated and must not be debated during code review.

---

# 17. Dependency policy

## 17.1 Version control

- Maven Wrapper pins Maven 3.9.16.
- Spring Boot and Spring Cloud BOMs manage Spring dependencies.
- OpenTelemetry uses its official BOM.
- Frontend uses exact lockfile-resolved versions.
- Container images use explicit versions and production digests.
- `latest` tags are prohibited in deployed manifests.

Maven 3.9.16 is the latest supported Maven 3 release; Maven 4 remains release-candidate software and is not selected. ([maven.apache.org](https://maven.apache.org/docs/history))

## 17.2 Upgrade cadence

- Security patches: expedited
- Ordinary patches: monthly review
- Minor versions: quarterly review
- Major versions: planned ADR and migration
- Framework EOL: release blocker

## 17.3 Automated updates

Use Dependabot or Renovate, finalized in CI/CD planning.

Automated pull requests must run:

- Build
- Unit tests
- Integration tests
- Contract compatibility checks
- Security scans
- Image scans
- Frontend bundle checks

No dependency update merges only because it is newer.

---

# 18. Rejected technologies

| Technology/option | Decision | Reason |
|---|---|---|
| Angular 22 for v1 | Deferred | Client generator currently targets Angular through 21 |
| Node 26 | Deferred | Current, not LTS until October 2026 |
| Java 21 | Rejected as baseline | Supported but superseded by Java 25 LTS for a new build |
| Spring Boot 3.5 | Rejected as baseline | New implementation targets Boot 4 generation |
| WebFlux/R2DBC | Rejected | JDBC transactions dominate correctness needs |
| Hibernate/JPA | Rejected as primary persistence | Insufficiently explicit for range/locking design |
| Redis at initial launch | Deferred | No measured requirement |
| Kafka | Rejected | Excessive operational complexity for workload |
| Elasticsearch/OpenSearch | Deferred | PostgreSQL projections are sufficient initially |
| NgRx global store | Deferred | Feature stores and Signals are sufficient initially |
| Micro-frontends | Rejected | Unnecessary complexity |
| SSR | Deferred | No measured SEO requirement |
| PWA/offline mutation | Rejected | Mutations require authoritative connectivity |
| Kubernetes as mandatory platform | Deferred | Cloud/cost analysis must justify it |
| Runtime schema registry | Deferred | Repository schemas and CI validation suffice initially |
| PostgreSQL 19 beta | Rejected | Non-production major |
| MapLibre 6 prerelease | Deferred | Use stable v5.24 line |
| Maven 4 release candidate | Rejected | Not GA |
| H2 for allocation tests | Rejected | Does not validate PostgreSQL constraints |
| Shared client secrets | Rejected | Use asymmetric service identity |

---

# 19. Architecture Decision Record set

## ADR-001 — Angular compatibility baseline

**Status:** Accepted  
**Decision:** Angular 21.2, Node 24 LTS and TypeScript 5.9.  
**Reason:** Stable compatibility with OpenAPI-generated Angular clients.  
**Consequence:** Angular 22 features are deferred.

## ADR-002 — Frontend state management

**Status:** Accepted  
**Decision:** Signals, RxJS and feature stores without a global Redux store.  
**Consequence:** Introduction of NgRx requires a later ADR.

## ADR-003 — Frontend component framework

**Status:** Accepted  
**Decision:** Angular Material/CDK with a project-owned theme and accessible wrappers.  
**Consequence:** Domain components remain outside the shared UI layer.

## ADR-004 — Java runtime

**Status:** Accepted  
**Decision:** Java 25 LTS without preview features.  
**Consequence:** Build, runtime and libraries must support Java 25.

## ADR-005 — Spring generation

**Status:** Accepted  
**Decision:** Spring Boot 4.1 and Spring Cloud 2025.1.  
**Consequence:** Jakarta/Spring Framework 7 APIs are the baseline.

## ADR-006 — Servlet and JDBC architecture

**Status:** Accepted  
**Decision:** Spring MVC plus Spring JDBC/JdbcClient.  
**Consequence:** WebFlux, R2DBC and JPA are not standard service dependencies.

## ADR-007 — BFF technology

**Status:** Accepted  
**Decision:** Spring Cloud Gateway Server Web MVC with Spring Security.  
**Consequence:** OAuth tokens remain server-side.

## ADR-008 — Session persistence

**Status:** Accepted  
**Decision:** Spring Session JDBC in PostgreSQL.  
**Consequence:** Redis is not required initially.

## ADR-009 — Identity Provider

**Status:** Accepted, conditional on proof of concept  
**Decision:** Keycloak 26.6.x.  
**Proof required:** Token exchange, `private_key_jwt`, MFA, session revocation and realm automation.

## ADR-010 — Transactional database

**Status:** Accepted  
**Decision:** PostgreSQL 18.x.  
**Consequence:** PostgreSQL-specific range and exclusion features are intentionally used.

## ADR-011 — Database migration

**Status:** Accepted  
**Decision:** Flyway 12.6.x with SQL-first migrations and dedicated migration jobs.

## ADR-012 — Message broker

**Status:** Accepted  
**Decision:** RabbitMQ 4.3.x with quorum queues for critical durable workloads.

## ADR-013 — REST contract standard

**Status:** Accepted  
**Decision:** OpenAPI 3.0.3, API-first.

## ADR-014 — REST code generation

**Status:** Accepted  
**Decision:** OpenAPI Generator 7.22.x for Angular clients and Java interfaces.

## ADR-015 — Asynchronous contract standard

**Status:** Accepted  
**Decision:** AsyncAPI 2.6.0, JSON Schema 2020-12 and CloudEvents 1.0.

## ADR-016 — Mapping

**Status:** Accepted  
**Decision:** MapLibre GL JS 5.24.x.  
**Deferred:** Tile and geocoding provider selection to ARC-010.

## ADR-017 — Analytics charts

**Status:** Accepted  
**Decision:** Apache ECharts 6.x with accessible alternatives.

## ADR-018 — Frontend testing

**Status:** Accepted  
**Decision:** Vitest 4.1 and Playwright 1.61.

## ADR-019 — Backend testing

**Status:** Accepted  
**Decision:** Spring Boot test stack, Testcontainers, jqwik, ArchUnit and Toxiproxy.

## ADR-020 — Telemetry standard

**Status:** Accepted  
**Decision:** OpenTelemetry and OTLP with the Java agent as default instrumentation.

## ADR-021 — Local observability

**Status:** Accepted  
**Decision:** Prometheus, Grafana, Loki and Tempo in simplified local modes.

## ADR-022 — Container build

**Status:** Accepted  
**Decision:** Spring Boot Cloud Native Buildpacks/Paketo for Java services.

## ADR-023 — Build tool

**Status:** Accepted  
**Decision:** Maven 3.9.16 through Maven Wrapper.

## ADR-024 — Initial cache policy

**Status:** Accepted  
**Decision:** No mandatory distributed cache.

## ADR-025 — Simulator implementation

**Status:** Accepted  
**Decision:** Java 25 application using JDK WebSocket and SQLite local persistence.

---

# 20. Required proofs of concept

Before implementation readiness, complete:

## POC-01 — Keycloak and BFF

Verify:

- Authorization Code with PKCE
- Server-side token storage
- Audience downscoping/token exchange
- MFA assurance claims
- Session revocation
- Logout
- Step-up flow
- `private_key_jwt`

## POC-02 — OpenAPI generation

Verify:

- OpenAPI 3.0.3 validation
- Angular 21 client generation
- Spring Boot 4 interface generation
- Problem Details
- Nullable and composed schemas
- Date/time and decimal handling
- Unknown enum handling

## POC-03 — RabbitMQ reliability

Verify:

- Publisher confirms
- Mandatory unroutable detection
- Quorum queue behaviour
- Dead-letter policy
- Consumer crash/redelivery
- Outbox/inbox integration

## POC-04 — PostgreSQL concurrency

Verify ARC-006 using PostgreSQL 18:

- `btree_gist`
- Exclusion constraints
- Guard locks
- Hold races
- Rescheduling
- Occupation overlap
- Deadlock retry

## POC-05 — Map CSP and accessibility

Verify:

- MapLibre CSP worker
- Keyboard controls
- List/map equivalence
- Bundle impact
- Marker clustering
- Provider attribution

## POC-06 — Observability

Verify:

- Trace propagation through BFF, REST and RabbitMQ
- Log/trace correlation
- Prometheus scraping
- Tempo trace query
- Loki log query
- No personal data in telemetry

---

# 21. Open items delegated to ARC-010

The next cloud and cost analysis must select:

1. Cloud provider
2. Deployment region
3. Managed versus self-hosted PostgreSQL
4. Managed versus self-hosted RabbitMQ
5. Keycloak hosting
6. Object storage
7. Email provider
8. Map tile provider
9. Geocoding provider
10. Secret manager
11. Container registry
12. Observability hosting
13. Certificate authority
14. DNS and sender domain
15. Monthly budget and alerts

These are provider decisions, not unresolved application-framework decisions.

---

# 22. Approval criteria

This technology baseline is approved when:

1. Every architecture component has an implementation technology.
2. Selected versions are mutually compatible.
3. No preview runtime is required.
4. Node and Java use LTS releases.
5. OpenAPI generation supports the selected Angular and Spring versions.
6. PostgreSQL supports ARC-006.
7. RabbitMQ supports the required reliability semantics.
8. The BFF can store sessions without requiring Redis.
9. Keycloak proof-of-concept criteria are accepted.
10. Local development can run through Docker Compose.
11. Tests use real PostgreSQL and RabbitMQ where correctness depends on them.
12. Observability supports metrics, logs and traces.
13. Container images run as non-root.
14. Dependency updates are controlled and tested.
15. External provider choices are explicitly delegated to ARC-010.

---

# 23. Consequences

## Positive

- Stable LTS language runtimes
- Strong alignment with PostgreSQL concurrency design
- Reduced infrastructure through JDBC sessions
- Standards-based REST and events
- Generated typed API boundaries
- Mature security and identity platform
- Realistic message-broker reliability
- Accessible Angular component baseline
- Complete local observability
- Reproducible non-root images

## Negative

- Angular 22 adoption is delayed.
- PostgreSQL-specific SQL reduces database portability.
- Keycloak requires meaningful operational management.
- RabbitMQ quorum queues use more resources.
- The observability stack is heavy for local development.
- API generation introduces build-time governance.
- Spring Boot 4 and Java 25 require current ecosystem compatibility.
- SQLite adds a second database technology for the simulator.

These consequences are accepted because the selected stack preserves correctness while remaining feasible for an individual implementation.

---

# 24. Next architecture artifact

The next document is:

**Cloud Provider and Cost Analysis v1.0**

It must compare realistic deployment options and include:

- Required managed components
- Free-tier limitations
- Monthly cost at idle and reference load
- Regional availability
- EU data location
- Container deployment options
- PostgreSQL and RabbitMQ hosting
- Keycloak hosting
- Object storage
- Email
- Maps/geocoding
- Observability
- Backups
- Secret management
- Operational complexity
- Portability
- Recommended deployment target
