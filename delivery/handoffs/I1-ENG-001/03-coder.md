---
role: coder
taskId: I1-ENG-001
previousState: CLAIMED
resultingState: IMPLEMENTING
baselineCommit: 2e8844355e309fbfd28cba9a80acf27482a66169
impactLevel: L1
date: 2026-09-10T18:45:00Z
---

# I1-ENG-001 — Coder handoff

## Implemented scope (packet `delivery/tasks/I1-ENG-001.yaml`)

1. **Root build** — aggregator `pom.xml` (13 modules); Spring Boot modules
   parent `spring-boot-starter-parent:4.1.1` (latest 4.1.x stable on Maven
   Central; 4.2.x is milestone-only); shared libraries parent the aggregator
   with the `spring-boot-dependencies:4.1.1` BOM imported for version
   management only. Maven wrapper pinned to 3.9.16 (approved baseline).
   `.editorconfig`, `.gitattributes`, `.gitignore` extensions (SCOPE-001).
2. **services/** — seven skeleton Spring Boot web+actuator modules
   (account, station-operations, booking-session, device-integration,
   discovery-insights, notification, governance-support): application class,
   `application.yml` (health/info probes), one context-loads test each.
   No business logic by design.
3. **apps/bff** — Spring Boot skeleton only. Approved gateway/OAuth2 stack
   (ADR set §7) deliberately deferred to the identity/BFF task; documented
   in the pom description.
4. **apps/web** — Angular 21.2 workspace generated with the pinned CLI
   (21.2.24; `@angular/core ^21.2.0`, TypeScript ~5.9), standalone,
   default vitest suite passing.
5. **simulator/charger-simulator** — plain-Jar skeleton per ADR-025
   (JDK WebSocket + SQLite come with device-integration tasks); `main()`
   deliberately fails fast so the skeleton cannot pretend to run.
6. **libraries/** — correlation, secure-logging, event-envelope,
   test-support skeletons with package-info ownership contracts and
   placeholder tests; real content is I1-ENG-002.
7. **infra/local/** — core profile compose: PostgreSQL 18, RabbitMQ 4.3,
   Keycloak 26.6 (start-dev, H2; production-like keycloak_db is the identity
   task's), Mailpit; digest-pinned, loopback-only, healthchecked;
   `.env.example` placeholders; per-area READMEs marking the I1-DAT-001 /
   identity / observability boundaries; observability compose is a stub by
   design.
8. **scripts/dev, scripts/db, tests/, release-manifests/** — bootstrap/
   teardown scripts (ENG-001 doc §8.1), boundary READMEs, seven test-suite
   locations, pinned-image manifest.
9. **README.md** — developer quickstart section appended to the docs
   homepage.

## Key decisions (within packet authority)

- postgres:18 mount point is `/var/lib/postgresql` (image's new single-mount
  layout; the old `.../data` path fails startup by design).
- Keycloak healthcheck uses bash `/dev/tcp` with printf octal escapes
  (`\015`/`\012`) — the image has bash but no curl/wget; octal escapes avoid
  YAML/bash backslash-layering ambiguity (two earlier draft variants were
  discarded for exactly that reason; the committed form is machine-verified
  through YAML parsing).
- Mailpit health endpoint is the UI root `/` (the `/api/v1/healthz` path 404s
  on the current image build).
- Java poms declare the approved Java 25 baseline. Local diagnostic builds
  were additionally run at release 21 (see tester handoff); the poms were
  never lowered.

## Disclosed deviation

`.gitignore` extension — outside packet allowedFiles, recorded as
`delivery/deviations/I1-ENG-001/SCOPE-001-gitignore.yaml` (protective,
I0 precedent, owner may reject).
