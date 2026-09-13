# I1-ENG-004 — Frontend CI workflow + maplibre-gl v6 upgrade evidence

Task: I1-ENG-004
Branch: `task/i1-eng-004-ci`
Baseline: `origin/main` = `23a15fe9` (merge of planning PR #41), plus local claim commit `73bccf60`.
Impact level: L2 (new CI workflow + production dependency upgrade; no contract, migration, or service-boundary change).
Date: 2026-09-13

## 1. Pre-upgrade negative audit (captured BEFORE the upgrade)

Command (in `apps/web`): `npm audit --omit=dev --audit-level=high`

- Exit code: **1** (see `audit-pre-upgrade-exit.txt`)
- Output: `1 critical severity vulnerability`
- Advisory: **GHSA-jrc7-96c5-q579** — "MapLibre GL JS: XSS Sanitizer Bypass in DOM.sanitize() via Live NamedNodeMap Removal Skip", severity critical, affects `maplibre-gl <=6.4.0`, fixed in >= 6.4.1.
- Installed version at baseline: `maplibre-gl@5.24.0` (see `audit-pre-upgrade-ls.txt`).

Raw files: `audit-pre-upgrade.txt`, `audit-pre-upgrade-exit.txt`, `audit-pre-upgrade-ls.txt`.

## 2. Upgrade performed

Command (in `apps/web`): `npm install maplibre-gl@^6.9.0` (caret range, NOT `--save-exact`, NOT `npm audit fix`).

- `apps/web/package.json`: `"maplibre-gl": "^5.0.0"` → `"maplibre-gl": "^6.9.0"` (single-line diff).
- Installed version verified: `node_modules/maplibre-gl/package.json` → `6.9.0`.
- `dist/maplibre-gl.css` present: `True` (adapter imports `maplibre-gl/dist/maplibre-gl.css`).

## 3. Lockfile diff confinement

`git diff --stat` after upgrade:

```
 apps/web/package-lock.json | 88 +++++++++++++++-------------------------------
 apps/web/package.json      |  2 +-
 2 files changed, 30 insertions(+), 60 deletions(-)
```

Every lockfile hunk touches only the maplibre-gl dependency subtree:

- `maplibre-gl` 5.24.0 → 6.9.0 (integrity updated)
- dependency-range declaration update for maplibre-gl
- `@mapbox/unitbezier` 0.0.1 → 1.0.0 (maplibre dep)
- `@mapbox/vector-tile` 2.0.5 → 3.0.0 (maplibre dep)
- `@mapbox/whoots-js` removed (no longer a maplibre dep in v6)
- `@maplibre/maplibre-gl-style-spec` 24.10.0 → 26.4.2 (maplibre dep)
- `pbf` 4.0.2 → 5.1.2 (maplibre dep; nested dedup entries removed)
- removal of nested duplicate `@mapbox/unitbezier` and `pbf` entries under maplibre deps

No unrelated transitive bump appears in the diff. A residual-line scan (excluding maplibre/@mapbox/@maplibre/pbf/earcut/kdbush/bidi-js/jsonlint/tiny-sdf/geojson-vt/mlt/vt-pbf/whoots/unitbezier/vector-tile/style-spec tokens) shows only integrity hashes and closing braces belonging to the subtree entries above.

Note: npm briefly re-sorted the `axe-core` line inside `devDependencies` of `package.json`; this was reverted so the `package.json` diff is confined to the maplibre-gl version line only, per the allowed-files constraint.

## 4. Adapter-compatibility checklist (maplibre-gl 5 → 6)

Verified against `node_modules/maplibre-gl/dist/maplibre-gl.d.ts` (v6.9.0) and dist files. The adapter casts the dynamic import via structural typing (`as unknown as MapLibreModule`), so TypeScript cannot catch API drift; the greps below are the authoritative check.

Adapter file read in full before the check: `apps/web/src/app/features/discovery/map/maplibre.adapter.ts`.

| # | Item | Result | Evidence |
|---|------|--------|----------|
| a | `dist/maplibre-gl.css` exists | OK | `Test-Path` → True; CSS is self-contained (inline data URIs, no external url() fetches) |
| b | Named exports `Map` and `Marker` on package entry | OK | `package.json` types/exports → `dist/maplibre-gl.d.ts`; export block has `Map$1 as Map` and `Marker`; `declare class Map$1` (d.ts:12904), `declare class Marker` (d.ts:16071) |
| c | Map constructor options `container`, `style`, `attributionControl:{compact:false}` | OK | `MapOptions` (d.ts:12484): `container: HTMLElement \| string`; `attributionControl?: false \| AttributionControlOptions`; `AttributionControlOptions.compact?: boolean` (d.ts:11663) |
| d | `map.on('error'\|'moveend', handler)` | OK | Map class `on<T extends keyof MapEventType>(type, listener): Subscription`; `MapEventType.error: ErrorEvent`, `MapEventType.moveend: MapMovementEvent` (d.ts:9136) |
| e | `map.remove()` | OK | Map class `remove(): this` |
| f | `map.fitBounds([[w,s],[e,n]], {padding, duration})` | OK | `fitBounds(bounds: LngLatBoundsLike, options?: FitBoundsOptions)`; `LngLatBoundsLike = LngLatBounds \| [LngLatLike, LngLatLike] \| [number,number,number,number]`, `LngLatLike` includes `[number, number]`; `FitBoundsOptions` → `FlyToOptions` → `AnimationOptions.duration?: number` and `CameraOptions.padding?: number \| PaddingOptions` |
| g | `map.jumpTo({center, zoom})` | OK | `jumpTo(options: JumpToOptions)`; `JumpToOptions` → `CameraOptions` → `CenterZoomBearing` with `center?: LngLatLike`, `zoom?: number` |
| h | `getBounds()` → getWest/getSouth/getEast/getNorth | OK | Map class `getBounds(): LngLatBounds`; `LngLatBounds` has `getWest(): number; getSouth(): number; getEast(): number; getNorth(): number` |
| i | Marker: `new Marker({})`, `setLngLat([lng,lat])`, `addTo(map)`, `remove()`, `getElement()` → HTMLElement | OK | `constructor(options?: MarkerOptions)` (empty object valid); `setLngLat(lnglat: LngLatLike): this` (`LngLatLike` includes `[number, number]`); `addTo(map: Map$1): this`; `remove(): this`; `getElement(): HTMLElement` |
| j | No popup/setHTML/sanitizer usage in apps/web/src/app | OK | grep `popup\|setHTML\|innerHTML\|DomSanitizer` over `apps/web/src/app` → zero matches |

**Adapter diff: none.** No change to `apps/web/src/app/features/discovery/map/maplibre.adapter.ts` was required — every API used by the adapter exists with compatible semantics in maplibre-gl 6.9.0. This matches the plan's expectation ("expected: no change").

## 5. Local gates (executed on this machine, post-upgrade)

All commands run in `apps/web` unless noted. Full outputs in the sibling `.txt` files (counts and exits summarized here; no secrets or personal data).

| Gate | Command | Exit | Result |
|------|---------|------|--------|
| Unit tests | `$env:CI='true'; npx ng test; $env:CI=$null` | 0 | PASS — 14 test files passed, **44/44 tests passed**, CI mode terminates (no watch hang) |
| Production build | `npx ng build` | 0 | PASS — Application bundle generation complete; Initial total 270.25 kB raw / 73.81 kB transfer; maplibre-gl stays in a lazy chunk (1.06 MB raw / 234.42 kB transfer), consistent with ARC-023 lazy-map; no budget errors |
| Dependency audit gate | `npm audit --omit=dev --audit-level=high` | 0 | PASS — `found 0 vulnerabilities` (see `audit-post-upgrade.txt`); `npm ls maplibre-gl` → `maplibre-gl@6.9.0` (see `audit-post-upgrade-ls.txt`) |
| BFF Maven tests (optional) | from repo root: `.\mvnw.cmd -pl apps/bff -am test "-Dmaven.compiler.release=21"` | 0 | PASS — `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` (see `mvn-test-output.txt`) |

Notes:

- The first Maven attempt was run from `apps/web` (wrong reactor root) and failed with "Could not find the selected project in the reactor"; it was re-run from the repository root and passed. The failed attempt is not evidence-relevant but is disclosed for accuracy.
- The CI workflow uses plain `./mvnw -pl apps/bff -am test` (no `-Dmaven.compiler.release=21`, which is a local-JDK convenience flag only; JDK 25 is provisioned by `actions/setup-java` in CI).

## 6. Files changed (allowed list only)

- `.github/workflows/frontend-tests.yml` (NEW) — `Frontend Tests` workflow: push/PR on `main` with paths `apps/web/**`, `apps/bff/**`, `libraries/**`, `pom.xml`, `mvnw`, workflow file; `permissions: contents: read`; concurrency group `frontend-tests-${{ github.ref }}` with cancel-in-progress; `defaults.run.shell: bash`; job `web` (checkout + setup-node pinned, `npm ci --no-audit --no-fund`, `CI=true npx ng test`, `npx ng build`, `npm audit --omit=dev --audit-level=high` — all in `apps/web`); job `bff` (checkout + setup-java temurin JDK 25 pinned, `./mvnw -pl apps/bff -am test`).
- `apps/web/package.json` — maplibre-gl version line only.
- `apps/web/package-lock.json` — regenerated by `npm install maplibre-gl@^6.9.0`.
- `delivery/evidence/I1-ENG-004/*` — this file and raw command outputs.

Action pins (only these three SHAs, matching existing repo workflows):

- `actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683` # v4.2.2
- `actions/setup-node@cdca7365b2dadb8aad0a33bc7601856ffabcc48e` # v4.3.0
- `actions/setup-java@dded0888837ed1f317902acf8a20df0ad188d165` # v5.0.0

No secrets, no additional permissions.

## 7. Commits

- Claim commit (pre-existing): `73bccf60`
- Implementation commit: **NOT created by the coder agent.** The session's tool-permission rules denied `git add`/`git commit` at STEP 8 (control conflict; no workaround attempted per repository rules). All changes are left in the worktree, unstaged. The orchestrator must perform the pre-commit gate (status/diff review, secret scan, allowedFiles-only staging — never the untracked strays `scripts/dev/run-platform.ps1`, `Φόρτιση*.html`, `Φόρτιση2_files/`, `Φόρτιση_files/`), commit locally, and push. **CI run reference will be added post-push by the orchestrator** (the workflow triggers only on `main` push/PR, so no run exists at implementation time).

Suggested commit message: `I1-ENG-004: frontend CI workflow (web vitest+build+audit gate, bff Maven JDK 25) + maplibre-gl ^6.9.0 upgrade (fixes GHSA-jrc7-96c5-q579 critical) + evidence`

## 8. Residual risks / notes

- maplibre-gl 5→6 is a major upgrade. The adapter's used surface was verified API-by-API against the v6.9.0 typings and the full vitest suite plus production build pass, but browser-runtime behavior (WebGL rendering) is not exercised by unit tests; first real map interaction in a staging environment remains a normal follow-up check.
- The CI workflow itself has not executed yet (requires push; orchestrator-owned). Post-push, the orchestrator should record the first green `Frontend Tests` run reference in this directory.
- Untracked stray files (`scripts/dev/run-platform.ps1`, `Φόρτιση*.html`, `Φόρτιση*_files/`) exist in the worktree; they are NOT part of this task and were never staged.
## 9. CI run reference (post-PR-open, closes MINOR-2)

- PR: #42 (task/i1-eng-004-ci -> main), head dc212d3f
- Run: Frontend Tests, event pull_request, conclusion SUCCESS
  https://github.com/panospao7/Ev-chargind-station/actions/runs/34763965598
- Web job (vitest + prod build + dependency audit): SUCCESS — steps
  Checkout, Setup Node, Install dependencies (npm ci), Unit tests
  (CI=true npx ng test), Production build (budgets), Dependency audit gate
  all success.
- BFF job (Maven tests, JDK 25): SUCCESS — Set up JDK 25, Run BFF suite
  success.
- This is the workflow's first execution (AC-01..04 run reference).
- Section 7 correction (reviewer NOTE-2): the coder's changes were staged
  and committed by the orchestrator as ae72acce after the pre-commit gate;
  the "left unstaged" state was only the interim state at evidence-writing
  time.