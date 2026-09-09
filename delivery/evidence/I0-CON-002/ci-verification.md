# I0-CON-002 — Registry Rebuild and Schema Authoring Evidence

- Task ID: I0-CON-002
- Baseline commit: `c86af67ff996fd7329518cff4fb6141fb12f38e4`
- Candidate commit: this task branch; SHA recorded in `delivery/status.yaml` at close-out
- Timestamp: 2026-09-09T22:30:00Z
- Environment: local Windows workstation (Node v22.12.0); CI executes Node 24 from `.nvmrc`

## AC-01 — registries reference existing, compiling schemas

- `npm run contracts:schemas` → exit 0; "Found 42 JSON schema files … 42 schemas compiled
  against the 2020-12 meta-schema".
- `npm run contracts:registries` → exit 0; "38/38 declared schema targets exist".

## AC-02 — registries match approved documents

- lifecycles: 20 entries reproducing DOM-002 §1.1–§1.19 + §1.3a (122 states, 152 transitions,
  0 broken endpoints); ARC-022 §16 prohibitions verified absent.
- problem-codes: 30 codes; 22/22 ARC-022 §16 canonical codes with exact HTTP statuses;
  30 unique; all statuses integer 400–599.
- messages: 38 entries (32 events + 6 commands), unique names, `com.evplatform.*` namespaces,
  producers/consumers per ARC-020 §6/§8, single logical handler per command (ARC-022 §15).
- policies: 19 policies with `sourceRef`; corrected to DOM-002/DOM-004/ARC-006/PRV-001
  (5-minute hold; canonical services; consumption at local commit).

## AC-03 — traceability covers W1

- 42 rows from REQ-001 (31 W1-S1, 11 W1-S2), 0 duplicates, all `status: PLANNED`
  (CON-176: nothing VERIFIED without CI evidence), 0 W1 OPEN.
- `npm run contracts:docs` → exit 0: "42 traced requirements, 42 W1, 0 W1 OPEN".

## AC-04 — full gate

- `npm run contracts:verify` → exit 0 (openapi, asyncapi, schemas, registries, privacy,
  docs, secrets, self-test all pass). Self-test: 21/21 negative/positive fixtures.
- `npm run contracts:asyncapi` → exit 0.
- CI run on this PR is the recorded run reference; after merge, the main-branch run is the
  fully-green evidence that lets the owner close CON-175/176 in GOV-006.

## Limitations

- `commands/start-charging-command.json` (pre-existing) lacks ARC-020 §7 `issuedBy`/`expiresAt`;
  left as-is pending an owner decision (minimal enrichment).
- ARC-020/ARC-022 device-fact divergence resolved in favor of ARC-022 §15 (authoritative for
  W1 message contracts); documented in `02-coder.md`.

Result: PASS
