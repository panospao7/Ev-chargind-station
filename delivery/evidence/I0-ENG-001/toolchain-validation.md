# I0-ENG-001 — Toolchain Validation Evidence

- Task ID: I0-ENG-001
- Baseline commit: `3d17040635565f3608cfe208d928ca81c35d577f` (post I0-DEL-001 merge, `origin/main`)
- Candidate commit: this task branch; exact SHA recorded in `delivery/status.yaml` at close-out
- Executor: coding agent (ZCode session), local Windows workstation
- Timestamp: 2026-09-09T21:40:00Z
- Limitations: local workstation runs Node v22.12.0 (nvm-for-Windows has no Node 24 installed).
  The engines field (`>=24 <25`) is correctly declared and CI executes Node 24 via
  `node-version-file: .nvmrc`; CI is the authoritative Node-24 gate (AC-10). Local runs produce
  an expected EBADENGINE warning.

## AC-01 — Node 24 declared, lockfile-backed install

| Command | Result |
|---|---|
| `cat .nvmrc` | `24` |
| `package.json` engines | `"node": ">=24.0.0 <25"` |
| `npm ci --no-audit --no-fund` (clean, run twice — after dependency edits and again after final state) | PASS (exit 0); second run re-verified package.json/package-lock.json sync |
| `node --version` (local) | v22.12.0 (see limitation) |
| `node --version` (CI) | v24.x via setup-node `node-version-file: .nvmrc` — verified on PR run |

## AC-02 — No global installs, every tool lockfile-resolved

- `package.json` devDependencies (all exact-pinned, no ranges):
  `@asyncapi/cli 2.13.0`, `@secretlint/secretlint-rule-preset-recommend 8.5.0`,
  `@stoplight/spectral-cli 6.16.3`, `ajv 8.17.1`, `glob 10.4.5`, `js-yaml 4.1.0`, `secretlint 8.5.0`
- No `npx --yes <pkg>@<ver>` floating pins remain in the workflow; CI installs strictly via `npm ci`
  and invokes the same version-controlled npm scripts as local execution (AC-03).
- `ajv-cli` devDependency removed (was unused by any npm script; ajv is used programmatically).
- Version determinism is enforced by `overrides` (see Reproducibility notes).

## AC-03 — Local/CI command parity

CI workflow `.github/workflows/g3-contract-validation.yml` now invokes, per job, exactly:
`npm run contracts:openapi`, `contracts:asyncapi`, `contracts:schemas`, `contracts:registries`,
`contracts:privacy`, `contracts:docs`, `contracts:secrets`, `contracts:self-test` — identical to the
local aggregate `npm run contracts:verify`.

## Reproducibility notes (dependency drift found and pinned)

1. `@asyncapi/cli@3.0.0` has a broken postinstall (`scripts/enableAutoComplete.js` missing from the
   published tarball) — `npm ci` fails. Pinned to `2.13.0`, which installs cleanly and validates the
   AsyncAPI 2.6.0 document (matches the previously proven CI pin).
2. spectral (6.14.3 and 6.16.3) crashes at startup with ajv ≥8.18: ajv's codegen emits invalid
   JavaScript for `ajv-errors@3.0.0` errorMessage compilation (`SyntaxError: Unexpected token ':'`
   in `@stoplight/spectral-functions/dist/alphabetical.js`). Empirically: ajv 8.16.0 BROKEN,
   ajv 8.17.1 OK, 8.18.0/8.20.0 BROKEN (see scripts/contracts toolchain commits for the repro).
   Fixed with a scoped `overrides` entry forcing `ajv 8.17.1` inside `@stoplight/spectral-cli`'s
   subtree; the asyncapi parser's own ajv resolution is untouched.
3. spectral bumped 6.14.3 → 6.16.3 (current release line; validated locally).

Result: PASS
