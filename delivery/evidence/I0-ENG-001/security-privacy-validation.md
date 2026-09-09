# I0-ENG-001 — Security and Privacy Validation Evidence

- Task ID: I0-ENG-001
- Baseline commit: `3d17040635565f3608cfe208d928ca81c35d577f`
- Candidate commit: this task branch; exact SHA recorded in `delivery/status.yaml` at close-out
- Executor: coding agent (ZCode session), local Windows workstation
- Timestamp: 2026-09-09T21:40:00Z

## AC-07 — fail-closed privacy and secret checks

### Privacy / data-minimization scan (`npm run contracts:privacy`)

- Result on real repository: PASS (exit 0), 24 contract files scanned.
- The scanner now scans per line (previously a whole-file `/example|description|x-/`
  exemption could hide real findings elsewhere in a file that mentioned "example" once).
- Fail-closed negative fixtures (self-test):
  - Discovery contract containing a `driverId` parameter → FAIL with "subject identifier" (exit 1) ✓
  - Schema declaring an unannotated `password` property → FAIL with "sensitive field" (exit 1) ✓
- Precision refinement (documented, not a weakening): lines whose content is a
  `type:`/`scheme:` declaration are exempt — they carry specification vocabulary
  (e.g. AsyncAPI `type: userPassword` for RabbitMQ auth), not credential values.
  Field keys (`password:`) and free text remain scanned. The negative fixtures above
  continue to fail, proving the rule is live.
- Prohibited Discovery subject identifiers (`accountref`, `driverid`, `subjectid`,
  `driverref`, `accountid`) are enforced against every `*discovery*` contract file.

### Secret scanning (`npm run contracts:secrets`)

- secretlint 8.5.0 with the committed rule config `scripts/contracts/secretlintrc.json`
  (preset-recommend), invoked with the correct `--secretlintrc` flag.
- Result on real repository: PASS (exit 0), zero findings.
- Fail-closed negative fixtures (self-test): a file containing a pattern-valid AWS access
  key id fails the run (exit 1) ✓; a clean file passes ✓.
- The previous workflow ran secretlint with `|| true` ("non-blocking") — that suppression
  is removed; the gate now fails the workflow on any secret finding.

## Workflow hardening (AC-04)

- All external actions remain pinned to full immutable commit SHAs
  (checkout `11bd7190…` v4.2.2, setup-node `cdca7365…` v4.3.0).
- Workflow permissions reduced to `contents: read` (the `checks: write` grant was unused
  and dropped — least privilege).
- Node version sourced from the committed `.nvmrc` (`node-version-file`), eliminating the
  env-var/local drift vector.
- New `self-test` job proves in CI that every validator category fails closed on
  controlled invalid fixtures.

## Prohibited data check

No access tokens, client secrets, passwords, private keys, or personal identifiers were
introduced by this task's changes (verified by the passing secret/privacy scans on the
working tree and by review of the diff).

Result: PASS
