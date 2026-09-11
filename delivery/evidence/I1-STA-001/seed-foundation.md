# I1-STA-001 — S1-01 infrastructure seed validation evidence

- **Task ID:** I1-STA-001 (L3; packet approved via owner merge of PR #16)
- **Baseline commit:** b73faffacedd3dd2e80cd3422aa3e9463a81bc9c (planning branch tip)
- **Environment:** Windows 10.0.26200 x64, Git Bash; Docker Desktop 27.3.1 (engine running); JDK 21.0.1 (diagnostic); Maven wrapper 3.9.16
- **Date:** 2026-09-11
- **Result:** PASS (authoritative Java-25 execution via the new service-tests CI workflow)

## Suites

`./mvnw -pl services/station-operations-service,libraries/test-support -am test`
→ BUILD SUCCESS:

| Suite | Tests | Result |
|---|---|---|
| test-support (DAT foundation) | 16 | PASS — incl. all seven services migrating cleanly with STA at V1+V2 |
| station-operations seed/acceptance | 7 | PASS — table-set equality (13 tables), full S1-01 dataset invariants incl. all S1-03 policy constants, idempotency, five immutability negatives, runtime-no-DDL, reset+reseed |
| station-operations context smoke | 1 | PASS |

Full reactor `./mvnw test` → BUILD SUCCESS.

## Dataset (canonical S1-01, deterministic identifiers)

1 ACTIVE operator organization (+ admin/operator members) · 2 PUBLISHED
Greek stations (Athens/Thessaloniki fixture coordinates) · 4 ACTIVE EVSEs
(2 per station) · 8 connectors (CCS 150 kW + TYPE2 22 kW per EVSE) · 14
opening periods · 1 ACTIVE tariff version (energy 480 minor/kWh, occupancy
10 minor/min, EUR) · 1 ACTIVE booking-policy version (hold 300 s,
increments 15 min, min 15, max 240, advance 14 d, horizon 60 min,
freshness 300 s, grace 15 min) · 4 simulator assignments. Zero credentials,
tokens, or personal data.

## Gates

`npm run contracts:verify` exit 0 · delivery validator ALL CHECKS PASSED ·
self-test 8/8 · secretlint 0 findings · `git diff --check` clean.

## Limitations

Local Java is the disclosed diagnostic release 21; the new Service Tests
workflow executes the suite authoritatively on JDK 25 (paths match on the
PR). The reset's trigger bypass is confined to the migrator role in a
finally-guarded block (tested). No production environment exists; the reset
is a local/CI fixture operation.
