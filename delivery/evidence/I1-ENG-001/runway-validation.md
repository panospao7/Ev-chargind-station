# I1-ENG-001 — Runway validation evidence

- **Task ID:** I1-ENG-001
- **Baseline commit:** 2e8844355e309fbfd28cba9a80acf27482a66169 (origin/main after PR #7)
- **Candidate commit:** see task branch `task/i1-eng-001-runway`
- **Environment:** Windows 10.0.26200 x64, Git Bash; Docker Desktop 27.3.1 (engine running); JDK 21.0.1; Node 22.12.0 / npm 11.13.0; Maven wrapper pinned 3.9.16
- **Date:** 2026-09-10
- **Result:** PASS (with disclosed constraints — see tester handoff 04)

## Compose core profile (AC-02)

`docker compose -f infra/local/compose.yaml up -d --wait` — exit 0; all four
services report **healthy**:

```text
evplatform-keycloak   keycloak/keycloak:26.6@sha256:0aae0de7fca85525f727d3354df17896092de8bb26ae4c12d89c77e5df8cbce4   Up (healthy)   127.0.0.1:8180->8080, 127.0.0.1:9001->9000
evplatform-mailpit    axllent/mailpit@sha256:98b916bd3c8d61f7633a52d3ea2f58d00620cb01ca57ab59edde68c347a95365          Up (healthy)   127.0.0.1:1025->1025, 127.0.0.1:8025->8025
evplatform-postgres   postgres:18@sha256:4ef4dbc939d61acea57712655ddb4b4ab27419c913f94cca0cd57cb3ea3c2280              Up (healthy)   127.0.0.1:5432->5432
evplatform-rabbitmq   rabbitmq:4.3-management@sha256:57bddb6fbc3498b5d8b5a14dc6f4506073ebcf94c66ba2a7678c335faa8dd631  Up (healthy)   127.0.0.1:5672->5672, 127.0.0.1:15672->15672
```

All host bindings loopback-only (ENG-001 doc §6.2); images digest-pinned
(§3); manifest recorded in `release-manifests/local-images.md`.

## Java modules (AC-01, diagnostic release)

`./mvnw -Djava.version=21 -Dmaven.compiler.release=21 test` — **BUILD
SUCCESS, 13/13 modules**: 7 service contexts + BFF context start via
`@SpringBootTest`; simulator fails-fast test passes; 4 library placeholder
tests pass. Committed poms keep `java.version=25`; the release-25 build is
NOT_RUN on this machine (no JDK 25; agent-side install prohibited) — owner
or EPIC-02 CI closes this.

## Angular workspace (AC-01)

`npm ci` (469 packages) → PASS; `npm run build` → PASS (213.70 kB initial
bundle); `npm test -- --watch=false` → 2/2 PASS.

## Gates

- `npm run contracts:verify` → exit 0 (G3 unchanged and green)
- `node scripts/delivery/validate.mjs` → ALL CHECKS PASSED
- `node scripts/delivery/self-test.mjs` → 8 passed / 0 failed
- secretlint over new files → clean
- `git diff --check` → clean

## Limitations

Local-only verification; CI coverage for the Java/Angular tree arrives with
EPIC-02. No secrets or personal data in this evidence; Keycloak/DB/broker
credentials in `.env.example` and compose defaults are documented
development-only values.
