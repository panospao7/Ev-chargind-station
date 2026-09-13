# I1-APP-001 — self-check notes (run 4, final stretch)

Working notes for the run-4 test-green pass. Disclosed per task rules:
spec fixes vs. real product fixes.

## Spec-only fixes

- PUB-03 specs + axe spec: test routers lacked `withComponentInputBinding()`;
  production `app.config.ts` already had it. Route shape mirrored the app
  config everywhere except the option flag.
- PUB-02/axe PUB-02 + pub-03 specs: post-flush/post-navigation change
  detection now uses `fixture.whenStable()` (proven pattern in the axe
  PUB-03 test) instead of `TestBed.inject(LocaleService)` no-ops.
- Map spec + list-map-equivalence spec: extra `whenStable()` cycle for the
  ngAfterViewInit → initialize() → then(setResults) chain.
- translate.pipe spec: explicit `detectChanges()` after `setLocale('en')`
  (whenStable resolves with no pending tasks).

## Test-infra fix (not product code)

- `MapPresentationAdapterMock.queueInitialize()` set
  `rejectNextInitialize = true` unconditionally — even a resolved outcome
  rejected, so every initialize() degraded. The mock now classifies the
  outcome asynchronously: resolved stays resolved, rejected re-arms the
  rejection (still re-created per call, never an unhandled rejection).

## Real product fixes (disclosed)

1. `TranslatePipe` was a default (pure) pipe while reading the locale
   signal inside `transform`. Pure pipes cache by input identity, so a
   runtime locale switch never re-translated — the pipe violated its own
   documented contract ("a locale switch must re-translate"). Minimal fix:
   `@Pipe({ name: 'translate', pure: false })`. This is the standard
   Angular pattern for pipes reading external mutable/signal state.
2. `Pub02SearchComponent` aria-live notice: the invalid-value strip
   navigation re-runs `parseParams` with the sanitized URL, which set
   `invalidNotice` back to `false` in the same stability window — the
   announcement was removed before it could be perceived, violating
   ARC-023 §9.2 ("invalid values are removed and announced") and the
   spec assertion "strips invalid query values, announces it via
   aria-live, and still renders results". Minimal fix: when the re-parse
   is the strip navigation's own echo (URL already sanitized), keep the
   announcement instead of clearing it.

## Additional run-4 findings

- `tsconfig.app.json` excluded only `*.spec.ts`, so `src/testing/run-axe.ts`
  (vitest `expect`) entered the production build → TS2304. Fixed by
  excluding `src/testing/**` from the app build (test infra only).
- el-GR Intl currency ground truth uses U+00A0 (no-break space) before
  `€` ("4,80\u00A0€"); the spec literal used an ASCII space. The pub-03
  spec now asserts against `Intl.NumberFormat` output itself.
- Spec typo: dictionary key `details_total_evses` is "Σύνολο παροχών"
  (genitive); the pub-03 spec asserted nominative "Σύνολο παροχές".
- The `DiscoveryStore.search()` defers the HTTP call via `timer(0)`,
  which Angular's scheduler does not track as a pending task: once
  rendering settles, `whenStable()` resolves on microtasks alone and the
  timer never fires. Specs yield one explicit macrotask
  (`setTimeout 0`) before `expectOne`. Disclosed as a test-timing
  characteristic, not a product defect (dedup/cancel semantics rely on
  the timer turn).

## Final run-4 state

- `npm test` (apps/web): 13/13 files, 38/38 tests PASS (one fewer than
  the prior 39 because the NG0201 "sanity" test block was deleted per
  the approved fix list).
- `npm run build` (apps/web): SUCCESS. Initial 271.49 kB raw /
  74.12 kB transfer; maplibre-gl lazy chunk 1.04 MB / 231.18 kB
  transfer. Pre-existing maplibre CommonJS warning unchanged.
- Repo root `npm run contracts:verify`: exit 0 (21 passed, 0 failed).
- Storage grep (`localStorage|sessionStorage|document.cookie|indexedDB`)
  over apps/web/src: 0 hits.
- `api/v1` grep over apps/web/src excluding app/api/**: 0 hits
  (3 hits inside the sanctioned adapter constant).
- No commits made; all changes left uncommitted for human review.
