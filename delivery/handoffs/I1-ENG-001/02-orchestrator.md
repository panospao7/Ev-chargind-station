---
role: orchestrator
taskId: I1-ENG-001
previousState: READY
resultingState: CLAIMED
baselineCommit: 2e8844355e309fbfd28cba9a80acf27482a66169
impactLevel: L1
date: 2026-09-10T17:40:19Z
---

# I1-ENG-001 — Orchestrator claim handoff

## Claim

Task claimed from iteration I1 wave 1 after the owner merged the planning
branch (PR #7, merge commit 2e884435). Baseline = origin/main 2e884435.
Branch: `task/i1-eng-001-runway`. Packet: `delivery/tasks/I1-ENG-001.yaml`
(L1, READY at claim).

Definition of Ready re-checked at claim: packet complete; dependencies
(I0 tasks) VERIFIED on main; no W1-critical OPEN decision blocks local
runway work (GOV-007 §7); reviewers known; criteria measurable.

## Environment disclosure (verified by probe, 2026-09-10)

| Requirement (packet/ENG doc §3) | Local machine | Consequence |
|---|---|---|
| Java 25 LTS | JDK 21.0.1 + 17 only; no JAVA_HOME | See JDK note below |
| Maven 3.9.16 via wrapper | Maven 3.9.15 via scoop (for wrapper bootstrap only) | Wrapper pins 3.9.16 |
| Node 24 LTS (Angular workspace) | Node 22.12.0 | Known I0 limitation; CI runs 24 via .nvmrc |
| Docker engine (compose, Testcontainers) | Docker Desktop 27.3.1 installed, engine NOT running at claim | Starting Docker Desktop to run AC-02 verification |

**JDK note (open constraint, disclosed up front):** the approved baseline is
Java 25; this machine has no JDK 25 and agents must not install global
dependencies (AGENTS.md §12). Plan: all Maven modules declare the approved
baseline; local build verification will be attempted with Maven toolchains
provisioning (project-scoped, equivalent in kind to the wrapper downloading
Maven itself). If toolchain provisioning is not achievable, the Java build is
reported NOT_RUN with this exact reason and the owner installs JDK 25 — the
packet's AC-01 will then be partially verified by inspection only, and the
gap stays an unresolved finding until CI (EPIC-02, later task) or the owner
closes it.

## Implementation plan (coder handoff follows as 03-coder.md)

1. Root: multi-module pom.xml, Maven wrapper (pinned 3.9.16), .editorconfig,
   .gitattributes, README developer-quickstart section.
2. services/: seven skeleton Spring Boot modules (app class, application.yml,
   actuator health) — no business logic.
3. libraries/: four skeleton modules (correlation, secure-logging,
   event-envelope, test-support) — packages + placeholder tests only;
   real content is I1-ENG-002.
4. apps/bff: Spring Boot skeleton (gateway/OAuth2 dependencies deferred to
   the identity task, noted in README).
5. apps/web: Angular 21.2 workspace via pinned CLI (npm local, non-global).
6. simulator/charger-simulator: Java 25 skeleton per ADR-025.
7. infra/local: compose.yaml core profile with digest-pinned images
   (PostgreSQL 18, RabbitMQ 4.3, Keycloak 26.6, Mailpit), loopback-only;
   .env.example placeholders; postgres init intentionally left to I1-DAT-001.
8. scripts/dev, scripts/db, tests/ skeletons; release-manifests image record.

## Roles disclosure

Single agent session performs orchestrator + coder + tester roles
(disclosed, same pattern as I0). Independent reviewer round required before
human review; owner remains the merge authority.
