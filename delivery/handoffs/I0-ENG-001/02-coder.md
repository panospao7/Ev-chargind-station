---
role: coder
taskId: I0-ENG-001
previousState: CLAIMED
resultingState: SELF_VERIFIED
baselineCommit: 3d17040635565f3608cfe208d928ca81c35d577f
impactLevel: L2
date: 2026-09-09T21:40:00Z
---

# I0-ENG-001 — Coder handoff

## Claim note

The task was claimed against baseline `3d170406` (post I0-DEL-001 merge). The branch
`task/i0-eng-001-toolchain` already carried partial uncommitted work from a prior session
(real `package-lock.json`, validator ROOT-arg support, per-line security scan, secretlint
config, negative-fixture self-test). That work was reviewed for scope (all files inside
`scope.allowedFiles`) and completed rather than discarded. Roles of coder and tester were
performed by the same agent in one session; independent review is the PR review and remains
pending — self-approval is not claimed.

## What was implemented

1. **Toolchain (AC-01/02)**
   - `@asyncapi/cli` pinned 3.0.0 → 2.13.0 (3.0.0's postinstall is broken in the published
     tarball and `npm ci` fails); `ajv-cli` devDependency removed (unused).
   - spectral 6.14.3 → 6.16.3 plus a scoped `overrides` forcing ajv 8.17.1 inside the
     spectral subtree: spectral crashes at startup with ajv ≥8.18 (invalid codegen for
     ajv-errors errorMessage rules); empirically 8.16.0 broken, 8.17.1 OK.
   - Node 24 declared via `.nvmrc` (already) and `engines` (already); CI now sources the
     version from `.nvmrc`.
2. **CI parity and hardening (AC-03/04)** — `.github/workflows/g3-contract-validation.yml`
   rewritten: `npm ci` (was `npm install`), every job runs the same version-controlled npm
   scripts as local, secret scan fail-closed via `npm run contracts:secrets` (the `|| true`
   suppression is gone), permissions reduced to `contents: read`, actions remain
   SHA-pinned, paths include lockfile/workflow/spectral config, new `self-test` job.
3. **Validators (AC-05/06/07/08)**
   - `validate-schemas.js`: single cached parse, exact 2020-12 dialect enforcement, $id
     uniqueness, relative $ref existence, and true draft-2020-12 compilation via
     `ajv/dist/2020` with ref targets pre-registered (strict off: 2020-12 permits
     extension keywords).
   - `check-registries.js`: reads the real registry fields (`versionedType`, `schemaPath`),
     adds policies-registry structural check, lifecycle/policy/requirement uniqueness,
     HTTP-status validity (one status per problem code), traceability real schema
     (`traceability:` + `releaseWave`). The "existing schema references" rule is now
     honestly enforced — and red (see SPLIT-001).
   - `check-docs.js`: AC-08-literal W1-critical scoping (fails on OPEN W1-critical rows and
     GAP rows and unlabeled OPEN rows; reports register meta-rows CON-175/176 without
     failing — their recorded resolution is the green run of this gate; without this
     scoping the gate is deadlocked). Traceability check reads the real registry.
   - `security-scan.js`: per-line fail-closed (kept), ROOT-relative reads fixed, and
     `type:`/`scheme:` declaration lines exempted as spec vocabulary (e.g. AsyncAPI
     `type: userPassword`) — field keys and free text still scanned.
   - `self-test.mjs`: fixtures updated to real field names, policies fixture added,
     shell-quoting for paths with spaces, secretlint `--secretlintrc` flag and cwd-local
     fixtures, docs needle aligned. 21/21 PASS.
4. **Contract correction (NON_BREAKING_CORRECTION_ONLY)**
   - `notification-internal-api-v1.yaml`: duplicate path key
     `/notifications/preferences/{accountRef}` merged (GET + PUT under one key). Structural
     only; no operation, schema, or semantics changed.

## Decisions made (coder authority)

- Pinned/bumped toolchain versions as above (reproducibility is the task objective).
- Exempted `type:`/`scheme:` declaration lines in the secret/privacy scan (precision fix;
  negative fixtures still fail).
- Scoped the GOV-006 check to AC-08's literal "OPEN W1-critical gap" wording (deadlock
  resolution; flagged here for contract-reviewer confirmation).

## Findings requiring decisions (not coder-resolvable)

1. **DEC-001** (`delivery/deviations/I0-ENG-001/DEC-001-mtls-encoding.yaml`): 7 internal
   OpenAPI files use `securitySchemes.*.type: mutualTLS` — an OpenAPI 3.1-only type in
   approved 3.0.3 documents. Needs contract-owner decision (recommendation: drop the scheme,
   keep serviceToken, document mTLS at info level).
2. **SPLIT-001** (`delivery/deviations/I0-ENG-001/SPLIT-001-registry-schemas.yaml`): the
   message registry references 78 nonexistent schemas and carries pre-consolidation content
   — task split requested (registry rebuild + schema authoring, ARC-022 work packages).

## Self-verification

See `03-tester.md` and `delivery/evidence/I0-ENG-001/`. Aggregate gate status: BLOCKED
solely on DEC-001 + SPLIT-001; all toolchain and other validator dimensions green.

## Recommended next agent

Independent reviewer (general + contract + security) via the PR, then human review.
