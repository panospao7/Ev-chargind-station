#!/usr/bin/env node
// G3 self-test: proves every validator category fails closed on controlled
// invalid fixtures and passes controlled valid ones (I0-ENG-001 AC-09).
// Fixture trees are generated into a temp dir; each node validator receives
// the fixture root as argv[2]; CLI validators receive explicit file paths.

import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..', '..');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'g3-selftest-'));
// secretlint resolves targets against the process cwd and rejects paths
// outside it, so its fixtures live inside the repo and are removed after
// the run.
const localTmp = path.join(repoRoot, '.g3-selftest');
fs.mkdirSync(localTmp, { recursive: true });
let passes = 0;
let failures = 0;

function writeTree(root, files) {
  for (const [rel, content] of Object.entries(files)) {
    const full = path.join(root, rel);
    fs.mkdirSync(path.dirname(full), { recursive: true });
    fs.writeFileSync(full, content);
  }
}

function runNodeValidator(script, root) {
  return spawnSync(process.execPath, [path.join(scriptDir, script), root], { encoding: 'utf8' });
}

function runBin(cmd, args) {
  // shell:true concatenates args unquoted, so quote any arg containing
  // whitespace (the repo path itself contains spaces).
  const quoted = args.map(a => (/\s/.test(a) ? `"${a}"` : a));
  return spawnSync(cmd, quoted, { encoding: 'utf8', shell: true, cwd: repoRoot });
}

function record(name, proc, expectFail, needle) {
  const exitedNonzero = proc.status !== 0;
  let ok = expectFail ? exitedNonzero : !exitedNonzero;
  let extra = '';
  if (ok && needle) {
    const out = String(proc.stdout || '') + String(proc.stderr || '');
    if (!out.includes(needle)) {
      ok = false;
      extra = ` (output missing "${needle}")`;
    }
  }
  if (ok) {
    passes++;
    console.log(`  PASS: ${name}`);
  } else {
    failures++;
    console.error(`  FAIL: ${name}${extra} [exit ${proc.status}]`);
    const out = (String(proc.stdout || '') + String(proc.stderr || '')).trim();
    if (out) console.error(out.split(/\r?\n/).slice(0, 6).map(l => '        ' + l).join('\n'));
  }
}

// ---------- shared fixture content ----------

const TRACE_VALID = `requirements:
  - id: REQ-W1-001
    releaseApplicability: W1
    status: CLOSED
`;

function registriesTree(overrides = {}) {
  return {
    'contracts/registries/messages-v1.yaml': `messages:
  - name: ChargingStarted
    versionedType: com.evplatform.events.ChargingStarted
    schemaPath: events/charging-session-started-event.json
  - name: StartCharging
    versionedType: com.evplatform.commands.StartCharging
    command: true
    handler: booking-session-service
    schemaPath: commands/start-charging-command.json
release_waves:
  W1: true
x-data-classification: public
`,
    'contracts/registries/problem-codes-v1.yaml': `problemCodes:
  - code: BOOKING_CONFLICT
    httpStatus: 409
  - code: SESSION_NOT_FOUND
    httpStatus: 404
`,
    'contracts/registries/lifecycles-v1.yaml': `lifecycles:
  - name: Booking
    states:
      - name: CREATED
      - name: CONFIRMED
    permittedTransitions:
      - from: CREATED
        to: CONFIRMED
`,
    'contracts/registries/policies-v1.yaml': `policies:
  - policyId: AUTH-CONSUMPTION-001
    description: A StartAuthorization is consumed exactly once.
`,
    'contracts/registries/traceability-v1.yaml': TRACE_VALID,
    'contracts/schemas/events/charging-session-started-event.json': JSON.stringify({ $schema: 'https://json-schema.org/draft/2020-12/schema', $id: 'https://evplatform.example/schemas/events/charging-session-started-event.json', type: 'object' }),
    'contracts/schemas/commands/start-charging-command.json': JSON.stringify({ $schema: 'https://json-schema.org/draft/2020-12/schema', $id: 'https://evplatform.example/schemas/commands/start-charging-command.json', type: 'object' }),
    ...overrides
  };
}

// ---------- 1. JSON Schema validator ----------

console.log('[category] JSON Schema validator (validate-schemas.js)');

