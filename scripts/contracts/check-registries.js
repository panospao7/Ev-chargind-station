const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const ROOT = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
const contractsDir = path.join(ROOT, 'contracts');
let exitCode = 0;

function loadYaml(relPath) {
  const full = path.join(contractsDir, relPath);
  return yaml.load(fs.readFileSync(full, 'utf8'));
}

function checkMessageRegistry() {
  console.log('--- Message Registry ---');
  const reg = loadYaml('registries/messages-v1.yaml');
  if (!reg.messages || !Array.isArray(reg.messages)) {
    console.error('FAIL: messages-v1.yaml has no messages array');
    exitCode = 1;
    return;
  }
  const names = reg.messages.map(m => m.name);
  const dups = names.filter((n, i) => names.indexOf(n) !== i);
  if (dups.length > 0) {
    console.error('FAIL: Duplicate names:', [...new Set(dups)]);
    exitCode = 1;
  }
  console.log(`  ${names.length} message names are unique`);
  // Registries declare either `type` or the versioned form `versionedType`;
  // both must use the canonical namespace.
  const bad = reg.messages.filter(m => {
    const type = m.versionedType || m.type;
    return !type || !type.startsWith('com.evplatform.');
  });
  if (bad.length > 0) {
    console.error('FAIL: Non-compliant namespaces:', bad.map(m => `${m.name}: ${m.versionedType || m.type || 'missing'}`));
    exitCode = 1;
  }
  console.log(`  ${reg.messages.length} messages use com.evplatform namespace`);
  // Schema references use either `schema` or `schemaPath` (relative to
  // contracts/schemas/); every declared target must exist on disk.
  const missing = reg.messages
    .map(m => ({ name: m.name, ref: m.schemaPath || m.schema }))
    .filter(m => m.ref && !fs.existsSync(path.join(contractsDir, 'schemas', m.ref)));
  if (missing.length > 0) {
    console.error(`FAIL: Missing schemas: ${missing.length} declared schema targets do not exist:`);
    for (const m of missing) {
      console.error(`  - ${m.name}: schemas/${m.ref}`);
    }
    exitCode = 1;
  }
  console.log(`  ${reg.messages.length - missing.length}/${reg.messages.length} declared schema targets exist`);
  const noHandler = reg.messages.filter(m => m.command && !m.handler);
  if (noHandler.length > 0) {
    console.error('FAIL: Commands without handler:', noHandler.map(m => m.name));
    exitCode = 1;
  }
  console.log('  Command handlers checked');
  if (!reg.release_waves) {
    console.error('FAIL: Missing release_waves');
    exitCode = 1;
  }
  console.log('  Release waves defined');
  if (!reg['x-data-classification']) {
    console.warn('  WARN: No x-data-classification defined');
  }
  console.log('  Message registry checks passed');
}

function checkProblemCodes() {
  console.log('--- Problem Codes Registry ---');
  const reg = loadYaml('registries/problem-codes-v1.yaml');
  if (!reg.problemCodes || !Array.isArray(reg.problemCodes)) {
    console.error('FAIL: problem-codes-v1.yaml has no problemCodes array');
    exitCode = 1;
    return;
  }
  const seen = {};
  reg.problemCodes.forEach(c => {
    if (seen[c.code]) {
      console.error(`FAIL: Duplicate code: ${c.code} (a problem code maps to exactly one HTTP status)`);
      exitCode = 1;
    }
    seen[c.code] = true;
  });
  console.log(`  ${reg.problemCodes.length} problem codes are unique`);
  reg.problemCodes.forEach(c => {
    if (!c.httpStatus || !Number.isInteger(c.httpStatus) || c.httpStatus < 400 || c.httpStatus > 599) {
      console.error(`FAIL: Code ${c.code} has invalid httpStatus: ${JSON.stringify(c.httpStatus)}`);
      exitCode = 1;
    }
  });
  console.log('  All codes have a valid HTTP error status');
}

function checkLifecycles() {
  console.log('--- Lifecycle Registry ---');
  const reg = loadYaml('registries/lifecycles-v1.yaml');
  if (!reg.lifecycles || !Array.isArray(reg.lifecycles)) {
    console.error('FAIL: lifecycles-v1.yaml has no lifecycles array');
    exitCode = 1;
    return;
  }
  const lifecycleNames = new Set();
  reg.lifecycles.forEach(a => {
    if (!a.name) {
      console.error('FAIL: lifecycle without name');
      exitCode = 1;
      return;
    }
    if (lifecycleNames.has(a.name)) {
      console.error(`FAIL: Duplicate lifecycle name: ${a.name}`);
      exitCode = 1;
    }
    lifecycleNames.add(a.name);
    const stateNames = new Set((a.states || []).map(s => s.name));
    (a.permittedTransitions || []).forEach(t => {
      if (!stateNames.has(t.from)) {
        console.error(`FAIL: ${a.name}: unknown source state "${t.from}"`);
        exitCode = 1;
      }
      if (!stateNames.has(t.to)) {
        console.error(`FAIL: ${a.name}: unknown target state "${t.to}"`);
        exitCode = 1;
      }
    });
    console.log(`  ${a.name}: ${(a.states || []).length} states, ${(a.permittedTransitions || []).length} transitions valid`);
  });
}

function checkPolicies() {
  console.log('--- Policy Registry ---');
  const reg = loadYaml('registries/policies-v1.yaml');
  if (!reg.policies || !Array.isArray(reg.policies)) {
    console.error('FAIL: policies-v1.yaml has no policies array');
    exitCode = 1;
    return;
  }
  const seen = new Set();
  let invalid = 0;
  for (const p of reg.policies) {
    if (!p.policyId) {
      console.error('FAIL: policy without policyId');
      exitCode = 1;
      invalid++;
      continue;
    }
    if (seen.has(p.policyId)) {
      console.error(`FAIL: Duplicate policyId: ${p.policyId}`);
      exitCode = 1;
      invalid++;
    }
    seen.add(p.policyId);
    if (!p.description) {
      console.error(`FAIL: Policy ${p.policyId} missing description`);
      exitCode = 1;
      invalid++;
    }
  }
  console.log(`  ${reg.policies.length} policies checked, ${reg.policies.length - invalid} valid`);
}

function checkTraceability() {
  console.log('--- Traceability Registry ---');
  const reg = loadYaml('registries/traceability-v1.yaml');
  const items = reg && (reg.requirements || reg.traceability);
  if (!Array.isArray(items)) {
    console.error('FAIL: traceability-v1.yaml has no requirements array');
    exitCode = 1;
    return;
  }
  const seen = new Set();
  let w1Open = 0;
  let invalid = 0;
  for (const item of items) {
    const id = item && (item.requirementId || item.id);
    if (!id) {
      console.error('FAIL: traceability row without requirementId');
      exitCode = 1;
      invalid++;
      continue;
    }
    if (seen.has(id)) {
      console.error(`FAIL: Duplicate traceability requirement: ${id}`);
      exitCode = 1;
      invalid++;
    }
    seen.add(id);
    const wave = String(item.releaseApplicability || item.releaseWave || '');
    if (wave.startsWith('W1') && item.status === 'OPEN') {
      console.error(`FAIL: W1 requirement still OPEN: ${id}`);
      exitCode = 1;
      w1Open++;
    }
  }
  console.log(`  ${items.length} requirements checked, ${invalid} invalid, ${w1Open} W1 open`);
}

checkMessageRegistry();
checkProblemCodes();
checkLifecycles();
checkPolicies();
checkTraceability();

process.exit(exitCode);
