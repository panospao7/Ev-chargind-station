# I1-APP-001 — Angular public discovery UI evidence

- **Task ID:** I1-APP-001 (L2; packet approved via owner merge of PR #38;
  owner decisions recorded in packet: maplibre-gl, BFF proxy, hand-written
  adapters, PUB-01..03, axe-core, bounds-URL/radius reconciliation)
- **Baseline commit:** 7389191d (origin/main, PR #38 merge)
- **Candidate commits:** 3b09a475 (implementation) + fix-round commit (this)
- **Branch:** task/i1-app-001-ui
- **Date:** 2026-09-13

## What was delivered — the first user-visible UI

The Angular SPA serves the public discovery screens per ARC-023: PUB-01
(`/{locale}` entry), PUB-02 (`/{locale}/stations` map/list search with the
URL query contract, filters, explicit map/list mode), PUB-03
(`/{locale}/stations/{stationRef}` station details with EVSEs, connectors,
Intl-formatted tariff, freshness). Locale routing `/el` (default) + `/en`
with runtime switching that preserves route + query. MapLibre GL JS 5.24.0
behind the replaceable tile adapter (OSM raster tiles + attribution for
local dev). The BFF exposes the same-origin public proxy (two allowlisted
read routes, correlation ids, header hygiene, Problem Details passthrough).

## Review rounds and findings closed

| Finding | Disposition | Proof |
|---|---|---|
| General F-1 (MAJOR): locale switch dropped route + desynced signal | `localeLink()` preserves full route + query; NavigationEnd re-derives locale | shell implementation; locale.service re-derivation |
| General F-2 (MAJOR): map pan auto-searched (ARC-023 §26 violation) | bounds recorded internally; adopted only via "Search this area" | moveend-no-search test + button-adopts test |
| General F-3 (MAJOR): vacuous BFF header test | real client + embedded stub + outbound-header observation | cookieAndAuthorization test (BFF suite) |
| General F-4 (MAJOR, security): stationRef unvalidated/unencoded | STATION_REF_PATTERN controller gate + client encode() | encoded-ref + valid-ref tests |
| General F-5..F-8 (MINORs) | all fixed (matcher redirect, shared/ui relocation, contract-true filter validation, timer(0) removed) | suite green |
| Security F-S1: dot-segment refs | `(?!\.+$)` pattern + client pathSegment encode | dot-segment tests |
| Security F-S2: test comment overstated | claim scope corrected in comment | comment |
| Security F-S3: evidence missing | this file | — |

## Gates (final)

- **Web suite: 44/44 green** (14 files, vitest via `CI=true npx ng test`)
- **BFF suite: 9/9 green** (8 proxy + context smoke, MockMvc, release 21
  diagnostic; CI temurin 25 authoritative)
- **Production build: success** — initial 270.35 kB raw / 73.95 kB transfer;
  maplibre-gl lazy chunk (dynamic import, out of the initial bundle)
- **G3 contracts:verify: exit 0** (21 passed — sanity, no contract changes)
- **Storage grep: 0 hits** (`localStorage|sessionStorage|document.cookie|
  indexedDB` over apps/web/src)
- **URL-construction grep: 0 hits** outside `app/api/` (adapter-only URL
  authority per ARC-008 §6.8)
- Independent security review: **PASS_WITH_FINDINGS** (no blockers; proxy
  exposure audit PASS — allowlist correct, no over-exposure; data-in-UI
  audit PASS — public fields only, no injection vectors, no sanitizer
  bypass; secret scan clean)

## Disclosed behaviors and residual items

1. **No frontend CI yet** — service-tests.yml does not cover apps/**
   (packet-disclosed; I1-ENG-004 follow-up). Local evidence stands for this
   slice; the PR's G3 workflow runs (traceability untouched → G3 may not
   trigger; disclosed).
2. **No rate limiting on the public proxy** — deferred to EPIC-04/
   deployment per packet; must land before production exposure (owner
   risk-acceptance required at that point; not accepted here).
3. **ARC-023 bounds model reconciled** — URL carries the §9.2 bounds
   contract; the adapter maps bounds → the API's center+radius model
   (circle covers the rectangle; slight over-fetch disclosed).
4. **ARC-023 §2 Angular Material/CDK deferred** — plain semantic HTML this
   slice; Material adoption booked for a UI-foundation follow-up.
5. **axe color-contrast disabled** in jsdom (no layout engine) — automated
   evidence is partial by nature; keyboard/zoom/contrast manual checks
   continue per ARC-023 §25.5.
6. **PUB-03 omits** availability (S1-03, Booking-owned) and opening hours
   (data path absent per DEV-I1-DSC-002-01 §2) — both contract-true.
7. **Verbatim query forwarding** (security NOTE F-S4) — revisit with the
   gateway (EPIC-04); bounded by the same anonymous downstream surface.
8. **Hardcoded Problem Details literal** (F-S5) — registry-driven problem
   infrastructure is a follow-up.
9. `DISCOVERY_BASE_URL` defaults to cleartext loopback HTTP — production
   must configure TLS (deployment-stage).
10. Dependency-vulnerability scanning for apps/** not yet established
    (pairs with I1-ENG-004).

No secrets or personal data in this evidence.