{
  const root = path.join(tmp, 'schemas-valid');
  writeTree(root, {
    'contracts/schemas/a.json': JSON.stringify({ $schema: 'https://json-schema.org/draft/2020-12/schema', $id: 'https://evplatform.example/schemas/a.json', type: 'object' })
  });
  record('valid schema tree passes', runNodeValidator('validate-schemas.js', root), false);
}
{
  const root = path.join(tmp, 'schemas-invalid-ref');
  writeTree(root, {
    'contracts/schemas/a.json': JSON.stringify({ $schema: 'https://json-schema.org/draft/2020-12/schema', $id: 'https://evplatform.example/schemas/a.json', type: 'object', '$ref': '../missing.json' })
  });
  record('unresolved relative $ref fails', runNodeValidator('validate-schemas.js', root), true, 'Unresolved $ref');
}
{
  const root = path.join(tmp, 'schemas-invalid-dup-id');
  writeTree(root, {
    'contracts/schemas/a.json': JSON.stringify({ $schema: 'https://json-schema.org/draft/2020-12/schema', $id: 'https://evplatform.example/schemas/dup.json', type: 'object' }),
    'contracts/schemas/b.json': JSON.stringify({ $schema: 'https://json-schema.org/draft/2020-12/schema', $id: 'https://evplatform.example/schemas/dup.json', type: 'object' })
  });
  record('duplicate $id fails', runNodeValidator('validate-schemas.js', root), true, 'Duplicate $id');
}
{
  const root = path.join(tmp, 'schemas-invalid-metaschema');
  writeTree(root, {
    'contracts/schemas/a.json': JSON.stringify({ $id: 'https://evplatform.example/schemas/a.json', type: 'object' })
  });
  record('missing $schema fails', runNodeValidator('validate-schemas.js', root), true, 'missing $schema');
}

// ---------- 2. Registry validator ----------

console.log('[category] Registry validator (check-registries.js)');

{
  const root = path.join(tmp, 'registries-valid');
  writeTree(root, registriesTree());
  record('valid registries pass', runNodeValidator('check-registries.js', root), false);
}
{
  const root = path.join(tmp, 'registries-invalid-dup-message');
  writeTree(root, registriesTree({
    'contracts/registries/messages-v1.yaml': `messages:
  - name: ChargingStarted
    versionedType: com.evplatform.events.ChargingStarted
    schemaPath: events/charging-session-started-event.json
  - name: ChargingStarted
    versionedType: com.evplatform.events.ChargingStarted
    schemaPath: events/charging-session-started-event.json
release_waves:
  W1: true
`
  }));
  record('duplicate message name fails', runNodeValidator('check-registries.js', root), true, 'Duplicate names');
}
{
  const root = path.join(tmp, 'registries-invalid-missing-schema');
  writeTree(root, registriesTree({
    'contracts/registries/messages-v1.yaml': `messages:
  - name: ChargingStarted
    versionedType: com.evplatform.events.ChargingStarted
    schemaPath: events/does-not-exist.json
release_waves:
  W1: true
`
  }));
  record('missing schema reference fails', runNodeValidator('check-registries.js', root), true, 'Missing schemas');
}
{
  const root = path.join(tmp, 'registries-invalid-dup-problem-code');
  writeTree(root, registriesTree({
    'contracts/registries/problem-codes-v1.yaml': `problemCodes:
  - code: BOOKING_CONFLICT
    httpStatus: 409
  - code: BOOKING_CONFLICT
    httpStatus: 500
`
  }));
  record('conflicting duplicate problem code fails', runNodeValidator('check-registries.js', root), true, 'Duplicate code');
}
{
  const root = path.join(tmp, 'registries-invalid-lifecycle-state');
  writeTree(root, registriesTree({
    'contracts/registries/lifecycles-v1.yaml': `lifecycles:
  - name: Booking
    states:
      - name: CREATED
    permittedTransitions:
      - from: CREATED
        to: CONFIRMED
`
  }));
  record('unknown lifecycle transition state fails', runNodeValidator('check-registries.js', root), true, 'unknown target state');
}

// ---------- 3. Privacy / security scan ----------

console.log('[category] Privacy and security scan (security-scan.js)');

{
  const root = path.join(tmp, 'security-valid');
  writeTree(root, {
    'contracts/openapi/clean-api-v1.yaml': `openapi: 3.0.3
info:
  title: Clean API
  version: 1.0.0
paths: {}
`
  });
  record('clean contracts pass', runNodeValidator('security-scan.js', root), false);
}
{
  const root = path.join(tmp, 'security-invalid-subject-id');
  writeTree(root, {
    'contracts/openapi/public-discovery-api-v1.yaml': `openapi: 3.0.3
info:
  title: Discovery API
  version: 1.0.0
paths:
  /search:
    get:
      parameters:
        - name: driverId
          in: query
          schema:
            type: string
      responses:
        '200':
          description: ok
`
  });
  record('Discovery subject identifier fails', runNodeValidator('security-scan.js', root), true, 'subject identifier');
}
{
  const root = path.join(tmp, 'security-invalid-sensitive-field');
  writeTree(root, {
    'contracts/openapi/some-api-v1.yaml': `openapi: 3.0.3
info:
  title: Some API
  version: 1.0.0
paths: {}
components:
  schemas:
    Login:
      type: object
      properties:
        password:
          type: string
`
  });
  record('unannotated sensitive field fails', runNodeValidator('security-scan.js', root), true, 'sensitive field');
}

