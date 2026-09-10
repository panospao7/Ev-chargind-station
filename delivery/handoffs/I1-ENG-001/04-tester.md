---
role: tester
taskId: I1-ENG-001
previousState: IMPLEMENTING
resultingState: SELF_VERIFIED
baselineCommit: 2e8844355e309fbfd28cba9a80acf27482a66169
impactLevel: L1
date: 2026-09-10T18:50:00Z
---

# I1-ENG-001 — Tester handoff

## Test execution (all commands run in this worktree, 2026-09-10)

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `docker compose -f infra/local/compose.yaml config --quiet` | PASS | syntax + interpolation valid |
| 2 | `docker compose -f infra/local/compose.yaml up -d --wait` | PASS | all 4 containers Healthy (see evidence file) |
| 3 | `docker compose ... ps` | PASS | digest-pinned images, loopback-only ports confirmed in `docker compose ps` output |
| 4 | `./mvnw -Djava.version=21 -Dmaven.compiler.release=21 test` | PASS | 13/13 modules; all 7 service + BFF Spring contexts start; simulator + 4 libraries pass |
| 5 | `./mvnw -q validate` (no release override) | PASS | full reactor graph resolves at pinned 3.9.16 |
| 6 | `cd apps/web && npm ci` | PASS | 469 packages, clean install |
| 7 | `npm run build` (Angular) | PASS | production bundle generated |
| 8 | `npm test -- --watch=false` (Angular) | PASS | vitest 2/2 |
| 9 | `npm run contracts:verify` (G3) | PASS (exit 0) | pre-existing warning-severity lint items only |
| 10 | `node scripts/delivery/validate.mjs` | PASS | ALL CHECKS PASSED |
| 11 | `node scripts/delivery/self-test.mjs` | PASS | 8 passed, 0 failed |
| 12 | secretlint over new infra/modules files | PASS | no findings |
| 13 | `git diff --check` | PASS | no whitespace errors |

## NOT_RUN / constrained items (disclosed, not hidden)

1. **`./mvnw test` at the pinned `java.version=25`** — NOT_RUN. This machine
   has JDK 21.0.1 / 17 only; AGENTS.md §12 forbids agents from installing
   toolchains globally. The committed poms keep `java.version=25`
   (approved baseline, never lowered). Item #4 ran the identical source tree
   with `-Djava.version=21 -Dmaven.compiler.release=21` as a diagnostic —
   it proves module graph, dependencies, Spring context wiring and source
   syntax, but it is NOT the authoritative build. Resolution paths: owner
   installs JDK 25 locally, or EPIC-02 CI runs it on JDK 25. Until then this
   is an unresolved finding (L1-risk only: skeleton code is version-agnostic).
2. **Testcontainers PostgreSQL/RabbitMQ smoke in libraries/test-support**
   (AC-03) — NOT_RUN as a committed test: it is deferred to I1-ENG-002, which
   fills `libraries/test-support` with real content. AC-03's substance
   (Testcontainers can start PostgreSQL 18 + RabbitMQ 4.3) is partially
   evidenced by item #2 running the real containers natively. Flagged for
   the reviewer's judgment; do not mark AC-03 fully met until ENG-002.
3. **CI on this branch** — G3 workflow only (services CI is EPIC-02).

## Verdict

Acceptance criteria: AC-01 PASS (structure + builds; Java-25 caveat
disclosed), AC-02 PASS, AC-03 PARTIAL (deferred to I1-ENG-002), AC-04 PASS,
AC-05 PASS. No failing test was deleted, skipped, or weakened; no validator
was loosened.
