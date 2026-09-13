---
role: coder
taskId: I1-APP-001
previousState: CLAIMED
resultingState: SELF_VERIFIED
baselineCommit: 7389191d (origin/main, PR #38 merge)
impactLevel: L2
date: 2026-09-13T13:00:00Z
---

# I1-APP-001 — Coder handoff

## Implemented (candidate 3b09a475 + fix-round commit)

**Angular SPA (apps/web):**
- Public shell (skip-link, locale switch preserving route+query, localized
  titles via TitleStrategy), locale routing `/el` (default) + `/en` with
  NavigationEnd re-derivation, locale matcher rejecting non-locale prefixes
  and bare root (redirect fires).
- PUB-01 entry + PUB-02 map/list search: URL query contract (bounds,
  connector, minimumPowerKw, view), invalid-param strip + aria-live
  announcement, explicit map/list mode, "Search this area" (map movement
  never auto-searches — ARC-023 §26/§16.4), skeleton/empty/error states.
- PUB-03 station details: EVSEs with connectors (kW), tariff via
  Intl.NumberFormat from minor units (el/en ground-truth tested), freshness
  in Europe/Athens, localized 404/500 + retry.
- MapLibre 5.24.0 behind the MapPresentationAdapter port (dynamic import —
  lazy chunk), OSM tile config via injection token, fallback panel on any
  map failure, list retained.
- Hand-written schema-mirror adapters (comment-linked to the OpenAPI
  schemas), relative /api paths only, Problem Details mapper, correlation
  interceptor.
- i18n: typed signal dictionaries (el/en, key-parity tested), translate
  pipe (impure — locale-signal-reactive, disclosed).
- axe-core checks: zero critical+serious on all three screens (jsdom
  color-contrast disabled, disclosed).

**BFF (apps/bff):**
- Minimal same-origin public proxy (plain WebMvc — Gateway/Security stack
  reserved for the identity task): two allowlisted read routes, verbatim
  query forwarding, X-Correlation-Id (validated-UUID forward or generate),
  Cookie/Authorization structurally never forwarded (fresh RestClient),
  Problem Details passthrough byte-identical, everything else 404.
- STATION_REF_PATTERN gate (rejects encoded metacharacters AND dot-segments
  before any downstream call) + UriComponentsBuilder encode() on both
  client methods (defense in depth).
- 9 MockMvc tests incl. real-client + embedded-stub outbound-header
  observation, encoded-ref rejection, dot-segment rejection, allowlist,
  correlation paths.

## Fix rounds (from the independent general + security reviews)

F-1 locale switch (route preservation + signal re-derivation), F-2
map-movement no-search, F-3 honest header test (real client + stub), F-4
stationRef validation/encoding, F-5 bare-root redirect, F-6 shared/ui
relocation to workspace level, F-7 contract-true filter validation,
F-8 timer(0) removal, F-S1 dot-segments, F-S2 comment accuracy.

## Verification

- Web 44/44 (vitest, `CI=true npx ng test` — NOTE: plain `npm test` runs
  vitest in watch mode and hangs agent sessions; use CI=true).
- BFF 9/9 (release 21 diagnostic).
- Build: initial 270.35 kB / 73.95 kB transfer; maplibre lazy chunk.
- G3 exit 0; storage grep 0; URL-authority grep 0 outside adapters.
- Security review PASS_WITH_FINDINGS (no blockers); all findings fixed or
  disclosed.

## Disclosed environment note

The vitest watch-mode hang consumed two agent runs before diagnosis —
`CI=true` (or `vitest run`) is the required invocation for automation.