// ---------- 4. Documentation consistency ----------

console.log('[category] Documentation consistency (check-docs.js)');

const DOCS_VALID = {
  'docs/00_governance/06_contradiction_and_resolution_register_v1.0.md': '# Register\n\nNo open contradictions.\n',
  'contracts/registries/traceability-v1.yaml': TRACE_VALID
};
{
  const root = path.join(tmp, 'docs-valid');
  writeTree(root, DOCS_VALID);
  record('clean docs pass', runNodeValidator('check-docs.js', root), false);
}
{
  const root = path.join(tmp, 'docs-invalid-open-contradiction');
  writeTree(root, {
    ...DOCS_VALID,
    'docs/00_governance/06_contradiction_and_resolution_register_v1.0.md': '# Register\n\nC-001 status: **OPEN** W1-critical gap.\n'
  });
  record('OPEN contradiction fails', runNodeValidator('check-docs.js', root), true, 'W1-critical gaps still OPEN');
}
{
  const root = path.join(tmp, 'docs-invalid-w1-open');
  writeTree(root, {
    ...DOCS_VALID,
    'contracts/registries/traceability-v1.yaml': `requirements:
  - id: REQ-W1-002
    releaseApplicability: W1
    status: OPEN
`
  });
  record('OPEN W1-critical requirement fails', runNodeValidator('check-docs.js', root), true, 'still OPEN');
}

// ---------- 5. OpenAPI (Spectral) ----------

console.log('[category] OpenAPI validation (spectral, error severity fails the run)');

const SPECTRAL_ARGS = ['--no-install', 'spectral', 'lint', '--fail-severity=error', '-r', path.join(repoRoot, '.spectral.yaml')];
{
  const file = path.join(tmp, 'openapi-valid.yaml');
  fs.writeFileSync(file, `openapi: 3.0.3\ninfo:\n  title: Fixture API\n  version: 1.0.0\n  description: Minimal valid fixture\npaths: {}\n`);
  record('valid OpenAPI passes', runBin('npx', [...SPECTRAL_ARGS, file]), false);
}
{
  const file = path.join(tmp, 'openapi-invalid.yaml');
  fs.writeFileSync(file, `openapi: 3.0.3\ninfo:\n  version: 1.0.0\npaths: {}\n`);
  record('OpenAPI missing info.title fails', runBin('npx', [...SPECTRAL_ARGS, file]), true);
}

// ---------- 6. AsyncAPI ----------

console.log('[category] AsyncAPI validation (AsyncAPI CLI)');

{
  const file = path.join(tmp, 'asyncapi-valid.yaml');
  fs.writeFileSync(file, `asyncapi: 2.6.0\ninfo:\n  title: Platform Messaging\n  version: 1.0.0\nchannels:\n  booking.events:\n    publish:\n      operationId: onBookingEvent\n      message:\n        name: BookingConfirmed\n        payload:\n          type: object\n`);
  record('valid AsyncAPI passes', runBin('npx', ['--no-install', 'asyncapi', 'validate', file]), false);
}
{
  const file = path.join(tmp, 'asyncapi-invalid.yaml');
  fs.writeFileSync(file, `asyncapi: 2.6.0\nchannels: {}\n`);
  record('AsyncAPI missing info fails', runBin('npx', ['--no-install', 'asyncapi', 'validate', file]), true);
}

// ---------- 7. Secret scanning (secretlint, fail closed) ----------

console.log('[category] Secret scanning (secretlint)');

const SECRETLINT_CONFIG = path.join(repoRoot, 'scripts', 'contracts', 'secretlintrc.json');
function secretlintTarget(file) {
  return path.relative(repoRoot, file).split(path.sep).join('/');
}
{
  const file = path.join(localTmp, 'secrets-clean.yaml');
  fs.writeFileSync(file, `openapi: 3.0.3\ninfo:\n  title: Clean\n  version: 1.0.0\npaths: {}\n`);
  record('clean file passes secretlint', runBin('npx', ['--no-install', 'secretlint', '--secretlintrc', SECRETLINT_CONFIG, secretlintTarget(file)]), false);
}
{
  const file = path.join(localTmp, 'secrets-invalid.yaml');
  // Pattern-valid AWS access key id — deliberately NOT one of AWS's
  // documented example keys, which secretlint's AWS rule allowlists.
  fs.writeFileSync(file, `credentials:\n  aws_access_key_id: AKIAJ7XYD5KG2PLQRTSA\n`);
  record('embedded AWS access key fails', runBin('npx', ['--no-install', 'secretlint', '--secretlintrc', SECRETLINT_CONFIG, secretlintTarget(file)]), true);
}

// ---------- summary ----------

fs.rmSync(tmp, { recursive: true, force: true });
fs.rmSync(localTmp, { recursive: true, force: true });
console.log(`\n${passes} passed, ${failures} failed`);
process.exit(failures === 0 ? 0 : 1);
